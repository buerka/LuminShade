package com.luminshade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class ColorPickerPreviewView(
    context: Context,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : View(context) {

    var wmParams: WindowManager.LayoutParams? = null
    var windowManager: WindowManager? = null

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 20, 20, 24)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 123, 143, 191)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 232, 232, 236)
        textSize = 18f
    }
    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val confirmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 123, 143, 191)
        style = Paint.Style.FILL
    }
    private val cancelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 58, 58, 64)
        style = Paint.Style.FILL
    }
    private val cellPaint = Paint().apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val confirmRect = RectF()
    private val cancelRect = RectF()

    private var title: String = ""
    private var color: Int? = null
    private var sample: IntArray = IntArray(0)
    private var sampleSize = 0
    private var sampleX = 0
    private var sampleY = 0
    private var startRawX = 0f
    private var startRawY = 0f
    private var startViewX = 0
    private var startViewY = 0
    private var isDragging = false

    fun update(frame: Bitmap?, x: Int, y: Int, title: String) {
        this.title = title
        sampleX = x
        sampleY = y
        if (frame == null || x !in 0 until frame.width || y !in 0 until frame.height) {
            color = null
            sample = IntArray(0)
            sampleSize = 0
            invalidate()
            return
        }

        color = Color.rgb(Color.red(frame.getPixel(x, y)), Color.green(frame.getPixel(x, y)), Color.blue(frame.getPixel(x, y)))
        sampleSize = 9
        sample = IntArray(sampleSize * sampleSize)
        val radius = sampleSize / 2
        for (row in 0 until sampleSize) {
            for (col in 0 until sampleSize) {
                val sx = min(frame.width - 1, max(0, x + col - radius))
                val sy = min(frame.height - 1, max(0, y + row - radius))
                val c = frame.getPixel(sx, sy)
                sample[row * sampleSize + col] = Color.rgb(Color.red(c), Color.green(c), Color.blue(c))
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val radius = 8f * density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, panelPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        val pad = 12f * density
        textPaint.textSize = 21f * density
        subTextPaint.textSize = 14f * density

        canvas.drawText(title, pad, pad + 20f * density, textPaint)
        val current = color
        val hex = if (current == null) "--" else colorToHex(current)
        canvas.drawText(hex, pad, pad + 43f * density, textPaint)
        canvas.drawText("x:$sampleX  y:$sampleY", pad, pad + 63f * density, subTextPaint)

        val swatchSize = 42f * density
        val swatchLeft = width - pad - swatchSize
        val swatchTop = pad + 8f * density
        swatchPaint.color = current ?: Color.TRANSPARENT
        canvas.drawRoundRect(
            swatchLeft,
            swatchTop,
            swatchLeft + swatchSize,
            swatchTop + swatchSize,
            6f * density,
            6f * density,
            swatchPaint
        )
        canvas.drawRoundRect(
            swatchLeft,
            swatchTop,
            swatchLeft + swatchSize,
            swatchTop + swatchSize,
            6f * density,
            6f * density,
            borderPaint
        )

        val buttonHeight = 34f * density
        val buttonGap = 8f * density
        val buttonTop = height - pad - buttonHeight

        if (sampleSize > 0) {
            val gridLeft = pad
            val gridTop = pad + 78f * density
            val gridSize = min(width - pad * 2, buttonTop - gridTop - 10f * density)
            val cell = gridSize / sampleSize
            for (row in 0 until sampleSize) {
                for (col in 0 until sampleSize) {
                    cellPaint.color = sample[row * sampleSize + col]
                    val left = gridLeft + col * cell
                    val top = gridTop + row * cell
                    canvas.drawRect(left, top, left + cell, top + cell, cellPaint)
                    canvas.drawRect(left, top, left + cell, top + cell, gridPaint)
                }
            }

            val center = sampleSize / 2
            val left = gridLeft + center * cell
            val top = gridTop + center * cell
            canvas.drawRect(left, top, left + cell, top + cell, centerPaint)
        }

        val buttonWidth = (width - pad * 2 - buttonGap) / 2f
        cancelRect.set(pad, buttonTop, pad + buttonWidth, buttonTop + buttonHeight)
        confirmRect.set(cancelRect.right + buttonGap, buttonTop, width - pad, buttonTop + buttonHeight)
        canvas.drawRoundRect(cancelRect, 6f * density, 6f * density, cancelPaint)
        canvas.drawRoundRect(confirmRect, 6f * density, 6f * density, confirmPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15f * density
        canvas.drawText(context.getString(R.string.cancel), cancelRect.centerX(), cancelRect.centerY() + 5f * density, textPaint)
        canvas.drawText(context.getString(R.string.confirm_pick_color), confirmRect.centerX(), confirmRect.centerY() + 5f * density, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = windowManager
        val lp = wmParams
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                startViewX = lp?.x ?: 0
                startViewY = lp?.y ?: 0
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) {
                    isDragging = true
                }
                if (isDragging && wm != null && lp != null) {
                    lp.x = (startViewX + dx).toInt()
                    lp.y = (startViewY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    when {
                        confirmRect.contains(event.x, event.y) -> onConfirm()
                        cancelRect.contains(event.x, event.y) -> onCancel()
                    }
                }
                return true
            }
        }
        return true
    }

    private fun colorToHex(color: Int): String {
        return "#%02X%02X%02X".format(Color.red(color), Color.green(color), Color.blue(color))
    }
}
