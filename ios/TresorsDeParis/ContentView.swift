import SwiftUI

struct ContentView: View {
    // `-startTab N` as a launch argument opens another tab (used for simulator screenshots)
    @State private var selectedTab = UserDefaults.standard.integer(forKey: "startTab")

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)
            MapView()
                .tabItem { Label("Map", systemImage: "map") }
                .tag(1)
            CategoriesView()
                .tabItem { Label("Categories", systemImage: "square.grid.2x2") }
                .tag(2)
            SearchView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(3)
            FavoritesView()
                .tabItem { Label("Favorites", systemImage: "heart") }
                .tag(4)
        }
    }
}

#Preview {
    ContentView()
        .environment(FavoritesStore())
}
