package com.luminshade

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.luminshade.data.PresetData
import com.luminshade.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var presetManager: PresetManager
    private lateinit var adapter: PresetAdapter
    private val presets = mutableListOf<PresetData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetManager = PresetManager(this)
        setupPresetList()
        setupButtons()
        setupAlphaSlider()
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshPresets()
    }

    private fun setupPresetList() {
        presets.addAll(presetManager.loadAll())
        adapter = PresetAdapter(
            items = presets,
            activeId = { presetManager.getActivePresetId() },
            onLoad = { loadPreset(it) },
            onRename = { showRenameDialog(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvPresets.layoutManager = LinearLayoutManager(this)
        binding.rvPresets.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnGrantPermission.setOnClickListener { openOverlaySettings() }
        binding.btnNewPreset.setOnClickListener { showNewPresetDialog() }
        binding.btnAddMask.setOnClickListener { sendToService(OverlayService.ACTION_ADD_MASK) }
        binding.btnClearMasks.setOnClickListener { sendToService(OverlayService.ACTION_CLEAR_MASKS) }
        binding.btnSavePreset.setOnClickListener { saveCurrentToActivePreset() }
        binding.btnBatteryOptimize.setOnClickListener { openBatterySettings() }
    }

    private fun setupAlphaSlider() {
        binding.seekbarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val alpha = progress / 100f
                    val i = Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_SET_ALPHA
                        putExtra(OverlayService.EXTRA_ALPHA, alpha)
                    }
                    startService(i)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        binding.seekbarAlpha.progress = 100
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_permission_title))
                .setMessage(getString(R.string.dialog_permission_msg))
                .setPositiveButton(getString(R.string.go_to_settings)) { _, _ -> openOverlaySettings() }
                .setCancelable(false)
                .show()
        } else {
            startOverlayService()
        }
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")))
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
    }

    private fun sendToService(action: String, block: (Intent.() -> Unit)? = null) {
        if (!Settings.canDrawOverlays(this)) {
            checkOverlayPermission(); return
        }
        val i = Intent(this, OverlayService::class.java).apply {
            this.action = action
            block?.invoke(this)
        }
        startService(i)
    }

    private fun loadPreset(preset: PresetData) {
        sendToService(OverlayService.ACTION_LOAD_PRESET) {
            putExtra(OverlayService.EXTRA_PRESET_ID, preset.id)
        }
        presetManager.setActivePresetId(preset.id)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, getString(R.string.preset_loaded, preset.name), Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentToActivePreset() {
        val id = presetManager.getActivePresetId()
        if (id == null) {
            Toast.makeText(this, getString(R.string.no_active_preset), Toast.LENGTH_SHORT).show()
            return
        }
        sendToService(OverlayService.ACTION_SAVE_TO_PRESET) {
            putExtra(OverlayService.EXTRA_PRESET_ID, id)
        }
        val name = presets.find { it.id == id }?.name ?: id
        Toast.makeText(this, getString(R.string.preset_saved, name), Toast.LENGTH_SHORT).show()
    }

    private fun showNewPresetDialog() {
        val input = EditText(this).apply { hint = getString(R.string.hint_preset_name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_new_preset))
            .setView(input)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val p = presetManager.newPreset(name)
                    presets.add(p)
                    adapter.notifyItemInserted(presets.size - 1)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRenameDialog(preset: PresetData) {
        val input = EditText(this).apply { setText(preset.name) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_rename))
            .setView(input)
            .setPositiveButton(getString(R.string.rename)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    preset.name = name
                    presetManager.updatePreset(preset)
                    adapter.notifyItemChanged(presets.indexOf(preset))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmDelete(preset: PresetData) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete))
            .setMessage(getString(R.string.dialog_delete_msg, preset.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val idx = presets.indexOf(preset)
                presetManager.deletePreset(preset.id)
                presets.removeAt(idx)
                adapter.notifyItemRemoved(idx)
                if (presetManager.getActivePresetId() == preset.id) {
                    presetManager.setActivePresetId(null)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun refreshPermissionState() {
        val granted = Settings.canDrawOverlays(this)
        binding.tvPermissionState.text = getString(
            if (granted) R.string.permission_granted else R.string.permission_denied
        )
        binding.btnGrantPermission.isEnabled = !granted
        if (granted) startOverlayService()
    }

    private fun refreshPresets() {
        presets.clear()
        presets.addAll(presetManager.loadAll())
        adapter.notifyDataSetChanged()
    }
}
