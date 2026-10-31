package com.videogen.offline.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ImageFetcher {
    suspend fun fetchImage(prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val enhanced = "cinematic, high detail, professional photography, 4k, " + prompt
        val encoded = URLEncoder.encode(enhanced, "UTF-8")
        val seed = System.currentTimeMillis()
        val urlStr = "https://image.pollinations.ai/prompt/$encoded?width=768&height=768&nologo=true&seed=$seed&model=flux"

        val conn = URL("https://image.pollinations.ai/prompt/$encoded?width=768&height=768&nologo=true&seed=$seed&model=flux").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }

            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            bmp ?: throw Exception("Gagal decode gambar")
        } finally {
            conn.disconnect()
        }
    }
}
