package com.tresorsdeparis.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saved articles, stored with their full content so they can be read offline. */
class FavoritesStore(context: Context) {
    data class Favorite(val post: Post, val content: String, val savedAt: Long)

    private val file = File(context.filesDir, "favorites.json")
    val favorites = mutableStateListOf<Favorite>()

    init {
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                favorites += Favorite(
                    post = Post.fromJson(json.getJSONObject("post")),
                    content = json.getString("content"),
                    savedAt = json.getLong("savedAt"),
                )
            }
        }
    }

    fun isFavorite(postId: Int) = favorites.any { it.post.id == postId }

    fun content(postId: Int) = favorites.firstOrNull { it.post.id == postId }?.content

    fun toggle(post: Post, content: String) {
        if (isFavorite(post.id)) {
            favorites.removeAll { it.post.id == post.id }
        } else {
            favorites.add(0, Favorite(post, content, System.currentTimeMillis()))
        }
        save()
    }

    /** Keeps the offline copy of a saved article in sync with the website. */
    fun updateContent(postId: Int, content: String) {
        val index = favorites.indexOfFirst { it.post.id == postId }
        if (index < 0 || favorites[index].content == content) return
        favorites[index] = favorites[index].copy(content = content)
        save()
    }

    fun remove(postId: Int) {
        favorites.removeAll { it.post.id == postId }
        save()
    }

    private fun save() {
        val array = JSONArray()
        favorites.forEach {
            array.put(JSONObject().put("post", it.post.toJson()).put("content", it.content).put("savedAt", it.savedAt))
        }
        runCatching { file.writeText(array.toString()) }
    }
}
