package com.luminshade.data

import org.json.JSONArray
import org.json.JSONObject

data class PresetData(
    val id: String,
    var name: String,
    val masks: MutableList<MaskData> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        val arr = JSONArray()
        masks.forEach { arr.put(it.toJson()) }
        put("masks", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): PresetData {
            val masks = mutableListOf<MaskData>()
            val arr = obj.optJSONArray("masks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    masks.add(MaskData.fromJson(arr.getJSONObject(i)))
                }
            }
            return PresetData(
                id = obj.getString("id"),
                name = obj.getString("name"),
                masks = masks
            )
        }
    }
}
