import SwiftUI

extension Activity {
    /// The activity's name, or the article's title in the chosen language.
    @MainActor var shownTitle: String { post.map { translated($0.title) } ?? name }
}

/// Ready-made subjects offered while the practical info is still empty.
private let infoSuggestions = ["Metro and transport", "Tickets and passes", "Hotel", "Emergency numbers", "Packing list"]

/// Saved articles, and the trips planned with them.
struct FavoritesView: View {
    @Environment(FavoritesStore.self) private var favorites
    @Environment(TripStore.self) private var trips
    @State private var path = NavigationPath()
    @State private var showTrips = false
    @State private var planning: Post?

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Picker(t("Show"), selection: $showTrips) {
                    Text(t("Favorites")).tag(false)
                    Text(t("My trip")).tag(true)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)

                if showTrips {
                    TripsList(path: $path)
                } else if favorites.favorites.isEmpty {
                    ContentUnavailableView(
                        t("No favorites yet"),
                        systemImage: "heart",
                        description: Text(t("Tap the heart on an article to save it here. Saved articles can be read offline and added to your trip."))
                    )
                } else {
                    List {
                        ForEach(favorites.favorites) { favorite in
                            HStack(spacing: 8) {
                                PostRow(post: favorite.post)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                                    .onTapGesture { path.append(favorite.post) }
                                    .accessibilityAddTraits(.isButton)
                                let planned = trips.isPlanned(favorite.post.id)
                                Button {
                                    planning = favorite.post
                                } label: {
                                    Image(systemName: planned ? "calendar.badge.checkmark" : "calendar.badge.plus")
                                        .font(.title3)
                                        .foregroundStyle(planned ? Color.accentColor : .secondary)
                                        .frame(width: 44, height: 44)
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel(t("Add to your trip"))
                            }
                        }
                        .onDelete { favorites.remove(at: $0) }
                    }
                    .listStyle(.plain)
                    .toolbar { EditButton() }
                }
            }
            .navigationTitle(showTrips ? t("My trip") : t("Favorites"))
            .navigationDestination(for: Post.self) { ArticleView(post: $0) }
            .navigationDestination(for: TripRoute.self) { route in
                switch route {
                case .trip(let id): TripView(tripID: id)
                case .day(let id, let day): TripDayView(tripID: id, startDay: day, path: $path)
                case .activity(let trip, let activity): ActivityView(tripID: trip, activityID: activity)
                }
            }
            .sheet(item: $planning) { AddToTripSheet(post: $0) }
        }
    }
}

/// All trips, or a prompt to make the first one.
struct TripsList: View {
    @Environment(TripStore.self) private var trips
    @Binding var path: NavigationPath
    @State private var creating = false

    var body: some View {
        Group {
            if trips.trips.isEmpty {
                ContentUnavailableView {
                    Label(t("Plan your trip"), systemImage: "suitcase")
                } description: {
                    Text(t("Make a day-by-day programme for your stay in Paris with your favorite articles and your own plans."))
                } actions: {
                    Button(t("Create a trip")) { creating = true }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                List {
                    ForEach(trips.trips) { trip in
                        NavigationLink(value: TripRoute.trip(trip.id)) {
                            HStack(spacing: 14) {
                                IconCircle(systemImage: "suitcase.fill")
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(trip.name)
                                        .font(.title3.bold())
                                        .fontDesign(.serif)
                                    Text(tripSummary(trip))
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 6)
                        }
                    }
                    Button {
                        creating = true
                    } label: {
                        Label(t("New trip"), systemImage: "plus")
                    }
                }
            }
        }
        .sheet(isPresented: $creating) {
            NewTripSheet { name, days in
                let trip = trips.create(name: name, dayCount: days)
                path.append(TripRoute.trip(trip.id))
            }
        }
    }
}

private func tripSummary(_ trip: Trip) -> String {
    plural(trip.days.count, "%d day", "%d days") + " · " + activitiesLabel(trip.activityCount)
}

private func itemsLabel(_ section: Int, _ count: Int) -> String {
    section == infoSection ? plural(count, "%d item", "%d items") : activitiesLabel(count)
}

private struct IconCircle: View {
    let systemImage: String
    var foreground = Color.accentColor
    var background = Color.accentColor.opacity(0.15)

    var body: some View {
        Image(systemName: systemImage)
            .font(.title3)
            .foregroundStyle(foreground)
            .frame(width: 44, height: 44)
            .background(background, in: Circle())
    }
}

/// Asks for the name of a new trip and its number of days.
struct NewTripSheet: View {
    let onCreate: (String, Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = t("My Paris city trip")
    @State private var days = 3

    var body: some View {
        NavigationStack {
            Form {
                TextField(t("Name of the trip"), text: $name)
                Stepper(value: $days, in: 1...TripStore.maxDays) {
                    Text(t("Number of days") + ": \(days)")
                }
            }
            .navigationTitle(t("New trip"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("Cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(t("Create")) {
                        dismiss()
                        onCreate(name.trimmingCharacters(in: .whitespacesAndNewlines), days)
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// Overview of a trip: the practical info and all days.
struct TripView: View {
    let tripID: UUID
    @Environment(TripStore.self) private var trips
    @Environment(\.dismiss) private var dismiss
    @State private var renaming = false
    @State private var newName = ""
    @State private var deleting = false

    var body: some View {
        if let trip = trips.trip(tripID) {
            List {
                Section {
                    NavigationLink(value: TripRoute.day(tripID, infoSection)) {
                        InfoRow(info: trip.info)
                    }
                    .listRowBackground(Color.accentColor.opacity(0.12))
                } header: {
                    Text(tripSummary(trip))
                        .textCase(nil)
                }

                Section {
                    ForEach(Array(trip.days.enumerated()), id: \.element.id) { index, day in
                        NavigationLink(value: TripRoute.day(tripID, index)) {
                            DayRow(index: index, day: day)
                        }
                    }
                    Button {
                        trips.addDay(tripID)
                    } label: {
                        Label(t("Add a day"), systemImage: "plus")
                    }
                    .disabled(trip.days.count >= TripStore.maxDays)
                } header: {
                    Text(t("Programme"))
                }
            }
            .navigationTitle(trip.name)
            .toolbar {
                Menu {
                    Button(t("Rename trip"), systemImage: "pencil") {
                        newName = trip.name
                        renaming = true
                    }
                    Button(t("Delete trip"), systemImage: "trash", role: .destructive) { deleting = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel(t("More"))
            }
            .alert(t("Rename trip"), isPresented: $renaming) {
                TextField(t("Name of the trip"), text: $newName)
                Button(t("Cancel"), role: .cancel) {}
                Button(t("Save")) {
                    let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty { trips.rename(tripID, to: name) }
                }
            }
            .confirmationDialog(t("Delete “%s”?", trip.name), isPresented: $deleting, titleVisibility: .visible) {
                Button(t("Delete"), role: .destructive) {
                    dismiss()
                    trips.delete(tripID)
                }
            } message: {
                Text(t("The plans and notes of all days are deleted. Your favorites stay saved."))
            }
        } else {
            ContentUnavailableView(t("This trip no longer exists"), systemImage: "suitcase")
        }
    }
}

/// The practical info of a trip, shown above the days.
private struct InfoRow: View {
    let info: TripDay

    var body: some View {
        HStack(spacing: 14) {
            IconCircle(systemImage: "info", background: Color(uiColor: .systemBackground))
            VStack(alignment: .leading, spacing: 3) {
                Text(t("Practical info"))
                    .font(.headline)
                Text(preview)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 6)
    }

    private var preview: String {
        if !info.activities.isEmpty { return info.activities.map { $0.shownTitle }.joined(separator: " · ") }
        if !info.notes.isEmpty { return info.notes }
        return t("Metro, tickets, addresses and other handy things for the whole trip")
    }
}

private struct DayRow: View {
    let index: Int
    let day: TripDay

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Text("\(index + 1)")
                .font(.headline)
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.accentColor, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(dayLabel(index))
                    .font(.headline)
                Text(day.activities.isEmpty ? t("Nothing planned yet") : itemsLabel(index, day.activities.count))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                let hasTimes = day.activities.contains { !$0.time.isEmpty }
                ForEach(day.activities.prefix(4)) { activity in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(activity.time.isEmpty ? "•" : activity.time)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.accentColor)
                            .frame(width: hasTimes ? 48 : 12, alignment: .leading)
                        Text(activity.shownTitle)
                            .font(.subheadline)
                            .lineLimit(1)
                    }
                }
                .padding(.top, 2)
                if day.activities.count > 4 {
                    Text(t("and %d more", day.activities.count - 4))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if !day.notes.isEmpty {
                    Label(day.notes, systemImage: "note.text")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .padding(.top, 2)
                }
            }
        }
        .padding(.vertical, 6)
    }
}

/// Buttons for the practical info and every day; the selected one has a check mark.
private struct SectionPicker: View {
    let trip: Trip
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(trip.sectionIndices, id: \.self) { index in
                        Button {
                            onSelect(index)
                        } label: {
                            HStack(spacing: 4) {
                                if index == selected {
                                    Image(systemName: "checkmark")
                                } else if index == infoSection {
                                    Image(systemName: "info.circle")
                                }
                                Text(dayLabel(index))
                            }
                        }
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.capsule)
                        .tint(index == selected ? .accentColor : .secondary)
                        .fontWeight(index == selected ? .semibold : .regular)
                        .id(index)
                    }
                }
                .padding(.horizontal)
            }
            .onAppear { proxy.scrollTo(selected, anchor: .center) }
        }
    }
}

/// One day of a trip, or its practical info, with buttons to switch between them. Tapping
/// an activity opens it; the arrows button shows arrows to change the order.
struct TripDayView: View {
    let tripID: UUID
    @Binding var path: NavigationPath
    @Environment(TripStore.self) private var trips
    @State private var dayIndex: Int
    @State private var reordering = false
    @State private var pickingFavorite = false
    @State private var creating = false
    @State private var newName = ""
    @State private var removingDay = false

    init(tripID: UUID, startDay: Int, path: Binding<NavigationPath>) {
        self.tripID = tripID
        _path = path
        _dayIndex = State(initialValue: startDay)
    }

    var body: some View {
        if let trip = trips.trip(tripID) {
            let index = trip.section(dayIndex) != nil ? dayIndex : max(trip.days.count - 1, infoSection)
            let day = trip.section(index) ?? TripDay()
            let isInfo = index == infoSection
            List {
                Section {
                    SectionPicker(trip: trip, selected: index) {
                        dayIndex = $0
                        reordering = false
                    }
                    .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                    .listRowBackground(Color.clear)
                }

                Section(isInfo ? t("General notes") : t("Notes for the day")) {
                    TextField(
                        isInfo ? t("E.g. the hotel's address, how the metro works, opening hours")
                            : t("E.g. take it easy today, back at the hotel by 18:00"),
                        text: Binding(
                            get: { trips.trip(tripID)?.section(index)?.notes ?? "" },
                            set: { trips.setNotes(tripID, day: index, notes: $0) }
                        ),
                        axis: .vertical
                    )
                    .lineLimit(2...8)
                }

                Section(isInfo ? t("Saved info") : t("Programme")) {
                    if day.activities.isEmpty {
                        Text(isInfo
                             ? t("Keep things here that you need during the whole trip, like an article about the metro or your hotel's details.")
                             : t("Nothing planned for this day yet. Add one of your favorites or an activity of your own."))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if isInfo {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(infoSuggestions, id: \.self) { suggestion in
                                        Button {
                                            create(t(suggestion), in: index)
                                        } label: {
                                            Label(t(suggestion), systemImage: "plus")
                                        }
                                        .buttonStyle(.bordered)
                                        .buttonBorderShape(.capsule)
                                    }
                                }
                            }
                        }
                    }
                    ForEach(Array(day.activities.enumerated()), id: \.element.id) { position, activity in
                        if reordering {
                            HStack {
                                ActivityRow(activity: activity)
                                VStack(spacing: 4) {
                                    Button {
                                        trips.move(activity.id, in: tripID, day: index, by: -1)
                                    } label: {
                                        Image(systemName: "chevron.up").frame(width: 44, height: 32)
                                    }
                                    .disabled(position == 0)
                                    .accessibilityLabel(t("Move up"))
                                    Button {
                                        trips.move(activity.id, in: tripID, day: index, by: 1)
                                    } label: {
                                        Image(systemName: "chevron.down").frame(width: 44, height: 32)
                                    }
                                    .disabled(position == day.activities.count - 1)
                                    .accessibilityLabel(t("Move down"))
                                }
                                .buttonStyle(.borderless)
                            }
                        } else {
                            NavigationLink(value: TripRoute.activity(tripID, activity.id)) {
                                ActivityRow(activity: activity)
                            }
                        }
                    }
                }

                if !reordering {
                    Section {
                        Button {
                            pickingFavorite = true
                        } label: {
                            Label(t("Favorite"), systemImage: "heart")
                        }
                        Button {
                            newName = ""
                            creating = true
                        } label: {
                            Label(isInfo ? t("Own info") : t("Own activity"), systemImage: "plus")
                        }
                    }
                }
            }
            .navigationTitle(dayLabel(index))
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                if reordering {
                    Button(t("Done")) { reordering = false }
                } else {
                    if day.activities.count > 1 {
                        Button {
                            reordering = true
                        } label: {
                            Image(systemName: "arrow.up.arrow.down")
                        }
                        .accessibilityLabel(t("Change the order"))
                    }
                    if !isInfo, trip.days.count > 1 {
                        Menu {
                            Button(t("Remove this day"), systemImage: "trash", role: .destructive) { removingDay = true }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                        .accessibilityLabel(t("More"))
                    }
                }
            }
            .sheet(isPresented: $pickingFavorite) {
                FavoritePickerSheet(tripID: tripID, day: index)
            }
            .alert(isInfo ? t("New info") : t("New activity"), isPresented: $creating) {
                TextField(t("Name"), text: $newName)
                Button(t("Cancel"), role: .cancel) {}
                Button(t("Add")) {
                    let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty { create(name, in: index) }
                }
            } message: {
                Text(isInfo ? t("E.g. How the metro works") : t("E.g. concert, dinner reservation"))
            }
            .confirmationDialog(t("Remove %s?", dayLabel(index)), isPresented: $removingDay, titleVisibility: .visible) {
                Button(t("Remove"), role: .destructive) {
                    dayIndex = max(index - 1, 0)
                    trips.removeDay(tripID, at: index)
                }
            } message: {
                Text(day.activities.isEmpty && day.notes.isEmpty
                     ? t("The days after it move up one place.")
                     : t("Its activities and notes are removed too. The days after it move up one place."))
            }
        } else {
            ContentUnavailableView(t("This trip no longer exists"), systemImage: "suitcase")
        }
    }

    /// Adds an own activity or piece of info and opens it to fill in the rest.
    private func create(_ name: String, in section: Int) {
        let activity = Activity(name: name)
        trips.save(activity, in: tripID, day: section)
        path.append(TripRoute.activity(tripID, activity.id))
    }
}

private struct ActivityRow: View {
    let activity: Activity

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let post = activity.post {
                PostImage(url: post.imageURL)
                    .frame(width: 60, height: 60)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                Image(systemName: "calendar")
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 60, height: 60)
                    .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
            }
            VStack(alignment: .leading, spacing: 3) {
                if !activity.time.isEmpty {
                    Text(activity.time)
                        .font(.subheadline.bold())
                        .foregroundStyle(Color.accentColor)
                }
                Text(activity.shownTitle)
                    .font(.headline)
                    .lineLimit(2)
                if !activity.location.isEmpty {
                    Label(activity.location, systemImage: "mappin.and.ellipse")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                if !activity.notes.isEmpty {
                    Label(activity.notes, systemImage: "note.text")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 4)
    }
}

/// One planned activity or piece of info. Everything is changed here, the day included,
/// and saved straight away.
struct ActivityView: View {
    let tripID: UUID
    let activityID: UUID
    @Environment(TripStore.self) private var trips
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var name = ""
    @State private var location = ""
    @State private var notes = ""
    @State private var loaded = false
    @State private var removing = false

    var body: some View {
        if let trip = trips.trip(tripID), let found = trip.find(activityID) {
            let section = found.section
            let activity = found.activity
            Form {
                if let post = activity.post {
                    Section {
                        PostImage(url: post.imageURL)
                            .frame(height: 180)
                            .frame(maxWidth: .infinity)
                            .clipped()
                            .listRowInsets(EdgeInsets())
                        Text(translated(post.title))
                            .font(.title3.bold())
                            .fontDesign(.serif)
                        NavigationLink(value: post) {
                            Label(t("Read the article"), systemImage: "book")
                        }
                    }
                } else {
                    Section {
                        TextField(t("Name"), text: $name)
                            .font(.title3.bold())
                            .onChange(of: name) {
                                // A name is required; a blank field keeps the last one
                                let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !trimmed.isEmpty { change { $0.name = trimmed } }
                            }
                    } header: {
                        Text(t("Name"))
                    } footer: {
                        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            Text(t("A name is required")).foregroundStyle(.red)
                        }
                    }
                }

                // Where in the trip: tapping another day moves it there straight away
                Section {
                    SectionPicker(trip: trip, selected: section) { target in
                        if target != section {
                            withAnimation { trips.move(activityID, in: tripID, from: section, to: target) }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
                } header: {
                    Text(t("Day"))
                } footer: {
                    Text(t("Tap another day to move it there."))
                }

                if section != infoSection || !activity.time.isEmpty {
                    Section(t("Time")) {
                        Toggle(t("Choose a time"), isOn: Binding(
                            get: { !activity.time.isEmpty },
                            set: { on in change { $0.time = on ? "10:00" : "" } }
                        ))
                        if !activity.time.isEmpty {
                            DatePicker(t("Time"), selection: Binding(
                                get: { Self.date(from: activity.time) },
                                set: { date in change { $0.time = Self.format(date) } }
                            ), displayedComponents: .hourAndMinute)
                        }
                    }
                }

                Section(t("Address or place")) {
                    TextField(t("E.g. 5 Avenue Anatole France"), text: $location)
                        .onChange(of: location) {
                            change { $0.location = location.trimmingCharacters(in: .whitespacesAndNewlines) }
                        }
                    if !location.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button {
                            var components = URLComponents(string: "https://maps.apple.com/")
                            components?.queryItems = [URLQueryItem(name: "q", value: location)]
                            if let url = components?.url { openURL(url) }
                        } label: {
                            Label(t("Open in maps"), systemImage: "map")
                        }
                    }
                }

                Section(t("Notes")) {
                    TextField(
                        section == infoSection ? t("Write down what you want to remember")
                            : t("E.g. tickets already bought, check opening hours"),
                        text: $notes,
                        axis: .vertical
                    )
                    .lineLimit(4...12)
                    .onChange(of: notes) { change { $0.notes = notes } }
                }

                Section {
                    Button(role: .destructive) {
                        removing = true
                    } label: {
                        Label(t("Remove from %s", dayLabel(section)), systemImage: "trash")
                    }
                } footer: {
                    Text(t("Changes are saved automatically."))
                }
            }
            .navigationTitle(dayLabel(section))
            .navigationBarTitleDisplayMode(.inline)
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                Button(role: .destructive) {
                    removing = true
                } label: {
                    Image(systemName: "trash")
                }
                .accessibilityLabel(t("Remove"))
            }
            .onAppear {
                guard !loaded else { return }
                name = activity.name
                location = activity.location
                notes = activity.notes
                loaded = true
            }
            .confirmationDialog(t("Remove “%s”?", activity.shownTitle), isPresented: $removing, titleVisibility: .visible) {
                Button(t("Remove"), role: .destructive) {
                    dismiss()
                    trips.remove(activityID, from: tripID, day: section)
                }
            } message: {
                Text(activity.post != nil ? t("The article stays in your favorites.") : t("Its time, place and notes are removed too."))
            }
        } else {
            ContentUnavailableView(t("This activity no longer exists"), systemImage: "calendar")
        }
    }

    private func change(_ transform: (inout Activity) -> Void) {
        guard loaded else { return }
        trips.change(activityID, in: tripID, transform)
    }

    private static func date(from time: String) -> Date {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        return Calendar.current.date(
            bySettingHour: parts.first ?? 10, minute: parts.count > 1 ? parts[1] : 0, second: 0, of: .now
        ) ?? .now
    }

    private static func format(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }
}

/// Lists the favorites; tapping one adds it to the day.
private struct FavoritePickerSheet: View {
    let tripID: UUID
    let day: Int
    @Environment(FavoritesStore.self) private var favorites
    @Environment(TripStore.self) private var trips
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if favorites.favorites.isEmpty {
                    ContentUnavailableView(
                        t("No favorites yet"),
                        systemImage: "heart",
                        description: Text(t("You have no favorites yet. Tap the heart on an article to save it, then add it to your trip here."))
                    )
                } else {
                    List(favorites.favorites) { favorite in
                        let plannedOn = trips.days(of: tripID, with: favorite.post.id)
                        let onThisDay = plannedOn.contains(day)
                        Button {
                            trips.save(Activity(post: favorite.post), in: tripID, day: day)
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                PostImage(url: favorite.post.imageURL)
                                    .frame(width: 56, height: 56)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(translated(favorite.post.title))
                                        .font(.headline)
                                        .lineLimit(2)
                                    if onThisDay {
                                        Text(t("Already in %s", dayLabel(day))).font(.footnote).foregroundStyle(.secondary)
                                    } else if !plannedOn.isEmpty {
                                        Text(t("In %s", plannedOn.map(dayLabel).joined(separator: ", ")))
                                            .font(.footnote).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Image(systemName: onThisDay ? "checkmark" : "plus.circle")
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                        .buttonStyle(.plain)
                        .disabled(onThisDay)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(t("Add a favorite to %s", dayLabel(day)))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("Cancel")) { dismiss() }
                }
            }
        }
    }
}

/// Adds an article to a day of a trip or its practical info, from the favorites list.
/// Offers to make a trip first when there is none.
private struct AddToTripSheet: View {
    let post: Post
    @Environment(TripStore.self) private var trips
    @Environment(\.dismiss) private var dismiss
    @State private var creating = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(translated(post.title))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                if trips.trips.isEmpty {
                    Text(t("You have no trip yet. Create one to plan your days in Paris."))
                }
                ForEach(trips.trips) { trip in
                    let plannedOn = trips.days(of: trip.id, with: post.id)
                    Section(trips.trips.count > 1 ? trip.name : "") {
                        ForEach(trip.sectionIndices, id: \.self) { index in
                            let added = plannedOn.contains(index)
                            let day = trip.section(index) ?? TripDay()
                            Button {
                                trips.save(Activity(post: post), in: trip.id, day: index)
                                dismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    if index == infoSection {
                                        Image(systemName: "info.circle")
                                            .foregroundStyle(Color.accentColor)
                                            .frame(width: 24)
                                    } else {
                                        Text("\(index + 1)")
                                            .font(.headline)
                                            .foregroundStyle(Color.accentColor)
                                            .frame(width: 24)
                                    }
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(dayLabel(index)).font(.headline)
                                        Text(added ? t("Already added")
                                             : day.activities.isEmpty ? t("Nothing planned yet")
                                             : day.activities.map { $0.shownTitle }.joined(separator: " · "))
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Image(systemName: added ? "checkmark" : "plus.circle")
                                        .foregroundStyle(Color.accentColor)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(added)
                        }
                    }
                }
                Section {
                    Button {
                        creating = true
                    } label: {
                        Label(trips.trips.isEmpty ? t("Create a trip") : t("New trip"), systemImage: "plus")
                    }
                }
            }
            .navigationTitle(t("Add to your trip"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("Cancel")) { dismiss() }
                }
            }
            .sheet(isPresented: $creating) {
                NewTripSheet { name, days in trips.create(name: name, dayCount: days) }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
