import Foundation

extension String {
    private static let namedEntities: [String: String] = [
        "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": "\u{00A0}",
        "hellip": "…", "ndash": "–", "mdash": "—", "lsquo": "‘", "rsquo": "’",
        "ldquo": "“", "rdquo": "”", "laquo": "«", "raquo": "»", "euro": "€",
        "eacute": "é", "egrave": "è", "ecirc": "ê", "agrave": "à", "acirc": "â",
        "ccedil": "ç", "ocirc": "ô", "icirc": "î", "ugrave": "ù", "ucirc": "û",
    ]

    private static let entityPattern = try! NSRegularExpression(pattern: "&(#[xX]?[0-9A-Fa-f]+|[A-Za-z]+);")
    private static let tagPattern = try! NSRegularExpression(pattern: "<[^>]+>")
    private static let whitespacePattern = try! NSRegularExpression(pattern: "\\s+")

    /// Replaces HTML entities such as `&amp;` and `&#8217;` with their characters.
    var htmlDecoded: String {
        let ns = self as NSString
        var result = ""
        var last = 0
        for match in Self.entityPattern.matches(in: self, range: NSRange(location: 0, length: ns.length)) {
            result += ns.substring(with: NSRange(location: last, length: match.range.location - last))
            let entity = ns.substring(with: match.range(at: 1))
            result += Self.decode(entity: entity) ?? ns.substring(with: match.range)
            last = match.range.location + match.range.length
        }
        result += ns.substring(from: last)
        return result
    }

    /// Plain text from an HTML fragment: tags removed, entities decoded, whitespace collapsed.
    var htmlStripped: String {
        let range = NSRange(location: 0, length: (self as NSString).length)
        let noTags = Self.tagPattern.stringByReplacingMatches(in: self, range: range, withTemplate: " ")
        let decoded = noTags.htmlDecoded
        let decodedRange = NSRange(location: 0, length: (decoded as NSString).length)
        return Self.whitespacePattern
            .stringByReplacingMatches(in: decoded, range: decodedRange, withTemplate: " ")
            .trimmingCharacters(in: .whitespaces)
    }

    /// Escapes text for safe insertion into HTML.
    var htmlEscaped: String {
        replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static func decode(entity: String) -> String? {
        guard entity.hasPrefix("#") else { return namedEntities[entity] }
        let digits = entity.dropFirst()
        let value = digits.first == "x" || digits.first == "X"
            ? UInt32(digits.dropFirst(), radix: 16)
            : UInt32(digits, radix: 10)
        guard let value, let scalar = Unicode.Scalar(value) else { return nil }
        return String(Character(scalar))
    }
}
