package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

class SwarmEngine(private val width: Int, private val height: Int, private val prompt: String) {
    
    private val particleCount = 25000
    private val px = FloatArray(particleCount)
    private val py = FloatArray(particleCount)
    private val paint = Paint().apply { isAntiAlias = true }
    private val brain = NeuralNetwork()
    
    private val baseColor: Int
    private val speedMult: Float
    
    init {
        val lower = prompt.lowercase()
        when {
            lower.contains("fire") -> { baseColor = Color.rgb(255, 80, 0); speedMult = 3.0f }
            lower.contains("water") -> { baseColor = Color.rgb(0, 150, 255); speedMult = 1.5f }
            lower.contains("galaxy") -> { baseColor = Color.rgb(180, 0, 255); speedMult = 2.0f }
            lower.contains("forest") -> { baseColor = Color.rgb(0, 255, 100); speedMult = 1.0f }
            else -> { baseColor = Color.rgb(200, 200, 200); speedMult = 2.0f }
        }

        // Sebar partikel secara acak
        for (i in 0 until particleCount) {
            px[i] = Random.nextFloat() * width
            py[i] = Random.nextFloat() * height
        }
    }

    fun renderFrame(frameCount: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        // Setiap 30 frame, AI bermutasi (berevolusi)
        if (frameCount % 30 == 0 && frameCount > 0) {
            brain.evolve(0.5f)
        }

        for (i in 0 until particleCount) {
            val x = px[i]
            val y = py[i]

            // Normalisasi posisi ke -1.0 sampai 1.0 untuk input Neural Network
            val nx = (x / width) * 2f - 1f
            val ny = (y / height) * 2f - 1f

            // AI Berpikir: Menentukan arah gerak
            val (vx, vy) = brain.think(nx, ny)

            // Update posisi
            var newX = x + (vx * speedMult)
            var newY = y + (vy * speedMult)

            // Bungkus layar (Infinite Loop)
            if (newX < 0) newX += width
            if (newX >= width) newX -= width
            if (newY < 0) newY += height
            if (newY >= height) newY -= height

            px[i] = newX
            py[i] = newY

            // Gambar efek glow
            val alpha = (100 + (vx * 50).toInt()).coerceIn(30, 255)
            paint.color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            paint.strokeWidth = 1.5f + (sin((frameCount + i).toFloat() * 0.1f) * 0.5f)
            canvas.drawPoint(newX, newY, paint)
        }

        return bitmap
    }
}
