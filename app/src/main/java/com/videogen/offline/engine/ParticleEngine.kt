package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

class ParticleEngine(private val width: Int, private val height: Int, private val prompt: String) {
    private val particleCount = 30000
    private val particles = FloatArray(particleCount * 2)
    private val paint = Paint().apply { isAntiAlias = true }
    private val speedMultiplier: Float
    private val flowType: Int
    private val baseColor: Int

    init {
        val lower = prompt.lowercase()
        when {
            lower.contains("fire") || lower.contains("sunset") -> { speedMultiplier = -2.5f; flowType = 1; baseColor = Color.rgb(255, 100, 0) }
            lower.contains("water") || lower.contains("ocean") -> { speedMultiplier = 2.0f; flowType = 2; baseColor = Color.rgb(0, 150, 255) }
            lower.contains("galaxy") || lower.contains("space") -> { speedMultiplier = 0.5f; flowType = 3; baseColor = Color.rgb(150, 0, 255) }
            else -> { speedMultiplier = 1.0f; flowType = 0; baseColor = Color.rgb(100, 255, 100) }
        }
        val rand = java.util.Random()
        for (i in 0 until particleCount) {
            particles[i * 2] = rand.nextFloat() * width
            particles[i * 2 + 1] = rand.nextFloat() * height
        }
    }

    fun renderFrame(time: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        for (i in 0 until particleCount) {
            val startX = particles[i * 2]; val startY = particles[i * 2 + 1]
            val noiseX: Float; val noiseY: Float
            when (flowType) {
                1 -> { noiseX = sin(startY * 0.02f + time * 2f) * 1.5f; noiseY = speedMultiplier + cos(startX * 0.02f + time) * 1.0f }
                2 -> { noiseX = sin(startY * 0.01f + time) * 2.0f + speedMultiplier; noiseY = cos(startX * 0.01f + time) * 2.0f }
                3 -> { noiseX = cos(startY * 0.01f + time) * speedMultiplier; noiseY = sin(startX * 0.01f + time) * speedMultiplier }
                else -> { noiseX = sin(startY * 0.02f + time) * speedMultiplier; noiseY = cos(startX * 0.02f + time) * speedMultiplier }
            }
            var newX = startX + noiseX; var newY = startY + noiseY
            if (newX < 0) newX += width; if (newX > width) newX -= width
            if (newY < 0) newY += height; if (newY > height) newY -= height
            particles[i * 2] = newX; particles[i * 2 + 1] = newY
            val alpha = (150 + (noiseX * 20).toInt()).coerceIn(50, 255)
            paint.color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val radius = 1.5f + (sin(time + i) * 0.5f)
            canvas.drawCircle(newX, newY, radius, paint)
        }
        return bitmap
    }
}
