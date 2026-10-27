import SwiftUI
import SafariServices
import WebKit

/// One article rendered natively from its WordPress content, with favorite and share buttons.
struct ArticleView: View {
    let post: Post
    @Environment(FavoritesStore.self) private var favorites
    @State private var content: String?
    @State private var failed = false
    @State private var openedLink: OpenedLink?

    var body: some View {
        Group {
            if let content {
                ArticleWebView(html: Self.page(for: post, content: content)) { url in
                    openedLink = OpenedLink(url: url)
                }
                .ignoresSafeArea(edges: .bottom)
            } else if failed {
                OfflineView { Task { await load() } }
            } else {
                ProgressView()
                    .controlSize(.large)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    if let content {
                        favorites.toggle(post, content: content)
                    }
                } label: {
                    Image(systemName: favorites.isFavorite(post.id) ? "heart.fill" : "heart")
                }
                .accessibilityLabel(favorites.isFavorite(post.id) ? "Remove from favorites" : "Add to favorites")
                .disabled(content == nil)

                ShareLink(item: post.link, subject: Text(post.title))
            }
        }
        .sheet(item: $openedLink) { link in
            SafariView(url: link.url)
                .ignoresSafeArea()
        }
        .task { await load() }
    }

    private func load() async {
        // Saved favorites open instantly and offline; refresh them when online
        if let saved = favorites.content(for: post.id) {
            content = saved
        }
        failed = false
        do {
            content = try await WordPressAPI.content(postID: post.id)
        } catch {
            failed = content == nil
        }
    }

    private static func page(for post: Post, content: String) -> String {
        let date = post.date.formatted(.dateTime.day().month(.wide).year())
        let hero = post.imageURL.map { "<img class=\"hero\" src=\"\($0.absoluteString.htmlEscaped)\" alt=\"\">" } ?? ""
        return """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root { color-scheme: light dark; }
          body { font: -apple-system-body; margin: 0 16px 48px; line-height: 1.55; -webkit-text-size-adjust: 100%; }
          img, video { max-width: 100%; height: auto; border-radius: 8px; }
          iframe { max-width: 100%; }
          figure { margin: 16px 0; }
          a { color: #B8892B; }
          h1, h2, h3 { line-height: 1.25; }
          .hero { display: block; width: calc(100% + 32px); max-width: none; margin: 0 -16px; border-radius: 0; }
          .title { font-size: 28px; margin: 20px 0 4px; }
          .date { color: gray; font-size: 14px; margin-bottom: 16px; }
        </style>
        </head>
        <body>
        \(hero)
        <h1 class="title">\(post.title.htmlEscaped)</h1>
        <div class="date">\(date)</div>
        \(content)
        </body>
        </html>
        """
    }
}

private struct OpenedLink: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

private struct ArticleWebView: UIViewRepresentable {
    let html: String
    let openLink: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(openLink: openLink)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .systemBackground
        let coordinator = context.coordinator
        AdBlocker.install(on: webView) {
            coordinator.rulesReady = true
            if let html = coordinator.html {
                webView.loadHTMLString(html, baseURL: WordPressAPI.site)
            }
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard context.coordinator.html != html else { return }
        context.coordinator.html = html
        // Until the ad block rules are in place, the html is loaded once they are
        guard context.coordinator.rulesReady else { return }
        webView.loadHTMLString(html, baseURL: WordPressAPI.site)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        let openLink: (URL) -> Void
        var html: String?
        var rulesReady = false

        init(openLink: @escaping (URL) -> Void) {
            self.openLink = openLink
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            // Let embeds (YouTube etc.) load; send tapped links out of the article
            guard navigationAction.navigationType == .linkActivated,
                  let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            decisionHandler(.cancel)
            if ["http", "https"].contains(url.scheme?.lowercased()) {
                openLink(url)
            } else {
                UIApplication.shared.open(url)
            }
        }
    }
}

private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}
