package com.tresorsdeparis.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A department ("77") or a Paris arrondissement ("75008"). */
data class MapArea(
    /** The start of the postcode tags that belong to this area. */
    val code: String,
    val badge: String,
    val title: String,
    val name: String,
    /** How the area is named in a sentence, like "the 8th". */
    val short: String,
    val labelX: Float,
    val labelY: Float,
    val bounds: List<Float>,
    val tone: Int,
    /** Outlines as flat x,y pairs in map coordinates. */
    val rings: List<FloatArray>,
) {
    val isDepartment get() = code.length == 2

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

data class MapPlace(val name: String, val x: Float, val y: Float)

/** Map of Île-de-France, pre-projected to a [width] x [height] plane. */
class RegionMapData(
    val width: Float,
    val height: Float,
    /** Left, top, right and bottom of Paris. */
    val parisBounds: List<Float>,
    val departments: List<MapArea>,
    val arrondissements: List<MapArea>,
    /** Rivers as flat x,y pairs. */
    val rivers: List<FloatArray>,
    val parks: List<Park>,
    val landmarks: List<MapPlace>,
    val towns: List<MapPlace>,
    val attribution: String,
) {
    val parisWidth get() = parisBounds[2] - parisBounds[0]

    fun area(code: String?): MapArea? = (departments + arrondissements).firstOrNull { it.code == code }

    companion object {
        @Volatile private var cached: RegionMapData? = null

        fun load(context: Context): RegionMapData = cached ?: synchronized(this) {
            cached ?: parse(context.assets.open("ile-de-france.json").bufferedReader().use { it.readText() })
                .also { cached = it }
        }

        private fun JSONArray.toFloats() = FloatArray(length()) { getDouble(it).toFloat() }

        private fun JSONArray.rings() = (0 until length()).map { getJSONArray(it).toFloats() }

        private fun <T> JSONArray.objects(transform: (JSONObject) -> T) = (0 until length()).map { transform(getJSONObject(it)) }

        private fun JSONArray.areas() = objects { item ->
            val label = item.getJSONArray("label")
            MapArea(
                code = item.getString("code"),
                badge = item.getString("badge"),
                title = item.getString("title"),
                name = item.getString("name"),
                short = item.getString("short"),
                labelX = label.getDouble(0).toFloat(),
                labelY = label.getDouble(1).toFloat(),
                bounds = item.getJSONArray("bounds").toFloats().toList(),
                tone = item.getInt("tone"),
                rings = item.getJSONArray("rings").rings(),
            )
        }

        private fun JSONArray.places() = objects { item ->
            val point = item.getJSONArray("point")
            MapPlace(item.getString("name"), point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
        }

        private fun parse(text: String): RegionMapData {
            val json = JSONObject(text)
            val parks = json.getJSONArray("parks").objects { item ->
                val label = item.getJSONArray("label")
                Park(
                    name = item.getString("name"),
                    kind = item.getString("kind"),
                    labelX = label.getDouble(0).toFloat(),
                    labelY = label.getDouble(1).toFloat(),
                    rings = item.getJSONArray("rings").rings(),
                )
            }
            return RegionMapData(
                width = json.getDouble("width").toFloat(),
                height = json.getDouble("height").toFloat(),
                parisBounds = json.getJSONArray("parisBounds").toFloats().toList(),
                departments = json.getJSONArray("departments").areas(),
                arrondissements = json.getJSONArray("arrondissements").areas(),
                rivers = json.getJSONArray("rivers").rings(),
                parks = parks,
                landmarks = json.getJSONArray("landmarks").places(),
                towns = json.getJSONArray("towns").places(),
                attribution = json.getString("attribution"),
            )
        }
    }
}
