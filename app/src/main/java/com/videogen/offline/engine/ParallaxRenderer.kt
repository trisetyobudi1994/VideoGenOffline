package com.videogen.offline.engine

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ParallaxRenderer(
    private val src: Bitmap,
    depth: DepthMap,
    private val amp: Float = 0.035f,
    private val zoom: Float = 1.06f
) {
    private val w = src.width
    private val h = src.height
    private val srcPx = IntArray(w * h)
    private val d = FloatArray(w * h)

    init {
        src.getPixels(srcPx, 0, w, 0, 0, w, h)
        // Ubah ukuran peta kedalaman ke ukuran gambar (bilinear)
        val dw = depth.width
        val dh = depth.height
        for (y in 0 until h) {
            val fy = if (h > 1) y * (dh - 1).toFloat() / (h - 1) else 0f
            val y0 = fy.toInt().coerceIn(0, dh - 1)
            val y1 = (y0 + 1).coerceIn(0, dh - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = if (w > 1) x * (dw - 1).toFloat() / (w - 1) else 0f
                val x0 = fx.toInt().coerceIn(0, dw - 1)
                val x1 = (x0 + 1).coerceIn(0, dw - 1)
                val tx = fx - x0
                val a = depth.values[y0 * dw + x0] * (1 - tx) + depth.values[y0 * dw + x1] * tx
                val b = depth.values[y1 * dw + x0] * (1 - tx) + depth.values[y1 * dw + x1] * tx
                d[y * w + x] = a * (1 - ty) + b * ty
            }
        }
    }

    fun renderFrame(index: Int, total: Int): Bitmap {
        val ang = 2.0 * PI * index / total
        val dx = (sin(ang) * amp * w).toFloat()
        val dy = (cos(ang) * amp * 0.5 * h).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val out = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val k = (d[y * w + x] - 0.5f) * 2f   // -1 (jauh) .. 1 (dekat)
                val sx = cx + (x - cx) / zoom - dx * k
                val sy = cy + (y - cy) / zoom - dy * k
                val ix = sx.toInt().coerceIn(0, w - 1)
                val iy = sy.toInt().coerceIn(0, h - 1)
                out[y * w + x] = srcPx[iy * w + ix]
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
