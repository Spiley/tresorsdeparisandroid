package com.tresorsdeparis.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.tresorsdeparis.data.Arrondissement
import com.tresorsdeparis.data.ArrondissementTag
import com.tresorsdeparis.data.ParisMapData
import com.tresorsdeparis.data.PostSource
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val CardHeight = 320.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(app: AppState, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val map = remember { ParisMapData.load(context) }
    LaunchedEffect(Unit) {
        if (!app.tagsLoaded) app.loadArrondissementTags()
    }
    val tags = app.arrondissementTags
    var selectedNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = map.arrondissements.firstOrNull { it.number == selectedNumber }
    var showList by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ParisMap(
            map = map,
            counts = tags.mapValues { it.value.count },
            selected = selected,
            onSelect = { selectedNumber = it?.number },
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
                Text("Explore Paris", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Tap an arrondissement",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FilledTonalIconButton(
            onClick = { showList = true },
            modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopEnd).size(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "All arrondissements")
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
            shown.value?.let { arrondissement ->
                ArrondissementCard(
                    app = app,
                    arrondissement = arrondissement,
                    tag = tags[arrondissement.number],
                    open = open,
                    onClose = { selectedNumber = null },
                )
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(map.arrondissements, key = { it.number }) { arrondissement ->
                    ListItem(
                        leadingContent = { NumberBadge(arrondissement.number) },
                        headlineContent = { Text(arrondissement.title) },
                        supportingContent = { Text(arrondissement.name) },
                        trailingContent = tags[arrondissement.number]?.let { tag ->
                            { Text("${tag.count}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        modifier = Modifier.clickable {
                            showList = false
                            open(Screen.ArrondissementPosts(arrondissement))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

/** The bottom card for a selected arrondissement with a preview of its articles. */
@Composable
private fun ArrondissementCard(
    app: AppState,
    arrondissement: Arrondissement,
    tag: ArrondissementTag?,
    open: (Screen) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().height(CardHeight).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(arrondissement.number)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(arrondissement.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        arrondissement.name + (tag?.let { " · ${it.count} ${if (it.count == 1) "article" else "articles"}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    tag != null -> {
                        val state = app.posts(PostSource.Tag(tag.id))
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
                                            post.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            state.failed -> Text(
                                "No connection. Try again later.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    app.tagsFailed -> Text(
                        "No connection. Try again later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    app.tagsLoaded -> Text(
                        "No articles about the ${arrondissement.ordinal} yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            if (tag != null) {
                Button(
                    onClick = { open(Screen.ArrondissementPosts(arrondissement)) },
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth(),
                ) {
                    Text("See all ${tag.count} ${if (tag.count == 1) "article" else "articles"}")
                }
            }
        }
    }
}

/** Colours for the map in light and dark mode. */
private class MapColors(
    val land: Color,
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
            land = Color(0xFF2A2621), landBusy = Color(0xFF7A5C22), border = Color(0xFF121212),
            water = Color(0xFF2F5679), wood = Color(0xFF34503A), park = Color(0xFF3A5A40), cemetery = Color(0xFF3B4639),
            text = Color(0xFFF2E8D5), halo = Color(0xFF1A1714), parkText = Color(0xFF9CC79A), accent = accent,
        )
    } else {
        MapColors(
            land = Color(0xFFF7EBD3), landBusy = Color(0xFFE2B95C), border = Color.White,
            water = Color(0xFF9CC6EA), wood = Color(0xFFB3CFA3), park = Color(0xFFA8CC98), cemetery = Color(0xFFC3CFBA),
            text = Color(0xFF3B2D14), halo = Color(0xFFFFFBF2), parkText = Color(0xFF3F6B3A), accent = accent,
        )
    }
}

/**
 * A zoomable, detailed map of Paris. Tapping an arrondissement selects it and zooms the map
 * to it; tapping outside the city clears the selection.
 */
@Composable
fun ParisMap(
    map: ParisMapData,
    counts: Map<Int, Int>,
    selected: Arrondissement?,
    onSelect: (Arrondissement?) -> Unit,
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
        fun path(rings: List<FloatArray>) = Path().apply {
            rings.forEach { ring ->
                moveTo(ring[0], ring[1])
                for (i in 2 until ring.size step 2) lineTo(ring[i], ring[i + 1])
                close()
            }
        }
        MapPaths(
            arrondissements = map.arrondissements.associate { it.number to path(it.rings) },
            parks = map.parks.map { it to path(it.rings) },
            seine = Path().apply {
                moveTo(map.seine[0], map.seine[1])
                for (i in 2 until map.seine.size step 2) lineTo(map.seine[i], map.seine[i + 1])
            },
        )
    }
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    var zoom by remember { mutableFloatStateOf(0f) } // 0 until the first layout
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val fit = min(width / map.width, height / map.height)
        val origin = Offset((width - map.width * fit) / 2, (height - map.height * fit) / 2)
        val inset = with(density) { bottomInset.toPx() }

        // view = offset + zoom * (origin + point * fit)
        fun toView(x: Float, y: Float, z: Float = zoom, o: Offset = offset) =
            Offset(o.x + z * (origin.x + x * fit), o.y + z * (origin.y + y * fit))

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

        // Start zoomed in on central Paris so the map fills the screen
        if (zoom == 0f) {
            val start = (height * 0.6f / (map.height * fit)).coerceIn(1f, 2.2f)
            zoom = start
            offset = offsetFor(map.width * 0.53f, map.height * 0.5f, start, Offset(width / 2, height * 0.48f))
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

        // Bring a newly selected arrondissement into view above the card
        LaunchedEffect(selected?.number) {
            val arrondissement = selected ?: return@LaunchedEffect
            val targetZoom = max(zoom, 2f)
            animateTo(
                targetZoom,
                offsetFor(arrondissement.labelX, arrondissement.labelY, targetZoom, Offset(width / 2, (height - inset) / 2)),
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Map of Paris and its 20 arrondissements. Use the list button to choose one." }
                .pointerInput(map, width, height) {
                    detectTapGestures { tap ->
                        val point = toMap(tap)
                        currentOnSelect(map.arrondissements.firstOrNull { it.contains(point.x, point.y) })
                    }
                }
                .pointerInput(map, width, height, inset) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        val factor = newZoom / zoom
                        offset = clamp(centroid - (centroid - offset) * factor + pan, newZoom)
                        zoom = newZoom
                    }
                },
        ) {
            val scale = zoom * fit
            val px = 1f / scale // one screen pixel in map units

            withTransform({
                translate(offset.x + zoom * origin.x, offset.y + zoom * origin.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                map.arrondissements.forEach { arrondissement ->
                    val share = (counts[arrondissement.number] ?: 0).toFloat() / maxCount
                    var fill = lerp(colors.land, colors.landBusy, 0.15f + 0.6f * share)
                    if (arrondissement == selected) fill = lerp(fill, colors.accent, 0.45f)
                    drawPath(paths.arrondissements.getValue(arrondissement.number), fill)
                }
                paths.parks.forEach { (park, path) ->
                    drawPath(path, if (park.isWood) colors.wood else if (park.isCemetery) colors.cemetery else colors.park)
                }
                map.arrondissements.forEach { arrondissement ->
                    drawPath(
                        paths.arrondissements.getValue(arrondissement.number), colors.border,
                        style = Stroke(width = 2.dp.toPx() * px, join = StrokeJoin.Round),
                    )
                }
                drawPath(
                    paths.seine, colors.water,
                    style = Stroke(width = max(6f, 3.dp.toPx() * px), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                selected?.let {
                    drawPath(
                        paths.arrondissements.getValue(it.number), colors.accent,
                        style = Stroke(width = 3.dp.toPx() * px, join = StrokeJoin.Round),
                    )
                }
            }

            // Labels are drawn unscaled so they stay readable at every zoom level. Arrondissement
            // labels claim their space first; landmark and park names that would overlap are skipped.
            val placed = mutableListOf<Rect>()
            val numberStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = colors.text)
            val nameStyle = TextStyle(fontSize = 11.sp, color = colors.text.copy(alpha = 0.75f))
            val landmarkStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.text)
            val parkStyle = TextStyle(fontSize = 11.sp, fontStyle = FontStyle.Italic, color = colors.parkText)

            map.arrondissements.forEach { arrondissement ->
                val point = toView(arrondissement.labelX, arrondissement.labelY)
                drawLabel(textMeasurer, "${arrondissement.number}", point, numberStyle, colors.halo, placed, force = true)
                if (zoom >= 1.6f) {
                    drawLabel(textMeasurer, arrondissement.name, point + Offset(0f, 16.dp.toPx()), nameStyle, colors.halo, placed, force = true)
                }
            }
            if (zoom >= 1.9f) {
                map.landmarks.forEach { landmark ->
                    val point = toView(landmark.x, landmark.y)
                    drawCircle(colors.halo, radius = 4.5.dp.toPx(), center = point)
                    drawCircle(colors.accent, radius = 3.dp.toPx(), center = point)
                    val width = textMeasurer.measure(landmark.name, landmarkStyle).size.width
                    drawLabel(textMeasurer, landmark.name, point + Offset(7.dp.toPx() + width / 2f, 0f), landmarkStyle, colors.halo, placed)
                }
            }
            map.parks.forEach { park ->
                if (park.isWood || zoom >= 2.6f) {
                    drawLabel(textMeasurer, park.name, toView(park.labelX, park.labelY), parkStyle, colors.halo, placed)
                }
            }
        }

        if (zoom > 1.05f) {
            FilledTonalIconButton(
                onClick = { animateTo(1f, clamp(Offset.Zero, 1f)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + bottomInset)
                    .navigationBarsPadding()
                    .shadow(4.dp, CircleShape),
            ) {
                Icon(Icons.Outlined.ZoomInMap, contentDescription = "Show all of Paris")
            }
        }
    }
}

private class MapPaths(
    val arrondissements: Map<Int, Path>,
    val parks: List<Pair<com.tresorsdeparis.data.Park, Path>>,
    val seine: Path,
)

/**
 * Draws text centred on [center] with a soft halo so it stays readable on any fill.
 * Skips it when it would overlap a label in [placed], unless [force] is set.
 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    halo: Color,
    placed: MutableList<Rect>,
    force: Boolean = false,
) {
    val layout = measurer.measure(text, style)
    val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    val bounds = Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat())).inflate(2.dp.toPx())
    if (!force && placed.any { it.overlaps(bounds) }) return
    placed += bounds
    val haloLayout = measurer.measure(text, style.copy(color = halo))
    drawText(haloLayout, topLeft = topLeft, drawStyle = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawText(layout, topLeft = topLeft)
}
