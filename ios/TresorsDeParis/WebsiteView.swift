import SwiftUI

/// A page of the full tresors-de-paris.com website, as in the Android app.
struct WebsiteView: View {
    let url: URL
    @State private var isLoading = true
    @State private var failed = false
    @State private var reloadToken = 0

    init(url: URL = WordPressAPI.site) {
        self.url = url
    }

    var body: some View {
        ZStack {
            WebView(url: url, isLoading: $isLoading, failed: $failed, reloadToken: reloadToken)

            if failed {
                OfflineView {
                    failed = false
                    isLoading = true
                    reloadToken += 1
                }
                .background(Color(uiColor: .systemBackground))
            } else if isLoading {
                ZStack {
                    Color(uiColor: .systemBackground)
                        .opacity(0.5)
                        .ignoresSafeArea()
                    VStack {
                        ProgressView()
                            .controlSize(.large)
                            .padding(.bottom, 16)
                        Text(t("Loading TdP ..."))
                        Text(t("Your insider's guide to Paris"))
                    }
                }
            }
        }
        // White like the (always light) website, so dark mode doesn't flash behind it
        .background(Color.white)
    }
}

/// Shown when content can't be loaded, with a retry button.
struct OfflineView: View {
    let retry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(t("No connection"), systemImage: "wifi.slash")
        } description: {
            Text(t("Check your internet connection and try again. Saved favorites are still available offline."))
        } actions: {
            Button(t("Try again"), action: retry)
                .buttonStyle(.borderedProminent)
        }
    }
}
