import SwiftUI
import WebKit

/// Shows the full website. Reports loading/failure and reloads whenever `reloadToken` changes.
struct WebView: UIViewRepresentable {
    let url: URL
    @Binding var isLoading: Bool
    @Binding var failed: Bool
    var reloadToken = 0

    func makeCoordinator() -> Coordinator {
        Coordinator(isLoading: $isLoading, failed: $failed)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        // Swipe from the edge to go back/forward, like the Android back button
        webView.allowsBackForwardNavigationGestures = true
        // The website is designed for light mode only, as in the Android app
        webView.overrideUserInterfaceStyle = .light
        webView.isOpaque = false
        webView.backgroundColor = .white

        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(context.coordinator, action: #selector(Coordinator.refresh), for: .valueChanged)
        webView.scrollView.refreshControl = refreshControl

        context.coordinator.webView = webView
        context.coordinator.reloadToken = reloadToken
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard context.coordinator.reloadToken != reloadToken else { return }
        context.coordinator.reloadToken = reloadToken
        if webView.url == nil {
            webView.load(URLRequest(url: url))
        } else {
            webView.reload()
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        private static let webSchemes: Set<String> = ["http", "https", "about", "data", "blob"]
        @Binding var isLoading: Bool
        @Binding var failed: Bool
        weak var webView: WKWebView?
        var reloadToken = 0

        init(isLoading: Binding<Bool>, failed: Binding<Bool>) {
            _isLoading = isLoading
            _failed = failed
        }

        @objc func refresh() {
            webView?.reload()
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url,
                  let scheme = url.scheme?.lowercased() else {
                decisionHandler(.allow)
                return
            }

            // Hand app links (fb://, mailto:, tel:, ...) to the system instead of the web view
            if !Self.webSchemes.contains(scheme) {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
                return
            }

            // Only the website itself opens in the app; other sites open in Safari.
            // Embeds and ads load in sub-frames, so they are left alone.
            if navigationAction.targetFrame?.isMainFrame ?? true,
               ["http", "https"].contains(scheme),
               !Self.isOwnSite(url) {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
                return
            }

            decisionHandler(.allow)
        }

        private static func isOwnSite(_ url: URL) -> Bool {
            guard let host = url.host?.lowercased(),
                  let siteHost = WordPressAPI.site.host?.lowercased() else { return false }
            return host == siteHost || host.hasSuffix("." + siteHost)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            isLoading = false
            failed = false
            webView.scrollView.refreshControl?.endRefreshing()
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            handle(error, in: webView)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            handle(error, in: webView)
        }

        private func handle(_ error: Error, in webView: WKWebView) {
            webView.scrollView.refreshControl?.endRefreshing()
            // A cancelled load is replaced by a newer one, not a failure
            guard (error as NSError).code != NSURLErrorCancelled else { return }
            isLoading = false
            failed = true
        }

        // Open target="_blank" links in the same web view
        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
            }
            return nil
        }
    }
}
