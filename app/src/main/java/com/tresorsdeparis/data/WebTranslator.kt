package com.tresorsdeparis.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * Translates texts from the website into French with the website's own translator: the
 * Google website translator that GTranslate puts on tresors-de-paris.com. It runs in a
 * hidden web page, so no separate translation service or key is needed. Results are kept
 * on the device, so every text is translated only once.
 */
@SuppressLint("SetJavaScriptEnabled")
object WebTranslator {
    private const val BatchSize = 60
    private const val MaxPolls = 40

    /** The translator script, for pages that should show up in French. */
    val widget = """
        <div id="gt-element" style="display:none"></div>
        <style>
          iframe.skiptranslate, .goog-te-banner-frame, #goog-gt-tt, .goog-te-balloon-frame { display: none !important; }
          body { top: 0 !important; position: static !important; }
          font { background: none !important; box-shadow: none !important; }
        </style>
        <script>function gtInit(){ new google.translate.TranslateElement({pageLanguage: 'en', includedLanguages: 'fr', autoDisplay: false}, 'gt-element'); }</script>
        <script src="https://translate.google.com/translate_a/element.js?cb=gtInit"></script>
    """.trimIndent()

    private lateinit var context: Context
    private lateinit var file: File
    private val handler = Handler(Looper.getMainLooper())

    /** English text to French; a state map, so screens update when a translation arrives. */
    private val french = mutableStateMapOf<String, String>()
    private val queue = LinkedHashSet<String>()
    private val failed = HashSet<String>()
    private var webView: WebView? = null
    private var busy = false

    fun init(context: Context) {
        this.context = context.applicationContext
        file = File(context.filesDir, "translations-fr.json")
        runCatching {
            val json = JSONObject(file.readText())
            json.keys().forEach { french[it] = json.getString(it) }
        }
        setLanguageCookie()
    }

    /**
     * [english] in the chosen language. In French it returns the English text until the
     * translation is ready; reading it in a composable shows the French text when it is.
     */
    fun text(english: String): String {
        if (!Lang.isFrench || english.isBlank()) return english
        // The app's own wording first, so the website's "Home" reads "Accueil" like the tab
        French[english]?.let { return it }
        french[english]?.let { return it }
        if (english !in failed && queue.add(english) && !busy) {
            handler.removeCallbacks(next)
            handler.postDelayed(next, 150)
        }
        return english
    }

    /** Tells the website which language to show, for article pages and the website itself. */
    fun setLanguageCookie() {
        val value = if (Lang.isFrench) "/en/fr" else "/en/en"
        val cookies = CookieManager.getInstance()
        cookies.setCookie(WordPressApi.SITE, "googtrans=$value; path=/")
        cookies.setCookie(WordPressApi.SITE, "googtrans=$value; path=/; domain=.tresors-de-paris.com")
        cookies.flush()
    }

    /** Called after switching languages: tries texts again that failed before. */
    fun languageChanged() {
        failed.clear()
        setLanguageCookie()
    }

    private val next = Runnable { translateNext() }

    private fun translateNext() {
        if (busy || queue.isEmpty() || !Lang.isFrench) return
        busy = true
        val batch = queue.take(BatchSize)
        translate(batch) { results ->
            french.putAll(results)
            batch.forEach { text ->
                queue.remove(text)
                if (text !in results) failed += text
            }
            save()
            busy = false
            if (queue.isNotEmpty()) handler.post(next)
        }
    }

    private fun translate(texts: List<String>, done: (Map<String, String>) -> Unit) {
        val view = webView ?: WebView(context).also {
            it.settings.javaScriptEnabled = true
            webView = it
        }
        val page = buildString {
            append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"></head><body>")
            texts.forEach { append("<p class=\"t\">").append(it.htmlEscaped()).append("</p>") }
            append(widget)
            append("</body></html>")
        }
        var finished = false
        fun finish(results: Map<String, String>) {
            if (finished) return
            finished = true
            done(results)
        }

        // Google wraps every translated piece in <font>, so a paragraph with one is done
        val script = "JSON.stringify([...document.querySelectorAll('p.t')].map(p => p.querySelector('font') ? p.innerText : null))"
        fun poll(attempt: Int) {
            view.evaluateJavascript(script) { result ->
                val translated = runCatching {
                    val array = JSONArray(JSONTokener(result).nextValue() as String)
                    (0 until array.length()).map { if (array.isNull(it)) null else array.getString(it).trim() }
                }.getOrNull().orEmpty()
                if ((translated.size == texts.size && translated.all { it != null }) || attempt >= MaxPolls) {
                    val results = texts.zip(translated).mapNotNull { (english, french) ->
                        french?.takeIf { it.isNotEmpty() }?.let { english to it }
                    }.toMap()
                    Log.d("WebTranslator", "Translated ${results.size} of ${texts.size}")
                    finish(results)
                } else {
                    handler.postDelayed({ poll(attempt + 1) }, 500)
                }
            }
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                handler.postDelayed({ poll(0) }, 500)
            }
        }
        setLanguageCookie()
        view.loadDataWithBaseURL(WordPressApi.SITE, page, "text/html", "utf-8", null)
    }

    private fun save() {
        val json = JSONObject()
        french.forEach { (english, translation) -> json.put(english, translation) }
        runCatching { file.writeText(json.toString()) }
    }
}

/** A text from the website (title, excerpt, menu item) in the chosen language. */
fun translated(english: String): String = WebTranslator.text(english)
