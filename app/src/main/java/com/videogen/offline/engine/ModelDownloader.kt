package com.videogen.offline.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

    // Menerima List<String> untuk fallback URL
    suspend fun downloadFile(
        urls: List<String>,
        fileName: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (file.exists() && file.length() > 0) return@withContext file

        var lastException: Throwable? = null

        for ((index, url) in urls.withIndex()) {
            try {
                return@withContext downloadSingleUrl(url, file, onProgress)
            } catch (e: Throwable) {
                lastException = e
            }
        }
        throw IOException("Semua server gagal. Error: ${lastException?.message}")
    }

    private suspend fun downloadSingleUrl(
        urlStr: String,
        file: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val part = File(context.filesDir, "${file.name}.part")
        if (part.exists()) part.delete()

        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server membalas HTTP ${conn.responseCode}")
            }

            val total = conn.contentLengthLong
            if (total > 0 && context.filesDir.usableSpace < total + 50L * 1024 * 1024) {
                throw IOException("Ruang penyimpanan tidak cukup")
            }

            var done = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        ensureActive()
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                withContext(Dispatchers.Main) { onProgress(pct) }
                            }
                        }
                    }
                }
            }

            if (total > 0 && done != total) throw IOException("Unduhan tidak lengkap")
            if (!part.renameTo(file)) throw IOException("Gagal menyimpan file")
            return@withContext file
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }
}
