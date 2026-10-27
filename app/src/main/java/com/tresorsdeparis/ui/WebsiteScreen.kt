package com.tresorsdeparis.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tresorsdeparis.data.WordPressApi

/** The full tresors-de-paris.com website, as in the original app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsiteScreen(back: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val gold = MaterialTheme.colorScheme.primary.toArgb()

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    ScreenScaffold("Website", onBack = back) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    SwipeRefreshLayout(context).apply {
                        val refreshLayout = this
                        setColorSchemeColors(gold)
                        val view = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(true)
                            settings.displayZoomControls = false
                            webViewClient = object : WebViewClient() {
                                // Hand app links (fb://, mailto:, tel:, ...) to the system
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val scheme = request.url.scheme?.lowercase()
                                    if (scheme == "http" || scheme == "https") return false
                                    openLink(context, request.url)
                                    return true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    isLoading = false
                                    refreshLayout.isRefreshing = false
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (!request.isForMainFrame) return
                                    failed = true
                                    isLoading = false
                                    refreshLayout.isRefreshing = false
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    canGoBack = view.canGoBack()
                                }
                            }
                            loadUrl(WordPressApi.SITE)
                        }
                        webView = view
                        addView(view)
                        setOnRefreshListener {
                            failed = false
                            view.reload()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (failed) {
                OfflineView(
                    onRetry = {
                        failed = false
                        isLoading = true
                        webView?.reload()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            } else if (isLoading) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.padding(bottom = 16.dp))
                        Text("Loading TdP ...")
                        Text("Your Insider's Guide to Paris")
                    }
                }
            }
        }
    }
}
