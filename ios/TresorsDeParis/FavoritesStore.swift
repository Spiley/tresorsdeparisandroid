import Foundation
import Observation

/// Saved articles, stored with their full content so they can be read offline.
@Observable
@MainActor
final class FavoritesStore {
    struct Favorite: Codable, Identifiable {
        let post: Post
        let content: String
        let savedAt: Date
        var id: Int { post.id }
    }

    private(set) var favorites: [Favorite] = []
    private let fileURL = URL.documentsDirectory.appending(path: "favorites.json")

    init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([Favorite].self, from: data) {
            favorites = saved
        }
    }

    func isFavorite(_ postID: Int) -> Bool {
        favorites.contains { $0.id == postID }
    }

    func content(for postID: Int) -> String? {
        favorites.first { $0.id == postID }?.content
    }

    func toggle(_ post: Post, content: String) {
        if isFavorite(post.id) {
            favorites.removeAll { $0.id == post.id }
        } else {
            favorites.insert(Favorite(post: post, content: content, savedAt: .now), at: 0)
        }
        save()
    }

    func remove(at offsets: IndexSet) {
        favorites.remove(atOffsets: offsets)
        save()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(favorites) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
