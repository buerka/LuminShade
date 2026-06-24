package com.luminshade.data

import org.json.JSONObject

data class MaskData(
    val id: String,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    var alpha: Float = 1.0f,
    var locked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x)
        put("y", y)
        put("width", width)
        put("height", height)
        put("alpha", alpha.toDouble())
        put("locked", locked)
    }

    companion object {
        fun fromJson(obj: JSONObject) = MaskData(
            id = obj.getString("id"),
            x = obj.getInt("x"),
            y = obj.getInt("y"),
            width = obj.getInt("width"),
            height = obj.getInt("height"),
            alpha = obj.optDouble("alpha", 1.0).toFloat(),
            locked = obj.optBoolean("locked", false)
        )
    }
}
