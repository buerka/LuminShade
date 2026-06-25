package com.luminshade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class ColorPickerOverlayView(
    context: Context,
    private val title: String,
    private val onMove: (Int, Int) -> Unit,
) : View(context) {

    var wmParams: WindowManager.LayoutParams? = null
    var windowManager: WindowManager? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 24, 24, 28)
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 123, 143, 191)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 22f
    }
    private val rect = RectF()

    private var startRawX = 0f
    private var startRawY = 0f
    private var startViewX = 0
    private var startViewY = 0
    private var isDragging = false

    override fun onDraw(canvas: Canvas) {
        rect.set(4f, 4f, width - 4f, height - 4f)
        canvas.drawOval(rect, bgPaint)
        canvas.drawOval(rect, ringPaint)

        val cx = width / 2f
        val cy = height / 2f
        val arm = width * 0.24f
        canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
        canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
        canvas.drawCircle(cx, cy, width * 0.12f, crossPaint)

        textPaint.textSize = 18f
        canvas.drawText(if (title.length > 4) title.take(4) else title, cx, height - 10f, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = windowManager ?: return false
        val lp = wmParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                startViewX = lp.x
                startViewY = lp.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) {
                    isDragging = true
                }
                if (isDragging) {
                    lp.x = (startViewX + dx).toInt()
                    lp.y = (startViewY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                    post { notifyCenterChanged() }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                notifyCenterChanged()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                notifyCenterChanged()
                return true
            }
        }
        return false
    }

    private val locOnScreen = IntArray(2)

    private fun notifyCenterChanged() {
        if (width == 0 || height == 0) return
        getLocationOnScreen(locOnScreen)
        onMove(locOnScreen[0] + width / 2, locOnScreen[1] + height / 2)
    }
}
