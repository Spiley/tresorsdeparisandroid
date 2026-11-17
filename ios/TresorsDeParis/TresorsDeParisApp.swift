import SwiftUI

@main
struct TresorsDeParisApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var favorites = FavoritesStore()
    @State private var trips = TripStore()

    init() {
        // A larger cache keeps articles and images available when offline
        URLCache.shared = URLCache(memoryCapacity: 20_000_000, diskCapacity: 200_000_000)

        // Serif navigation titles echo the editorial look of the website
        let appearance = UINavigationBar.appearance()
        if let large = UIFont.systemFont(ofSize: 34, weight: .bold).fontDescriptor.withDesign(.serif) {
            appearance.largeTitleTextAttributes = [.font: UIFont(descriptor: large, size: 34)]
        }
        if let inline = UIFont.systemFont(ofSize: 17, weight: .semibold).fontDescriptor.withDesign(.serif) {
            appearance.titleTextAttributes = [.font: UIFont(descriptor: inline, size: 17)]
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(favorites)
                .environment(trips)
                .task {
                    await NewPostNotifier.askOnce()
                    #if DEBUG
                    let since = UserDefaults.standard.double(forKey: "checkPostsSince")
                    if since > 0 { await NewPostNotifier.check(since: since) }
                    #endif
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { NewPostNotifier.schedule() }
        }
        .backgroundTask(.appRefresh(NewPostNotifier.taskID)) {
            NewPostNotifier.schedule()
            await NewPostNotifier.check()
        }
    }
}
