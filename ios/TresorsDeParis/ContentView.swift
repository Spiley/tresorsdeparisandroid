import SwiftUI

struct ContentView: View {
    private let url = URL(string: "https://tresors-de-paris.com/")!
    @State private var isLoading = true

    var body: some View {
        ZStack {
            WebView(url: url, isLoading: $isLoading)
                .ignoresSafeArea(edges: .bottom)

            if isLoading {
                ZStack {
                    Color(uiColor: .systemBackground)
                        .opacity(0.5)
                        .ignoresSafeArea()
                    VStack {
                        ProgressView()
                            .controlSize(.large)
                            .padding(.bottom, 16)
                        Text("Loading TdP ...")
                        Text("Your Insider's Guide to Paris")
                    }
                }
            }
        }
        .background(Color(uiColor: .systemBackground))
    }
}

#Preview {
    ContentView()
}
