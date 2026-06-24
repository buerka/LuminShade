package com.luminshade

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luminshade.data.PresetData

class PresetAdapter(
    private val items: MutableList<PresetData>,
    private val activeId: () -> String?,
    private val onLoad: (PresetData) -> Unit,
    private val onRename: (PresetData) -> Unit,
    private val onDelete: (PresetData) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_preset_name)
        val btnLoad: TextView = view.findViewById(R.id.btn_load)
        val btnRename: ImageButton = view.findViewById(R.id.btn_rename)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        val activeIndicator: View = view.findViewById(R.id.view_active)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_preset, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = items[position]
        holder.name.text = preset.name
        holder.activeIndicator.visibility =
            if (preset.id == activeId()) View.VISIBLE else View.INVISIBLE
        holder.btnLoad.setOnClickListener { onLoad(preset) }
        holder.btnRename.setOnClickListener { onRename(preset) }
        holder.btnDelete.setOnClickListener { onDelete(preset) }
    }

    override fun getItemCount() = items.size
}
