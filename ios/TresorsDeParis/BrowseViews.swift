import SwiftUI

struct HomeView: View {
    @State private var model = PostListModel.shared(for: .latest)

    var body: some View {
        NavigationStack {
            FeaturedPostList(model: model) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Trésors de Paris")
                            .font(.title.bold())
                            .fontDesign(.serif)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text("Your insider's guide to Paris")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    NavigationLink {
                        WebsiteView()
                            .navigationTitle("Website")
                            .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        Image(systemName: "safari")
                            .font(.title3)
                            .frame(width: 44, height: 44)
                            .background(Color.accentColor.opacity(0.15), in: Circle())
                    }
                    .accessibilityLabel("Open the website")
                }
                .padding(.top, 8)
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
    }
}

/// The website's hamburger menu.
struct MenuView: View {
    @State private var items: [MenuItem] = []
    @State private var categories: [PostCategory] = []
    @State private var failed = false

    var body: some View {
        NavigationStack {
            Group {
                if !items.isEmpty {
                    MenuList(items: items)
                        .refreshable { await load() }
                } else if failed {
                    OfflineView { Task { await load() } }
                } else {
                    ProgressView().controlSize(.large)
                }
            }
            .navigationTitle("Menu")
            .navigationDestination(for: MenuItem.self) { MenuDestination(item: $0, categories: categories) }
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        .task {
            if items.isEmpty { await load() }
        }
    }

    private func load() async {
        failed = false
        do {
            categories = try await WordPressAPI.categories()
            items = try await WordPressAPI.menu()
        } catch {
            failed = true
        }
    }
}

private struct MenuList: View {
    let items: [MenuItem]
    @Environment(\.openURL) private var openURL

    var body: some View {
        List(items) { item in
            if isOwnSite(item) || !item.children.isEmpty {
                NavigationLink(item.title, value: item)
            } else {
                // Other websites open in Safari
                Button(item.title) { openURL(item.url) }
                    .foregroundStyle(.primary)
            }
        }
    }

    private func isOwnSite(_ item: MenuItem) -> Bool {
        item.url.absoluteString.hasPrefix(WordPressAPI.site.absoluteString)
    }
}

/// Where a menu item leads: its submenu, a list of articles, or a page of the website.
private struct MenuDestination: View {
    let item: MenuItem
    let categories: [PostCategory]

    private var category: PostCategory? {
        categories.first { $0.slug == item.categorySlug }
    }

    /// A submenu starts with the page of the item itself, unless that is empty or already listed.
    private var submenu: [MenuItem] {
        if item.children.contains(where: { $0.url == item.url }) || category?.count == 0 {
            return item.children
        }
        return [MenuItem(title: "All \(item.title)", url: item.url, children: [])] + item.children
    }

    var body: some View {
        if !item.children.isEmpty {
            MenuList(items: submenu)
                .navigationTitle(item.title)
        } else if let category {
            CategoryPostsView(category: category, title: item.title)
        } else {
            WebsiteView(url: item.url)
                .navigationTitle(item.title)
                .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct CategoryPostsView: View {
    let title: String
    @State private var model: PostListModel

    init(category: PostCategory, title: String? = nil) {
        self.title = title ?? category.name
        _model = State(initialValue: PostListModel(source: .category(category.id)))
    }

    var body: some View {
        PostListView(model: model)
            .navigationTitle(title)
    }
}

struct SearchView: View {
    @State private var query = ""
    @State private var model: PostListModel?

    var body: some View {
        NavigationStack {
            Group {
                if let model {
                    PostListView(model: model, emptyMessage: "No results for “\(model.searchQuery)”")
                        .id(model.searchQuery)
                } else {
                    ContentUnavailableView(
                        "Search articles",
                        systemImage: "magnifyingglass",
                        description: Text("Find restaurants, hidden gems, events and more in Paris.")
                    )
                }
            }
            .navigationTitle("Search")
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        .searchable(text: $query, prompt: "Search Trésors de Paris")
        .onSubmit(of: .search) {
            let trimmed = query.trimmingCharacters(in: .whitespaces)
            model = trimmed.isEmpty ? nil : PostListModel(source: .search(trimmed))
        }
        .onChange(of: query) {
            if query.isEmpty { model = nil }
        }
    }
}

private extension PostListModel {
    var searchQuery: String {
        if case .search(let query) = source { return query }
        return ""
    }
}

struct FavoritesView: View {
    @Environment(FavoritesStore.self) private var favorites

    var body: some View {
        NavigationStack {
            Group {
                if favorites.favorites.isEmpty {
                    ContentUnavailableView(
                        "No favorites yet",
                        systemImage: "heart",
                        description: Text("Tap the heart on an article to save it here. Saved articles can be read offline.")
                    )
                } else {
                    List {
                        ForEach(favorites.favorites) { favorite in
                            NavigationLink(value: favorite.post) {
                                PostRow(post: favorite.post)
                            }
                        }
                        .onDelete { favorites.remove(at: $0) }
                    }
                    .listStyle(.plain)
                    .toolbar { EditButton() }
                }
            }
            .navigationTitle("Favorites")
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
    }
}
