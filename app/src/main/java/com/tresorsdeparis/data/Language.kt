package com.tresorsdeparis.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class Language(val code: String, val label: String) {
    English("en", "English"),
    French("fr", "Français"),
    Dutch("nl", "Nederlands"),
    German("de", "Deutsch");

    /** The app's own texts in this language, by their English text. */
    val texts: Map<String, String>
        get() = when (this) {
            English -> emptyMap()
            French -> com.tresorsdeparis.data.French
            Dutch -> com.tresorsdeparis.data.Dutch
            German -> com.tresorsdeparis.data.German
        }
}

/**
 * The language of the app. The app's own texts come from [French], [Dutch] and [German];
 * articles, titles and menus from the website are translated by the website's own
 * translator ([WebTranslator]).
 */
object Lang {
    var current by mutableStateOf(Language.English)
        private set

    private lateinit var prefs: SharedPreferences

    val isFrench get() = current == Language.French
    val isEnglish get() = current == Language.English

    /** Uses the saved choice, or the phone's language when the app has it. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val saved = prefs.getString("language", null) ?: Locale.getDefault().language
        current = Language.entries.firstOrNull { it.code == saved } ?: Language.English
    }

    fun set(language: Language) {
        current = language
        prefs.edit().putString("language", language.code).apply()
        WebTranslator.languageChanged()
    }

    val locale: Locale get() = Locale.forLanguageTag(current.code)
}

/**
 * An app text in the chosen language. The English text is also the key into [French] etc.;
 * `%s` and `%d` in it are filled with [args]. Reading it in a composable updates the
 * screen when the language changes.
 */
fun t(en: String, vararg args: Any): String {
    val text = Lang.current.texts[en] ?: en
    return if (args.isEmpty()) text else String.format(Lang.locale, text, *args)
}

/** "1 article" or "3 articles", in the chosen language. */
fun plural(count: Int, one: String, many: String) = if (count == 1) t(one, count) else t(many, count)
