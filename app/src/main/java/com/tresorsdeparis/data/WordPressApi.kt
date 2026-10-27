package com.tresorsdeparis.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class Post(
    val id: Int,
    val date: Long,
    val link: String,
    val title: String,
    val excerpt: String,
    val imageUrl: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("date", date)
        .put("link", link)
        .put("title", title)
        .put("excerpt", excerpt)
        .put("imageUrl", imageUrl)

    companion object {
        fun fromJson(json: JSONObject) = Post(
            id = json.getInt("id"),
            date = json.getLong("date"),
            link = json.getString("link"),
            title = json.getString("title"),
            excerpt = json.optString("excerpt"),
            imageUrl = json.optString("imageUrl").ifEmpty { null },
        )
    }
}

data class PostCategory(val id: Int, val name: String, val count: Int, val parent: Int)

data class ArrondissementTag(val id: Int, val count: Int)

sealed interface PostSource {
    val key: String

    data object Latest : PostSource {
        override val key = "latest"
    }

    data class Category(val id: Int) : PostSource {
        override val key = "category-$id"
    }

    data class Tag(val id: Int) : PostSource {
        override val key = "tag-$id"
    }

    data class Search(val query: String) : PostSource {
        override val key = "search-$query"
    }
}

/** Reads articles from the WordPress REST API behind tresors-de-paris.com. */
object WordPressApi {
    const val SITE = "https://tresors-de-paris.com/"
    private const val BASE = "https://tresors-de-paris.com/wp-json/wp/v2/"

    private lateinit var cacheDir: File

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "api").apply { mkdirs() }
    }

    data class Page(val posts: List<Post>, val totalPages: Int)

    suspend fun posts(page: Int, source: PostSource): Page {
        val params = mutableListOf(
            "per_page" to "20",
            "page" to page.toString(),
            "_embed" to "wp:featuredmedia",
            "_fields" to "id,date,link,title,excerpt,_links,_embedded",
        )
        when (source) {
            PostSource.Latest -> Unit
            is PostSource.Category -> params += "categories" to source.id.toString()
            is PostSource.Tag -> params += "tags" to source.id.toString()
            is PostSource.Search -> params += "search" to source.query
        }
        val response = fetch(url("posts", params))
        val array = JSONArray(response.body)
        val posts = (0 until array.length()).mapNotNull { parsePost(array.getJSONObject(it)) }
        return Page(posts, response.totalPages)
    }

    suspend fun content(postId: Int): String {
        val response = fetch(url("posts/$postId", listOf("_fields" to "content")))
        return JSONObject(response.body).getJSONObject("content").getString("rendered")
    }

    suspend fun categories(): List<PostCategory> {
        val params = listOf("per_page" to "100", "hide_empty" to "true", "_fields" to "id,name,count,parent")
        val array = JSONArray(fetch(url("categories", params)).body)
        return (0 until array.length()).map {
            val json = array.getJSONObject(it)
            PostCategory(
                id = json.getInt("id"),
                name = json.getString("name").htmlStripped(),
                count = json.getInt("count"),
                parent = json.getInt("parent"),
            )
        }
    }

    /** The arrondissement tags (slugs 75001...75020), keyed by arrondissement number. */
    suspend fun arrondissementTags(): Map<Int, ArrondissementTag> {
        val slugs = (1..20).joinToString(",") { (75000 + it).toString() }
        val params = listOf("slug" to slugs, "per_page" to "30", "_fields" to "id,slug,count")
        val array = JSONArray(fetch(url("tags", params)).body)
        return (0 until array.length()).mapNotNull {
            val json = array.getJSONObject(it)
            val number = json.getString("slug").toIntOrNull()?.minus(75000) ?: return@mapNotNull null
            number to ArrondissementTag(json.getInt("id"), json.getInt("count"))
        }.toMap()
    }

    private fun url(path: String, params: List<Pair<String, String>>): String =
        BASE + path + "?" + params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

    private class Response(val body: String, val totalPages: Int)

    /** Loads from the network, falling back to the last saved response when offline. */
    private suspend fun fetch(url: String): Response = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, sha1(url))
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val totalPages = connection.getHeaderField("X-WP-TotalPages")?.toIntOrNull() ?: 1
                cacheFile.writeText("$totalPages\n$body")
                Response(body, totalPages)
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            if (!cacheFile.exists()) throw e
            val cached = cacheFile.readText()
            val newline = cached.indexOf('\n')
            Response(cached.substring(newline + 1), cached.substring(0, newline).toIntOrNull() ?: 1)
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun parsePost(json: JSONObject): Post? {
        val date = runCatching { dateFormat().parse(json.getString("date"))?.time }.getOrNull() ?: return null
        return Post(
            id = json.getInt("id"),
            date = date,
            link = json.getString("link"),
            title = json.getJSONObject("title").getString("rendered").htmlStripped(),
            excerpt = json.optJSONObject("excerpt")?.optString("rendered").orEmpty().htmlStripped(),
            imageUrl = featuredImage(json),
        )
    }

    // Missing or private media come back as error objects, so every lookup is optional
    private fun featuredImage(json: JSONObject): String? {
        val media = json.optJSONObject("_embedded")?.optJSONArray("wp:featuredmedia")?.optJSONObject(0) ?: return null
        val sizes = media.optJSONObject("media_details")?.optJSONObject("sizes")
        return listOf("medium_large", "large")
            .firstNotNullOfOrNull { sizes?.optJSONObject(it)?.optString("source_url")?.ifEmpty { null } }
            ?: media.optString("source_url").ifEmpty { null }
    }

    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
    }
}
