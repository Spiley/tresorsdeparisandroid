package com.tresorsdeparis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.tresorsdeparis.data.Post
import com.tresorsdeparis.data.PostSource
import com.tresorsdeparis.data.WordPressApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Paged list of articles for one [source]. Lives above the screens so it survives navigation. */
class PostListState(private val source: PostSource, private val scope: CoroutineScope) {
    var posts by mutableStateOf<List<Post>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    private var page = 0
    private var totalPages = 1
    private var job: Job? = null

    val canLoadMore get() = page < totalPages

    fun refresh(pulled: Boolean = false) {
        job?.cancel()
        isRefreshing = pulled
        load(1)
    }

    fun loadMore() {
        if (!isLoading && canLoadMore) load(page + 1)
    }

    private fun load(next: Int) {
        isLoading = true
        job = scope.launch {
            try {
                val result = WordPressApi.posts(next, source)
                posts = if (next == 1) result.posts else (posts + result.posts).distinctBy { it.id }
                page = next
                totalPages = result.totalPages
                failed = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed = true
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }
}

/**
 * A scrolling list of articles with pull-to-refresh, paging and an offline state.
 * With [featured] the newest article is shown as a large hero and the rest as image cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostList(
    state: PostListState,
    onOpen: (Post) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No articles found.",
    featured: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(state) {
        if (state.posts.isEmpty() && !state.isLoading) state.refresh()
    }

    Column(modifier.fillMaxSize()) {
        if (state.posts.isEmpty()) {
            header?.invoke()
            Box(Modifier.fillMaxSize()) {
                when {
                    state.failed -> OfflineView(onRetry = { state.refresh() })
                    state.isLoading || state.canLoadMore -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> EmptyView(Icons.Outlined.SearchOff, emptyMessage)
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { state.refresh(pulled = true) },
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (header != null) item(key = "header") { header() }
                    items(state.posts, key = { it.id }) { post ->
                        when {
                            !featured -> PostRow(post, onClick = { onOpen(post) })
                            post.id == state.posts.first().id -> HeroCard(post, onClick = { onOpen(post) })
                            else -> StoryCard(post, onClick = { onOpen(post) })
                        }
                        if (post.id == state.posts.last().id) {
                            LaunchedEffect(post.id) { state.loadMore() }
                        }
                    }
                    if (state.canLoadMore || state.failed) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.failed) {
                                    TextButton(onClick = { state.loadMore() }) { Text("Try again") }
                                } else {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val dateFormat = DateFormat.getDateInstance(DateFormat.LONG)

fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

/** Compact row with a rounded thumbnail, for category, search and favorite lists. */
@Composable
fun PostRow(post: Post, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PostImage(post.imageUrl, Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatDate(post.date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                post.excerpt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The newest article: a large photo with the title over a dark gradient. */
@Composable
fun HeroCard(post: Post, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
    ) {
        PostImage(post.imageUrl, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "LATEST STORY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                post.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                post.excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A card with a wide photo, used for the article feed on Home. */
@Composable
fun StoryCard(post: Post, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        PostImage(post.imageUrl, Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(24.dp)))
        Column(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatDate(post.date).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(post.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                post.excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PostImage(url: String?, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { ImagePlaceholder() },
        error = { ImagePlaceholder() },
    )
}

@Composable
private fun ImagePlaceholder() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shown when content can't be loaded, with a retry button. */
@Composable
fun OfflineView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyView(
        icon = Icons.Outlined.WifiOff,
        title = "No connection",
        message = "Check your internet connection and try again. Saved favorites are still available offline.",
        modifier = modifier,
    ) {
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun EmptyView(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
