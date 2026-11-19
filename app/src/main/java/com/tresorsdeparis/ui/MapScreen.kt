package com.tresorsdeparis.ui

import com.tresorsdeparis.data.translated
import com.tresorsdeparis.data.Lang
import com.tresorsdeparis.data.Language
import com.tresorsdeparis.data.t
import com.tresorsdeparis.data.plural
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tresorsdeparis.data.MapArea
import com.tresorsdeparis.data.PostSource
import com.tresorsdeparis.data.PostalTag
import com.tresorsdeparis.data.RegionMapData
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** The selected area's card; taller with a larger font size, so titles are not cut off. */
private val CardHeight: Dp
    @Composable get() = 320.dp + 150.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)

/** The most the map can be enlarged: far enough to see the Paris landmarks. */
private const val MaxZoom = 40f

// Width of Paris on screen, in dp, from which each map detail shows
private const val TappableArrondissements = 180f
private const val ThinArrondissementBorders = 80f
private const val ArrondissementNumbers = 240f
private const val AllArrondissementNumbers = 300f
private const val ArrondissementNames = 620f
private const val Landmarks = 740f
private const val SmallParks = 1010f
private const val TownsUntil = 700f

/** The postcode tags of an area: all of a department's, or the one of an arrondissement. */
private fun List<PostalTag>.of(area: MapArea) = filter { it.code.startsWith(area.code) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(app: AppState, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val map = remember { RegionMapData.load(context) }
    LaunchedEffect(Unit) {
        if (!app.tagsLoaded) app.loadPostalTags()
    }
    val tags = app.postalTags
    val counts = remember(map, tags) {
        (map.departments + map.arrondissements).associate { area -> area.code to tags.of(area).sumOf { it.count } }
    }
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = map.area(selectedCode)
    var showList by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(mapColors().outside)) {
        RegionMap(
            map = map,
            counts = counts,
            selected = selected,
            onSelect = { selectedCode = it?.code },
            bottomInset = if (selected != null) CardHeight else 0.dp,
            modifier = Modifier.fillMaxSize(),
        )

        // Title
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopStart),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("Île-de-France", style = MaterialTheme.typography.headlineSmall)
                Text(
                    t("Tap a department or arrondissement"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FilledTonalIconButton(
            onClick = { showList = true },
            modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopEnd).size(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = t("All departments and arrondissements"))
        }

        Text(
            map.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Keep showing the last selection while the card slides away
            val shown = remember { mutableStateOf(selected) }
            if (selected != null) shown.value = selected
            shown.value?.let { area ->
                AreaCard(app = app, area = area, tags = tags.of(area), open = open, onClose = { selectedCode = null })
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(map.departments + map.arrondissements, key = { it.code }) { area ->
                    ListItem(
                        leadingContent = { NumberBadge(area.badge) },
                        headlineContent = { Text(areaTitle(area)) },
                        supportingContent = { Text(areaName(area)) },
                        trailingContent = counts[area.code]?.takeIf { it > 0 }?.let { count ->
                            { Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        modifier = Modifier.clickable {
                            showList = false
                            open(Screen.AreaPosts(area))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(badge: String) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(badge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

/** The bottom card for a selected area with a preview of its articles. */
@Composable
private fun AreaCard(
    app: AppState,
    area: MapArea,
    tags: List<PostalTag>,
    open: (Screen) -> Unit,
    onClose: () -> Unit,
) {
    // Articles can have several postcodes, so counts only add up for a single tag
    val count = tags.singleOrNull()?.count
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().height(CardHeight).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(area.badge)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(areaTitle(area), style = MaterialTheme.typography.titleLarge)
                    Text(
                        areaName(area) + (count?.let { " · " + plural(it, "%d article", "%d articles") } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = t("Close")) }
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    tags.isNotEmpty() -> {
                        val state = app.posts(PostSource.Tags(tags.map { it.id }))
                        LaunchedEffect(state) {
                            if (state.posts.isEmpty() && !state.isLoading) state.refresh()
                        }
                        when {
                            state.posts.isNotEmpty() -> LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.posts.take(10), key = { it.id }) { post ->
                                    Column(Modifier.width(170.dp).clickable { open(Screen.Article(post)) }) {
                                        PostImage(post.imageUrl, Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            translated(post.title),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            state.failed -> Text(
                                t("No connection. Try again later."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    app.tagsFailed -> Text(
                        t("No connection. Try again later."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    app.tagsLoaded -> Text(
                        noArticlesAbout(area),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            if (tags.isNotEmpty()) {
                Button(
                    onClick = { open(Screen.AreaPosts(area)) },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth(),
                ) {
                    Text(if (count == null) t("See all articles") else plural(count, "See %d article", "See all %d articles"))
                }
            }
        }
    }
}

/** Colours for the map in light and dark mode. */
private class MapColors(
    val land: Color,
    val outside: Color,
    val landBusy: Color,
    val border: Color,
    val water: Color,
    val wood: Color,
    val park: Color,
    val cemetery: Color,
    val text: Color,
    val halo: Color,
    val parkText: Color,
    val accent: Color,
)

@Composable
private fun mapColors(): MapColors {
    val accent = MaterialTheme.colorScheme.primary
    return if (isSystemInDarkTheme()) {
        MapColors(
            land = Color(0xFF2A2621), outside = Color(0xFF1B1916), landBusy = Color(0xFF7A5C22), border = Color(0xFF121212),
            water = Color(0xFF2F5679), wood = Color(0xFF34503A), park = Color(0xFF3A5A40), cemetery = Color(0xFF3B4639),
            text = Color(0xFFF2E8D5), halo = Color(0xFF1A1714), parkText = Color(0xFF9CC79A), accent = accent,
        )
    } else {
        MapColors(
            land = Color(0xFFF7EBD3), outside = Color(0xFFEFEDE8), landBusy = Color(0xFFE2B95C), border = Color.White,
            water = Color(0xFF9CC6EA), wood = Color(0xFFB3CFA3), park = Color(0xFFA8CC98), cemetery = Color(0xFFC3CFBA),
            text = Color(0xFF3B2D14), halo = Color(0xFFFFFBF2), parkText = Color(0xFF3F6B3A), accent = accent,
        )
    }
}

/**
 * A zoomable map of Île-de-France. Tapping a department selects it and zooms to it. Once Paris
 * is large enough, tapping it selects an arrondissement; tapping outside the region clears the selection.
 */
@Composable
fun RegionMap(
    map: RegionMapData,
    counts: Map<String, Int>,
    selected: MapArea?,
    onSelect: (MapArea?) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = mapColors()
    // No cache: it ignores colour and stroke, which would mix up halos and labels
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val paths = remember(map) {
        fun lines(path: Path, line: FloatArray) {
            path.moveTo(line[0], line[1])
            for (i in 2 until line.size step 2) path.lineTo(line[i], line[i + 1])
        }
        fun path(rings: List<FloatArray>) = Path().apply {
            rings.forEach { ring ->
                lines(this, ring)
                close()
            }
        }
        MapPaths(
            areas = (map.departments + map.arrondissements).associate { it.code to path(it.rings) },
            parks = map.parks.map { it to path(it.rings) },
            rivers = Path().apply { map.rivers.forEach { lines(this, it) } },
        )
    }
    val outerDepartments = remember(map) { map.departments.filter { it.code != "75" } }
    val maxCount = (map.arrondissements + outerDepartments).maxOf { counts[it.code] ?: 0 }.coerceAtLeast(1)
    var zoom by remember { mutableFloatStateOf(0f) } // 0 until the first layout
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fit = min(width / map.width, height / map.height)
        val origin = Offset((width - map.width * fit) / 2, (height - map.height * fit) / 2)
        val inset = with(density) { bottomInset.toPx() }

        // view = offset + zoom * (origin + point * fit)
        fun toView(x: Float, y: Float) = Offset(offset.x + zoom * (origin.x + x * fit), offset.y + zoom * (origin.y + y * fit))

        fun toMap(p: Offset) = Offset(((p.x - offset.x) / zoom - origin.x) / fit, ((p.y - offset.y) / zoom - origin.y) / fit)

        // Keeps the map on screen; content smaller than the view can still slide above the bottom card
        fun clamp(o: Offset, z: Float): Offset {
            val left = o.x + z * origin.x
            val top = o.y + z * origin.y
            val contentWidth = z * map.width * fit
            val contentHeight = z * map.height * fit
            val clampedLeft = left.coerceIn(min(0f, width - contentWidth), max(0f, width - contentWidth))
            val clampedTop = top.coerceIn(min(0f, height - contentHeight) - inset, max(0f, height - contentHeight))
            return Offset(clampedLeft - z * origin.x, clampedTop - z * origin.y)
        }

        /** Offset that puts map point (x, y) at view point [target] at zoom [z]. */
        fun offsetFor(x: Float, y: Float, z: Float, target: Offset) =
            clamp(target - Offset(z * (origin.x + x * fit), z * (origin.y + y * fit)), z)

        /** How wide Paris is on screen, in dp. */
        fun parisWidth() = zoom * fit * map.parisWidth / density.density

        // Start with the whole region. Wait for a real size: a zero size would make the zoom NaN and the map blank
        if (zoom == 0f && width > 0f && height > 0f) {
            zoom = 1f
            offset = clamp(Offset.Zero, 1f)
        }

        fun animateTo(targetZoom: Float, targetOffset: Offset) {
            val startZoom = zoom
            val startOffset = offset
            scope.launch {
                animate(0f, 1f, animationSpec = tween(450)) { t, _ ->
                    zoom = startZoom + (targetZoom - startZoom) * t
                    offset = startOffset + (targetOffset - startOffset) * t
                }
            }
        }

        // Bring a newly selected area into view above the card
        LaunchedEffect(selected?.code) {
            val area = selected ?: return@LaunchedEffect
            val target = Offset(width / 2, (height - inset) / 2)
            if (area.isDepartment) {
                val (left, top, right, bottom) = area.bounds
                val targetZoom = (min(width / ((right - left) * fit), (height - inset) / ((bottom - top) * fit)) * 0.88f)
                    .coerceIn(1f, MaxZoom)
                animateTo(targetZoom, offsetFor((left + right) / 2, (top + bottom) / 2, targetZoom, target))
            } else {
                val targetZoom = max(zoom, ArrondissementNames * density.density / (fit * map.parisWidth))
                animateTo(targetZoom, offsetFor(area.labelX, area.labelY, targetZoom, target))
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = t("Map of Île-de-France with its departments and the arrondissements of Paris. Use the list button to choose one.") }
                .pointerInput(map, width, height) {
                    detectTapGestures { tap ->
                        val point = toMap(tap)
                        val arrondissement = if (parisWidth() >= TappableArrondissements) {
                            map.arrondissements.firstOrNull { it.contains(point.x, point.y) }
                        } else {
                            null
                        }
                        currentOnSelect(arrondissement ?: map.departments.firstOrNull { it.contains(point.x, point.y) })
                    }
                }
                .pointerInput(map, width, height, inset) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, MaxZoom)
                        val factor = newZoom / zoom
                        offset = clamp(centroid - (centroid - offset) * factor + pan, newZoom)
                        zoom = newZoom
                    }
                },
        ) {
            if (zoom <= 0f || fit <= 0f) return@Canvas
            val scale = zoom * fit
            val px = 1f / scale // one screen pixel in map units
            val paris = parisWidth()
            val parisSelected = selected?.code == "75"

            withTransform({
                translate(offset.x + zoom * origin.x, offset.y + zoom * origin.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                outerDepartments.forEach { department ->
                    val share = (counts[department.code] ?: 0).toFloat() / maxCount
                    var fill = lerp(colors.land, colors.landBusy, 0.08f + 0.1f * department.tone + 0.5f * share)
                    if (department == selected) fill = lerp(fill, colors.accent, 0.45f)
                    drawPath(paths.areas.getValue(department.code), fill)
                }
                map.arrondissements.forEach { arrondissement ->
                    val share = (counts[arrondissement.code] ?: 0).toFloat() / maxCount
                    var fill = lerp(colors.land, colors.landBusy, 0.15f + 0.6f * share)
                    if (arrondissement == selected) fill = lerp(fill, colors.accent, 0.45f)
                    if (parisSelected) fill = lerp(fill, colors.accent, 0.25f)
                    drawPath(paths.areas.getValue(arrondissement.code), fill)
                }
                paths.parks.forEach { (park, path) ->
                    drawPath(path, if (park.isWood) colors.wood else if (park.isCemetery) colors.cemetery else colors.park)
                }
                if (paris >= ThinArrondissementBorders) {
                    val border = (if (paris >= AllArrondissementNumbers) 2.dp else 0.75.dp).toPx() * px
                    map.arrondissements.forEach {
                        drawPath(paths.areas.getValue(it.code), colors.border, style = Stroke(width = border, join = StrokeJoin.Round))
                    }
                }
                map.departments.forEach {
                    drawPath(paths.areas.getValue(it.code), colors.border, style = Stroke(width = 1.5.dp.toPx() * px, join = StrokeJoin.Round))
                }
                drawPath(
                    paths.rivers, colors.water,
                    style = Stroke(width = max(0.9f, 1.5.dp.toPx() * px), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                selected?.let {
                    drawPath(
                        paths.areas.getValue(it.code), colors.accent,
                        style = Stroke(width = 3.dp.toPx() * px, join = StrokeJoin.Round),
                    )
                }
            }

            // Labels are drawn unscaled so they stay readable at every zoom level. Arrondissement
            // and department labels claim their space first; names that would overlap are skipped.
            val placed = mutableListOf<Rect>()
            val numberStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = colors.text)
            val nameStyle = TextStyle(fontSize = 11.sp, color = colors.text.copy(alpha = 0.75f))
            val placeStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.text)
            val parkStyle = TextStyle(fontSize = 11.sp, fontStyle = FontStyle.Italic, color = colors.parkText)

            fun drawPlace(place: com.tresorsdeparis.data.MapPlace) {
                val point = toView(place.x, place.y)
                val width = textMeasurer.measure(t(place.name), placeStyle).size.width
                if (drawLabel(textMeasurer, t(place.name), point + Offset(7.dp.toPx() + width / 2f, 0f), placeStyle, colors.halo, placed)) {
                    drawCircle(colors.halo, radius = 4.5.dp.toPx(), center = point)
                    drawCircle(colors.accent, radius = 3.dp.toPx(), center = point)
                }
            }

            if (paris >= ArrondissementNumbers) {
                map.arrondissements.forEach { arrondissement ->
                    val point = toView(arrondissement.labelX, arrondissement.labelY)
                    drawLabel(textMeasurer, arrondissement.badge, point, numberStyle, colors.halo, placed, force = paris >= AllArrondissementNumbers)
                    if (paris >= ArrondissementNames) {
                        drawLabel(textMeasurer, arrondissement.name, point + Offset(0f, 16.dp.toPx()), nameStyle, colors.halo, placed, force = true)
                    }
                }
            }
            map.departments.forEach { department ->
                if (department.code == "75" && paris >= ArrondissementNumbers) return@forEach
                val point = toView(department.labelX, department.labelY)
                if (drawLabel(textMeasurer, department.badge, point, numberStyle, colors.halo, placed)) {
                    drawLabel(textMeasurer, department.title, point + Offset(0f, 22.dp.toPx()), nameStyle, colors.halo, placed)
                }
            }
            if (paris < TownsUntil) map.towns.forEach(::drawPlace)
            if (paris >= Landmarks) map.landmarks.forEach(::drawPlace)
            map.parks.forEach { park ->
                if (paris >= (if (park.isWood) AllArrondissementNumbers else SmallParks)) {
                    drawLabel(textMeasurer, t(park.name), toView(park.labelX, park.labelY), parkStyle, colors.halo, placed)
                }
            }
        }

        if (zoom > 1.05f) {
            // A small FAB draws its shadow at its own size, so the icon stays centred in light mode too
            SmallFloatingActionButton(
                onClick = { animateTo(1f, clamp(Offset.Zero, 1f)) },
                shape = CircleShape,
                // The theme colour is see-through, which would show the shadow beneath it
                containerColor = MaterialTheme.colorScheme.secondaryContainer.compositeOver(MaterialTheme.colorScheme.surface),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + bottomInset)
                    .navigationBarsPadding(),
            ) {
                Icon(Icons.Outlined.ZoomInMap, contentDescription = t("Show all of Île-de-France"))
            }
        }
    }
}

private class MapPaths(
    val areas: Map<String, Path>,
    val parks: List<Pair<com.tresorsdeparis.data.Park, Path>>,
    val rivers: Path,
)

/**
 * Draws text centred on [center] with a soft halo so it stays readable on any fill.
 * Skips it when it is off screen or would overlap a label in [placed], unless [force] is
 * set, and tells whether it was drawn.
 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    halo: Color,
    placed: MutableList<Rect>,
    force: Boolean = false,
): Boolean {
    if (center.x < -size.width || center.x > 2 * size.width || center.y < -100f || center.y > size.height + 100f) return false
    val layout = measurer.measure(text, style)
    val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    val bounds = Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat())).inflate(2.dp.toPx())
    if (!force && placed.any { it.overlaps(bounds) }) return false
    placed += bounds
    val haloLayout = measurer.measure(text, style.copy(color = halo))
    drawText(haloLayout, topLeft = topLeft, drawStyle = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawText(layout, topLeft = topLeft)
    return true
}

/** "8th arrondissement", or "8e arrondissement" in French. */
fun areaTitle(area: MapArea): String {
    if (area.isDepartment) return area.title
    val number = area.badge
    return when (Lang.current) {
        Language.English -> area.title
        Language.French -> if (number == "1") "1er arrondissement" else "${number}e arrondissement"
        Language.Dutch -> "${number}e arrondissement"
        Language.German -> "$number. Arrondissement"
    }
}

/** "Department 77" or the arrondissement's name, like "Louvre". */
fun areaName(area: MapArea): String {
    if (!area.isDepartment) return area.name
    return when (Lang.current) {
        Language.English -> area.name
        Language.French, Language.German -> area.name.replace("Department", "Département")
        Language.Dutch -> area.name.replace("Department", "Departement")
    }
}

fun noArticlesAbout(area: MapArea): String =
    if (Lang.isEnglish) "No articles about ${area.short} yet." else t("No articles about %s yet.", areaTitle(area))
