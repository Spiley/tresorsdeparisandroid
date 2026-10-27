import SwiftUI

struct Arrondissement: Identifiable, Decodable, Hashable {
    let number: Int
    let name: String
    let label: [Double]
    let rings: [[Double]]

    var id: Int { number }

    var ordinal: String {
        switch number {
        case 1: "1st"
        case 2: "2nd"
        case 3: "3rd"
        default: "\(number)th"
        }
    }

    var title: String { "\(ordinal) arrondissement" }
    var labelPoint: CGPoint { CGPoint(x: label[0], y: label[1]) }

    static func == (lhs: Self, rhs: Self) -> Bool { lhs.number == rhs.number }
    func hash(into hasher: inout Hasher) { hasher.combine(number) }
}

struct Park: Decodable {
    let name: String
    let kind: String
    let label: [Double]
    let rings: [[Double]]

    var isWood: Bool { kind == "wood" }
    var isCemetery: Bool { kind == "cemetery" }
}

struct Landmark: Decodable {
    let name: String
    let point: [Double]
}

/// Paris map data from Paris Open Data, pre-projected to a `width` x `height` plane.
struct ParisMapData: Decodable {
    let width: Double
    let height: Double
    let arrondissements: [Arrondissement]
    /// The Seine through Paris as flat x,y pairs.
    let seine: [Double]
    let parks: [Park]
    let landmarks: [Landmark]
    let attribution: String

    static let shared: ParisMapData = {
        let url = Bundle.main.url(forResource: "arrondissements", withExtension: "json")!
        return try! JSONDecoder().decode(ParisMapData.self, from: Data(contentsOf: url))
    }()

    private static func points(_ flat: [Double]) -> [CGPoint] {
        stride(from: 0, to: flat.count - 1, by: 2).map { CGPoint(x: flat[$0], y: flat[$0 + 1]) }
    }

    private static func path(_ rings: [[Double]]) -> Path {
        var path = Path()
        for ring in rings {
            path.addLines(points(ring))
            path.closeSubpath()
        }
        return path
    }

    /// Outlines in map coordinates, keyed by arrondissement number.
    static let arrondissementPaths: [Int: Path] = Dictionary(
        uniqueKeysWithValues: shared.arrondissements.map { ($0.number, path($0.rings)) }
    )
    static let parkPaths: [Path] = shared.parks.map { path($0.rings) }
    static let seinePath: Path = {
        var path = Path()
        path.addLines(points(shared.seine))
        return path
    }()
}

/// Maps between map coordinates and view coordinates: view = offset + zoom * (origin + point * fit).
private struct MapTransform {
    let size: CGSize
    let fit: CGFloat
    let origin: CGPoint
    var zoom: CGFloat
    var offset: CGPoint

    init(size: CGSize, map: ParisMapData, zoom: CGFloat, offset: CGPoint) {
        self.size = size
        fit = min(size.width / map.width, size.height / map.height)
        origin = CGPoint(x: (size.width - map.width * fit) / 2, y: (size.height - map.height * fit) / 2)
        self.zoom = zoom
        self.offset = offset
    }

    func toView(_ p: CGPoint) -> CGPoint {
        CGPoint(x: offset.x + zoom * (origin.x + p.x * fit), y: offset.y + zoom * (origin.y + p.y * fit))
    }

    func toMap(_ v: CGPoint) -> CGPoint {
        CGPoint(x: ((v.x - offset.x) / zoom - origin.x) / fit, y: ((v.y - offset.y) / zoom - origin.y) / fit)
    }

    /// Keeps the map on screen; content smaller than the view can still slide above the bottom card.
    func clamped(_ o: CGPoint, zoom z: CGFloat, map: ParisMapData, bottomInset: CGFloat) -> CGPoint {
        let contentWidth = z * map.width * fit
        let contentHeight = z * map.height * fit
        let left = o.x + z * origin.x
        let top = o.y + z * origin.y
        let clampedLeft = min(max(left, min(0, size.width - contentWidth)), max(0, size.width - contentWidth))
        let clampedTop = min(max(top, min(0, size.height - contentHeight) - bottomInset), max(0, size.height - contentHeight))
        return CGPoint(x: clampedLeft - z * origin.x, y: clampedTop - z * origin.y)
    }

    /// Offset that puts map point `p` at view point `target` at zoom `z`.
    func offset(placing p: CGPoint, at target: CGPoint, zoom z: CGFloat) -> CGPoint {
        CGPoint(x: target.x - z * (origin.x + p.x * fit), y: target.y - z * (origin.y + p.y * fit))
    }
}

/// A zoomable, detailed map of Paris. Tapping an arrondissement selects it and zooms to it.
struct ParisMapView: View {
    let counts: [Int: Int]
    @Binding var selected: Arrondissement?
    var bottomInset: CGFloat = 0

    @State private var zoom: CGFloat = 0 // 0 until the first layout
    @State private var offset: CGPoint = .zero
    @State private var gestureStart: (zoom: CGFloat, offset: CGPoint)?

    private let map = ParisMapData.shared

    /// Starts zoomed in on central Paris so the map fills the screen. Waits for a real size:
    /// the first layout can report zero, which would make the zoom NaN and the map blank.
    private func setUpCamera(in size: CGSize) {
        guard zoom == 0, size.width > 0, size.height > 0 else { return }
        let fit = MapTransform(size: size, map: map, zoom: 1, offset: .zero)
        let start = min(max(size.height * 0.6 / (map.height * fit.fit), 1), 2.2)
        guard start.isFinite else { return }
        zoom = start
        offset = fit.clamped(
            fit.offset(
                placing: CGPoint(x: map.width * 0.53, y: map.height * 0.5),
                at: CGPoint(x: size.width / 2, y: size.height * 0.48),
                zoom: start
            ),
            zoom: start, map: map, bottomInset: bottomInset
        )
    }

    var body: some View {
        GeometryReader { geo in
            let t = MapTransform(size: geo.size, map: map, zoom: max(zoom, 1), offset: offset)

            MapCanvas(counts: counts, selected: selected, size: geo.size, zoom: max(zoom, 1), offsetX: offset.x, offsetY: offset.y)
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture().onEnded { value in
                        let point = t.toMap(value.location)
                        selected = map.arrondissements.first {
                            ParisMapData.arrondissementPaths[$0.number]?.contains(point) == true
                        }
                    }
                )
                .simultaneousGesture(
                    MagnifyGesture()
                        .onChanged { value in
                            let start = gestureStart ?? (t.zoom, offset)
                            if gestureStart == nil { gestureStart = start }
                            let newZoom = min(max(start.zoom * value.magnification, 1), 6)
                            let anchor = value.startLocation
                            let factor = newZoom / start.zoom
                            let moved = CGPoint(
                                x: anchor.x - (anchor.x - start.offset.x) * factor,
                                y: anchor.y - (anchor.y - start.offset.y) * factor
                            )
                            zoom = newZoom
                            offset = t.clamped(moved, zoom: newZoom, map: map, bottomInset: bottomInset)
                        }
                        .onEnded { _ in gestureStart = nil }
                )
                .simultaneousGesture(
                    DragGesture(minimumDistance: 8)
                        .onChanged { value in
                            let start = gestureStart ?? (t.zoom, offset)
                            if gestureStart == nil { gestureStart = start }
                            let moved = CGPoint(
                                x: start.offset.x + value.translation.width,
                                y: start.offset.y + value.translation.height
                            )
                            offset = t.clamped(moved, zoom: t.zoom, map: map, bottomInset: bottomInset)
                        }
                        .onEnded { _ in gestureStart = nil }
                )
                .onAppear { setUpCamera(in: geo.size) }
                .onChange(of: geo.size) { setUpCamera(in: geo.size) }
                .onChange(of: selected) {
                    // Bring the selection into view above the card
                    guard let selected else { return }
                    let target = max(t.zoom, 2)
                    withAnimation(.spring(duration: 0.45)) {
                        zoom = target
                        offset = t.clamped(
                            t.offset(
                                placing: selected.labelPoint,
                                at: CGPoint(x: geo.size.width / 2, y: (geo.size.height - bottomInset) / 2),
                                zoom: target
                            ),
                            zoom: target, map: map, bottomInset: bottomInset
                        )
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    if zoom > 1.05 {
                        Button {
                            withAnimation(.spring(duration: 0.45)) {
                                zoom = 1
                                offset = t.clamped(.zero, zoom: 1, map: map, bottomInset: bottomInset)
                            }
                        } label: {
                            Image(systemName: "arrow.down.right.and.arrow.up.left")
                                .font(.body.weight(.semibold))
                                .frame(width: 44, height: 44)
                                .background(.regularMaterial, in: Circle())
                                .shadow(radius: 4, y: 2)
                        }
                        .accessibilityLabel("Show all of Paris")
                        .padding(.trailing, 16)
                        .padding(.bottom, 16 + bottomInset)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Map of Paris and its 20 arrondissements")
                .accessibilityHint("Use the list button to choose an arrondissement")
        }
    }
}

/// Draws the map. Animatable so zoom and pan changes animate smoothly.
private struct MapCanvas: View, Animatable {
    let counts: [Int: Int]
    let selected: Arrondissement?
    let size: CGSize
    var zoom: CGFloat
    var offsetX: CGFloat
    var offsetY: CGFloat

    @Environment(\.colorScheme) private var colorScheme
    private let map = ParisMapData.shared

    var animatableData: AnimatablePair<CGFloat, AnimatablePair<CGFloat, CGFloat>> {
        get { AnimatablePair(zoom, AnimatablePair(offsetX, offsetY)) }
        set {
            zoom = newValue.first
            offsetX = newValue.second.first
            offsetY = newValue.second.second
        }
    }

    private struct Palette {
        let land, landBusy, border, water, wood, park, cemetery, text, halo, parkText: Color
    }

    private var palette: Palette {
        colorScheme == .dark
            ? Palette(
                land: Color(red: 0.165, green: 0.149, blue: 0.129), landBusy: Color(red: 0.478, green: 0.361, blue: 0.133),
                border: Color(red: 0.07, green: 0.07, blue: 0.07), water: Color(red: 0.184, green: 0.337, blue: 0.475),
                wood: Color(red: 0.204, green: 0.314, blue: 0.227), park: Color(red: 0.227, green: 0.353, blue: 0.251),
                cemetery: Color(red: 0.231, green: 0.275, blue: 0.224), text: Color(red: 0.949, green: 0.910, blue: 0.835),
                halo: Color(red: 0.102, green: 0.090, blue: 0.078), parkText: Color(red: 0.612, green: 0.780, blue: 0.604)
            )
            : Palette(
                land: Color(red: 0.969, green: 0.922, blue: 0.827), landBusy: Color(red: 0.886, green: 0.725, blue: 0.361),
                border: .white, water: Color(red: 0.612, green: 0.776, blue: 0.918),
                wood: Color(red: 0.702, green: 0.812, blue: 0.639), park: Color(red: 0.659, green: 0.800, blue: 0.596),
                cemetery: Color(red: 0.765, green: 0.812, blue: 0.729), text: Color(red: 0.231, green: 0.176, blue: 0.078),
                halo: Color(red: 1, green: 0.984, blue: 0.949), parkText: Color(red: 0.247, green: 0.420, blue: 0.227)
            )
    }

    var body: some View {
        Canvas { context, _ in
            guard size.width > 0, size.height > 0, zoom.isFinite, offsetX.isFinite, offsetY.isFinite else { return }
            let p = palette
            let accent = Color.accentColor
            let t = MapTransform(size: size, map: map, zoom: zoom, offset: CGPoint(x: offsetX, y: offsetY))
            let scale = t.zoom * t.fit
            let px = 1 / scale // one screen point in map units
            let maxCount = Double(max(counts.values.max() ?? 0, 1))

            var mapContext = context
            mapContext.translateBy(x: t.offset.x + t.zoom * t.origin.x, y: t.offset.y + t.zoom * t.origin.y)
            mapContext.scaleBy(x: scale, y: scale)

            for arrondissement in map.arrondissements {
                guard let path = ParisMapData.arrondissementPaths[arrondissement.number] else { continue }
                let share = Double(counts[arrondissement.number] ?? 0) / maxCount
                mapContext.fill(path, with: .color(p.land))
                mapContext.fill(path, with: .color(p.landBusy.opacity(0.15 + 0.6 * share)))
                if arrondissement == selected {
                    mapContext.fill(path, with: .color(accent.opacity(0.4)))
                }
            }
            for (park, path) in zip(map.parks, ParisMapData.parkPaths) {
                mapContext.fill(path, with: .color(park.isWood ? p.wood : park.isCemetery ? p.cemetery : p.park))
            }
            for path in ParisMapData.arrondissementPaths.values {
                mapContext.stroke(path, with: .color(p.border), style: StrokeStyle(lineWidth: 2 * px, lineJoin: .round))
            }
            mapContext.stroke(
                ParisMapData.seinePath, with: .color(p.water),
                style: StrokeStyle(lineWidth: max(6, 3 * px), lineCap: .round, lineJoin: .round)
            )
            if let selected, let path = ParisMapData.arrondissementPaths[selected.number] {
                mapContext.stroke(path, with: .color(accent), style: StrokeStyle(lineWidth: 3 * px, lineJoin: .round))
            }

            // Labels are drawn unscaled so they stay readable at every zoom level. Arrondissement
            // labels claim their space first; landmark and park names that would overlap are skipped.
            var placed: [CGRect] = []
            var labels = context
            labels.addFilter(.shadow(color: p.halo, radius: 1.2))
            labels.addFilter(.shadow(color: p.halo, radius: 1.2))

            func draw(_ text: Text, at center: CGPoint, force: Bool = false) {
                let resolved = labels.resolve(text)
                let size = resolved.measure(in: CGSize(width: 400, height: 100))
                let rect = CGRect(x: center.x - size.width / 2, y: center.y - size.height / 2, width: size.width, height: size.height)
                    .insetBy(dx: -2, dy: -2)
                if !force && placed.contains(where: { $0.intersects(rect) }) { return }
                placed.append(rect)
                labels.draw(resolved, at: center)
            }

            for arrondissement in map.arrondissements {
                let point = t.toView(arrondissement.labelPoint)
                draw(
                    Text("\(arrondissement.number)").font(.system(size: 16, weight: .bold, design: .serif)).foregroundStyle(p.text),
                    at: point, force: true
                )
                if t.zoom >= 1.6 {
                    draw(
                        Text(arrondissement.name).font(.system(size: 11)).foregroundStyle(p.text.opacity(0.75)),
                        at: CGPoint(x: point.x, y: point.y + 16), force: true
                    )
                }
            }
            if t.zoom >= 1.9 {
                for landmark in map.landmarks {
                    let point = t.toView(CGPoint(x: landmark.point[0], y: landmark.point[1]))
                    context.fill(Path(ellipseIn: CGRect(x: point.x - 4.5, y: point.y - 4.5, width: 9, height: 9)), with: .color(p.halo))
                    context.fill(Path(ellipseIn: CGRect(x: point.x - 3, y: point.y - 3, width: 6, height: 6)), with: .color(accent))
                    let text = Text(landmark.name).font(.system(size: 11, weight: .medium)).foregroundStyle(p.text)
                    let width = labels.resolve(text).measure(in: CGSize(width: 400, height: 100)).width
                    draw(text, at: CGPoint(x: point.x + 7 + width / 2, y: point.y))
                }
            }
            for park in map.parks where park.isWood || t.zoom >= 2.6 {
                draw(
                    Text(park.name).font(.system(size: 11).italic()).foregroundStyle(p.parkText),
                    at: t.toView(CGPoint(x: park.label[0], y: park.label[1]))
                )
            }
        }
    }
}

struct MapView: View {
    @State private var tags: [Int: WordPressAPI.Tag] = [:]
    @State private var tagsFailed = false
    // `-selectArrondissement N` as a launch argument preselects one (used for simulator screenshots)
    @State private var selected: Arrondissement? = ParisMapData.shared.arrondissements.first {
        $0.number == UserDefaults.standard.integer(forKey: "selectArrondissement")
    }
    @State private var showList = false
    @State private var path = NavigationPath()
    private let map = ParisMapData.shared
    private let cardHeight: CGFloat = 300

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Color(uiColor: .systemBackground).ignoresSafeArea()

                ParisMapView(
                    counts: tags.mapValues(\.count),
                    selected: $selected.animation(.spring(duration: 0.4)),
                    bottomInset: selected == nil ? 0 : cardHeight
                )
                .ignoresSafeArea(edges: .top)
            }
            .overlay(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Explore Paris").font(.title2.bold()).fontDesign(.serif)
                    Text("Tap an arrondissement").font(.caption).foregroundStyle(.secondary)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
                .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
                .padding(16)
            }
            .overlay(alignment: .topTrailing) {
                Button { showList = true } label: {
                    Image(systemName: "list.bullet")
                        .font(.body.weight(.semibold))
                        .frame(width: 48, height: 48)
                        .background(.regularMaterial, in: Circle())
                        .shadow(color: .black.opacity(0.12), radius: 8, y: 3)
                }
                .accessibilityLabel("All arrondissements")
                .padding(16)
            }
            .overlay(alignment: .bottomLeading) {
                if selected == nil {
                    Text(map.attribution)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .padding(12)
                }
            }
            .overlay(alignment: .bottom) {
                if let selected {
                    ArrondissementCard(
                        arrondissement: selected,
                        tag: tags[selected.number],
                        tagsFailed: tagsFailed,
                        onClose: { withAnimation(.spring(duration: 0.4)) { self.selected = nil } }
                    )
                    .frame(height: cardHeight)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Arrondissement.self) {
                ArrondissementPostsView(arrondissement: $0, tagID: tags[$0.number]?.id)
            }
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
            .sheet(isPresented: $showList) {
                ArrondissementList(tags: tags) { arrondissement in
                    showList = false
                    path.append(arrondissement)
                }
                .presentationDetents([.medium, .large])
            }
            .task {
                if tags.isEmpty { await loadTags() }
            }
        }
    }

    private func loadTags() async {
        do {
            tags = try await WordPressAPI.arrondissementTags()
            tagsFailed = false
        } catch {
            tagsFailed = true
        }
    }
}

private struct NumberBadge: View {
    let number: Int

    var body: some View {
        Text("\(number)")
            .font(.headline)
            .foregroundStyle(.white)
            .frame(width: 36, height: 36)
            .background(Color.accentColor, in: Circle())
    }
}

/// The bottom card for a selected arrondissement with a preview of its articles.
private struct ArrondissementCard: View {
    let arrondissement: Arrondissement
    let tag: WordPressAPI.Tag?
    let tagsFailed: Bool
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                NumberBadge(number: arrondissement.number)
                VStack(alignment: .leading, spacing: 2) {
                    Text(arrondissement.title).font(.title3.bold()).fontDesign(.serif)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(.secondary)
                        .frame(width: 30, height: 30)
                        .background(Color.secondary.opacity(0.15), in: Circle())
                }
                .accessibilityLabel("Close")
            }
            .padding(.horizontal, 20)

            Group {
                if let tag {
                    ArticlePreviewRow(model: PostListModel.shared(for: .tag(tag.id)))
                } else if tagsFailed {
                    Text("No connection. Try again later.").foregroundStyle(.secondary)
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if let tag {
                NavigationLink(value: arrondissement) {
                    Text("See all \(tag.count) \(tag.count == 1 ? "article" : "articles")")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.accentColor, in: Capsule())
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 20)
            }
        }
        .padding(.vertical, 18)
        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 28))
        .shadow(color: .black.opacity(0.18), radius: 16, y: 6)
    }

    private var subtitle: String {
        guard let tag else { return arrondissement.name }
        return "\(arrondissement.name) · \(tag.count) \(tag.count == 1 ? "article" : "articles")"
    }
}

private struct ArticlePreviewRow: View {
    let model: PostListModel

    var body: some View {
        Group {
            if !model.posts.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 12) {
                        ForEach(model.posts.prefix(10)) { post in
                            NavigationLink(value: post) {
                                VStack(alignment: .leading, spacing: 6) {
                                    PostImage(url: post.imageURL)
                                        .frame(width: 170, height: 104)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                    Text(post.title)
                                        .font(.footnote.weight(.semibold))
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                        .foregroundStyle(.primary)
                                }
                                .frame(width: 170, alignment: .leading)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            } else if model.failed {
                Text("No connection. Try again later.").foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .task {
            if model.posts.isEmpty && !model.isLoading { await model.refresh() }
        }
    }
}

private struct ArrondissementList: View {
    let tags: [Int: WordPressAPI.Tag]
    let onSelect: (Arrondissement) -> Void

    var body: some View {
        NavigationStack {
            List(ParisMapData.shared.arrondissements) { arrondissement in
                Button { onSelect(arrondissement) } label: {
                    HStack(spacing: 14) {
                        NumberBadge(number: arrondissement.number)
                        VStack(alignment: .leading) {
                            Text(arrondissement.title).foregroundStyle(.primary)
                            Text(arrondissement.name).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let count = tags[arrondissement.number]?.count {
                            Text("\(count)").foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Arrondissements")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct ArrondissementPostsView: View {
    let arrondissement: Arrondissement
    @State private var model: PostListModel?
    @State private var failed = false
    @State private var noArticles = false

    init(arrondissement: Arrondissement, tagID: Int?) {
        self.arrondissement = arrondissement
        _model = State(initialValue: tagID.map { PostListModel.shared(for: .tag($0)) })
    }

    var body: some View {
        Group {
            if let model {
                PostListView(model: model, emptyMessage: "No articles about the \(arrondissement.ordinal) yet.")
            } else if noArticles {
                ContentUnavailableView("No articles about the \(arrondissement.ordinal) yet.", systemImage: "map")
            } else if failed {
                OfflineView { Task { await resolveTag() } }
            } else {
                ProgressView().controlSize(.large)
            }
        }
        .navigationTitle(arrondissement.title)
        .toolbar(.visible, for: .navigationBar)
        .task {
            if model == nil { await resolveTag() }
        }
    }

    /// Looks up the tag when the map opened before the tags had loaded (e.g. offline).
    private func resolveTag() async {
        failed = false
        do {
            let tags = try await WordPressAPI.arrondissementTags()
            if let id = tags[arrondissement.number]?.id {
                model = PostListModel.shared(for: .tag(id))
            } else {
                noArticles = true
            }
        } catch {
            failed = true
        }
    }
}
