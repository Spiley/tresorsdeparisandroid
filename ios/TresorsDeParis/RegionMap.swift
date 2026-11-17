import SwiftUI

/// A department ("77") or a Paris arrondissement ("75008").
struct MapArea: Identifiable, Decodable, Hashable {
    /// The start of the postcode tags that belong to this area.
    let code: String
    let badge: String
    let title: String
    let name: String
    /// How the area is named in a sentence, like "the 8th".
    let short: String
    let label: [Double]
    let bounds: [Double]
    let tone: Int
    let rings: [[Double]]

    var id: String { code }
    var isDepartment: Bool { code.count == 2 }
    var labelPoint: CGPoint { CGPoint(x: label[0], y: label[1]) }

    static func == (lhs: Self, rhs: Self) -> Bool { lhs.code == rhs.code }
    func hash(into hasher: inout Hasher) { hasher.combine(code) }

    /// The postcode tags of this area: all of a department's, or the one of an arrondissement.
    func tags(in tags: [WordPressAPI.Tag]) -> [WordPressAPI.Tag] {
        tags.filter { $0.code?.hasPrefix(code) == true }
    }
}

struct Park: Decodable {
    let name: String
    let kind: String
    let label: [Double]
    let rings: [[Double]]

    var isWood: Bool { kind == "wood" }
    var isCemetery: Bool { kind == "cemetery" }
}

struct MapPlace: Decodable {
    let name: String
    let point: [Double]

    var location: CGPoint { CGPoint(x: point[0], y: point[1]) }
}

/// Map of Île-de-France, pre-projected to a `width` x `height` plane.
struct RegionMapData: Decodable {
    let width: Double
    let height: Double
    /// Left, top, right and bottom of Paris.
    let parisBounds: [Double]
    let departments: [MapArea]
    let arrondissements: [MapArea]
    /// Rivers as flat x,y pairs.
    let rivers: [[Double]]
    let parks: [Park]
    let landmarks: [MapPlace]
    let towns: [MapPlace]
    let attribution: String

    var parisWidth: Double { parisBounds[2] - parisBounds[0] }
    var areas: [MapArea] { departments + arrondissements }
    var outerDepartments: [MapArea] { departments.filter { $0.code != "75" } }

    static let shared: RegionMapData = {
        let url = Bundle.main.url(forResource: "ile-de-france", withExtension: "json")!
        return try! JSONDecoder().decode(RegionMapData.self, from: Data(contentsOf: url))
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

    /// Outlines in map coordinates, keyed by area code.
    static let areaPaths: [String: Path] = Dictionary(
        uniqueKeysWithValues: shared.areas.map { ($0.code, path($0.rings)) }
    )
    static let parkPaths: [Path] = shared.parks.map { path($0.rings) }
    static let riverPath: Path = {
        var path = Path()
        for river in shared.rivers {
            path.addLines(points(river))
        }
        return path
    }()
}

/// Width of Paris on screen, in points, from which each map detail shows.
private enum ParisWidth {
    static let tappableArrondissements: CGFloat = 180
    static let thinArrondissementBorders: CGFloat = 80
    static let arrondissementNumbers: CGFloat = 240
    static let allArrondissementNumbers: CGFloat = 300
    static let arrondissementNames: CGFloat = 620
    static let landmarks: CGFloat = 740
    static let smallParks: CGFloat = 1010
    static let townsUntil: CGFloat = 700
}

/// The most the map can be enlarged: far enough to see the Paris landmarks.
private let maxZoom: CGFloat = 40

/// Maps between map coordinates and view coordinates: view = offset + zoom * (origin + point * fit).
private struct MapTransform {
    let size: CGSize
    let fit: CGFloat
    let origin: CGPoint
    var zoom: CGFloat
    var offset: CGPoint

    init(size: CGSize, map: RegionMapData, zoom: CGFloat, offset: CGPoint) {
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
    func clamped(_ o: CGPoint, zoom z: CGFloat, map: RegionMapData, bottomInset: CGFloat) -> CGPoint {
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

/// A zoomable map of Île-de-France. Tapping a department selects it and zooms to it. Once Paris
/// is large enough, tapping it selects an arrondissement.
struct RegionMapView: View {
    let counts: [String: Int]
    @Binding var selected: MapArea?
    var bottomInset: CGFloat = 0

    @State private var zoom: CGFloat = 0 // 0 until the first layout
    @State private var offset: CGPoint = .zero
    @State private var gestureStart: (zoom: CGFloat, offset: CGPoint)?

    private let map = RegionMapData.shared

    /// Starts with the whole region. Waits for a real size: the first layout can report zero,
    /// which would make the zoom NaN and the map blank.
    private func setUpCamera(in size: CGSize) {
        guard zoom == 0, size.width > 0, size.height > 0 else { return }
        zoom = 1
        offset = MapTransform(size: size, map: map, zoom: 1, offset: .zero)
            .clamped(.zero, zoom: 1, map: map, bottomInset: bottomInset)
        // A preselected area (simulator screenshots) appeared before there was a size to zoom to
        if let selected { focus(on: selected, in: size, animated: false) }
    }

    /// Brings an area into view above the bottom card.
    private func focus(on area: MapArea, in size: CGSize, animated: Bool = true) {
        let t = MapTransform(size: size, map: map, zoom: max(zoom, 1), offset: offset)
        let visible = size.height - bottomInset
        let target: CGFloat
        let center: CGPoint
        if area.isDepartment {
            let b = area.bounds
            let fitted = min(size.width / ((b[2] - b[0]) * t.fit), visible / ((b[3] - b[1]) * t.fit)) * 0.88
            target = min(max(fitted, 1), maxZoom)
            center = CGPoint(x: (b[0] + b[2]) / 2, y: (b[1] + b[3]) / 2)
        } else {
            target = max(t.zoom, ParisWidth.arrondissementNames / (t.fit * map.parisWidth))
            center = area.labelPoint
        }
        let moved = t.clamped(
            t.offset(placing: center, at: CGPoint(x: size.width / 2, y: visible / 2), zoom: target),
            zoom: target, map: map, bottomInset: bottomInset
        )
        let animation: Animation? = animated ? .spring(duration: 0.45) : nil
        withAnimation(animation) {
            zoom = target
            offset = moved
        }
    }

    var body: some View {
        GeometryReader { geo in
            let t = MapTransform(size: geo.size, map: map, zoom: max(zoom, 1), offset: offset)

            MapCanvas(counts: counts, selected: selected, size: geo.size, zoom: max(zoom, 1), offsetX: offset.x, offsetY: offset.y)
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture().onEnded { value in
                        let point = t.toMap(value.location)
                        func hit(_ area: MapArea) -> Bool {
                            RegionMapData.areaPaths[area.code]?.contains(point) == true
                        }
                        let paris = t.zoom * t.fit * map.parisWidth
                        let arrondissement = paris >= ParisWidth.tappableArrondissements
                            ? map.arrondissements.first(where: hit) : nil
                        selected = arrondissement ?? map.departments.first(where: hit)
                    }
                )
                .simultaneousGesture(
                    MagnifyGesture()
                        .onChanged { value in
                            let start = gestureStart ?? (t.zoom, offset)
                            if gestureStart == nil { gestureStart = start }
                            let newZoom = min(max(start.zoom * value.magnification, 1), maxZoom)
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
                    if let selected { focus(on: selected, in: geo.size) }
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
                        .accessibilityLabel("Show all of Île-de-France".translatedForApp)
                        .padding(.trailing, 16)
                        .padding(.bottom, 16 + bottomInset)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Map of Île-de-France with its departments and the arrondissements of Paris. Use the list button to choose one.".translatedForApp)
        }
    }
}

/// Draws the map. Animatable so zoom and pan changes animate smoothly.
private struct MapCanvas: View, Animatable {
    let counts: [String: Int]
    let selected: MapArea?
    let size: CGSize
    var zoom: CGFloat
    var offsetX: CGFloat
    var offsetY: CGFloat

    @Environment(\.colorScheme) private var colorScheme
    private let map = RegionMapData.shared

    var animatableData: AnimatablePair<CGFloat, AnimatablePair<CGFloat, CGFloat>> {
        get { AnimatablePair(zoom, AnimatablePair(offsetX, offsetY)) }
        set {
            zoom = newValue.first
            offsetX = newValue.second.first
            offsetY = newValue.second.second
        }
    }

    private struct Palette {
        let land, outside, landBusy, border, water, wood, park, cemetery, text, halo, parkText: Color
    }

    private var palette: Palette {
        colorScheme == .dark
            ? Palette(
                land: Color(red: 0.165, green: 0.149, blue: 0.129), outside: Color(red: 0.106, green: 0.098, blue: 0.086), landBusy: Color(red: 0.478, green: 0.361, blue: 0.133),
                border: Color(red: 0.07, green: 0.07, blue: 0.07), water: Color(red: 0.184, green: 0.337, blue: 0.475),
                wood: Color(red: 0.204, green: 0.314, blue: 0.227), park: Color(red: 0.227, green: 0.353, blue: 0.251),
                cemetery: Color(red: 0.231, green: 0.275, blue: 0.224), text: Color(red: 0.949, green: 0.910, blue: 0.835),
                halo: Color(red: 0.102, green: 0.090, blue: 0.078), parkText: Color(red: 0.612, green: 0.780, blue: 0.604)
            )
            : Palette(
                land: Color(red: 0.969, green: 0.922, blue: 0.827), outside: Color(red: 0.937, green: 0.929, blue: 0.910), landBusy: Color(red: 0.886, green: 0.725, blue: 0.361),
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
            let paris = scale * map.parisWidth // how wide Paris is on screen
            let parisSelected = selected?.code == "75"
            let maxCount = Double(max((map.arrondissements + map.outerDepartments).map { counts[$0.code] ?? 0 }.max() ?? 0, 1))

            var mapContext = context
            mapContext.translateBy(x: t.offset.x + t.zoom * t.origin.x, y: t.offset.y + t.zoom * t.origin.y)
            mapContext.scaleBy(x: scale, y: scale)

            for department in map.outerDepartments {
                guard let path = RegionMapData.areaPaths[department.code] else { continue }
                let share = Double(counts[department.code] ?? 0) / maxCount
                mapContext.fill(path, with: .color(p.land))
                mapContext.fill(path, with: .color(p.landBusy.opacity(0.08 + 0.1 * Double(department.tone) + 0.5 * share)))
                if department == selected {
                    mapContext.fill(path, with: .color(accent.opacity(0.4)))
                }
            }
            for arrondissement in map.arrondissements {
                guard let path = RegionMapData.areaPaths[arrondissement.code] else { continue }
                let share = Double(counts[arrondissement.code] ?? 0) / maxCount
                mapContext.fill(path, with: .color(p.land))
                mapContext.fill(path, with: .color(p.landBusy.opacity(0.15 + 0.6 * share)))
                if arrondissement == selected {
                    mapContext.fill(path, with: .color(accent.opacity(0.4)))
                } else if parisSelected {
                    mapContext.fill(path, with: .color(accent.opacity(0.2)))
                }
            }
            for (park, path) in zip(map.parks, RegionMapData.parkPaths) {
                mapContext.fill(path, with: .color(park.isWood ? p.wood : park.isCemetery ? p.cemetery : p.park))
            }
            if paris >= ParisWidth.thinArrondissementBorders {
                let width = (paris >= ParisWidth.allArrondissementNumbers ? 2 : 0.75) * px
                for arrondissement in map.arrondissements {
                    guard let path = RegionMapData.areaPaths[arrondissement.code] else { continue }
                    mapContext.stroke(path, with: .color(p.border), style: StrokeStyle(lineWidth: width, lineJoin: .round))
                }
            }
            for department in map.departments {
                guard let path = RegionMapData.areaPaths[department.code] else { continue }
                mapContext.stroke(path, with: .color(p.border), style: StrokeStyle(lineWidth: 1.5 * px, lineJoin: .round))
            }
            mapContext.stroke(
                RegionMapData.riverPath, with: .color(p.water),
                style: StrokeStyle(lineWidth: max(0.9, 1.5 * px), lineCap: .round, lineJoin: .round)
            )
            if let selected, let path = RegionMapData.areaPaths[selected.code] {
                mapContext.stroke(path, with: .color(accent), style: StrokeStyle(lineWidth: 3 * px, lineJoin: .round))
            }

            // Labels are drawn unscaled so they stay readable at every zoom level. Arrondissement
            // and department labels claim their space first; names that would overlap are skipped.
            var placed: [CGRect] = []
            var labels = context
            labels.addFilter(.shadow(color: p.halo, radius: 1.2))
            labels.addFilter(.shadow(color: p.halo, radius: 1.2))
            let screen = CGRect(origin: .zero, size: size).insetBy(dx: -size.width, dy: -100)

            @discardableResult
            func draw(_ text: Text, at center: CGPoint, force: Bool = false) -> Bool {
                guard screen.contains(center) else { return false }
                let resolved = labels.resolve(text)
                let size = resolved.measure(in: CGSize(width: 400, height: 100))
                let rect = CGRect(x: center.x - size.width / 2, y: center.y - size.height / 2, width: size.width, height: size.height)
                    .insetBy(dx: -2, dy: -2)
                if !force && placed.contains(where: { $0.intersects(rect) }) { return false }
                placed.append(rect)
                labels.draw(resolved, at: center)
                return true
            }

            func number(_ badge: String) -> Text {
                Text(badge).font(.system(size: 16, weight: .bold, design: .serif)).foregroundStyle(p.text)
            }

            func name(_ name: String) -> Text {
                Text(name).font(.system(size: 11)).foregroundStyle(p.text.opacity(0.75))
            }

            func drawPlace(_ place: MapPlace) {
                let point = t.toView(place.location)
                let text = Text(place.name.translatedForApp).font(.system(size: 11, weight: .medium)).foregroundStyle(p.text)
                let width = labels.resolve(text).measure(in: CGSize(width: 400, height: 100)).width
                if draw(text, at: CGPoint(x: point.x + 7 + width / 2, y: point.y)) {
                    context.fill(Path(ellipseIn: CGRect(x: point.x - 4.5, y: point.y - 4.5, width: 9, height: 9)), with: .color(p.halo))
                    context.fill(Path(ellipseIn: CGRect(x: point.x - 3, y: point.y - 3, width: 6, height: 6)), with: .color(accent))
                }
            }

            if paris >= ParisWidth.arrondissementNumbers {
                for arrondissement in map.arrondissements {
                    let point = t.toView(arrondissement.labelPoint)
                    draw(number(arrondissement.badge), at: point, force: paris >= ParisWidth.allArrondissementNumbers)
                    if paris >= ParisWidth.arrondissementNames {
                        draw(name(arrondissement.name), at: CGPoint(x: point.x, y: point.y + 16), force: true)
                    }
                }
            }
            for department in map.departments where department.code != "75" || paris < ParisWidth.arrondissementNumbers {
                let point = t.toView(department.labelPoint)
                if draw(number(department.badge), at: point) {
                    draw(name(department.title), at: CGPoint(x: point.x, y: point.y + 22))
                }
            }
            if paris < ParisWidth.townsUntil {
                for town in map.towns { drawPlace(town) }
            }
            if paris >= ParisWidth.landmarks {
                for landmark in map.landmarks { drawPlace(landmark) }
            }
            for park in map.parks where paris >= (park.isWood ? ParisWidth.allArrondissementNumbers : ParisWidth.smallParks) {
                draw(
                    Text(park.name.translatedForApp).font(.system(size: 11).italic()).foregroundStyle(p.parkText),
                    at: t.toView(CGPoint(x: park.label[0], y: park.label[1]))
                )
            }
        }
        .background(palette.outside)
    }
}

struct MapView: View {
    @State private var tags: [WordPressAPI.Tag] = []
    @State private var tagsFailed = false
    // `-selectArrondissement N` as a launch argument preselects one (used for simulator screenshots)
    @State private var selected: MapArea? = RegionMapData.shared.arrondissements.first {
        $0.code == String(75000 + UserDefaults.standard.integer(forKey: "selectArrondissement"))
    }
    @State private var showList = false
    @State private var path = NavigationPath()
    private let map = RegionMapData.shared
    private let cardHeight: CGFloat = 300

    private var counts: [String: Int] {
        Dictionary(uniqueKeysWithValues: map.areas.map { area in
            (area.code, area.tags(in: tags).reduce(0) { $0 + $1.count })
        })
    }

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                Color(uiColor: .systemBackground).ignoresSafeArea()

                RegionMapView(
                    counts: counts,
                    selected: $selected.animation(.spring(duration: 0.4)),
                    bottomInset: selected == nil ? 0 : cardHeight
                )
                .ignoresSafeArea(edges: .top)
            }
            .overlay(alignment: .topLeading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Île-de-France").font(.title2.bold()).fontDesign(.serif)
                    Text(t("Tap a department or arrondissement")).font(.caption).foregroundStyle(.secondary)
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
                .accessibilityLabel(t("All departments and arrondissements"))
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
                    AreaCard(
                        area: selected,
                        tags: selected.tags(in: tags),
                        tagsLoaded: !tags.isEmpty,
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
            .navigationDestination(for: MapArea.self) { AreaPostsView(area: $0, tags: tags) }
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
            .sheet(isPresented: $showList) {
                AreaList(counts: counts) { area in
                    showList = false
                    path.append(area)
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
            tags = try await WordPressAPI.postalTags()
            tagsFailed = false
        } catch {
            tagsFailed = true
        }
    }
}

private struct NumberBadge: View {
    let badge: String

    var body: some View {
        Text(badge)
            .font(.headline)
            .foregroundStyle(.white)
            .frame(width: 36, height: 36)
            .background(Color.accentColor, in: Circle())
    }
}

/// The bottom card for a selected area with a preview of its articles.
private struct AreaCard: View {
    let area: MapArea
    let tags: [WordPressAPI.Tag]
    let tagsLoaded: Bool
    let tagsFailed: Bool
    let onClose: () -> Void

    /// Articles can have several postcodes, so counts only add up for a single tag.
    private var count: Int? { tags.count == 1 ? tags[0].count : nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                NumberBadge(badge: area.badge)
                VStack(alignment: .leading, spacing: 2) {
                    Text(area.shownTitle).font(.title3.bold()).fontDesign(.serif)
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
                .accessibilityLabel(t("Close"))
            }
            .padding(.horizontal, 20)

            Group {
                if !tags.isEmpty {
                    ArticlePreviewRow(model: PostListModel.shared(for: .tags(tags.map(\.id))))
                } else if tagsFailed {
                    Text(t("No connection. Try again later.")).foregroundStyle(.secondary)
                } else if tagsLoaded {
                    Text(area.noArticlesMessage).foregroundStyle(.secondary)
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            if !tags.isEmpty {
                NavigationLink(value: area) {
                    Text(seeAll)
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
        guard let count else { return area.shownName }
        return "\(area.shownName) · \(plural(count, "%d article", "%d articles"))"
    }

    private var seeAll: String {
        guard let count else { return t("See all articles") }
        return plural(count, "See %d article", "See all %d articles")
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
                                    Text(translated(post.title))
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
                Text(t("No connection. Try again later.")).foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .task {
            if model.posts.isEmpty && !model.isLoading { await model.refresh() }
        }
    }
}

private struct AreaList: View {
    let counts: [String: Int]
    let onSelect: (MapArea) -> Void

    private func section(_ title: String, _ areas: [MapArea]) -> some View {
        Section(title) {
            ForEach(areas) { area in
                Button { onSelect(area) } label: {
                    HStack(spacing: 14) {
                        NumberBadge(badge: area.badge)
                        VStack(alignment: .leading) {
                            Text(area.shownTitle).foregroundStyle(.primary)
                            Text(area.shownName).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let count = counts[area.code], count > 0 {
                            Text("\(count)").foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    var body: some View {
        NavigationStack {
            List {
                section(t("Departments"), RegionMapData.shared.departments)
                section("Paris", RegionMapData.shared.arrondissements)
            }
            .listStyle(.plain)
            .navigationTitle("Île-de-France")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct AreaPostsView: View {
    let area: MapArea
    @State private var model: PostListModel?
    @State private var failed = false
    @State private var noArticles = false

    init(area: MapArea, tags: [WordPressAPI.Tag]) {
        self.area = area
        let ids = area.tags(in: tags).map(\.id)
        _model = State(initialValue: ids.isEmpty ? nil : PostListModel.shared(for: .tags(ids)))
    }

    var body: some View {
        Group {
            if let model {
                PostListView(model: model, emptyMessage: area.noArticlesMessage)
            } else if noArticles {
                ContentUnavailableView(area.noArticlesMessage, systemImage: "map")
            } else if failed {
                OfflineView { Task { await resolveTags() } }
            } else {
                ProgressView().controlSize(.large)
            }
        }
        .navigationTitle(area.shownTitle)
        .toolbar(.visible, for: .navigationBar)
        .task {
            if model == nil { await resolveTags() }
        }
    }

    /// Looks up the tags when the map opened before they had loaded (e.g. offline).
    private func resolveTags() async {
        failed = false
        do {
            let ids = area.tags(in: try await WordPressAPI.postalTags()).map(\.id)
            if ids.isEmpty {
                noArticles = true
            } else {
                model = PostListModel.shared(for: .tags(ids))
            }
        } catch {
            failed = true
        }
    }
}

extension MapArea {
    /// "8th arrondissement", or "8e arrondissement" in French.
    var shownTitle: String {
        guard Lang.shared.isFrench, !isDepartment else { return title }
        return badge == "1" ? "1er arrondissement" : "\(badge)e arrondissement"
    }

    /// "Department 77" or the arrondissement's name, like "Louvre".
    var shownName: String {
        Lang.shared.isFrench && isDepartment ? name.replacingOccurrences(of: "Department", with: "Département") : name
    }

    var noArticlesMessage: String {
        Lang.shared.isFrench ? "Pas encore d'articles sur \(shownTitle)." : "No articles about \(short) yet."
    }
}
