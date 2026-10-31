package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin

class CreatureAnimator(private val width: Int, private val height: Int, private val scene: ParsedScene) {
    private val paint = Paint().apply { 
        color = Color.WHITE; strokeWidth = 5f; isAntiAlias = true 
    }

    fun renderFrame(time: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#1A1A1A")) // Background Gelap

        // Gambar Tanah
        paint.color = Color.parseColor("#333333")
        paint.strokeWidth = 10f
        canvas.drawLine(0f, height * 0.8f, width.toFloat(), height * 0.8f, paint)

        paint.color = Color.WHITE
        paint.strokeWidth = 6f

        // Posisi Kucing (Bergerak ke kanan seiring waktu)
        val catX = (width * 0.2f) + (time * 50f) % (width * 0.6f)
        val catY = height * 0.7f
        
        // Kecepatan langkah (berlari/berjalan)
        val speed = if (scene.action == "berlari") 15f else 8f
        
        // Animasi Kaki (Gelombang Sinus)
        val legOffset = sin(time * speed) * 20f

        // Badan Kucing
        canvas.drawLine(catX, catY, catX + 60f, catY, paint)
        
        // Kepala
        canvas.drawCircle(catX + 60f, catY - 20f, 20f, paint)
        // Telinga
        canvas.drawLine(catX + 50f, catY - 35f, catX + 55f, catY - 55f, paint)
        canvas.drawLine(catX + 70f, catY - 35f, catX + 65f, catY - 55f, paint)

        // Kaki Depan & Belakang (Bergerak bergantian)
        canvas.drawLine(catX + 40f, catY, catX + 40f + legOffset, catY + 40f, paint)
        canvas.drawLine(catX + 20f, catY, catX + 20f - legOffset, catY + 40f, paint)

        // Ekor
        canvas.drawLine(catX, catY, catX - 30f, catY - 30f + (sin(time * 3f) * 10f), paint)

        return bitmap
    }
}
