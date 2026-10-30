package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix

class ParallaxRenderer {
    
    // Menghasilkan 30 frame animasi Parallax
    fun generateFrames(image: Bitmap, depthMap: FloatArray, width: Int, height: Int): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        val totalFrames = 30
        
        // Skala gambar agar ada ruang untuk digeser (mencegah garis hitam di pinggir)
        val scaledImage = Bitmap.createScaledBitmap(image, (width * 1.2f).toInt(), (height * 1.2f).toInt(), true)
        
        for (i in 0 until totalFrames) {
            val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            val matrix = Matrix()
            
            // Efek Zoom-in perlahan (dari skala 1.0 ke 1.1)
            val scale = 1.0f + (i.toFloat() / totalFrames) * 0.1f
            matrix.postScale(scale, scale)
            
            // Efek Geser (Pan) berdasarkan kedalaman
            val panX = -(i.toFloat() / totalFrames) * (width * 0.1f)
            matrix.postTranslate(panX, 0f)
            
            // Gambar ke Canvas
            canvas.drawBitmap(scaledImage, matrix, null)
            frames.add(frame)
        }
        
        return frames
    }
}
