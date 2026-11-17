import Foundation
import Observation

/// Something planned on a day of a trip: a saved article or the traveller's own activity.
/// An article keeps its own copy of the post, so it stays in the plan when it is removed
/// from the favorites.
struct Activity: Codable, Identifiable, Hashable {
    var id = UUID()
    var post: Post?
    /// The name of an own activity; articles use their title.
    var name = ""
    /// Like "20:00", or empty.
    var time = ""
    var location = ""
    var notes = ""

    var title: String { post?.title ?? name }
}

struct TripDay: Codable, Identifiable, Hashable {
    var id = UUID()
    var notes = ""
    var activities: [Activity] = []
}

/// Index of a trip's "Practical info" section, used where a day index is expected.
let infoSection = -1

struct Trip: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var days: [TripDay]
    /// General information that belongs to no day, like how the metro works.
    var info = TripDay()
    var createdAt = Date.now

    var activityCount: Int { days.reduce(0) { $0 + $1.activities.count } }

    /// The practical info followed by all days.
    var sectionIndices: [Int] { [infoSection] + Array(days.indices) }

    /// A day, or the practical info for `infoSection`.
    func section(_ index: Int) -> TripDay? {
        index == infoSection ? info : days[safe: index]
    }

    mutating func setSection(_ index: Int, _ section: TripDay) {
        if index == infoSection { info = section } else if days.indices.contains(index) { days[index] = section }
    }

    /// Where an activity is: its section index and the activity itself.
    func find(_ activityID: UUID) -> (section: Int, activity: Activity)? {
        for index in sectionIndices {
            if let activity = section(index)?.activities.first(where: { $0.id == activityID }) {
                return (index, activity)
            }
        }
        return nil
    }

    init(name: String, days: [TripDay]) {
        self.name = name
        self.days = days
    }

    // Trips saved before the practical info existed have no "info"
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        days = try container.decode([TripDay].self, forKey: .days)
        info = try container.decodeIfPresent(TripDay.self, forKey: .info) ?? TripDay()
        createdAt = try container.decodeIfPresent(Date.self, forKey: .createdAt) ?? .now
    }
}

/// Screens of the trip planner, pushed on the Favorites tab.
enum TripRoute: Hashable {
    case trip(UUID)
    case day(UUID, Int)
    case activity(UUID, UUID)
}

/// "Day 3", or "Practical info" for `infoSection`.
func dayLabel(_ index: Int) -> String {
    index == infoSection ? t("Practical info") : t("Day %d", index + 1)
}

func activitiesLabel(_ count: Int) -> String { plural(count, "%d activity", "%d activities") }

/// The traveller's trips with their day plans. Every change is saved straight away.
@Observable
@MainActor
final class TripStore {
    static let maxDays = 30

    private(set) var trips: [Trip] = []
    private let fileURL = URL.documentsDirectory.appending(path: "trips.json")

    init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([Trip].self, from: data) {
            trips = saved
        }
    }

    func trip(_ id: UUID) -> Trip? {
        trips.first { $0.id == id }
    }

    /// Creates a trip with `dayCount` empty days and returns it.
    @discardableResult
    func create(name: String, dayCount: Int) -> Trip {
        let trip = Trip(name: name, days: (0..<dayCount).map { _ in TripDay() })
        trips.insert(trip, at: 0)
        save()
        return trip
    }

    func delete(_ tripID: UUID) {
        trips.removeAll { $0.id == tripID }
        save()
    }

    func rename(_ tripID: UUID, to name: String) {
        update(tripID) { $0.name = name }
    }

    func addDay(_ tripID: UUID) {
        update(tripID) { $0.days.append(TripDay()) }
    }

    func removeDay(_ tripID: UUID, at index: Int) {
        update(tripID) { trip in
            guard trip.days.indices.contains(index) else { return }
            trip.days.remove(at: index)
        }
    }

    func setNotes(_ tripID: UUID, day: Int, notes: String) {
        updateDay(tripID, day) { $0.notes = notes }
    }

    /// Adds an activity at the end of a day, or replaces the one with the same id.
    func save(_ activity: Activity, in tripID: UUID, day: Int) {
        updateDay(tripID, day) { day in
            if let index = day.activities.firstIndex(where: { $0.id == activity.id }) {
                day.activities[index] = activity
            } else {
                day.activities.append(activity)
            }
        }
    }

    func remove(_ activityID: UUID, from tripID: UUID, day: Int) {
        updateDay(tripID, day) { $0.activities.removeAll { $0.id == activityID } }
    }

    /// Moves an activity one place up (`by` -1) or down (`by` 1) within its day.
    func move(_ activityID: UUID, in tripID: UUID, day: Int, by offset: Int) {
        updateDay(tripID, day) { day in
            guard let from = day.activities.firstIndex(where: { $0.id == activityID }),
                  day.activities.indices.contains(from + offset) else { return }
            day.activities.swapAt(from, from + offset)
        }
    }

    /// Moves an activity to the end of another day or the practical info.
    func move(_ activityID: UUID, in tripID: UUID, from fromDay: Int, to toDay: Int) {
        update(tripID) { trip in
            guard fromDay != toDay, var from = trip.section(fromDay), var to = trip.section(toDay),
                  let index = from.activities.firstIndex(where: { $0.id == activityID }) else { return }
            to.activities.append(from.activities.remove(at: index))
            trip.setSection(fromDay, from)
            trip.setSection(toDay, to)
        }
    }

    /// Changes an activity wherever it is in the trip.
    func change(_ activityID: UUID, in tripID: UUID, _ change: (inout Activity) -> Void) {
        guard let section = trip(tripID)?.find(activityID)?.section else { return }
        updateDay(tripID, section) { day in
            if let index = day.activities.firstIndex(where: { $0.id == activityID }) {
                change(&day.activities[index])
            }
        }
    }

    /// The sections of a trip (days, or `infoSection`) in which an article is planned.
    func days(of tripID: UUID, with postID: Int) -> [Int] {
        guard let trip = trip(tripID) else { return [] }
        return trip.sectionIndices.filter { index in
            trip.section(index)?.activities.contains { $0.post?.id == postID } ?? false
        }
    }

    func isPlanned(_ postID: Int) -> Bool {
        trips.contains { trip in
            trip.sectionIndices.contains { index in
                trip.section(index)?.activities.contains { $0.post?.id == postID } ?? false
            }
        }
    }

    private func update(_ tripID: UUID, _ change: (inout Trip) -> Void) {
        guard let index = trips.firstIndex(where: { $0.id == tripID }) else { return }
        change(&trips[index])
        save()
    }

    private func updateDay(_ tripID: UUID, _ day: Int, _ change: (inout TripDay) -> Void) {
        update(tripID) { trip in
            guard var section = trip.section(day) else { return }
            change(&section)
            trip.setSection(day, section)
        }
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(trips) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
