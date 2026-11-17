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
                        Text(t("Your insider's guide to Paris"))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    LanguageButton()
                    NavigationLink {
                        WebsiteView()
                            .navigationTitle(t("Website"))
                            .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        Image(systemName: "safari")
                            .font(.title3)
                            .frame(width: 44, height: 44)
                            .background(Color.accentColor.opacity(0.15), in: Circle())
                    }
                    .accessibilityLabel(t("Open the website"))
                }
                .padding(.top, 8)
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        // Articles seen here are not announced by a notification later
        .onChange(of: model.posts.first?.id, initial: true) {
            NewPostNotifier.markSeen(model.posts)
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
            .navigationTitle(t("Menu"))
            .navigationDestination(for: MenuItem.self) { MenuDestination(item: $0, categories: categories) }
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        .task {
            if items.isEmpty { await load() }
        }
    }

    private func load() async {
        failed = false
        // The menu from last time shows at once
        if items.isEmpty {
            if let saved = try? await WordPressAPI.categories(saved: true) { categories = saved }
            if let saved = try? await WordPressAPI.menu(saved: true) { items = saved }
        }
        do {
            // Both at the same time
            async let newCategories = WordPressAPI.categories()
            async let newItems = WordPressAPI.menu()
            categories = try await newCategories
            items = try await newItems
        } catch {
            failed = items.isEmpty
        }
    }
}

private struct MenuList: View {
    let items: [MenuItem]
    @Environment(\.openURL) private var openURL

    var body: some View {
        List(items) { item in
            if isOwnSite(item) || !item.children.isEmpty {
                NavigationLink(translated(item.title), value: item)
            } else {
                // Other websites open in Safari
                Button(translated(item.title)) { openURL(item.url) }
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
        return [MenuItem(title: t("All %s", translated(item.title)), url: item.url, children: [])] + item.children
    }

    var body: some View {
        if !item.children.isEmpty {
            MenuList(items: submenu)
                .navigationTitle(translated(item.title))
        } else if let category {
            CategoryPostsView(category: category, title: translated(item.title))
        } else {
            WebsiteView(url: item.url)
                .navigationTitle(translated(item.title))
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
                    PostListView(model: model, emptyMessage: t("No results for “%s”", model.searchQuery))
                        .id(model.searchQuery)
                } else {
                    ContentUnavailableView(
                        t("Search articles"),
                        systemImage: "magnifyingglass",
                        description: Text(t("Find restaurants, hidden gems, events and more in Paris."))
                    )
                }
            }
            .navigationTitle(t("Search"))
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
        }
        .searchable(text: $query, prompt: t("Search Trésors de Paris"))
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

/// Switches the app between English and French.
struct LanguageButton: View {
    var body: some View {
        Menu {
            ForEach(Language.allCases) { language in
                Button {
                    Lang.shared.set(language)
                    WebTranslator.shared.languageChanged()
                } label: {
                    if language == Lang.shared.current {
                        Label(language.label, systemImage: "checkmark")
                    } else {
                        Text(language.label)
                    }
                }
            }
        } label: {
            Label(Lang.shared.current.rawValue.uppercased(), systemImage: "translate")
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 12)
                .frame(height: 44)
                .background(Color.accentColor.opacity(0.15), in: Capsule())
        }
        .accessibilityLabel(t("Language"))
    }
}
