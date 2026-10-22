import SwiftUI
import Observation

@Observable
@MainActor
final class PostListModel {
    enum Source: Hashable {
        case latest
        case category(Int)
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

struct PostRow: View {
    let post: Post

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            PostImage(url: post.imageURL)
                .frame(width: 96, height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 4) {
                Text(post.title)
                    .font(.headline)
                    .lineLimit(3)
                Text(post.date, format: .dateTime.day().month(.wide).year())
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(post.excerpt)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 4)
    }
}

struct PostImage: View {
    let url: URL?

    var body: some View {
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
}
