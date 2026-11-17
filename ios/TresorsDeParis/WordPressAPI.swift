import Foundation

struct Post: Identifiable, Codable, Hashable {
    let id: Int
    let date: Date
    let link: URL
    let title: String
    let excerpt: String
    let imageURL: URL?
}

struct PostCategory: Identifiable, Decodable, Hashable {
    let id: Int
    let name: String
    let slug: String
    let count: Int

    enum CodingKeys: String, CodingKey { case id, name, slug, count }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name).htmlDecoded
        slug = try c.decode(String.self, forKey: .slug)
        count = try c.decode(Int.self, forKey: .count)
    }
}

/// An item of the website's hamburger menu.
struct MenuItem: Identifiable, Hashable {
    let title: String
    let url: URL
    let children: [MenuItem]

    var id: String { title + url.absoluteString }

    /// The category this item links to, if any.
    var categorySlug: String? {
        url.pathComponents.contains("category") ? url.lastPathComponent : nil
    }
}

/// Reads articles from the WordPress REST API behind tresors-de-paris.com.
enum WordPressAPI {
    static let site = URL(string: "https://tresors-de-paris.com/")!
    private static let base = URL(string: "https://tresors-de-paris.com/wp-json/wp/v2/")!

    struct Page {
        let posts: [Post]
        let totalPages: Int
    }

    static func posts(page: Int, categoryID: Int? = nil, tagIDs: [Int]? = nil, search: String? = nil) async throws -> Page {
        var items = [
            URLQueryItem(name: "per_page", value: "20"),
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "_embed", value: "wp:featuredmedia"),
            URLQueryItem(name: "_fields", value: "id,date,link,title,excerpt,_links,_embedded"),
        ]
        if let categoryID {
            items.append(URLQueryItem(name: "categories", value: String(categoryID)))
        }
        if let tagIDs {
            items.append(URLQueryItem(name: "tags", value: tagIDs.map(String.init).joined(separator: ",")))
        }
        if let search {
            items.append(URLQueryItem(name: "search", value: search))
        }

        let (data, response) = try await fetch(url(path: "posts", items: items))
        let posts = try JSONDecoder().decode([PostDTO].self, from: data).compactMap(\.post)
        let totalPages = Int(response.value(forHTTPHeaderField: "X-WP-TotalPages") ?? "") ?? 1
        return Page(posts: posts, totalPages: totalPages)
    }

    static func content(postID: Int) async throws -> String {
        struct ContentDTO: Decodable {
            struct Rendered: Decodable { let rendered: String }
            let content: Rendered
        }
        let items = [URLQueryItem(name: "_fields", value: "content")]
        let (data, _) = try await fetch(url(path: "posts/\(postID)", items: items))
        return try JSONDecoder().decode(ContentDTO.self, from: data).content.rendered
    }

    static func categories() async throws -> [PostCategory] {
        let items = [
            URLQueryItem(name: "per_page", value: "100"),
            URLQueryItem(name: "_fields", value: "id,name,slug,count"),
        ]
        let (data, _) = try await fetch(url(path: "categories", items: items))
        return try JSONDecoder().decode([PostCategory].self, from: data)
    }

    struct Tag: Decodable {
        let id: Int
        let slug: String
        let count: Int

        /// The place code in the slug: a postcode like "75008", or a department like "77"
        /// (whose slug on the website may be "77-seine-et-marne").
        var code: String? {
            let parts = slug.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
            guard let first = parts.first, first.allSatisfy(\.isASCII), first.allSatisfy(\.isNumber),
                  first.count == 5 || first.count == 2,
                  first.count == 2 || parts.count == 1 else { return nil }
            return String(first)
        }
    }

    /// All tags with articles that are postcodes or departments; new ones on the website show up by themselves.
    static func postalTags() async throws -> [Tag] {
        var tags: [Tag] = []
        var page = 1
        var totalPages = 1
        repeat {
            let items = [
                URLQueryItem(name: "per_page", value: "100"),
                URLQueryItem(name: "page", value: String(page)),
                URLQueryItem(name: "hide_empty", value: "true"),
                URLQueryItem(name: "_fields", value: "id,slug,count"),
            ]
            let (data, response) = try await fetch(url(path: "tags", items: items))
            tags += try JSONDecoder().decode([Tag].self, from: data)
            totalPages = Int(response.value(forHTTPHeaderField: "X-WP-TotalPages") ?? "") ?? 1
            page += 1
        } while page <= totalPages
        return tags.filter { $0.code != nil }
    }

    /// The website's hamburger menu, read from its home page so it follows the website.
    static func menu() async throws -> [MenuItem] {
        let (data, _) = try await fetch(site)
        guard let html = String(data: data, encoding: .utf8), let items = parseMenu(html) else {
            throw URLError(.cannotParseResponse)
        }
        return items
    }

    private final class MenuNode {
        let title: String
        let url: URL
        var children: [MenuNode] = []

        init(title: String, url: URL) {
            self.title = title
            self.url = url
        }

        var item: MenuItem { MenuItem(title: title, url: url, children: children.map(\.item)) }
    }

    private static let menuTokens = try! NSRegularExpression(
        pattern: "<ul\\b[^>]*>|</ul>|<a\\b[^>]*?href=\"([^\"]*)\"[^>]*>(.*?)</a>",
        options: .dotMatchesLineSeparators
    )

    private static func parseMenu(_ html: String) -> [MenuItem]? {
        guard let start = html.range(of: "id=\"site-navigation\""),
              let end = html.range(of: "</nav>", range: start.upperBound..<html.endIndex) else { return nil }
        let navigation = String(html[start.upperBound..<end.lowerBound]) as NSString
        let root = MenuNode(title: "", url: site)
        // The nodes whose lists are open; a new list belongs to the last item of the one around it
        var open: [MenuNode] = []
        let range = NSRange(location: 0, length: navigation.length)
        for match in menuTokens.matches(in: navigation as String, range: range) {
            let token = navigation.substring(with: match.range)
            if token == "</ul>" {
                _ = open.popLast()
            } else if token.hasPrefix("<ul") {
                open.append(open.last?.children.last ?? root)
            } else if let url = URL(string: navigation.substring(with: match.range(at: 1)).htmlDecoded) {
                let title = navigation.substring(with: match.range(at: 2)).htmlStripped
                open.last?.children.append(MenuNode(title: title, url: url))
            }
        }
        return root.children.map(\.item)
    }

    private static func url(path: String, items: [URLQueryItem]) -> URL {
        var components = URLComponents(url: base.appending(path: path), resolvingAgainstBaseURL: false)!
        components.queryItems = items
        return components.url!
    }

    private static let offlineErrors: [URLError.Code] = [
        .notConnectedToInternet, .networkConnectionLost, .timedOut,
        .cannotConnectToHost, .cannotFindHost, .dataNotAllowed,
    ]

    /// Loads from the network, falling back to the last cached response when offline.
    private static func fetch(_ url: URL) async throws -> (Data, HTTPURLResponse) {
        do {
            return try await load(URLRequest(url: url))
        } catch let error as URLError where offlineErrors.contains(error.code) {
            var request = URLRequest(url: url)
            request.cachePolicy = .returnCacheDataDontLoad
            guard let cached = try? await load(request) else { throw error }
            return cached
        }
    }

    private static func load(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return (data, http)
    }
}

private struct PostDTO: Decodable {
    struct Rendered: Decodable { let rendered: String }

    struct Embedded: Decodable {
        let featuredMedia: [Media]?
        enum CodingKeys: String, CodingKey { case featuredMedia = "wp:featuredmedia" }
    }

    struct Media: Decodable {
        struct Size: Decodable {
            let sourceURL: URL
            enum CodingKeys: String, CodingKey { case sourceURL = "source_url" }
        }
        struct Details: Decodable { let sizes: [String: Size]? }

        let sourceURL: URL?
        let details: Details?

        enum CodingKeys: String, CodingKey {
            case sourceURL = "source_url"
            case details = "media_details"
        }

        // Missing or private media come back as error objects or with an empty
        // media_details array, so every field is optional.
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            sourceURL = try? c.decode(URL.self, forKey: .sourceURL)
            details = try? c.decode(Details.self, forKey: .details)
        }

        var bestURL: URL? {
            details?.sizes?["medium_large"]?.sourceURL ?? details?.sizes?["large"]?.sourceURL ?? sourceURL
        }
    }

    let id: Int
    let date: String
    let link: URL
    let title: Rendered
    let excerpt: Rendered?
    let embedded: Embedded?

    enum CodingKeys: String, CodingKey {
        case id, date, link, title, excerpt
        case embedded = "_embedded"
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Paris")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        return formatter
    }()

    var post: Post? {
        guard let date = Self.dateFormatter.date(from: date) else { return nil }
        return Post(
            id: id,
            date: date,
            link: link,
            title: title.rendered.htmlStripped,
            excerpt: excerpt?.rendered.htmlStripped ?? "",
            imageURL: embedded?.featuredMedia?.first?.bestURL
        )
    }
}
