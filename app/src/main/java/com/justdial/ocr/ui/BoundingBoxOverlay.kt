package com.justdial.ocr.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class BoundingBoxOverlay(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var boundingBox: RectF? = null
    private var boxLabel: String? = null

    private val boxPaint = Paint().apply {
        color = Color.GREEN // Changed color for better visibility
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    fun updateBoundingBox(newBox: RectF?, label: String?) {
        boundingBox = newBox
        boxLabel = label
        // Request a redraw
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the bounding box and label if they exist
        boundingBox?.let { box ->
            canvas.drawRect(box, boxPaint)
            boxLabel?.let { label ->
                canvas.drawRect(
                    box.left,
                    box.top - 60, // Position background above the box
                    box.left + textPaint.measureText(label),
                    box.top,
                    textBackgroundPaint
                )
                canvas.drawText(label, box.left + 10, box.top - 10, textPaint)
            }
        }
    }
}

