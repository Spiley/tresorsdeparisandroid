import SwiftUI

struct ContentView: View {
    // `-startTab N` as a launch argument opens another tab (used for simulator screenshots)
    @State private var selectedTab = UserDefaults.standard.integer(forKey: "startTab")

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
    }
}

#Preview {
    ContentView()
        .environment(FavoritesStore())
        .environment(TripStore())
}
