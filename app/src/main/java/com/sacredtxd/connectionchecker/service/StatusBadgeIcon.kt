package com.sacredtxd.connectionchecker.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.IconCompat

/**
 * Renders the badge text into a status bar icon. Android gives no way to put text in
 * the status bar directly, so the number is drawn into the icon bitmap itself — that
 * is what makes the latency readable without pulling the shade down.
 *
 * The system tints status bar icons as a silhouette, so the glyphs are drawn opaque
 * white on transparent and the tint recolours them.
 */
object StatusBadgeIcon {

    private const val SIZE_PX = 96

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun of(text: String): IconCompat {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        bitmap.applyCanvas { drawBadge(this, text) }
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun drawBadge(canvas: Canvas, text: String) {
        // Longer badges are set smaller so three or four glyphs still fit the icon.
        paint.textSize = when (text.length) {
            1 -> SIZE_PX * 0.86f
            2 -> SIZE_PX * 0.70f
            3 -> SIZE_PX * 0.52f
            else -> SIZE_PX * 0.40f
        }
        val metrics = paint.fontMetrics
        val baseline = SIZE_PX / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, SIZE_PX / 2f, baseline, paint)
    }
}
