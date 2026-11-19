import Foundation
import Observation

enum Language: String, CaseIterable, Identifiable {
    case english = "en"
    case french = "fr"
    case dutch = "nl"
    case german = "de"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .english: "English"
        case .french: "Français"
        case .dutch: "Nederlands"
        case .german: "Deutsch"
        }
    }

    /// The app's own texts in this language, by their English text.
    var texts: [String: String] {
        switch self {
        case .english: [:]
        case .french: French
        case .dutch: Dutch
        case .german: German
        }
    }
}

/// The language of the app. The app's own texts come from `French`, `Dutch` and `German`; articles, titles and
/// menus from the website are translated by the website's own translator (`WebTranslator`).
@Observable
final class Lang {
    static let shared = Lang()

    private(set) var current: Language

    func set(_ language: Language) {
        current = language
        UserDefaults.standard.set(language.rawValue, forKey: "language")
    }

    var isFrench: Bool { current == .french }
    var isEnglish: Bool { current == .english }

    var locale: Locale { Locale(identifier: current.rawValue) }

    /// Uses the saved choice, or the phone's language when the app has it.
    private init() {
        if let saved = UserDefaults.standard.string(forKey: "language").flatMap(Language.init(rawValue:)) {
            current = saved
        } else {
            let phone = Locale.preferredLanguages.first.map { String($0.prefix(2)) } ?? "en"
            current = Language(rawValue: phone) ?? .english
        }
    }
}

/// An app text in the chosen language. The English text is also the key into `French`;
/// `%s` and `%d` in it are filled with `args` (the same keys as on Android). Reading it in a
/// view updates the view when the language changes.
func t(_ english: String, _ args: CVarArg...) -> String {
    let text = Lang.shared.current.texts[english] ?? english
    guard !args.isEmpty else { return text }
    let format = text.replacingOccurrences(of: "%s", with: "%@").replacingOccurrences(of: "%d", with: "%ld")
    return String(format: format, locale: Lang.shared.locale, arguments: args)
}

/// "1 article" or "3 articles", in the chosen language.
func plural(_ count: Int, _ one: String, _ many: String) -> String {
    count == 1 ? t(one, count) : t(many, count)
}

extension String {
    /// This app text in the chosen language, for places where `t` is taken by a local name.
    var translatedForApp: String { t(self) }
}
