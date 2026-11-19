package com.tresorsdeparis.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Something planned on a day of a trip: a saved article or the traveller's own activity.
 * An article keeps its own copy of the post, so it stays in the plan when it is removed
 * from the favorites.
 */
data class Activity(
    val id: String = UUID.randomUUID().toString(),
    val post: Post? = null,
    /** The name of an own activity; articles use their title. */
    val name: String = "",
    /** Like "20:00", or empty. */
    val time: String = "",
    val location: String = "",
    val notes: String = "",
) {
    val title get() = post?.title ?: name

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("post", post?.toJson())
        .put("name", name)
        .put("time", time)
        .put("location", location)
        .put("notes", notes)

    companion object {
        fun fromJson(json: JSONObject) = Activity(
            id = json.getString("id"),
            post = json.optJSONObject("post")?.let(Post::fromJson),
            name = json.optString("name"),
            time = json.optString("time"),
            location = json.optString("location"),
            notes = json.optString("notes"),
        )
    }
}

data class TripDay(
    val id: String = UUID.randomUUID().toString(),
    val notes: String = "",
    val activities: List<Activity> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("notes", notes)
        .put("activities", JSONArray().apply { activities.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject) = TripDay(
            id = json.getString("id"),
            notes = json.optString("notes"),
            activities = json.optJSONArray("activities").objects(Activity::fromJson),
        )
    }
}

/** Index of a trip's "Practical info" section, used where a day index is expected. */
const val INFO = -1

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val days: List<TripDay>,
    /** General information that belongs to no day, like how the metro works. */
    val info: TripDay = TripDay(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val activityCount get() = days.sumOf { it.activities.size }

    /** A day, or the practical info for [INFO]. */
    fun section(index: Int): TripDay? = if (index == INFO) info else days.getOrNull(index)

    /** The practical info followed by all days. */
    val sectionIndices get() = listOf(INFO) + days.indices

    fun withSection(index: Int, section: TripDay): Trip =
        if (index == INFO) copy(info = section) else copy(days = days.toMutableList().also { it[index] = section })

    /** Where an activity is: its section index and the activity itself. */
    fun find(activityId: String): Pair<Int, Activity>? = sectionIndices.firstNotNullOfOrNull { index ->
        section(index)?.activities?.firstOrNull { it.id == activityId }?.let { index to it }
    }

    /**
     * The whole trip as text to send to someone: every day with its activities, and links
     * to the articles on the website, so the people it is shared with read them there.
     */
    fun shareText(): String = buildString {
        appendLine(name)
        fun section(title: String, day: TripDay) {
            if (day.activities.isEmpty() && day.notes.isBlank()) return
            appendLine()
            appendLine(title)
            if (day.notes.isNotBlank()) appendLine(day.notes.trim())
            day.activities.forEach { activity ->
                appendLine("• " + listOf(activity.time, translated(activity.title)).filter { it.isNotBlank() }.joinToString(" "))
                activity.post?.let { appendLine("  " + it.link) }
                if (activity.location.isNotBlank()) appendLine("  📍 " + activity.location.trim())
                if (activity.notes.isNotBlank()) appendLine("  " + activity.notes.trim().replace("\n", "\n  "))
            }
        }
        section(t("Practical info"), info)
        days.forEachIndexed { index, day -> section(t("Day %d", index + 1), day) }
        appendLine()
        appendLine(t("Planned with the Trésors de Paris app"))
        append(WordPressApi.SITE)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("createdAt", createdAt)
        .put("info", info.toJson())
        .put("days", JSONArray().apply { days.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject) = Trip(
            id = json.getString("id"),
            name = json.getString("name"),
            createdAt = json.optLong("createdAt"),
            days = json.optJSONArray("days").objects(TripDay::fromJson),
            info = json.optJSONObject("info")?.let(TripDay::fromJson) ?: TripDay(),
        )
    }
}

private fun <T> JSONArray?.objects(transform: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { transform(getJSONObject(it)) }

/** The traveller's trips with their day plans. Every change is saved straight away. */
class TripStore(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    val trips = mutableStateListOf<Trip>()

    init {
        runCatching { trips += JSONArray(file.readText()).objects(Trip::fromJson) }
    }

    fun trip(id: String) = trips.firstOrNull { it.id == id }

    /** Creates a trip with [dayCount] empty days and returns it. */
    fun create(name: String, dayCount: Int): Trip {
        val trip = Trip(name = name, days = List(dayCount) { TripDay() })
        trips.add(0, trip)
        save()
        return trip
    }

    fun delete(tripId: String) {
        trips.removeAll { it.id == tripId }
        save()
    }

    fun rename(tripId: String, name: String) = update(tripId) { it.copy(name = name) }

    fun addDay(tripId: String) = update(tripId) { it.copy(days = it.days + TripDay()) }

    fun removeDay(tripId: String, dayIndex: Int) = update(tripId) { trip ->
        trip.copy(days = trip.days.filterIndexed { index, _ -> index != dayIndex })
    }

    fun setDayNotes(tripId: String, dayIndex: Int, notes: String) = updateDay(tripId, dayIndex) { it.copy(notes = notes) }

    /** Adds an activity at the end of a day, or replaces the one with the same id. */
    fun saveActivity(tripId: String, dayIndex: Int, activity: Activity) = updateDay(tripId, dayIndex) { day ->
        val index = day.activities.indexOfFirst { it.id == activity.id }
        day.copy(activities = if (index < 0) day.activities + activity else day.activities.toMutableList().also { it[index] = activity })
    }

    fun removeActivity(tripId: String, dayIndex: Int, activityId: String) = updateDay(tripId, dayIndex) { day ->
        day.copy(activities = day.activities.filter { it.id != activityId })
    }

    /** Moves an activity one place up ([by] -1) or down ([by] 1) within its day. */
    fun moveActivity(tripId: String, dayIndex: Int, activityId: String, by: Int) = updateDay(tripId, dayIndex) { day ->
        val from = day.activities.indexOfFirst { it.id == activityId }
        val to = from + by
        if (from < 0 || to !in day.activities.indices) return@updateDay day
        day.copy(activities = day.activities.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Moves an activity to the end of another day or the practical info. */
    fun moveActivityToDay(tripId: String, fromDay: Int, activityId: String, toDay: Int) = update(tripId) { trip ->
        val from = trip.section(fromDay)
        val to = trip.section(toDay)
        val activity = from?.activities?.firstOrNull { it.id == activityId }
        if (activity == null || to == null || toDay == fromDay) return@update trip
        trip.withSection(fromDay, from.copy(activities = from.activities.filter { it.id != activityId }))
            .withSection(toDay, to.copy(activities = to.activities + activity))
    }

    /** Changes an activity wherever it is in the trip. */
    fun updateActivity(tripId: String, activityId: String, change: (Activity) -> Activity) {
        val section = trip(tripId)?.find(activityId)?.first ?: return
        updateDay(tripId, section) { day -> day.copy(activities = day.activities.map { if (it.id == activityId) change(it) else it }) }
    }

    /** The sections of [tripId] (days, or [INFO]) in which an article is planned. */
    fun daysWith(tripId: String, postId: Int): List<Int> {
        val trip = trip(tripId) ?: return emptyList()
        return trip.sectionIndices.filter { index -> trip.section(index)!!.activities.any { it.post?.id == postId } }
    }

    private fun update(tripId: String, change: (Trip) -> Trip) {
        val index = trips.indexOfFirst { it.id == tripId }
        if (index < 0) return
        trips[index] = change(trips[index])
        save()
    }

    private fun updateDay(tripId: String, dayIndex: Int, change: (TripDay) -> TripDay) = update(tripId) { trip ->
        val day = trip.section(dayIndex)
        if (day == null) trip else trip.withSection(dayIndex, change(day))
    }

    private fun save() {
        val array = JSONArray().apply { trips.forEach { put(it.toJson()) } }
        runCatching {
            // Write to a temporary file first, so a crash halfway never loses the plan
            val temp = File(file.parentFile, "trips.json.tmp")
            temp.writeText(array.toString())
            temp.renameTo(file) || run { file.writeText(array.toString()); true }
        }
    }
}
