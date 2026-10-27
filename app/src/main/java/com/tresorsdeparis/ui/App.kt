package com.tresorsdeparis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.tresorsdeparis.data.Arrondissement
import com.tresorsdeparis.data.ArrondissementTag
import com.tresorsdeparis.data.FavoritesStore
import com.tresorsdeparis.data.Post
import com.tresorsdeparis.data.PostCategory
import com.tresorsdeparis.data.PostSource
import com.tresorsdeparis.data.WordPressApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalFavorites = staticCompositionLocalOf<FavoritesStore> { error("FavoritesStore not provided") }

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Map("Map", Icons.Outlined.Map, Icons.Filled.Map),
    Categories("Categories", Icons.Outlined.GridView, Icons.Filled.GridView),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Favorites("Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
}

/** Screens pushed on top of a tab. */
sealed interface Screen {
    data class Article(val post: Post) : Screen
    data class CategoryPosts(val category: PostCategory) : Screen
    data class ArrondissementPosts(val arrondissement: Arrondissement) : Screen
    data object Website : Screen
}

/** App-wide state that should survive switching tabs and navigating back. */
class AppState(private val scope: CoroutineScope) {
    val stacks: Map<Tab, SnapshotStateList<Screen>> = Tab.entries.associateWith { mutableStateListOf() }
    private val postLists = mutableMapOf<String, PostListState>()

    fun posts(source: PostSource): PostListState =
        postLists.getOrPut(source.key) { PostListState(source, scope) }

    var categories by mutableStateOf<List<PostCategory>>(emptyList())
        private set
    var categoriesFailed by mutableStateOf(false)
        private set

    fun loadCategories() {
        categoriesFailed = false
        scope.launch {
            try {
                categories = WordPressApi.categories()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                categoriesFailed = true
            }
        }
    }

    var arrondissementTags by mutableStateOf<Map<Int, ArrondissementTag>>(emptyMap())
        private set
    var tagsLoaded by mutableStateOf(false)
        private set
    var tagsFailed by mutableStateOf(false)
        private set

    fun loadArrondissementTags() {
        tagsFailed = false
        scope.launch {
            try {
                arrondissementTags = WordPressApi.arrondissementTags()
                tagsLoaded = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tagsFailed = true
            }
        }
    }
}

@Composable
fun TresorsApp(startTab: Int = 0) {
    val scope = rememberCoroutineScope()
    val app = remember { AppState(scope) }
    var tab by rememberSaveable { mutableStateOf(Tab.entries.getOrElse(startTab) { Tab.Home }) }
    val stack = app.stacks.getValue(tab)
    val savedState = rememberSaveableStateHolder()

    fun stateKey(depth: Int) = "${tab.name}-$depth"

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        savedState.removeState(stateKey(stack.size))
        stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.isNotEmpty()) { back() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Home) { tab = Tab.Home }

    Scaffold(
        // Screens handle the status bar themselves; only the bottom bar's space is reserved here
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                // Tapping the current tab goes back to its first screen
                                while (stack.isNotEmpty()) back()
                            } else {
                                tab = item
                            }
                        },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
            savedState.SaveableStateProvider(stateKey(stack.size)) {
                when (val screen = stack.lastOrNull()) {
                    null -> when (tab) {
                        Tab.Home -> HomeScreen(app, ::open)
                        Tab.Map -> MapScreen(app, ::open)
                        Tab.Categories -> CategoriesScreen(app, ::open)
                        Tab.Search -> SearchScreen(app, ::open)
                        Tab.Favorites -> FavoritesScreen(::open)
                    }
                    is Screen.Article -> ArticleScreen(screen.post, ::back)
                    is Screen.CategoryPosts -> CategoryPostsScreen(app, screen.category, ::back, ::open)
                    is Screen.ArrondissementPosts -> ArrondissementPostsScreen(app, screen.arrondissement, ::back, ::open)
                    Screen.Website -> WebsiteScreen(::back)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // Tab roots get a large serif title that collapses on scroll; pushed screens a compact bar
    val large = onBack == null
    val scrollBehavior = if (large) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (large) {
                LargeTopAppBar(title = { Text(title) }, actions = actions, scrollBehavior = scrollBehavior)
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack!!) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = content,
    )
}
