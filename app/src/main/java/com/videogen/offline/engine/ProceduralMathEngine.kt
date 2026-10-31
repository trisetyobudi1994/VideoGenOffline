package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

class ProceduralMathEngine {
    
    /**
     * Menerjemahkan Teks menjadi Parameter Matematika.
     * Inilah "AI" kita: membaca kata kunci dan mengubahnya jadi rumus.
     */
    fun textToParameters(prompt: String): MathParams {
        val lower = prompt.lowercase()
        
        var rBase = 0.5f; var gBase = 0.5f; var bBase = 0.5f
        var noiseScale = 0.05f
        
        if (lower.contains("fire") || lower.contains("sunset")) { rBase = 1f; gBase = 0.3f; bBase = 0f }
        if (lower.contains("ocean") || lower.contains("water")) { rBase = 0f; gBase = 0.4f; bBase = 1f }
        if (lower.contains("forest") || lower.contains("nature")) { rBase = 0.1f; gBase = 0.8f; bBase = 0.2f }
        if (lower.contains("night") || lower.contains("space")) { rBase = 0.05f; gBase = 0.05f; bBase = 0.2f; noiseScale = 0.02f }
        
        return MathParams(rBase, gBase, bBase, noiseScale)
    }

    data class MathParams(val r: Float, val g: Float, val b: Float, val noiseScale: Float)

    /**
     * Menghasilkan 1 frame video berdasarkan Parameter & Waktu
     */
    fun generateFrame(w: Int, h: Int, params: MathParams, time: Float): Bitmap {
        val pixels = IntArray(w * h)
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Matematika Perlin Noise & Gelombang
                val nx = x * params.noiseScale
                val ny = y * params.noiseScale
                
                // Menciptakan efek awan/api/air yang bergerak
                val noiseVal = sin(nx + time) * cos(ny + time) + sin((nx + ny) * 0.5f - time)
                
                // Normalisasi nilai noise ke 0..1
                val n = (noiseVal + 2f) / 4f 
                
                // Modifikasi warna dasar dengan noise
                val r = ((params.r * n + (1f - n) * 0.1f) * 255).toInt().coerceIn(0, 255)
                val g = ((params.g * n + (1f - n) * 0.1f) * 255).toInt().coerceIn(0, 255)
                val b = ((params.b * n + (1f - n) * 0.1f) * 255).toInt().coerceIn(0, 255)
                
                pixels[y * w + x] = Color.rgb(r, g, b)
            }
        }
        
        // Gambar fraktal sederhana di tengah (opsional, untuk variasi)
        if (time > 0) {
            // (Kita bisa menambahkan rumus Mandelbrot di sini nanti)
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
