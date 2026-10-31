package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

class CreatureAnimator(private val width: Int, private val height: Int, private val scene: ParsedScene) {
    private val paint = Paint().apply { 
        strokeWidth = 5f; isAntiAlias = true 
    }

    fun renderFrame(time: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1A1A1A")) // Background Gelap

        // Gambar Tanah
        paint.color = Color.parseColor("#333333")
        paint.strokeWidth = 10f
        canvas.drawLine(0f, height * 0.8f, width.toFloat(), height * 0.8f, paint)

        // Warna dari AI (Gemini)
        val aiColor = try { Color.parseColor(scene.correctedText) } catch (e: Exception) { Color.WHITE }
        paint.color = aiColor
        paint.strokeWidth = 6f

        val catX = (width * 0.2f) + (time * 50f) % (width * 0.6f)
        val catY = height * 0.7f
        val speed = if (scene.action.contains("lari", true)) 15f else 8f
        val legOffset = sin(time * speed) * 20f

        canvas.drawLine(catX, catY, catX + 60f, catY, paint)
        canvas.drawCircle(catX + 60f, catY - 20f, 20f, paint)
        canvas.drawLine(catX + 50f, catY - 35f, catX + 55f, catY - 55f, paint)
        canvas.drawLine(catX + 70f, catY - 35f, catX + 65f, catY - 55f, paint)
        canvas.drawLine(catX + 40f, catY, catX + 40f + legOffset, catY + 40f, paint)
        canvas.drawLine(catX + 20f, catY, catX + 20f - legOffset, catY + 40f, paint)
        canvas.drawLine(catX, catY, catX - 30f, catY - 30f + (sin(time * 3f) * 10f), paint)

        return bitmap
    }
}
