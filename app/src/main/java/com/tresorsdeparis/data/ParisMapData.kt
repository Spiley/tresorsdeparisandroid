package com.tresorsdeparis.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Arrondissement(
    val number: Int,
    val name: String,
    val labelX: Float,
    val labelY: Float,
    /** Outlines as flat x,y pairs in map coordinates. */
    val rings: List<FloatArray>,
) {
    val ordinal: String
        get() = when (number) {
            1 -> "1st"
            2 -> "2nd"
            3 -> "3rd"
            else -> "${number}th"
        }

    val title: String get() = "$ordinal arrondissement"

    /** Ray-casting point-in-polygon test in map coordinates. */
    fun contains(x: Float, y: Float): Boolean {
        var inside = false
        for (ring in rings) {
            var j = ring.size - 2
            for (i in 0 until ring.size step 2) {
                val xi = ring[i]
                val yi = ring[i + 1]
                val xj = ring[j]
                val yj = ring[j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }
}

data class Park(val name: String, val kind: String, val labelX: Float, val labelY: Float, val rings: List<FloatArray>) {
    val isWood get() = kind == "wood"
    val isCemetery get() = kind == "cemetery"
}

data class Landmark(val name: String, val x: Float, val y: Float)

/** Paris map data from Paris Open Data, pre-projected to a [width] x [height] plane. */
class ParisMapData(
    val width: Float,
    val height: Float,
    val arrondissements: List<Arrondissement>,
    /** The Seine through Paris as flat x,y pairs. */
    val seine: FloatArray,
    val parks: List<Park>,
    val landmarks: List<Landmark>,
    val attribution: String,
) {
    companion object {
        @Volatile private var cached: ParisMapData? = null

        fun load(context: Context): ParisMapData = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("arrondissements.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }

        private fun JSONArray.toFloats() = FloatArray(length()) { getDouble(it).toFloat() }

        private fun JSONArray.rings() = (0 until length()).map { getJSONArray(it).toFloats() }

        private fun parse(text: String): ParisMapData {
            val json = JSONObject(text)
            val arrondissements = json.getJSONArray("arrondissements").let { items ->
                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    val label = item.getJSONArray("label")
                    Arrondissement(
                        number = item.getInt("number"),
                        name = item.getString("name"),
                        labelX = label.getDouble(0).toFloat(),
                        labelY = label.getDouble(1).toFloat(),
                        rings = item.getJSONArray("rings").rings(),
                    )
                }
            }
            val parks = json.getJSONArray("parks").let { items ->
                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    val label = item.getJSONArray("label")
                    Park(
                        name = item.getString("name"),
                        kind = item.getString("kind"),
                        labelX = label.getDouble(0).toFloat(),
                        labelY = label.getDouble(1).toFloat(),
                        rings = item.getJSONArray("rings").rings(),
                    )
                }
            }
            val landmarks = json.getJSONArray("landmarks").let { items ->
                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    val point = item.getJSONArray("point")
                    Landmark(item.getString("name"), point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                }
            }
            return ParisMapData(
                width = json.getDouble("width").toFloat(),
                height = json.getDouble("height").toFloat(),
                arrondissements = arrondissements,
                seine = json.getJSONArray("seine").toFloats(),
                parks = parks,
                landmarks = landmarks,
                attribution = json.getString("attribution"),
            )
        }
    }
}
