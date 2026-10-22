import SwiftUI

@main
struct TresorsDeParisApp: App {
    @State private var favorites = FavoritesStore()

    init() {
        // A larger cache keeps articles and images available when offline
        URLCache.shared = URLCache(memoryCapacity: 20_000_000, diskCapacity: 200_000_000)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(favorites)
        }
    }
}
