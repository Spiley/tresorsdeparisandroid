import SwiftUI

struct ContentView: View {
    // `-startTab N` as a launch argument opens another tab (used for simulator screenshots)
    @State private var selectedTab = UserDefaults.standard.integer(forKey: "startTab")

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)
            CategoriesView()
                .tabItem { Label("Categories", systemImage: "square.grid.2x2") }
                .tag(1)
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(2)
            FavoritesView()
                .tabItem { Label("Favorites", systemImage: "heart") }
                .tag(3)
            WebsiteView()
                .tabItem { Label("Website", systemImage: "safari") }
                .tag(4)
        }
    }
}

#Preview {
    ContentView()
        .environment(FavoritesStore())
}
