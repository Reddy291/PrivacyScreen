package com.privacyscreen.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View

/**
 * The layer drawn over every app.
 *
 * It always paints in solid black; how dark it looks is controlled by the
 * window's alpha (set in PrivacyService). That keeps touches passing through
 * to the app underneath on Android 12+.
 */
@SuppressLint("ViewConstructor")
class PrivacyOverlayView(context: Context) : View(context) {

    var mode: Prefs.Mode = Prefs.Mode.DIM
        set(v) { field = v; invalidate() }

    /** 0f (top) .. 1f (bottom) */
    var spotCenter = 0.5f
        set(v) { field = v; invalidate() }

    /** fraction of screen height that stays visible */
    var spotHeight = 0.25f
        set(v) { field = v; invalidate() }

    /** Face guard detected an onlooker: hide everything. */
    var blackout = false
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private val black = Paint().apply { color = Color.BLACK }
    private val stripe = Paint().apply { color = Color.BLACK; isAntiAlias = false }
    private val feather = Paint()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(140, 140, 140)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (blackout) {
            canvas.drawColor(Color.BLACK)
            drawCenteredText(canvas, context.getString(R.string.hidden_message))
            return
        }

        when (mode) {
            Prefs.Mode.DIM -> canvas.drawColor(Color.BLACK)

            Prefs.Mode.SPOTLIGHT -> {
                val band = h * spotHeight
                val center = (h * spotCenter).coerceIn(band / 2, h - band / 2)
                val top = center - band / 2
                val bottom = center + band / 2
                val f = 24 * density // soft edge

                canvas.drawRect(0f, 0f, w, top, black)
                canvas.drawRect(0f, bottom, w, h, black)

                // fade into the visible strip so the edge isn't harsh
                feather.shader = LinearGradient(0f, top, 0f, top + f,
                    Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, top, w, top + f, feather)
                feather.shader = LinearGradient(0f, bottom - f, 0f, bottom,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, bottom - f, w, bottom, feather)
            }

            Prefs.Mode.PATTERN -> {
                // Fine horizontal lines. Up close your eyes look "through" them;
                // from a distance or at an angle the text breaks up.
                val gap = (3 * density).coerceAtLeast(3f)
                stripe.strokeWidth = (1.5f * density).coerceAtLeast(1.5f)
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, stripe)
                    y += gap
                }
                // plus light vertical lines to break letter shapes
                stripe.strokeWidth = density.coerceAtLeast(1f)
                var x = 0f
                val vGap = gap * 3
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, stripe)
                    x += vGap
                }
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String) {
        val maxW = (width * 0.8f).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate((width - maxW) / 2f, (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }
}
