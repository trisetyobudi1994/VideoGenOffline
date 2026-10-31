package com.videogen.offline.engine

import android.graphics.Bitmap

class AutoregressiveGenerator(private val predictor: MotionPredictor) {

    fun generateNextFrame(previousFrame: Bitmap, step: Float): Bitmap {
        val w = previousFrame.width
        val h = previousFrame.height
        val px = IntArray(w * h)
        previousFrame.getPixels(px, 0, w, 0, 0, w, h)

        val (dx, dy) = predictor.predictMotion(previousFrame, step)

        val outPx = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val newX = (x + dx[y * w + x]).toInt().coerceIn(0, w - 1)
                val newY = (y + dy[y * w + x]).toInt().coerceIn(0, h - 1)
                outPx[y * w + x] = px[newY * w + newX]
            }
        }
        return Bitmap.createBitmap(outPx, w, h, Bitmap.Config.ARGB_8888)
    }

    fun upscale(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val scale = targetWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, targetWidth, (bitmap.height * scale).toInt(), true)
    }
}
