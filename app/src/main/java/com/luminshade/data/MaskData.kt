package com.luminshade.data

import org.json.JSONObject

data class MaskData(
    val id: String,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    var alpha: Float = 1.0f,
    var locked: Boolean = false,
    var effectMode: String = MODE_SHADE,
    var matchColor: Int = 0xFFFFFFFF.toInt(),
    var replaceColor: Int = 0xFFFFEB3B.toInt(),
    var colorTolerance: Int = 24,
    var spreadRadius: Int = 1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
        put("alpha", alpha.toDouble())
        put("locked", locked)
        put("effectMode", effectMode)
        put("matchColor", matchColor)
        put("replaceColor", replaceColor)
        put("colorTolerance", colorTolerance)
        put("spreadRadius", spreadRadius)
    }

    companion object {
        const val MODE_SHADE = "shade"
        const val MODE_COLOR_MATCH = "color_match"

        fun fromJson(obj: JSONObject) = MaskData(
            id = obj.getString("id"),
            x = obj.getInt("x"),
            y = obj.getInt("y"),
            width = obj.getInt("width"),
            height = obj.getInt("height"),
            alpha = obj.optDouble("alpha", 1.0).toFloat(),
            locked = obj.optBoolean("locked", false),
            effectMode = obj.optString("effectMode", MODE_SHADE),
            matchColor = obj.optInt("matchColor", 0xFFFFFFFF.toInt()),
            replaceColor = obj.optInt("replaceColor", 0xFFFFEB3B.toInt()),
            colorTolerance = obj.optInt("colorTolerance", 24),
            spreadRadius = obj.optInt("spreadRadius", 1)
        )
    }
}
