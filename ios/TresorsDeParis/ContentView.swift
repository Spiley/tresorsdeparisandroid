import SwiftUI

struct ContentView: View {
    // `-startTab N` as a launch argument opens another tab (used for simulator screenshots)
    @State private var selectedTab = UserDefaults.standard.integer(forKey: "startTab")
    @State private var router = NotificationRouter.shared
    @State private var updates = UpdateChecker()
    /// The article of a tapped notification
    @State private var notifiedPost: Post?
    @Environment(\.openURL) private var openURL

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem { Label(t("Home"), systemImage: "house") }
                .tag(0)
            MapView()
                .tabItem { Label(t("Map"), systemImage: "map") }
                .tag(1)
            MenuView()
                .tabItem { Label(t("Menu"), systemImage: "line.3.horizontal") }
                .tag(2)
            SearchView()
                .tabItem { Label(t("Search"), systemImage: "magnifyingglass") }
                .tag(3)
            FavoritesView()
                .tabItem { Label(t("Favorites"), systemImage: "heart") }
                .tag(4)
        }
        .sheet(item: $notifiedPost) { post in
            NavigationStack {
                ArticleView(post: post)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button(t("Close")) { notifiedPost = nil }
                        }
                    }
            }
        }
        .onChange(of: router.openPostID, initial: true) { _, id in
            // 0 means several new articles: those are at the top of Home
            guard let id else { return }
            router.openPostID = nil
            selectedTab = 0
            guard id > 0 else { return }
            Task {
                if let post = try? await WordPressAPI.post(id: id) { notifiedPost = post }
            }
        }
        .task { await updates.check() }
        .alert(
            t("Update available"),
            isPresented: Binding(get: { updates.available != nil }, set: { if !$0 { updates.available = nil } }),
            presenting: updates.available
        ) { update in
            Button(t("Later"), role: .cancel) {}
            Button(t("Update")) { openURL(update.url) }
                .keyboardShortcut(.defaultAction)
        } message: { update in
            Text(t("Version %s of Trésors de Paris is available. Update now to get the latest improvements.", update.version))
        }
    }
}

#Preview {
    ContentView()
        .environment(FavoritesStore())
        .environment(TripStore())
}
