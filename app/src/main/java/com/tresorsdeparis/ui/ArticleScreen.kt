package com.tresorsdeparis.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tresorsdeparis.data.Post
import com.tresorsdeparis.data.WordPressApi
import com.tresorsdeparis.data.htmlEscaped
import kotlinx.coroutines.CancellationException

/** One article rendered natively from its WordPress content, with favorite and share buttons. */
@Composable
fun ArticleScreen(post: Post, back: () -> Unit) {
    val favorites = LocalFavorites.current
    val context = LocalContext.current
    // Saved favorites open instantly and offline; refresh them when online
    var content by remember(post.id) { mutableStateOf(favorites.content(post.id)) }
    var failed by remember(post.id) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(post.id, attempt) {
        failed = false
        try {
            val fresh = WordPressApi.content(post.id)
            content = fresh
            favorites.updateContent(post.id, fresh)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failed = content == null
        }
    }

    val isFavorite = favorites.isFavorite(post.id)
    ScreenScaffold(
        title = "",
        onBack = back,
        actions = {
            IconButton(onClick = { content?.let { favorites.toggle(post, it) } }, enabled = content != null) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                )
            }
            IconButton(onClick = { share(context, post) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Share")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val html = content
            when {
                html != null -> ArticleWebView(articlePage(post, html, isSystemInDarkTheme()))
                failed -> OfflineView(onRetry = { attempt++ })
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArticleWebView(html: String) {
    val background = MaterialTheme.colorScheme.background.toArgb()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Needed for YouTube embeds in articles
                settings.javaScriptEnabled = true
                setBackgroundColor(background)
                webViewClient = object : WebViewClient() {
                    // Let embeds load; send tapped links out of the article
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        openLink(context, request.url)
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(WordPressApi.SITE, html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Opens web links in a Custom Tab and other links (fb://, mailto:, ...) in the matching app. */
fun openLink(context: Context, uri: Uri) {
    try {
        when (uri.scheme?.lowercase()) {
            "http", "https" -> CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
            else -> context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (_: ActivityNotFoundException) {
    }
}

private fun share(context: Context, post: Post) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, post.title)
        .putExtra(Intent.EXTRA_TEXT, post.link)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun articlePage(post: Post, content: String, dark: Boolean): String {
    val (background, text) = if (dark) "#121212" to "#E8E8E8" else "#FFFFFF" to "#1A1A1A"
    val link = if (dark) "#D4A94A" else "#B8892B"
    val hero = post.imageUrl?.let { "<img class=\"hero\" src=\"${it.htmlEscaped()}\" alt=\"\">" }.orEmpty()
    return """
        <!doctype html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body { font-family: sans-serif; font-size: 17px; margin: 0 16px 48px; line-height: 1.55;
                 background: $background; color: $text; }
          img, video { max-width: 100%; height: auto; border-radius: 8px; }
          iframe { max-width: 100%; }
          figure { margin: 16px 0; }
          a { color: $link; }
          h1, h2, h3 { line-height: 1.25; }
          .hero { display: block; width: calc(100% + 32px); max-width: none; margin: 0 -16px; border-radius: 0; }
          .title { font-size: 28px; margin: 20px 0 4px; }
          .date { color: gray; font-size: 14px; margin-bottom: 16px; }
        </style>
        </head>
        <body>
        $hero
        <h1 class="title">${post.title.htmlEscaped()}</h1>
        <div class="date">${formatDate(post.date)}</div>
        $content
        </body>
        </html>
    """.trimIndent()
}
