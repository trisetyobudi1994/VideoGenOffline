package com.videogen.offline.engine

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class CinematicAnimator(
    private val src: Bitmap,
    private val width: Int,
    private val height: Int,
    private val weather: String,
    private val timeOfDay: String
) {
    private val rand = Random(42)
    private val weatherParticles = List(280) {
        WPart(
            x = rand.nextFloat() * width,
            y = rand.nextFloat() * height,
            speed = rand.nextFloat() * 4f + 2f,
            size = rand.nextFloat() * 3f + 1f,
            phase = rand.nextFloat() * 6.28f
        )
    }
    data class WPart(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)

    private val grainPoints = List(600) {
        Triple(rand.nextFloat() * width, rand.nextFloat() * height, rand.nextFloat())
    }

    fun renderFrame(frameIndex: Int, totalFrames: Int): Bitmap {
        val progress = frameIndex.toFloat() / totalFrames
        val loopPhase = progress * 2f * PI.toFloat()

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Slow cinematic zoom & pan (SEAMLESS LOOP)
        val zoomFactor = 1.0f + (sin(loopPhase) * 0.5f + 0.5f) * 0.12f
        val panX = sin(loopPhase) * 12f
        val panY = cos(loopPhase) * 6f

        val baseScale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val finalScale = baseScale * zoomFactor
        val matrix = Matrix()
        matrix.postScale(finalScale, finalScale, src.width / 2f, src.height / 2f)
        matrix.postTranslate(
            (width - src.width * finalScale) / 2f + panX,
            (height - src.height * finalScale) / 2f + panY
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)

        // 2. Color grading (cinematic tint)
        applyColorGrading(canvas)

        // 3. Light rays
        drawLightRays(canvas, loopPhase)

        // 4. Weather particles
        drawWeather(canvas, frameIndex)

        // 5. Film grain
        drawFilmGrain(canvas, frameIndex)

        // 6. Vignette
        drawVignette(canvas)

        return bmp
    }

    private fun applyColorGrading(canvas: Canvas) {
        val paint = Paint()
        when (timeOfDay) {
            "night" -> { paint.color = Color.argb(50, 20, 30, 80); paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
            "sunset" -> { paint.color = Color.argb(60, 255, 100, 20); paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
            "dawn" -> { paint.color = Color.argb(50, 200, 150, 200); paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
            else -> { paint.color = Color.argb(25, 255, 220, 150); paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawLightRays(canvas: Canvas, phase: Float) {
        val cx = width * 0.85f
        val cy = height * 0.15f
        val pulse = (sin(phase * 2) * 0.3f + 0.7f).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 5) {
            val ang = (i * 35f + phase * 10f) * PI.toFloat() / 180f
            p.shader = RadialGradient(cx, cy, width * 0.7f,
                intArrayOf(Color.argb((100 * pulse).toInt(), 255, 245, 210), Color.TRANSPARENT),
                floatArrayOf(0.1f, 1f), Shader.TileMode.CLAMP)
            val path = Path()
            path.moveTo(cx, cy)
            path.lineTo(cx + cos(ang) * width * 1.5f, cy + sin(ang) * width * 1.5f)
            path.lineTo(cx + cos(ang + 0.12f) * width * 1.5f, cy + sin(ang + 0.12f) * width * 1.5f)
            path.close()
            canvas.drawPath(path, p)
        }
    }

    private fun drawWeather(canvas: Canvas, frameIndex: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (weather) {
            "rain" -> {
                p.color = Color.argb(190, 200, 220, 255); p.strokeWidth = 2f
                for (wp in weatherParticles) {
                    val y = (wp.y + frameIndex * wp.speed * 9f) % (height + 40f)
                    val x = wp.x + sin(frameIndex * 0.1f + wp.phase) * 15f
                    canvas.drawLine(x, y, x - 3f, y + 22f, p)
                }
            }
            "snow" -> {
                p.color = Color.WHITE
                for (wp in weatherParticles) {
                    val y = (wp.y + frameIndex * wp.speed * 2f) % (height + 20f)
                    val x = wp.x + sin(frameIndex * 0.05f + wp.phase) * 30f
                    p.alpha = (160 + sin(frameIndex * 0.1f + wp.phase) * 90).toInt().coerceIn(60, 255)
                    canvas.drawCircle(x, y, wp.size, p)
                }
            }
            "fire" -> {
                for (wp in weatherParticles.take(180)) {
                    val ph = (wp.phase + frameIndex * 0.05f) % 1f
                    val y = height * 0.95f - ph * height * 0.5f
                    val x = wp.x + sin(frameIndex * 0.1f + wp.phase) * 25f
                    p.color = if (wp.phase > 0.5f) Color.argb(((1-ph)*200).toInt().coerceIn(0,255), 255, 100, 0)
                               else Color.argb(((1-ph)*200).toInt().coerceIn(0,255), 255, 200, 50)
                    canvas.drawCircle(x, y, wp.size * (1-ph) + 2f, p)
                }
            }
            "stars" -> {
                p.color = Color.WHITE
                for (wp in weatherParticles) {
                    val tw = (sin(frameIndex * 0.15f + wp.phase) * 0.5f + 0.5f)
                    p.alpha = (tw * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(wp.x, wp.y * 0.7f, wp.size * 0.6f, p)
                }
            }
        }
    }

    private fun drawFilmGrain(canvas: Canvas, frameIndex: Int) {
        val p = Paint()
        p.color = Color.argb(18, 255, 255, 255)
        for ((gx, gy, seed) in grainPoints) {
            val px = ((gx + sin(frameIndex + seed * 100f) * 3f + width) % width)
            val py = ((gy + cos(frameIndex + seed * 100f) * 3f + height) % height)
            canvas.drawCircle(px, py, 0.9f, p)
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val p = Paint()
        p.shader = RadialGradient(width / 2f, height / 2f, width * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(180, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
    }
}
