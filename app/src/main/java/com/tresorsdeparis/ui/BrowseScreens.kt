package com.tresorsdeparis.ui

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tresorsdeparis.data.MapArea
import com.tresorsdeparis.data.MenuItem
import com.tresorsdeparis.data.PostCategory
import com.tresorsdeparis.data.PostSource
import com.tresorsdeparis.data.WordPressApi

@Composable
fun HomeScreen(app: AppState, open: (Screen) -> Unit) {
    PostList(
        app.posts(PostSource.Latest),
        onOpen = { open(Screen.Article(it)) },
        modifier = Modifier.statusBarsPadding(),
        featured = true,
        header = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Trésors de Paris", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                    Text(
                        "Your insider's guide to Paris",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { open(Screen.Website()) }) {
                    Icon(Icons.Outlined.Language, contentDescription = "Open the website")
                }
            }
        },
    )
}

/** The website's hamburger menu, or one of its submenus when [parent] is set. */
@Composable
fun MenuScreen(app: AppState, parent: MenuItem?, back: (() -> Unit)?, open: (Screen) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (app.menu.isEmpty()) app.loadMenu()
    }

    fun category(item: MenuItem) = item.url.takeIf { "/category/" in it }
        ?.trimEnd('/')?.substringAfterLast('/')
        ?.let { slug -> app.categories.firstOrNull { it.slug == slug } }

    fun select(item: MenuItem) {
        val category = category(item)
        when {
            item.children.isNotEmpty() -> open(Screen.Menu(item))
            category != null -> open(Screen.CategoryPosts(category.copy(name = item.title)))
            item.url.startsWith(WordPressApi.SITE) -> open(Screen.Website(item.url, item.title))
            else -> openLink(context, item.url.toUri())
        }
    }

    // A submenu starts with the page of the item itself, unless that is empty or already listed
    val items = when {
        parent == null -> app.menu
        parent.children.any { it.url == parent.url } || category(parent)?.count == 0 -> parent.children
        else -> listOf(parent.copy(title = "All ${parent.title}", children = emptyList())) + parent.children
    }

    ScreenScaffold(parent?.title ?: "Menu", onBack = back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                items.isEmpty() && app.menuFailed -> OfflineView(onRetry = app::loadMenu)
                items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column {
                                items.forEachIndexed { index, item ->
                                    ListItem(
                                        headlineContent = { Text(item.title) },
                                        trailingContent = {
                                            Icon(
                                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.clickable { select(item) },
                                    )
                                    if (index < items.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPostsScreen(app: AppState, category: PostCategory, back: () -> Unit, open: (Screen) -> Unit) {
    ScreenScaffold(category.name, onBack = back) { padding ->
        PostList(app.posts(PostSource.Category(category.id)), onOpen = { open(Screen.Article(it)) }, Modifier.padding(padding))
    }
}

@Composable
fun AreaPostsScreen(app: AppState, area: MapArea, back: () -> Unit, open: (Screen) -> Unit) {
    // The map may have opened before the tags loaded (e.g. offline), so look them up here too
    LaunchedEffect(Unit) {
        if (!app.tagsLoaded) app.loadPostalTags()
    }
    val tags = app.postalTags.filter { it.slug.startsWith(area.code) }
    val emptyMessage = "No articles about ${area.short} yet."

    ScreenScaffold(area.title, onBack = back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                tags.isNotEmpty() -> PostList(
                    app.posts(PostSource.Tags(tags.map { it.id })),
                    onOpen = { open(Screen.Article(it)) },
                    emptyMessage = emptyMessage,
                )
                app.tagsLoaded -> EmptyView(Icons.Outlined.Map, emptyMessage)
                app.tagsFailed -> OfflineView(onRetry = app::loadPostalTags)
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun SearchScreen(app: AppState, open: (Screen) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    ScreenScaffold("Search") { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) submitted = ""
                },
                placeholder = { Text("Search Trésors de Paris") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; submitted = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    submitted = query.trim()
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (submitted.isEmpty()) {
                EmptyView(Icons.Outlined.Search, "Search articles", "Find restaurants, hidden gems, events and more in Paris.")
            } else {
                PostList(
                    app.posts(PostSource.Search(submitted)),
                    onOpen = { open(Screen.Article(it)) },
                    emptyMessage = "No results for “$submitted”",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(open: (Screen) -> Unit) {
    val favorites = LocalFavorites.current
    ScreenScaffold("Favorites") { padding ->
        if (favorites.favorites.isEmpty()) {
            EmptyView(
                Icons.Outlined.FavoriteBorder,
                "No favorites yet",
                "Tap the heart on an article to save it here. Saved articles can be read offline.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(favorites.favorites, key = { it.post.id }) { favorite ->
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            favorites.remove(favorite.post.id)
                            true
                        } else {
                            false
                        }
                    })
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                    ) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                            PostRow(favorite.post, onClick = { open(Screen.Article(favorite.post)) })
                        }
                    }
                }
            }
        }
    }
}
