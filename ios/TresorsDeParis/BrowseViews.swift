import SwiftUI

struct HomeView: View {
    @State private var model = PostListModel.shared(for: .latest)

    var body: some View {
        NavigationStack {
            FeaturedPostList(model: model) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("TRÉSORS DE PARIS")
                            .font(.footnote.weight(.semibold))
                            .tracking(2)
                            .foregroundStyle(Color.accentColor)
                        Text("Your insider's guide to Paris")
                            .font(.title.bold())
                            .fontDesign(.serif)
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

struct CategoriesView: View {
    @State private var categories: [PostCategory] = []
    @State private var failed = false

    private var topLevel: [PostCategory] {
        categories.filter { $0.parent == 0 && $0.id != 1 }.sorted { $0.name < $1.name }
    }

    private func children(of parent: PostCategory) -> [PostCategory] {
        categories.filter { $0.parent == parent.id }.sorted { $0.name < $1.name }
    }

    var body: some View {
        NavigationStack {
            Group {
                if categories.isEmpty {
                    if failed {
                        OfflineView { Task { await load() } }
                    } else {
                        ProgressView().controlSize(.large)
                    }
                } else {
                    List {
                        ForEach(topLevel.filter { !children(of: $0).isEmpty }) { parent in
                            Section(parent.name) {
                                CategoryRow(category: parent, title: "All \(parent.name)")
                                ForEach(children(of: parent)) { CategoryRow(category: $0) }
                            }
                        }
                        Section("More topics") {
                            ForEach(topLevel.filter { children(of: $0).isEmpty }) { CategoryRow(category: $0) }
                        }
                    }
                    .refreshable { await load() }
                }
            }
            .navigationTitle("Categories")
            .navigationDestination(for: PostCategory.self) { CategoryPostsView(category: $0) }
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        .task {
            if categories.isEmpty { await load() }
        }
    }

    private func load() async {
        do {
            categories = try await WordPressAPI.categories()
            failed = false
        } catch {
            failed = true
        }
    }
}

private struct CategoryRow: View {
    let category: PostCategory
    var title: String?

    var body: some View {
        NavigationLink(value: category) {
            LabeledContent(title ?? category.name, value: "\(category.count)")
        }
    }
}

private struct CategoryPostsView: View {
    let category: PostCategory
    @State private var model: PostListModel

    init(category: PostCategory) {
        self.category = category
        _model = State(initialValue: PostListModel(source: .category(category.id)))
    }

    var body: some View {
        PostListView(model: model)
            .navigationTitle(category.name)
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
