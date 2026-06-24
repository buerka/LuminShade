package com.luminshade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.luminshade.data.MaskData
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

@SuppressLint("ViewConstructor")
class MaskView(
    context: Context,
    val data: MaskData,
    private val onRequestRemove: (MaskView) -> Unit,
    private val onLayoutChanged: () -> Unit
) : View(context) {

    var editMode = false
        set(value) { field = value; invalidate() }

    var wmParams: WindowManager.LayoutParams? = null
    var windowManager: WindowManager? = null

    private var isPeeking = false

    private val maskPaint = Paint().apply { style = Paint.Style.FILL }
    private val editBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 123, 143, 191)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 60, 70, 95)
        style = Paint.Style.FILL
    }
    private val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 195, 60, 60)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val DELETE_R = 28f
    private val HANDLE_DRAW = 32f
    private val HANDLE_TOUCH = 80f
    private val MIN_VISIBLE_PX = 80
    private val MIN_W = 100
    private val MIN_H = 50

    private enum class TouchMode { NONE, DRAG, RESIZE }
    private var touchMode = TouchMode.NONE

    private var startRawX = 0f
    private var startRawY = 0f
    private var startViewX = 0
    private var startViewY = 0
    private var startWidth = 0
    private var startHeight = 0
    private var hasMoved = false

    private var longPressRunnable: Runnable? = null
    private val LONG_PRESS_MS = 450L

    init { updatePaint() }

    private fun updatePaint() {
        val alpha = if (isPeeking) 25 else (data.alpha * 255).toInt()
        maskPaint.color = Color.argb(alpha, 0, 0, 0)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        if (!editMode) return

        // 编辑模式边框
        canvas.drawRect(2f, 2f, width - 2f, height - 2f, editBorderPaint)

        // 删除按钮（右上角）
        val dx = width - DELETE_R - 6f
        val dy = DELETE_R + 6f
        canvas.drawCircle(dx, dy, DELETE_R, deletePaint)
        textPaint.textSize = 26f
        canvas.drawText("×", dx, dy + 9f, textPaint)

        // 缩放手柄（右下角）
        val hx = width.toFloat()
        val hy = height.toFloat()
        canvas.drawRect(hx - HANDLE_DRAW * 2, hy - HANDLE_DRAW * 2, hx, hy, handlePaint)
        textPaint.textSize = 22f
        canvas.drawText("⇲", hx - HANDLE_DRAW, hy - HANDLE_DRAW + 8f, textPaint)
    }

    private fun inDeleteZone(x: Float, y: Float) =
        editMode && hypot((x - (width - DELETE_R - 6f)).toDouble(), (y - DELETE_R - 6f).toDouble()) < DELETE_R * 1.6

    private fun inResizeHandle(x: Float, y: Float) =
        editMode && x > width - HANDLE_TOUCH && y > height - HANDLE_TOUCH

    private fun clampedX(rawX: Int, w: Int): Int {
        val dm = context.resources.displayMetrics
        val minX = -w + MIN_VISIBLE_PX
        val maxX = dm.widthPixels - MIN_VISIBLE_PX
        return rawX.coerceIn(minX, maxX)
    }

    private fun clampedY(rawY: Int, h: Int): Int {
        val dm = context.resources.displayMetrics
        val minY = -h + MIN_VISIBLE_PX
        val maxY = dm.heightPixels - MIN_VISIBLE_PX
        return rawY.coerceIn(minY, maxY)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = windowManager ?: return false
        val lp = wmParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hasMoved = false
                startRawX = event.rawX
                startRawY = event.rawY

                if (editMode) {
                    touchMode = if (inResizeHandle(event.x, event.y)) {
                        startWidth = lp.width
                        startHeight = lp.height
                        TouchMode.RESIZE
                    } else {
                        startViewX = lp.x
                        startViewY = lp.y
                        TouchMode.DRAG
                    }
                } else {
                    touchMode = TouchMode.NONE
                    longPressRunnable = Runnable {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        isPeeking = true
                        updatePaint()
                        invalidate()
                    }
                    postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (!hasMoved && (abs(dx) > 8 || abs(dy) > 8)) {
                    hasMoved = true
                    longPressRunnable?.let { removeCallbacks(it) }
                }
                when (touchMode) {
                    TouchMode.DRAG -> if (hasMoved) {
                        lp.x = clampedX((startViewX + dx).toInt(), lp.width)
                        lp.y = clampedY((startViewY + dy).toInt(), lp.height)
                        data.x = lp.x; data.y = lp.y
                        wm.updateViewLayout(this, lp)
                        onLayoutChanged()
                    }
                    TouchMode.RESIZE -> {
                        lp.width = max(MIN_W, (startWidth + dx).toInt())
                        lp.height = max(MIN_H, (startHeight + dy).toInt())
                        data.width = lp.width; data.height = lp.height
                        wm.updateViewLayout(this, lp)
                        onLayoutChanged()
                    }
                    else -> {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }
                if (isPeeking) {
                    isPeeking = false
                    updatePaint()
                    invalidate()
                }
                if (!hasMoved && editMode && inDeleteZone(event.x, event.y)) {
                    onRequestRemove(this)
                }
                touchMode = TouchMode.NONE
                return true
            }
        }
        return false
    }

    fun setMaskAlpha(a: Float) {
        data.alpha = a
        if (!isPeeking) updatePaint()
        invalidate()
    }
}
