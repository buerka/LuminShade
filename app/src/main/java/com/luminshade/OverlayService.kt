package com.luminshade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
        const val EXTRA_PRESET_ID = "preset_id"
        const val EXTRA_ALPHA = "alpha"
        const val CHANNEL_ID = "luminshade_overlay"
        const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private lateinit var presetManager: PresetManager
    private val maskViews = mutableListOf<MaskView>()
    private var ballView: FloatingBallView? = null
    private var masksVisible = true
    private var editMode = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        presetManager = PresetManager(this)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
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
        )
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
        preset.masks.forEach { addMaskView(it.copy()) }
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

    private fun toggleEditMode() {
        editMode = !editMode
        maskViews.forEach { it.editMode = editMode }
        ballView?.editMode = editMode
        ballView?.invalidate()
        val msg = if (editMode) getString(R.string.toast_edit_on) else getString(R.string.toast_edit_off)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
        ballView?.let { wm.removeView(it) }
        clearAllMasks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
