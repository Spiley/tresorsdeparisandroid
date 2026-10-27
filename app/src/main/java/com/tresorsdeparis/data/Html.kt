package com.tresorsdeparis.data

import android.text.Html

private val whitespace = Regex("\\s+")

/** Plain text from an HTML fragment: tags removed, entities decoded, whitespace collapsed. */
fun String.htmlStripped(): String =
    Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace("￼", "")
        .replace(whitespace, " ")
        .trim()

/** Escapes text for safe insertion into HTML. */
fun String.htmlEscaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
