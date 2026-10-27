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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tresorsdeparis.data.Arrondissement
import com.tresorsdeparis.data.PostCategory
import com.tresorsdeparis.data.PostSource

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
                    Text(
                        "TRÉSORS DE PARIS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                    )
                    Text("Your insider's guide to Paris", style = MaterialTheme.typography.headlineMedium)
                }
                FilledTonalIconButton(onClick = { open(Screen.Website) }) {
                    Icon(Icons.Outlined.Language, contentDescription = "Open the website")
                }
            }
        },
    )
}

@Composable
fun CategoriesScreen(app: AppState, open: (Screen) -> Unit) {
    LaunchedEffect(Unit) {
        if (app.categories.isEmpty()) app.loadCategories()
    }
    val categories = app.categories
    val topLevel = categories.filter { it.parent == 0 && it.id != 1 }.sortedBy { it.name }
    fun children(parent: PostCategory) = categories.filter { it.parent == parent.id }.sortedBy { it.name }

    ScreenScaffold("Categories") { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                categories.isEmpty() && app.categoriesFailed -> OfflineView(onRetry = app::loadCategories)
                categories.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    topLevel.filter { children(it).isNotEmpty() }.forEach { parent ->
                        item(key = parent.id) {
                            CategoryGroup(parent.name, listOf(parent.copy(name = "All ${parent.name}")) + children(parent), open)
                        }
                    }
                    item(key = "more") {
                        CategoryGroup("More topics", topLevel.filter { children(it).isEmpty() }, open)
                    }
                }
            }
        }
    }
}

/** A titled, rounded group of categories. */
@Composable
private fun CategoryGroup(title: String, categories: List<PostCategory>, open: (Screen) -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                categories.forEachIndexed { index, category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${category.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { open(Screen.CategoryPosts(category)) },
                    )
                    if (index < categories.lastIndex) HorizontalDivider(Modifier.padding(start = 16.dp))
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
fun ArrondissementPostsScreen(app: AppState, arrondissement: Arrondissement, back: () -> Unit, open: (Screen) -> Unit) {
    // The map may have opened before the tags loaded (e.g. offline), so look them up here too
    LaunchedEffect(Unit) {
        if (!app.tagsLoaded) app.loadArrondissementTags()
    }
    val tag = app.arrondissementTags[arrondissement.number]
    val emptyMessage = "No articles about the ${arrondissement.ordinal} yet."

    ScreenScaffold(arrondissement.title, onBack = back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                tag != null -> PostList(app.posts(PostSource.Tag(tag.id)), onOpen = { open(Screen.Article(it)) }, emptyMessage = emptyMessage)
                app.tagsLoaded -> EmptyView(Icons.Outlined.Map, emptyMessage)
                app.tagsFailed -> OfflineView(onRetry = app::loadArrondissementTags)
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
