package com.tresorsdeparis.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tresorsdeparis.data.Activity
import com.tresorsdeparis.data.INFO
import com.tresorsdeparis.data.Post
import com.tresorsdeparis.data.Trip
import com.tresorsdeparis.data.TripDay
import com.tresorsdeparis.data.TripStore
import com.tresorsdeparis.data.plural
import com.tresorsdeparis.data.t
import com.tresorsdeparis.data.translated

private const val MaxDays = 30

/** The activity's name, or the article's title in the chosen language. */
private val Activity.shownTitle get() = post?.let { translated(it.title) } ?: name

/** "Day 3", or "Practical info" for [INFO]. */
fun dayLabel(index: Int) = if (index == INFO) t("Practical info") else t("Day %d", index + 1)

private fun itemsLabel(index: Int, count: Int) =
    if (index == INFO) plural(count, "%d item", "%d items") else plural(count, "%d activity", "%d activities")

/** Ready-made subjects offered while the practical info is still empty. */
private val InfoSuggestions = listOf("Metro and transport", "Tickets and passes", "Hotel", "Emergency numbers", "Packing list")

/** The "My trip" half of the Favorites tab: all trips, or a prompt to make the first one. */
@Composable
fun TripsList(open: (Screen) -> Unit, modifier: Modifier = Modifier) {
    val trips = LocalTrips.current
    var creating by remember { mutableStateOf(false) }

    if (trips.trips.isEmpty()) {
        EmptyView(
            Icons.Outlined.Luggage,
            t("Plan your trip"),
            t("Make a day-by-day programme for your stay in Paris with your favorite articles and your own plans."),
            modifier,
        ) {
            Button(onClick = { creating = true }) { Text(t("Create a trip")) }
        }
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trips.trips, key = { it.id }) { trip ->
                TripCard(trip, onClick = { open(Screen.Trip(trip.id)) })
            }
            item {
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("New trip"))
                }
            }
        }
    }

    if (creating) {
        NewTripDialog(
            onDismiss = { creating = false },
            onCreate = { name, days ->
                creating = false
                open(Screen.Trip(trips.create(name, days).id))
            },
        )
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Outlined.Luggage)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(trip.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    plural(trip.days.size, "%d day", "%d days") + " · " + plural(trip.activityCount, "%d activity", "%d activities"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Chevron()
        }
    }
}

/** Asks for the name of a new trip and its number of days. */
@Composable
fun NewTripDialog(onDismiss: () -> Unit, onCreate: (name: String, days: Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(t("My Paris city trip")) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("New trip")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("Name of the trip")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Number of days"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { days-- }, enabled = days > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = t("One day less"))
                    }
                    Text("$days", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                    FilledTonalIconButton(onClick = { days++ }, enabled = days < MaxDays) {
                        Icon(Icons.Outlined.Add, contentDescription = t("One day more"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), days) }, enabled = name.isNotBlank()) { Text(t("Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

/** Overview of a trip: the practical info and all days. */
@Composable
fun TripScreen(tripId: String, back: () -> Unit, open: (Screen) -> Unit) {
    val trips = LocalTrips.current
    val trip = trips.trip(tripId) ?: run {
        // The trip was deleted
        EmptyView(Icons.Outlined.Luggage, t("This trip no longer exists"))
        return
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ScreenScaffold(
        trip.name,
        onBack = back,
        actions = {
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, trip.name)
                    .putExtra(Intent.EXTRA_TEXT, trip.shareText())
                context.startActivity(Intent.createChooser(intent, t("Share trip")))
            }) {
                Icon(Icons.Outlined.Share, contentDescription = t("Share trip"))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = t("More")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Rename trip")) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text(t("Delete trip")) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    plural(trip.days.size, "%d day", "%d days") + " · " + plural(trip.activityCount, "%d activity", "%d activities"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "info") {
                InfoCard(trip.info, onClick = { open(Screen.TripDay(trip.id, INFO)) })
            }
            item {
                SectionTitle(t("Programme"))
            }
            itemsIndexed(trip.days, key = { _, day -> day.id }) { index, day ->
                DayCard(index, day, onClick = { open(Screen.TripDay(trip.id, index)) })
            }
            item {
                OutlinedButton(
                    onClick = { trips.addDay(trip.id) },
                    enabled = trip.days.size < MaxDays,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Add a day"))
                }
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = t("Rename trip"),
            label = t("Name of the trip"),
            initial = trip.name,
            onDismiss = { renaming = false },
            onSave = { trips.rename(trip.id, it); renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = t("Delete “%s”?", trip.name),
            message = t("The plans and notes of all days are deleted. Your favorites stay saved."),
            confirm = t("Delete"),
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                back()
                trips.delete(trip.id)
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun IconCircle(icon: ImageVector, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content)
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The practical info of a trip, shown above the days. */
@Composable
private fun InfoCard(info: TripDay, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Outlined.Info, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Practical info"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    when {
                        info.activities.isNotEmpty() -> info.activities.joinToString(" · ") { it.shownTitle }
                        info.notes.isNotBlank() -> info.notes
                        else -> t("Metro, tickets, addresses and other handy things for the whole trip")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Chevron()
        }
    }
}

@Composable
private fun DayCard(index: Int, day: TripDay, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayLabel(index), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (day.activities.isEmpty()) t("Nothing planned yet") else itemsLabel(index, day.activities.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Chevron()
                }
                if (day.activities.isNotEmpty()) {
                    // Leave room for times only when the day has any
                    val timeWidth = if (day.activities.any { it.time.isNotEmpty() }) 48.dp else 20.dp
                    Spacer(Modifier.height(10.dp))
                    day.activities.take(4).forEach { activity ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (activity.time.isNotEmpty()) {
                                Text(
                                    activity.time,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(timeWidth),
                                )
                            } else {
                                Box(Modifier.width(timeWidth)) {
                                    Box(Modifier.padding(start = 4.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                            Text(activity.shownTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (day.activities.size > 4) {
                        Text(
                            t("and %d more", day.activities.size - 4),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = timeWidth, top = 2.dp),
                        )
                    }
                }
                if (day.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Notes,
                            contentDescription = t("Notes"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            day.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One day of a trip, or its practical info, with chips to switch between them. Tapping an
 * activity opens it; "Reorder" shows arrows to change the order.
 */
@Composable
fun TripDayScreen(tripId: String, startDay: Int, back: () -> Unit, open: (Screen) -> Unit) {
    val switchTab = LocalSwitchTab.current
    val trips = LocalTrips.current
    val context = LocalContext.current
    val trip = trips.trip(tripId) ?: run {
        EmptyView(Icons.Outlined.Luggage, t("This trip no longer exists"))
        return
    }
    var dayIndex by rememberSaveable { mutableIntStateOf(startDay) }
    if (trip.section(dayIndex) == null) dayIndex = trip.days.lastIndex
    val day = trip.section(dayIndex)!!
    val isInfo = dayIndex == INFO

    var reordering by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pickingFavorite by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var removingDay by remember { mutableStateOf(false) }

    fun create(name: String) {
        val activity = Activity(name = name)
        trips.saveActivity(trip.id, dayIndex, activity)
        open(Screen.Activity(trip.id, activity.id))
    }

    ScreenScaffold(
        dayLabel(dayIndex),
        onBack = back,
        actions = {
            if (reordering) {
                TextButton(onClick = { reordering = false }) { Text(t("Done")) }
            } else {
                if (day.activities.size > 1) {
                    IconButton(onClick = { reordering = true }) { Icon(Icons.Outlined.SwapVert, contentDescription = t("Change the order")) }
                }
                if (!isInfo && trip.days.size > 1) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = t("More")) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(t("Remove this day")) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = { menu = false; removingDay = true },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                val chips = rememberLazyListState(initialFirstVisibleItemIndex = dayIndex.coerceAtLeast(0))
                LazyRow(
                    state = chips,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trip.sectionIndices) { index ->
                        FilterChip(
                            selected = index == dayIndex,
                            onClick = { dayIndex = index; reordering = false },
                            label = { Text(dayLabel(index)) },
                            leadingIcon = if (index == INFO) ({ Icon(Icons.Outlined.Info, contentDescription = null, Modifier.size(18.dp)) }) else null,
                        )
                    }
                }
            }
            item(key = "notes-${day.id}") {
                NotesCard(
                    initial = day.notes,
                    key = day.id,
                    label = if (isInfo) t("General notes") else t("Notes for the day"),
                    placeholder = if (isInfo) t("E.g. the hotel's address, how the metro works, opening hours") else t("E.g. take it easy today, back at the hotel by 18:00"),
                    onChange = { trips.setDayNotes(trip.id, dayIndex, it) },
                )
            }
            item {
                SectionTitle(
                    if (isInfo) t("Saved info") else t("Programme"),
                    Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp),
                )
            }
            if (day.activities.isEmpty()) {
                item {
                    Text(
                        if (isInfo) t("Keep things here that you need during the whole trip, like an article about the metro or your hotel's details.")
                        else t("Nothing planned for this day yet. Add one of your favorites or an activity of your own."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                if (isInfo) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(InfoSuggestions) { suggestion ->
                                SuggestionChip(
                                    onClick = { create(t(suggestion)) },
                                    label = { Text(t(suggestion)) },
                                    icon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(day.activities, key = { _, activity -> activity.id }) { index, activity ->
                ActivityCard(
                    activity = activity,
                    reordering = reordering,
                    onClick = { open(Screen.Activity(trip.id, activity.id)) },
                    onMoveUp = if (index > 0) ({ trips.moveActivity(trip.id, dayIndex, activity.id, -1) }) else null,
                    onMoveDown = if (index < day.activities.lastIndex) ({ trips.moveActivity(trip.id, dayIndex, activity.id, 1) }) else null,
                )
            }
            if (!reordering) {
                item {
                    // Inspiration first: the menu leads to more articles to read and save
                    Button(
                        onClick = { switchTab(Tab.Menu) },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t("Get inspired"), maxLines = 1)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(onClick = { pickingFavorite = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t("Favorites"), maxLines = 1)
                        }
                        FilledTonalButton(onClick = { creating = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isInfo) t("Own info") else t("Own activity"), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (pickingFavorite) {
        FavoritePickerSheet(
            trips = trips,
            trip = trip,
            dayIndex = dayIndex,
            onDismiss = { pickingFavorite = false },
            onPick = { post ->
                pickingFavorite = false
                trips.saveActivity(trip.id, dayIndex, Activity(post = post))
                Toast.makeText(context, t("Added to %s", dayLabel(dayIndex)), Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (creating) {
        TextInputDialog(
            title = if (isInfo) t("New info") else t("New activity"),
            label = t("Name"),
            placeholder = if (isInfo) t("E.g. How the metro works") else t("E.g. concert, dinner reservation"),
            initial = "",
            confirm = t("Add"),
            onDismiss = { creating = false },
            onSave = { creating = false; create(it) },
        )
    }
    if (removingDay) {
        ConfirmDialog(
            title = t("Remove %s?", dayLabel(dayIndex)),
            message = if (day.activities.isEmpty() && day.notes.isBlank()) {
                t("The days after it move up one place.")
            } else {
                t("Its activities and notes are removed too. The days after it move up one place.")
            },
            confirm = t("Remove"),
            onDismiss = { removingDay = false },
            onConfirm = {
                removingDay = false
                val removed = dayIndex
                dayIndex = (removed - 1).coerceAtLeast(0)
                trips.removeDay(trip.id, removed)
            },
        )
    }
}

/** A notes field without a box around it, saved on every change. */
@Composable
private fun NotesCard(initial: String, key: String, label: String, placeholder: String, onChange: (String) -> Unit) {
    // Edited locally so typing stays smooth
    var notes by remember(key) { mutableStateOf(initial) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(top = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            TextField(
                value = notes,
                onValueChange = { notes = it; onChange(it) },
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivityCard(
    activity: Activity,
    reordering: Boolean,
    onClick: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !reordering, onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivityThumbnail(activity, Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (activity.time.isNotEmpty()) {
                    Text(activity.time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text(activity.shownTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (activity.location.isNotBlank()) Detail(Icons.Outlined.Place, activity.location)
                if (activity.notes.isNotBlank()) Detail(Icons.AutoMirrored.Outlined.Notes, activity.notes)
            }
            if (reordering) {
                Column {
                    IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = t("Move up"))
                    }
                    IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = t("Move down"))
                    }
                }
            } else {
                Chevron()
            }
        }
    }
}

@Composable
private fun ActivityThumbnail(activity: Activity, modifier: Modifier) {
    val post = activity.post
    if (post != null) {
        PostImage(post.imageUrl, modifier.clip(RoundedCornerShape(14.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Detail(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One planned activity or piece of info. Everything is changed here, the day included,
 * and saved straight away.
 */
@Composable
fun ActivityScreen(tripId: String, activityId: String, back: () -> Unit, open: (Screen) -> Unit) {
    val trips = LocalTrips.current
    val context = LocalContext.current
    val trip = trips.trip(tripId)
    val found = trip?.find(activityId)
    if (trip == null || found == null) {
        ScreenScaffold("", onBack = back) { padding ->
            EmptyView(Icons.Outlined.Event, t("This activity no longer exists"), modifier = Modifier.padding(padding))
        }
        return
    }
    val (section, activity) = found
    val post = activity.post
    var removing by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    fun change(transform: (Activity) -> Activity) = trips.updateActivity(trip.id, activity.id, transform)

    ScreenScaffold(
        dayLabel(section),
        onBack = back,
        actions = {
            IconButton(onClick = { removing = true }) { Icon(Icons.Outlined.Delete, contentDescription = t("Remove")) }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            if (post != null) {
                PostImage(
                    post.imageUrl,
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)),
                )
                Text(
                    translated(post.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 8.dp),
                )
                FilledTonalButton(
                    onClick = { open(Screen.Article(post)) },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Read the article"))
                }
            } else {
                var name by remember(activity.id) { mutableStateOf(activity.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        // A name is required; a blank field keeps the last one
                        if (it.isNotBlank()) change { activity -> activity.copy(name = it.trim()) }
                    },
                    label = { Text(t("Name")) },
                    isError = name.isBlank(),
                    supportingText = if (name.isBlank()) ({ Text(t("A name is required")) }) else null,
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
            }

            // Where in the trip: tapping another day moves it there straight away
            FieldTitle(Icons.Outlined.Event, t("Day"))
            val chips = rememberLazyListState(initialFirstVisibleItemIndex = section.coerceAtLeast(0))
            LazyRow(
                state = chips,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(trip.sectionIndices) { index ->
                    FilterChip(
                        selected = index == section,
                        onClick = {
                            if (index != section) {
                                trips.moveActivityToDay(trip.id, section, activity.id, index)
                                Toast.makeText(context, t("Moved to %s", dayLabel(index)), Toast.LENGTH_SHORT).show()
                            }
                        },
                        label = { Text(dayLabel(index)) },
                        leadingIcon = if (index == section) ({ Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            Text(
                t("Tap another day to move it there."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
            )

            if (section != INFO || activity.time.isNotEmpty()) {
                FieldTitle(Icons.Outlined.Schedule, t("Time"))
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingTime = true }) {
                        Text(activity.time.ifEmpty { t("Choose a time") })
                    }
                    if (activity.time.isNotEmpty()) {
                        IconButton(onClick = { change { it.copy(time = "") } }) { Icon(Icons.Outlined.Clear, contentDescription = t("Clear time")) }
                    }
                }
            }

            FieldTitle(Icons.Outlined.Place, t("Address or place"))
            var location by remember(activity.id) { mutableStateOf(activity.location) }
            OutlinedTextField(
                value = location,
                onValueChange = { location = it; change { activity -> activity.copy(location = it.trim()) } },
                placeholder = { Text(t("E.g. 5 Avenue Anatole France")) },
                trailingIcon = if (location.isNotBlank()) {
                    {
                        IconButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))))
                            } catch (_: ActivityNotFoundException) {
                            }
                        }) { Icon(Icons.Outlined.Directions, contentDescription = t("Open in maps")) }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )

            FieldTitle(Icons.AutoMirrored.Outlined.Notes, t("Notes"))
            var notes by remember(activity.id) { mutableStateOf(activity.notes) }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; change { activity -> activity.copy(notes = it) } },
                placeholder = { Text(if (section == INFO) t("Write down what you want to remember") else t("E.g. tickets already bought, check opening hours")) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )

            Text(
                t("Changes are saved automatically."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            OutlinedButton(
                onClick = { removing = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(t("Remove from %s", dayLabel(section)))
            }
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = activity.time,
            onDismiss = { pickingTime = false },
            onPick = { time -> pickingTime = false; change { it.copy(time = time) } },
        )
    }
    if (removing) {
        ConfirmDialog(
            title = t("Remove “%s”?", activity.shownTitle),
            message = if (post != null) t("The article stays in your favorites.") else t("Its time, place and notes are removed too."),
            confirm = t("Remove"),
            onDismiss = { removing = false },
            onConfirm = {
                removing = false
                back()
                trips.removeActivity(trip.id, section, activity.id)
            },
        )
    }
}

@Composable
private fun FieldTitle(icon: ImageVector, text: String) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(":").mapNotNull { it.toIntOrNull() }
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0) ?: 10,
        initialMinute = parts.getOrNull(1) ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text(t("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

/** Lists the favorites; tapping one adds it to the day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritePickerSheet(trips: TripStore, trip: Trip, dayIndex: Int, onDismiss: () -> Unit, onPick: (Post) -> Unit) {
    val favorites = LocalFavorites.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            t("Add a favorite to %s", dayLabel(dayIndex)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
        )
        if (favorites.favorites.isEmpty()) {
            Text(
                t("You have no favorites yet. Tap the heart on an article to save it, then add it to your trip here."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            Spacer(Modifier.height(24.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(favorites.favorites, key = { it.post.id }) { favorite ->
                    val plannedOn = trips.daysWith(trip.id, favorite.post.id)
                    val onThisDay = dayIndex in plannedOn
                    ListItem(
                        leadingContent = {
                            PostImage(favorite.post.imageUrl, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                        },
                        headlineContent = { Text(translated(favorite.post.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = when {
                            onThisDay -> ({ Text(t("Already in %s", dayLabel(dayIndex))) })
                            plannedOn.isNotEmpty() -> ({ Text(t("In %s", plannedOn.joinToString { dayLabel(it) })) })
                            else -> null
                        },
                        trailingContent = {
                            Icon(
                                if (onThisDay) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = !onThisDay) { onPick(favorite.post) },
                    )
                }
            }
        }
    }
}

/**
 * Adds an article to a day of a trip or its practical info, from the favorites list.
 * Offers to make a trip first when there is none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToTripSheet(post: Post, onDismiss: () -> Unit) {
    val trips = LocalTrips.current
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            t("Add to your trip"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            translated(post.title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (trips.trips.isEmpty()) {
                item {
                    Text(
                        t("You have no trip yet. Create one to plan your days in Paris."),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            trips.trips.forEach { trip ->
                if (trips.trips.size > 1) {
                    item(key = "trip-${trip.id}") {
                        Text(
                            trip.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                val plannedOn = trips.daysWith(trip.id, post.id)
                items(trip.sectionIndices, key = { "${trip.id}-$it" }) { index ->
                    val added = index in plannedOn
                    val day = trip.section(index)!!
                    ListItem(
                        leadingContent = {
                            if (index == INFO) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                            }
                        },
                        headlineContent = { Text(dayLabel(index)) },
                        supportingContent = {
                            Text(
                                when {
                                    added -> t("Already added")
                                    day.activities.isEmpty() -> t("Nothing planned yet")
                                    else -> day.activities.joinToString(" · ") { it.shownTitle }
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(
                                if (added) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = !added) {
                            trips.saveActivity(trip.id, index, Activity(post = post))
                            Toast.makeText(context, t("Added to %s", dayLabel(index)), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { creating = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (trips.trips.isEmpty()) t("Create a trip") else t("New trip"))
                }
            }
        }
    }

    if (creating) {
        NewTripDialog(onDismiss = { creating = false }, onCreate = { name, days ->
            trips.create(name, days)
            creating = false
        })
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    placeholder: String? = null,
    confirm: String = t("Save"),
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } },
    )
}
