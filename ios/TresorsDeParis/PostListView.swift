import SwiftUI
import Observation

@Observable
@MainActor
final class PostListModel {
    enum Source: Hashable {
        case latest
        case category(Int)
        case tag(Int)
        case search(String)
    }

    let source: Source
    private(set) var posts: [Post] = []
    private(set) var isLoading = false
    private(set) var failed = false
    private var page = 0
    private var totalPages = 1

    init(source: Source) {
        self.source = source
    }

    private static var cache: [Source: PostListModel] = [:]

    /// One model per source, so previews and full lists share loaded articles.
    static func shared(for source: Source) -> PostListModel {
        if let model = cache[source] { return model }
        let model = PostListModel(source: source)
        cache[source] = model
        return model
    }

    var canLoadMore: Bool { page < totalPages }

    func refresh() async {
        await load(page: 1)
    }

    func loadMore() async {
        guard !isLoading, canLoadMore else { return }
        await load(page: page + 1)
    }

    private func load(page next: Int) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let result: WordPressAPI.Page
            switch source {
            case .latest:
                result = try await WordPressAPI.posts(page: next)
            case .category(let id):
                result = try await WordPressAPI.posts(page: next, categoryID: id)
            case .tag(let id):
                result = try await WordPressAPI.posts(page: next, tagID: id)
            case .search(let query):
                result = try await WordPressAPI.posts(page: next, search: query)
            }
            posts = next == 1 ? result.posts : posts + result.posts.filter { new in !posts.contains { $0.id == new.id } }
            page = next
            totalPages = result.totalPages
            failed = false
        } catch {
            failed = true
        }
    }
}

/// A scrolling list of articles with pull-to-refresh, paging and an offline state.
struct PostListView: View {
    let model: PostListModel
    var emptyMessage = "No articles found."

    var body: some View {
        Group {
            if model.posts.isEmpty {
                if model.failed {
                    OfflineView { Task { await model.refresh() } }
                } else if model.isLoading || model.canLoadMore {
                    ProgressView()
                        .controlSize(.large)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ContentUnavailableView(emptyMessage, systemImage: "doc.text.magnifyingglass")
                }
            } else {
                List {
                    ForEach(model.posts) { post in
                        NavigationLink(value: post) {
                            PostRow(post: post)
                        }
                        .onAppear {
                            if post.id == model.posts.last?.id {
                                Task { await model.loadMore() }
                            }
                        }
                    }
                    if model.canLoadMore || model.failed {
                        HStack {
                            Spacer()
                            if model.failed {
                                Button("Try again") { Task { await model.loadMore() } }
                            } else {
                                ProgressView()
                            }
                            Spacer()
                        }
                        .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
                .refreshable { await model.refresh() }
            }
        }
        .task {
            if model.posts.isEmpty && !model.isLoading {
                await model.refresh()
            }
        }
    }
}

/// Compact row with a rounded thumbnail, for category, search and favorite lists.
struct PostRow: View {
    let post: Post

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            PostImage(url: post.imageURL)
                .frame(width: 92, height: 92)
                .clipShape(RoundedRectangle(cornerRadius: 18))

            VStack(alignment: .leading, spacing: 4) {
                PostDate(date: post.date)
                Text(post.title)
                    .font(.headline)
                    .fontDesign(.serif)
                    .lineLimit(2)
                Text(post.excerpt)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 4)
    }
}

struct PostDate: View {
    let date: Date

    var body: some View {
        Text(date.formatted(.dateTime.day().month(.wide).year()).uppercased())
            .font(.caption2.weight(.semibold))
            .foregroundStyle(Color.accentColor)
    }
}

/// The article feed on Home: the newest article as a large hero, the rest as image cards.
struct FeaturedPostList<Header: View>: View {
    let model: PostListModel
    @ViewBuilder let header: Header

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                header
                if model.posts.isEmpty {
                    Group {
                        if model.failed {
                            OfflineView { Task { await model.refresh() } }
                        } else {
                            ProgressView().controlSize(.large)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 400)
                }
                ForEach(model.posts) { post in
                    NavigationLink(value: post) {
                        if post.id == model.posts.first?.id {
                            HeroCard(post: post)
                        } else {
                            StoryCard(post: post)
                        }
                    }
                    .buttonStyle(.plain)
                    .onAppear {
                        if post.id == model.posts.last?.id {
                            Task { await model.loadMore() }
                        }
                    }
                }
                if !model.posts.isEmpty && (model.canLoadMore || model.failed) {
                    Group {
                        if model.failed {
                            Button("Try again") { Task { await model.loadMore() } }
                        } else {
                            ProgressView()
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
        .refreshable { await model.refresh() }
        .task {
            if model.posts.isEmpty && !model.isLoading {
                await model.refresh()
            }
        }
    }
}

/// The newest article: a large photo with the title over a dark gradient.
struct HeroCard: View {
    let post: Post

    var body: some View {
        PostImage(url: post.imageURL)
            .frame(maxWidth: .infinity)
            .aspectRatio(0.85, contentMode: .fit)
            .overlay {
                LinearGradient(
                    stops: [.init(color: .clear, location: 0.35), .init(color: .black.opacity(0.85), location: 1)],
                    startPoint: .top, endPoint: .bottom
                )
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("LATEST STORY")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.accentColor)
                    Text(post.title)
                        .font(.title2.bold())
                        .fontDesign(.serif)
                        .foregroundStyle(.white)
                        .lineLimit(3)
                    Text(post.excerpt)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.85))
                        .lineLimit(2)
                }
                .padding(20)
            }
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .contentShape(RoundedRectangle(cornerRadius: 28))
    }
}

/// A card with a wide photo, used for the article feed on Home.
struct StoryCard: View {
    let post: Post

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PostImage(url: post.imageURL)
                .frame(maxWidth: .infinity)
                .aspectRatio(16 / 9, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 24))
            PostDate(date: post.date)
                .padding(.top, 4)
            Text(post.title)
                .font(.title3.bold())
                .fontDesign(.serif)
                .lineLimit(3)
            Text(post.excerpt)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .padding(.horizontal, 4)
        .contentShape(Rectangle())
    }
}

struct PostImage: View {
    let url: URL?

    var body: some View {
        // Fills whatever frame it is given, cropping the photo
        Color.clear.overlay {
            AsyncImage(url: url) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                ZStack {
                    Color.secondary.opacity(0.15)
                    Image(systemName: "photo")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .clipped()
    }
}
