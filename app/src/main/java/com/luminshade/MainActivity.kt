package com.luminshade

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.luminshade.data.PresetData
import com.luminshade.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 3001
        private const val SETTINGS_PREFS = "luminshade_color_match"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var presetManager: PresetManager
    private lateinit var adapter: PresetAdapter
    private val presets = mutableListOf<PresetData>()
    private var matchColor = 0xFFFFFFFF.toInt()
    private var replaceColor = 0xFFFFEB3B.toInt()
    private var colorTolerance = 24
    private var spreadRadius = 1
    private var screenCaptureReady = false
    private var pendingPickTarget: String? = null
    private var suppressColorMatchEvents = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetManager = PresetManager(this)
        loadColorMatchPrefs()
        setupPresetList()
        setupButtons()
        setupAlphaSlider()
        setupColorMatchControls()
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

    private fun setupColorMatchControls() {
        syncColorMatchControls()
        updateColorMatchUi()

        binding.switchColorMatch.setOnCheckedChangeListener { _, checked ->
            if (suppressColorMatchEvents) return@setOnCheckedChangeListener
            if (checked) {
                requestScreenCapture()
            } else {
                screenCaptureReady = false
                sendToService(OverlayService.ACTION_SET_COLOR_MATCH) {
                    putExtra(OverlayService.EXTRA_COLOR_MATCH_ENABLED, false)
                }
            }
        }

        binding.btnMatchColor.setOnClickListener {
            showColorDialog(matchColor) { color ->
                matchColor = color
                updateColorMatchUi()
                sendColorMatchSettings()
            }
        }

        binding.btnReplaceColor.setOnClickListener {
            showColorDialog(replaceColor) { color ->
                replaceColor = color
                updateColorMatchUi()
                sendColorMatchSettings()
            }
        }

        binding.btnPickMatchColor.setOnClickListener {
            startPickMode(OverlayService.PICK_TARGET_MATCH)
        }

        binding.btnPickReplaceColor.setOnClickListener {
            startPickMode(OverlayService.PICK_TARGET_REPLACE)
        }

        binding.seekbarTolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                colorTolerance = progress
                updateColorMatchUi()
                if (fromUser) sendColorMatchSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.seekbarSpread.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                spreadRadius = progress
                updateColorMatchUi()
                if (fromUser) sendColorMatchSettings()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
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

    private fun requestScreenCapture() {
        if (!Settings.canDrawOverlays(this)) {
            setColorMatchSwitchChecked(false)
            checkOverlayPermission()
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
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

    private fun sendColorMatchSettings() {
        saveColorMatchPrefs()
        sendToService(OverlayService.ACTION_SET_COLOR_MATCH_SETTINGS) {
            putExtra(OverlayService.EXTRA_MATCH_COLOR, matchColor)
            putExtra(OverlayService.EXTRA_REPLACE_COLOR, replaceColor)
            putExtra(OverlayService.EXTRA_COLOR_TOLERANCE, colorTolerance)
            putExtra(OverlayService.EXTRA_SPREAD_RADIUS, spreadRadius)
        }
    }

    private fun startPickMode(target: String) {
        pendingPickTarget = target
        if (!screenCaptureReady || !binding.switchColorMatch.isChecked) {
            requestScreenCapture()
            return
        }
        beginServicePick(target)
        pendingPickTarget = null
    }

    private fun beginServicePick(target: String) {
        sendColorMatchSettings()
        sendToService(OverlayService.ACTION_SET_COLOR_MATCH) {
            putExtra(OverlayService.EXTRA_COLOR_MATCH_ENABLED, true)
        }
        sendToService(OverlayService.ACTION_START_COLOR_PICK) {
            putExtra(OverlayService.EXTRA_PICK_TARGET, target)
        }
        moveTaskToBack(true)
    }

    private fun showColorDialog(initialColor: Int, onColor: (Int) -> Unit) {
        val input = EditText(this).apply {
            setText(colorToHex(initialColor))
            hint = getString(R.string.hint_color_hex)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_color_title))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val parsed = parseColor(input.text.toString())
                if (parsed == null) {
                    Toast.makeText(this, R.string.invalid_color, Toast.LENGTH_SHORT).show()
                } else {
                    onColor(parsed)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun parseColor(value: String): Int? = try {
        Color.parseColor(value.trim())
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun colorToHex(color: Int): String {
        val alpha = Color.alpha(color)
        return if (alpha == 255) {
            "#%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))
        } else {
            "#%02X%02X%02X%02X".format(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    private fun updateColorMatchUi() {
        binding.tvToleranceValue.text = getString(R.string.label_tolerance, colorTolerance)
        binding.tvSpreadValue.text = getString(R.string.label_spread_radius, spreadRadius)
        updateColorButton(binding.btnMatchColor, getString(R.string.btn_match_color), matchColor)
        updateColorButton(binding.btnReplaceColor, getString(R.string.btn_replace_color), replaceColor)
    }

    private fun loadColorMatchPrefs() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        matchColor = prefs.getInt("match_color", matchColor)
        replaceColor = prefs.getInt("replace_color", replaceColor)
        colorTolerance = prefs.getInt("tolerance", colorTolerance)
        spreadRadius = prefs.getInt("spread_radius", spreadRadius)
    }

    private fun saveColorMatchPrefs() {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
            .putInt("match_color", matchColor)
            .putInt("replace_color", replaceColor)
            .putInt("tolerance", colorTolerance)
            .putInt("spread_radius", spreadRadius)
            .apply()
    }

    private fun syncColorMatchControls() {
        suppressColorMatchEvents = true
        binding.seekbarTolerance.progress = colorTolerance
        binding.seekbarSpread.progress = spreadRadius
        suppressColorMatchEvents = false
    }

    private fun setColorMatchSwitchChecked(checked: Boolean) {
        suppressColorMatchEvents = true
        binding.switchColorMatch.isChecked = checked
        suppressColorMatchEvents = false
    }

    private fun updateColorButton(button: android.widget.Button, label: String, color: Int) {
        button.text = "$label ${colorToHex(color)}"
        button.backgroundTintList = ColorStateList.valueOf(color)
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        button.setTextColor(if (luminance < 150) Color.WHITE else Color.BLACK)
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
        loadColorMatchPrefs()
        syncColorMatchControls()
        updateColorMatchUi()
        presets.clear()
        presets.addAll(presetManager.loadAll())
        adapter.notifyDataSetChanged()
    }

    @Deprecated("Deprecated in Android framework, still sufficient for this app flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREEN_CAPTURE) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            screenCaptureReady = false
            pendingPickTarget = null
            setColorMatchSwitchChecked(false)
            return
        }

        screenCaptureReady = true
        setColorMatchSwitchChecked(true)
        sendToService(OverlayService.ACTION_START_SCREEN_CAPTURE) {
            putExtra(OverlayService.EXTRA_CAPTURE_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_CAPTURE_RESULT_DATA, data)
        }
        sendColorMatchSettings()
        sendToService(OverlayService.ACTION_SET_COLOR_MATCH) {
            putExtra(OverlayService.EXTRA_COLOR_MATCH_ENABLED, true)
        }
        pendingPickTarget?.let { target ->
            Handler(Looper.getMainLooper()).postDelayed({
                beginServicePick(target)
            }, 350L)
            pendingPickTarget = null
        }
    }
}
