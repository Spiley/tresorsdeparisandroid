import SwiftUI
import WebKit

/// Translates texts from the website into French with the website's own translator: the
/// Google website translator that GTranslate puts on tresors-de-paris.com. It runs in a
/// hidden web page, so no separate translation service or key is needed. Results are kept
/// on the device, so every text is translated only once.
@Observable
@MainActor
final class WebTranslator {
    static let shared = WebTranslator()

    private static let batchSize = 60
    private static let maxPolls = 40

    /// The translator script, for pages that should show up in French. The cookie tells
    /// the translator which language to show, as GTranslate does on the website.
    nonisolated static let widget = """
    <div id="gt-element" style="display:none"></div>
    <style>
      iframe.skiptranslate, .goog-te-banner-frame, #goog-gt-tt, .goog-te-balloon-frame { display: none !important; }
      body { top: 0 !important; position: static !important; }
      font { background: none !important; box-shadow: none !important; }
    </style>
    <script>document.cookie = 'googtrans=/en/fr; path=/';</script>
    <script>function gtInit(){ new google.translate.TranslateElement({pageLanguage: 'en', includedLanguages: 'fr', autoDisplay: false}, 'gt-element'); }</script>
    <script src="https://translate.google.com/translate_a/element.js?cb=gtInit"></script>
    """

    /// Sets the website's language cookie before its own scripts run, for the full website.
    static var languageScript: WKUserScript {
        let value = Lang.shared.isFrench ? "/en/fr" : "/en/en"
        return WKUserScript(
            source: "document.cookie = 'googtrans=\(value); path=/';",
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        )
    }

    /// English text to French; observed, so views update when a translation arrives.
    private var french: [String: String] = [:]
    @ObservationIgnored private var queue: [String] = []
    @ObservationIgnored private var queued: Set<String> = []
    @ObservationIgnored private var failed: Set<String> = []
    @ObservationIgnored private var busy = false
    @ObservationIgnored private var batchNumber = 0
    @ObservationIgnored private var webView: WKWebView?
    @ObservationIgnored private let fileURL = URL.documentsDirectory.appending(path: "translations-fr.json")

    private init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([String: String].self, from: data) {
            french = saved
        }
    }

    /// `english` in the chosen language. In French it returns the English text until the
    /// translation is ready; a view showing it updates when it is.
    func text(_ english: String) -> String {
        guard Lang.shared.isFrench, !english.isEmpty else { return english }
        // The app's own wording first, so the website's "Home" reads "Accueil" like the tab
        if let own = French[english] { return own }
        if let translated = french[english] { return translated }
        if !failed.contains(english), queued.insert(english).inserted {
            queue.append(english)
            if !busy {
                busy = true
                Task {
                    try? await Task.sleep(for: .milliseconds(150))
                    await translateQueue()
                }
            }
        }
        return english
    }

    /// Called after switching languages: tries texts again that failed before.
    func languageChanged() {
        failed.removeAll()
    }

    private func translateQueue() async {
        while !queue.isEmpty, Lang.shared.isFrench {
            let batch = Array(queue.prefix(Self.batchSize))
            let results = await translate(batch)
            french.merge(results) { $1 }
            queue.removeFirst(batch.count)
            for text in batch {
                queued.remove(text)
                if results[text] == nil { failed.insert(text) }
            }
            save()
        }
        busy = false
    }

    private func translate(_ texts: [String]) async -> [String: String] {
        let view = webView ?? makeWebView()
        batchNumber += 1
        let batch = batchNumber
        var page = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"></head><body data-batch=\"\(batch)\">"
        for text in texts {
            page += "<p class=\"t\">\(text.htmlEscaped)</p>"
        }
        page += Self.widget + "</body></html>"
        view.loadHTMLString(page, baseURL: WordPressAPI.site)

        // Google wraps every translated piece in <font>, so a paragraph with one is done
        let script = """
        (document.body && document.body.dataset.batch === '\(batch)')
          ? JSON.stringify([...document.querySelectorAll('p.t')].map(p => p.querySelector('font') ? p.innerText : null))
          : 'null'
        """
        var translated: [String?] = []
        for _ in 0..<Self.maxPolls {
            try? await Task.sleep(for: .milliseconds(500))
            guard let json = try? await view.evaluateJavaScript(script) as? String,
                  let data = json.data(using: .utf8),
                  let values = try? JSONDecoder().decode([String?].self, from: data) else { continue }
            translated = values
            if values.count == texts.count, values.allSatisfy({ $0 != nil }) { break }
        }
        var results: [String: String] = [:]
        for (english, value) in zip(texts, translated) {
            if let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty {
                results[english] = value
            }
        }
        return results
    }

    /// The page runs in a tiny, almost invisible web view in the window, because web pages
    /// outside a window are paused by the system.
    private func makeWebView() -> WKWebView {
        let view = WKWebView(frame: CGRect(x: 0, y: 0, width: 2, height: 2))
        view.alpha = 0.01
        view.isUserInteractionEnabled = false
        view.accessibilityElementsHidden = true
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        window?.addSubview(view)
        webView = view
        return view
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(french) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

/// A text from the website (title, excerpt, menu item) in the chosen language.
@MainActor
func translated(_ english: String) -> String {
    WebTranslator.shared.text(english)
}
