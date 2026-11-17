import Foundation
import Observation

enum Language: String, CaseIterable, Identifiable {
    case english = "en"
    case french = "fr"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .english: "English"
        case .french: "Français"
        }
    }
}

/// The language of the app. The app's own texts come from `French`; articles, titles and
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

    var locale: Locale { Locale(identifier: current.rawValue) }

    /// Uses the saved choice, or French on a phone set to French.
    private init() {
        if let saved = UserDefaults.standard.string(forKey: "language").flatMap(Language.init(rawValue:)) {
            current = saved
        } else {
            current = Locale.preferredLanguages.first?.hasPrefix("fr") == true ? .french : .english
        }
    }
}

/// An app text in the chosen language. The English text is also the key into `French`;
/// `%s` and `%d` in it are filled with `args` (the same keys as on Android). Reading it in a
/// view updates the view when the language changes.
func t(_ english: String, _ args: CVarArg...) -> String {
    let text = Lang.shared.isFrench ? (French[english] ?? english) : english
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
