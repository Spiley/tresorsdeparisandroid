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
    let count: Int
    let parent: Int

    enum CodingKeys: String, CodingKey { case id, name, count, parent }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name).htmlDecoded
        count = try c.decode(Int.self, forKey: .count)
        parent = try c.decode(Int.self, forKey: .parent)
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

    static func posts(page: Int, categoryID: Int? = nil, tagID: Int? = nil, search: String? = nil) async throws -> Page {
        var items = [
            URLQueryItem(name: "per_page", value: "20"),
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "_embed", value: "wp:featuredmedia"),
            URLQueryItem(name: "_fields", value: "id,date,link,title,excerpt,_links,_embedded"),
        ]
        if let categoryID {
            items.append(URLQueryItem(name: "categories", value: String(categoryID)))
        }
        if let tagID {
            items.append(URLQueryItem(name: "tags", value: String(tagID)))
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
            URLQueryItem(name: "hide_empty", value: "true"),
            URLQueryItem(name: "_fields", value: "id,name,count,parent"),
        ]
        let (data, _) = try await fetch(url(path: "categories", items: items))
        return try JSONDecoder().decode([PostCategory].self, from: data)
    }

    struct Tag: Decodable {
        let id: Int
        let slug: String
        let count: Int
    }

    /// The arrondissement tags (slugs 75001...75020), keyed by arrondissement number.
    static func arrondissementTags() async throws -> [Int: Tag] {
        let slugs = (1...20).map { String(75000 + $0) }.joined(separator: ",")
        let items = [
            URLQueryItem(name: "slug", value: slugs),
            URLQueryItem(name: "per_page", value: "30"),
            URLQueryItem(name: "_fields", value: "id,slug,count"),
        ]
        let (data, _) = try await fetch(url(path: "tags", items: items))
        let tags = try JSONDecoder().decode([Tag].self, from: data)
        return Dictionary(tags.compactMap { tag in Int(tag.slug).map { ($0 - 75000, tag) } }) { first, _ in first }
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
