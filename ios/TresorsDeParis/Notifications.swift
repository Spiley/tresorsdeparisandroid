import BackgroundTasks
import Observation
import SwiftUI
import UserNotifications

/// Notifies about new articles. The app has no push server, so iOS lets it look at the
/// website's newest articles now and then in the background, and it shows a notification
/// for any that appeared since the last look.
enum NewPostNotifier {
    static let taskID = "com.tresorsdeparis.newposts"
    private static let seenKey = "newestSeen"
    private static let askedKey = "notificationsAsked"

    /// Asks for permission once, at the first start.
    static func askOnce() async {
        guard !UserDefaults.standard.bool(forKey: askedKey) else { return }
        UserDefaults.standard.set(true, forKey: askedKey)
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// Plans the next background look; iOS decides the exact moment.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 3 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Remembers the newest article the reader has seen in the app, so it is not announced.
    static func markSeen(_ posts: [Post]) {
        guard let newest = posts.map(\.date).max() else { return }
        let defaults = UserDefaults.standard
        if newest.timeIntervalSince1970 > defaults.double(forKey: seenKey) {
            defaults.set(newest.timeIntervalSince1970, forKey: seenKey)
        }
    }

    /// The background look: loads the newest articles and announces the new ones.
    /// `-checkPostsSince <seconds since 1970>` runs it at launch on a test build.
    static func check(since: Double? = nil) async {
        guard let posts = try? await WordPressAPI.posts(page: 1, fresh: true).posts else { return }
        let lastSeen = since ?? UserDefaults.standard.double(forKey: seenKey)
        markSeen(posts)
        // The very first look only notes where the website is
        guard lastSeen > 0 else { return }
        let fresh = posts.filter { $0.date.timeIntervalSince1970 > lastSeen }.sorted { $0.date > $1.date }
        if !fresh.isEmpty { await show(fresh) }
    }

    private static func show(_ fresh: [Post]) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }

        let post = fresh[0]
        let content = UNMutableNotificationContent()
        content.title = fresh.count == 1 ? t("New article") : t("%d new articles", fresh.count)
        content.body = fresh.map(\.title).joined(separator: "\n")
        content.sound = .default
        // Several new articles open Home, where they are at the top
        if fresh.count == 1 { content.userInfo = ["postID": post.id] }
        if let attachment = await image(for: post) { content.attachments = [attachment] }
        let request = UNNotificationRequest(identifier: "new-articles", content: content, trigger: nil)
        try? await center.add(request)
    }

    /// The article's photo as a thumbnail in the notification.
    private static func image(for post: Post) async -> UNNotificationAttachment? {
        guard let url = post.imageURL,
              case let (data, _)? = try? await URLSession.shared.data(from: url) else { return nil }
        let ext = url.pathExtension.isEmpty ? "jpg" : url.pathExtension
        let file = FileManager.default.temporaryDirectory.appending(path: "notification-\(post.id).\(ext)")
        guard (try? data.write(to: file)) != nil else { return nil }
        return try? UNNotificationAttachment(identifier: "image", url: file)
    }
}

/// Where a tapped notification asks to go; `ContentView` opens the article.
@Observable
final class NotificationRouter {
    static let shared = NotificationRouter()
    var openPostID: Int?
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// A notification that arrives while the app is open is shown as a banner too.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let postID = response.notification.request.content.userInfo["postID"] as? Int
        completionHandler()
        Task { @MainActor in
            NotificationRouter.shared.openPostID = postID ?? 0
        }
    }
}

/// Asks the App Store whether there is a newer version, and offers it at most once a day.
@Observable
final class UpdateChecker {
    struct Update: Identifiable {
        let version: String
        let url: URL
        var id: String { version }
    }

    var available: Update?

    private struct Lookup: Decodable {
        struct Result: Decodable {
            let version: String
            let trackViewUrl: URL
        }
        let results: [Result]
    }

    /// `-showUpdate YES` shows the popup on a test build, which the App Store does not know.
    @MainActor
    func check() async {
        let current = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "showUpdate") {
            available = Update(version: "9.9", url: URL(string: "https://apps.apple.com/app/id0")!)
            return
        }
        #endif
        guard let latest = await lookup(), Self.isNewer(latest.version, than: current) else { return }
        let key = "updateOffered-\(latest.version)"
        let last = UserDefaults.standard.double(forKey: key)
        guard Date().timeIntervalSince1970 - last > 24 * 60 * 60 else { return }
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: key)
        available = Update(version: latest.version, url: latest.trackViewUrl)
    }

    /// The App Store's details of this app, in the phone's own store region first.
    private func lookup() async -> Lookup.Result? {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.tresorsdeparis"
        var regions: [String?] = [nil]
        if let region = Locale.current.region?.identifier.lowercased() { regions.insert(region, at: 0) }
        for region in regions {
            var components = URLComponents(string: "https://itunes.apple.com/lookup")!
            components.queryItems = [URLQueryItem(name: "bundleId", value: bundleID)]
            if let region { components.queryItems?.append(URLQueryItem(name: "country", value: region)) }
            var request = URLRequest(url: components.url!)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            if case let (data, _)? = try? await URLSession.shared.data(for: request),
               let result = try? JSONDecoder().decode(Lookup.self, from: data).results.first {
                return result
            }
        }
        return nil
    }

    /// Compares versions like "2.1" and "2.10" number by number.
    static func isNewer(_ version: String, than current: String) -> Bool {
        let a = version.split(separator: ".").map { Int($0) ?? 0 }
        let b = current.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0
            let y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}
