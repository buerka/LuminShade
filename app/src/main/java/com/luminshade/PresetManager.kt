package com.luminshade

import android.content.Context
import com.luminshade.data.PresetData
import org.json.JSONArray
import java.util.UUID

class PresetManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("luminshade_presets", Context.MODE_PRIVATE)

    fun loadAll(): MutableList<PresetData> {
        val json = prefs.getString("presets", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<PresetData>()
        for (i in 0 until arr.length()) {
            list.add(PresetData.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun saveAll(presets: List<PresetData>) {
        val arr = JSONArray()
        presets.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("presets", arr.toString()).apply()
    }

    fun newPreset(name: String): PresetData {
        val preset = PresetData(id = UUID.randomUUID().toString(), name = name)
        val all = loadAll()
        all.add(preset)
        saveAll(all)
        return preset
    }

    fun deletePreset(id: String) {
        val all = loadAll().filter { it.id != id }
        saveAll(all)
    }

    fun updatePreset(preset: PresetData) {
        val all = loadAll()
        val idx = all.indexOfFirst { it.id == preset.id }
        if (idx >= 0) all[idx] = preset else all.add(preset)
        saveAll(all)
    }

    fun getActivePresetId(): String? = prefs.getString("active_preset", null)

    fun setActivePresetId(id: String?) {
        prefs.edit().putString("active_preset", id).apply()
    }
}
