package com.tusur.app.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = 0xFFE0E0F0.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = 0xFF3D3DA8.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    var progress: Float = 0.92f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = trackPaint.strokeWidth / 2 + 8f
        oval.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(oval, -220f, 260f, false, trackPaint)
        canvas.drawArc(oval, -220f, 260f * progress, false, progressPaint)
    }
}
