package com.luminshade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.luminshade.data.MaskData
import java.util.UUID

class OverlayService : Service() {

    companion object {
        const val ACTION_ADD_MASK = "com.luminshade.ADD_MASK"
        const val ACTION_CLEAR_MASKS = "com.luminshade.CLEAR_MASKS"
        const val ACTION_TOGGLE_VISIBILITY = "com.luminshade.TOGGLE_VISIBILITY"
        const val ACTION_LOAD_PRESET = "com.luminshade.LOAD_PRESET"
        const val ACTION_SAVE_TO_PRESET = "com.luminshade.SAVE_TO_PRESET"
        const val ACTION_SET_ALPHA = "com.luminshade.SET_ALPHA"
        const val ACTION_TOGGLE_EDIT_MODE = "com.luminshade.TOGGLE_EDIT_MODE"
        const val ACTION_START_SCREEN_CAPTURE = "com.luminshade.START_SCREEN_CAPTURE"
        const val ACTION_SET_COLOR_MATCH = "com.luminshade.SET_COLOR_MATCH"
        const val ACTION_SET_COLOR_MATCH_SETTINGS = "com.luminshade.SET_COLOR_MATCH_SETTINGS"
        const val ACTION_START_COLOR_PICK = "com.luminshade.START_COLOR_PICK"
        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_ALPHA = "alpha"
        const val EXTRA_CAPTURE_RESULT_CODE = "capture_result_code"
        const val EXTRA_CAPTURE_RESULT_DATA = "capture_result_data"
        const val EXTRA_COLOR_MATCH_ENABLED = "color_match_enabled"
        const val EXTRA_MATCH_COLOR = "match_color"
        const val EXTRA_REPLACE_COLOR = "replace_color"
        const val EXTRA_COLOR_TOLERANCE = "color_tolerance"
        const val EXTRA_SPREAD_RADIUS = "spread_radius"
        const val EXTRA_PICK_TARGET = "pick_target"
        const val PICK_TARGET_MATCH = "match"
        const val PICK_TARGET_REPLACE = "replace"
        const val CHANNEL_ID = "luminshade_overlay"
        const val NOTIF_ID = 1
        private const val SETTINGS_PREFS = "luminshade_color_match"
    }

    private lateinit var wm: WindowManager
    private lateinit var presetManager: PresetManager
    private val maskViews = mutableListOf<MaskView>()
    private var ballView: FloatingBallView? = null
    private var masksVisible = true
    private var editMode = false
    private var colorMatchEnabled = false
    private var currentMatchColor = 0xFFFFFFFF.toInt()
    private var currentReplaceColor = 0xFFFFEB3B.toInt()
    private var currentColorTolerance = 24
    private var currentSpreadRadius = 1

    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrame: Bitmap? = null
    private var pickerView: ColorPickerOverlayView? = null
    private var pickerPreviewView: ColorPickerPreviewView? = null
    private var pickerTitle = ""
    private var pickerCenterX = 0
    private var pickerCenterY = 0

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        presetManager = PresetManager(this)
        loadColorMatchPrefs()
        createNotificationChannel()
        promoteForegroundForOverlay()
        addFloatingBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_MASK -> addMask()
            ACTION_CLEAR_MASKS -> clearAllMasks()
            ACTION_TOGGLE_VISIBILITY -> toggleVisibility()
            ACTION_LOAD_PRESET -> {
                val id = intent.getStringExtra(EXTRA_PRESET_ID)
                if (id != null) loadPreset(id)
            }
            ACTION_SAVE_TO_PRESET -> {
                val id = intent.getStringExtra(EXTRA_PRESET_ID)
                if (id != null) saveToPreset(id)
            }
            ACTION_SET_ALPHA -> {
                val a = intent.getFloatExtra(EXTRA_ALPHA, 1.0f)
                setAllAlpha(a)
            }
            ACTION_TOGGLE_EDIT_MODE -> toggleEditMode()
            ACTION_START_SCREEN_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_CAPTURE_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CAPTURE_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CAPTURE_RESULT_DATA)
                }
                if (resultData != null) startScreenCapture(resultCode, resultData)
            }
            ACTION_SET_COLOR_MATCH -> {
                val enabled = intent.getBooleanExtra(EXTRA_COLOR_MATCH_ENABLED, false)
                setColorMatchEnabled(enabled)
            }
            ACTION_SET_COLOR_MATCH_SETTINGS -> {
                currentMatchColor = intent.getIntExtra(EXTRA_MATCH_COLOR, currentMatchColor)
                currentReplaceColor = intent.getIntExtra(EXTRA_REPLACE_COLOR, currentReplaceColor)
                currentColorTolerance = intent.getIntExtra(EXTRA_COLOR_TOLERANCE, currentColorTolerance)
                currentSpreadRadius = intent.getIntExtra(EXTRA_SPREAD_RADIUS, currentSpreadRadius)
                saveColorMatchPrefs()
                setColorMatchSettings()
            }
            ACTION_START_COLOR_PICK -> {
                startColorPick(intent.getStringExtra(EXTRA_PICK_TARGET) ?: PICK_TARGET_MATCH)
            }
        }
        return START_STICKY
    }

    private fun addMask() {
        val dm = resources.displayMetrics
        val data = MaskData(
            id = UUID.randomUUID().toString(),
            x = dm.widthPixels / 4,
            y = dm.heightPixels / 3,
            width = dm.widthPixels / 2,
            height = dm.heightPixels / 6
        ).apply {
            if (colorMatchEnabled) effectMode = MaskData.MODE_COLOR_MATCH
            matchColor = currentMatchColor
            replaceColor = currentReplaceColor
            colorTolerance = currentColorTolerance
            spreadRadius = currentSpreadRadius
        }
        addMaskView(data)
    }

    private fun addMaskView(data: MaskData) {
        val view = MaskView(
            context = this,
            data = data,
            onRequestRemove = { mv -> removeMask(mv) },
            onLayoutChanged = { /* layout auto-saved on demand via saveToPreset */ }
        )
        val lp = buildMaskParams(data)
        view.editMode = editMode
        view.wmParams = lp
        view.windowManager = wm
        wm.addView(view, lp)
        maskViews.add(view)
        if (!masksVisible) view.visibility = android.view.View.GONE
        if (data.effectMode == MaskData.MODE_COLOR_MATCH) view.updateScreenFrame(lastFrame)
    }

    private fun removeMask(mv: MaskView) {
        wm.removeView(mv)
        maskViews.remove(mv)
    }

    private fun clearAllMasks() {
        maskViews.toList().forEach { wm.removeView(it) }
        maskViews.clear()
    }

    private fun toggleVisibility() {
        masksVisible = !masksVisible
        val vis = if (masksVisible) android.view.View.VISIBLE else android.view.View.GONE
        maskViews.forEach { it.visibility = vis }
        ballView?.masksVisible = masksVisible
        ballView?.invalidate()
    }

    private fun loadPreset(presetId: String) {
        val preset = presetManager.loadAll().find { it.id == presetId } ?: return
        clearAllMasks()
        preset.masks.forEach { mask ->
            val copy = mask.copy().apply {
                effectMode = if (colorMatchEnabled) MaskData.MODE_COLOR_MATCH else MaskData.MODE_SHADE
                matchColor = currentMatchColor
                replaceColor = currentReplaceColor
                colorTolerance = currentColorTolerance
                spreadRadius = currentSpreadRadius
            }
            addMaskView(copy)
        }
        if (colorMatchEnabled && mediaProjection == null) {
            Toast.makeText(this, R.string.toast_capture_required, Toast.LENGTH_SHORT).show()
        }
        presetManager.setActivePresetId(presetId)
    }

    private fun saveToPreset(presetId: String) {
        val all = presetManager.loadAll()
        val preset = all.find { it.id == presetId } ?: return
        preset.masks.clear()
        maskViews.forEach { preset.masks.add(it.data.copy()) }
        presetManager.updatePreset(preset)
    }

    private fun setAllAlpha(alpha: Float) {
        maskViews.forEach { it.setMaskAlpha(alpha) }
    }

    private fun loadColorMatchPrefs() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        currentMatchColor = prefs.getInt("match_color", currentMatchColor)
        currentReplaceColor = prefs.getInt("replace_color", currentReplaceColor)
        currentColorTolerance = prefs.getInt("tolerance", currentColorTolerance)
        currentSpreadRadius = prefs.getInt("spread_radius", currentSpreadRadius)
    }

    private fun saveColorMatchPrefs() {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE).edit()
            .putInt("match_color", currentMatchColor)
            .putInt("replace_color", currentReplaceColor)
            .putInt("tolerance", currentColorTolerance)
            .putInt("spread_radius", currentSpreadRadius)
            .apply()
    }

    private fun setColorMatchEnabled(enabled: Boolean) {
        colorMatchEnabled = enabled
        saveColorMatchPrefs()
        maskViews.forEach {
            it.setColorMatchEnabled(enabled)
            it.setColorMatchSettings(currentMatchColor, currentReplaceColor, currentColorTolerance, currentSpreadRadius)
            if (enabled) it.updateScreenFrame(lastFrame)
        }
        if (enabled && mediaProjection == null) {
            Toast.makeText(this, R.string.toast_capture_required, Toast.LENGTH_SHORT).show()
        }
        if (!enabled) stopScreenCapture()
    }

    private fun setColorMatchSettings() {
        maskViews.forEach {
            it.setColorMatchSettings(currentMatchColor, currentReplaceColor, currentColorTolerance, currentSpreadRadius)
            it.updateScreenFrame(lastFrame)
        }
    }

    private fun toggleEditMode() {
        editMode = !editMode
        maskViews.forEach { it.editMode = editMode }
        if (!editMode) maskViews.forEach { it.updateScreenFrame(lastFrame) }
        ballView?.editMode = editMode
        ballView?.invalidate()
        val msg = if (editMode) getString(R.string.toast_edit_on) else getString(R.string.toast_edit_off)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            stopScreenCapture()
            promoteForegroundForCapture()
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Toast.makeText(this, R.string.toast_capture_required, Toast.LENGTH_SHORT).show()
                return
            }
            val projection = mediaProjection ?: return
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        if (mediaProjection === projection) stopScreenCapture(stopProjection = false)
                    }
                }
            }, mainHandler)
            createCapturePipeline()
            Toast.makeText(this, R.string.toast_capture_started, Toast.LENGTH_SHORT).show()
            if (colorMatchEnabled) maskViews.forEach { it.updateScreenFrame(lastFrame) }
        } catch (e: Exception) {
            stopScreenCapture(stopProjection = false)
            Toast.makeText(
                this,
                getString(R.string.toast_capture_failed, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun promoteForegroundForOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun promoteForegroundForCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun createCapturePipeline() {
        val projection = mediaProjection ?: return
        val dm = getRealDisplayMetrics()
        val width = dm.widthPixels
        val height = dm.heightPixels

        captureThread = HandlerThread("LuminShadeCapture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ availableReader ->
                val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val bitmapWidth = rowStride / pixelStride
                    val padded = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(buffer)
                    val frame = Bitmap.createBitmap(padded, 0, 0, width, height)
                    padded.recycle()
                    mainHandler.post {
                        lastFrame?.recycle()
                        lastFrame = frame
                        updatePickerPreview()
                        if (colorMatchEnabled) maskViews.forEach { it.updateScreenFrame(frame) }
                    }
                } finally {
                    image.close()
                }
            }, captureHandler)
        }
        virtualDisplay = projection.createVirtualDisplay(
            "LuminShadeCapture",
            width,
            height,
            dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )
    }

    private fun stopScreenCapture(stopProjection: Boolean = true) {
        removePickerView()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        mediaProjection = null
        if (stopProjection) projection?.stop()
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
        lastFrame?.recycle()
        lastFrame = null
        if (stopProjection) promoteForegroundForOverlay()
    }

    private fun startColorPick(target: String) {
        if (mediaProjection == null) {
            Toast.makeText(this, R.string.toast_capture_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (lastFrame == null) {
            Toast.makeText(this, R.string.toast_capture_waiting, Toast.LENGTH_SHORT).show()
            return
        }

        removePickerView()
        val title = if (target == PICK_TARGET_REPLACE) {
            getString(R.string.color_picker_replace_title)
        } else {
            getString(R.string.color_picker_match_title)
        }
        val picker = ColorPickerOverlayView(
            context = this,
            title = title,
            onMove = { x, y ->
                pickerCenterX = x
                pickerCenterY = y
                updatePickerPreview()
            }
        )
        val dm = getRealDisplayMetrics()
        val size = (72 * dm.density).toInt()
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - size) / 2
            y = (dm.heightPixels - size) / 2
        }
        picker.wmParams = lp
        picker.windowManager = wm
        pickerTitle = title
        pickerCenterX = lp.x + size / 2
        pickerCenterY = lp.y + size / 2
        addPickerPreview(target)
        wm.addView(picker, lp)
        pickerView = picker
        picker.post { updatePickerCenterFromView(picker) }
        Toast.makeText(this, R.string.toast_picker_ready, Toast.LENGTH_SHORT).show()
    }

    private fun sampleScreenColor(x: Int, y: Int): Int? {
        val frame = lastFrame ?: return null
        val point = mapScreenToFrame(x, y, frame) ?: return null
        val color = frame.getPixel(point.first, point.second)
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun removePickerView() {
        pickerView?.let {
            try {
                wm.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        pickerView = null
        pickerPreviewView?.let {
            try {
                wm.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        pickerPreviewView = null
    }

    private fun addPickerPreview(target: String) {
        val dm = resources.displayMetrics
        val density = dm.density
        val width = (188 * density).toInt()
        val height = (250 * density).toInt()
        val preview = ColorPickerPreviewView(
            context = this,
            onConfirm = { confirmPickedColor(target) },
            onCancel = {
                removePickerView()
                Toast.makeText(this, R.string.toast_picker_cancelled, Toast.LENGTH_SHORT).show()
            }
        )
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = (12 * density).toInt()
        }
        wm.addView(preview, lp)
        preview.wmParams = lp
        preview.windowManager = wm
        pickerPreviewView = preview
    }

    private fun updatePickerPreview() {
        val frame = lastFrame
        val point = if (frame == null) null else mapScreenToFrame(pickerCenterX, pickerCenterY, frame)
        pickerPreviewView?.update(frame, point?.first ?: pickerCenterX, point?.second ?: pickerCenterY, pickerTitle)
    }

    private val pickerLoc = IntArray(2)

    private fun updatePickerCenterFromView(view: ColorPickerOverlayView) {
        if (view.width == 0 || view.height == 0) return
        view.getLocationOnScreen(pickerLoc)
        pickerCenterX = pickerLoc[0] + view.width / 2
        pickerCenterY = pickerLoc[1] + view.height / 2
        updatePickerPreview()
    }

    private fun confirmPickedColor(target: String) {
        val color = sampleScreenColor(pickerCenterX, pickerCenterY)
        removePickerView()
        if (color != null) {
            if (target == PICK_TARGET_REPLACE) {
                currentReplaceColor = color
            } else {
                currentMatchColor = color
            }
            saveColorMatchPrefs()
            setColorMatchSettings()
            Toast.makeText(this, getString(R.string.toast_color_picked, colorToHex(color)), Toast.LENGTH_SHORT).show()
        }
    }

    private fun colorToHex(color: Int): String {
        return "#%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun mapScreenToFrame(x: Int, y: Int, frame: Bitmap): Pair<Int, Int>? {
        val dm = getRealDisplayMetrics()
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) return null
        val fx = ((x.toLong() * frame.width) / dm.widthPixels).toInt().coerceIn(0, frame.width - 1)
        val fy = ((y.toLong() * frame.height) / dm.heightPixels).toInt().coerceIn(0, frame.height - 1)
        return fx to fy
    }

    private fun getRealDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun addFloatingBall() {
        val ball = FloatingBallView(
            context = this,
            onToggleVisibility = { toggleVisibility() },
            onToggleEditMode = { toggleEditMode() },
            onAddMask = { addMask() }
        )
        val dm = resources.displayMetrics
        val size = (56 * dm.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - size * 3 / 4
            y = dm.heightPixels / 3
        }
        ball.wmParams = lp
        ball.windowManager = wm
        wm.addView(ball, lp)
        ballView = ball
    }

    private fun buildMaskParams(data: MaskData) = WindowManager.LayoutParams(
        data.width, data.height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = data.x
        y = data.y
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopScreenCapture()
        ballView?.let { wm.removeView(it) }
        clearAllMasks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
