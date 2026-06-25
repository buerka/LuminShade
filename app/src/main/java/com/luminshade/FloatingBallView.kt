package com.luminshade

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FloatingBallView(
    context: Context,
    private val onToggleVisibility: () -> Unit,
    private val onToggleEditMode: () -> Unit,
    private val onAddMask: () -> Unit,
    private val onPeekStart: () -> Unit,
    private val onPeekEnd: () -> Unit
) : View(context) {

    var masksVisible = true
    var editMode = false

    var wmParams: WindowManager.LayoutParams? = null
    var windowManager: WindowManager? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    private var startRawX = 0f
    private var startRawY = 0f
    private var startViewX = 0
    private var startViewY = 0
    private var isDragging = false
    private var longPressFired = false
    private var peeking = false
    private var peekFired = false

    private var peekRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private val PEEK_MS = 200L
    private val LONG_PRESS_MS = 550L

    override fun onDraw(canvas: Canvas) {
        bgPaint.color = if (editMode) Color.argb(230, 75, 95, 155)
        else Color.argb(210, 28, 28, 28)
        rect.set(4f, 4f, width - 4f, height - 4f)
        canvas.drawRoundRect(rect, width / 2f, height / 2f, bgPaint)

        when {
            editMode -> {
                textPaint.textSize = 42f
                canvas.drawText("＋", width / 2f, height / 2f + 15f, textPaint)
            }
            masksVisible -> {
                textPaint.textSize = 32f
                canvas.drawText("👁", width / 2f, height / 2f + 12f, textPaint)
            }
            else -> {
                textPaint.textSize = 30f
                canvas.drawText("✕", width / 2f, height / 2f + 10f, textPaint)
            }
        }
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
                longPressFired = false
                peeking = false
                peekFired = false

                peekRunnable = Runnable {
                    if (!isDragging) {
                        peeking = true
                        peekFired = true
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onPeekStart()
                    }
                }
                longPressRunnable = Runnable {
                    if (!isDragging) {
                        if (peeking) {
                            peeking = false
                            onPeekEnd()
                        }
                        longPressFired = true
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleEditMode()
                    }
                }
                postDelayed(peekRunnable, PEEK_MS)
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (!isDragging && (abs(dx) > 10 || abs(dy) > 10)) {
                    isDragging = true
                    peekRunnable?.let { removeCallbacks(it) }
                    longPressRunnable?.let { removeCallbacks(it) }
                    if (peeking) {
                        peeking = false
                        onPeekEnd()
                    }
                }
                if (isDragging) {
                    lp.x = (startViewX + dx).toInt()
                    lp.y = (startViewY + dy).toInt()
                    wm.updateViewLayout(this, lp)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                peekRunnable?.let { removeCallbacks(it) }
                longPressRunnable?.let { removeCallbacks(it) }
                if (peeking) {
                    peeking = false
                    onPeekEnd()
                }
                if (isDragging) {
                    snapToEdge(lp, wm)
                } else if (!longPressFired && !peekFired) {
                    if (editMode) onAddMask() else onToggleVisibility()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                peekRunnable?.let { removeCallbacks(it) }
                longPressRunnable?.let { removeCallbacks(it) }
                if (peeking) {
                    peeking = false
                    onPeekEnd()
                }
                return true
            }
        }
        return false
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams, wm: WindowManager) {
        val screenW = context.resources.displayMetrics.widthPixels
        lp.x = if (lp.x + width / 2 < screenW / 2) -width / 4 else screenW - width * 3 / 4
        wm.updateViewLayout(this, lp)
    }
}
