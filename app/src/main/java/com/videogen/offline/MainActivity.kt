package com.videogen.offline

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.videogen.offline.engine.DepthEstimator
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.ParallaxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val MODEL_PATH = "models/midas_v2.tflite"
    private val TOTAL_FRAMES = 60
    private val FPS = 30

    private var selectedPhoto: Bitmap? = null
    private lateinit var btnPick: Button

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bmp = loadScaled(uri)
                selectedPhoto = bmp
                btnPick.text = "1. Foto dipilih (${bmp.width}x${bmp.height}) - ganti"
            } catch (e: Throwable) {
                showDialog("Gagal membaca foto", e.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 96, 48, 48)

        val title = TextView(this)
        title.text = "VideoGenOffline — Parallax 2.5D"
        title.textSize = 18f

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = View.GONE

        btnPick = Button(this)
        btnPick.text = "1. Pilih foto (disarankan, bukan gambar dummy)"
        btnPick.setOnClickListener { pickImage.launch("image/*") }

        val btnGenerate = Button(this)
        btnGenerate.text = "2. BUAT VIDEO PARALLAX"
        btnGenerate.setOnClickListener {
            btnGenerate.isEnabled = false
            progress.visibility = View.VISIBLE
            progress.progress = 0

            lifecycleScope.launch {
                try {
                    val report = withContext(Dispatchers.Default) {
                        val t0 = SystemClock.elapsedRealtime()

                        runOnUiThread { progress.progress = 10; btnGenerate.text = "Menyiapkan gambar..." }
                        val base = selectedPhoto ?: createDummyLandscape(512, 512)

                        runOnUiThread { progress.progress = 25; btnGenerate.text = "Menganalisis kedalaman..." }
                        val tDepth0 = SystemClock.elapsedRealtime()
                        val estimator = DepthEstimator(this@MainActivity, MODEL_PATH)
                        val modelInfo = estimator.inputInfo()
                        val depth = try { estimator.estimateDepth(base) } finally { estimator.close() }
                        val depthMs = SystemClock.elapsedRealtime() - tDepth0

                        val previewFile = File(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            "depth_preview.png"
                        )
                        saveDepthPreview(depth.values, depth.width, depth.height, previewFile)

                        runOnUiThread { progress.progress = 50; btnGenerate.text = "Merender & encode..." }
                        val renderer = ParallaxRenderer(base, depth)
                        val videoFile = File(
                            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                            "Parallax_${System.currentTimeMillis()}.mp4"
                        )
                        val tEnc0 = SystemClock.elapsedRealtime()
                        Mp4Writer(videoFile, base.width, base.height, FPS).encode(TOTAL_FRAMES) { i ->
                            renderer.renderFrame(i, TOTAL_FRAMES)
                        }
                        val encMs = SystemClock.elapsedRealtime() - tEnc0

                        val galleryPath = saveToGallery(videoFile)
                        val totalMs = SystemClock.elapsedRealtime() - t0

                        "Video: ${videoFile.length() / 1024} KB, ${TOTAL_FRAMES} frame @ ${FPS} fps\n" +
                            "Ukuran: ${base.width}x${base.height}\n\n" +
                            "Model: $modelInfo\n" +
                            "Rentang kedalaman mentah: ${depth.rawMin} .. ${depth.rawMax}\n\n" +
                            "Waktu kedalaman: ${depthMs / 1000.0} dtk\n" +
                            "Waktu render+encode: ${encMs / 1000.0} dtk\n" +
                            "Total: ${totalMs / 1000.0} dtk\n\n" +
                            "Galeri: $galleryPath\n" +
                            "Pratinjau kedalaman: ${previewFile.absolutePath}"
                    }

                    showDialog("Sukses", report)
                } catch (e: Throwable) {
                    showDialog("GAGAL", e.toString())
                } finally {
                    btnGenerate.text = "2. BUAT VIDEO PARALLAX"
                    btnGenerate.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }

        layout.addView(title)
        layout.addView(btnPick)
        layout.addView(btnGenerate)
        layout.addView(progress)
        setContentView(layout)
    }

    private fun loadScaled(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("Foto tidak bisa dibaca")

        val scale = minOf(1f, 512f / maxOf(raw.width, raw.height))
        var w = (raw.width * scale).toInt()
        var h = (raw.height * scale).toInt()
        w = maxOf(2, (w / 2) * 2)
        h = maxOf(2, (h / 2) * 2)
        return Bitmap.createScaledBitmap(raw, w, h, true)
    }

    private fun saveDepthPreview(values: FloatArray, w: Int, h: Int, file: File) {
        val px = IntArray(w * h) {
            val g = (values[it] * 255f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun saveToGallery(file: File): String {
        val values = ContentValues()
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoGen")
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "gagal menyalin ke galeri"
        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        return "Movies/VideoGen/${file.name}"
    }

    private fun createDummyLandscape(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.color = Color.parseColor("#87CEEB")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.YELLOW
        canvas.drawCircle(width * 0.8f, height * 0.2f, 50f, paint)

        paint.color = Color.parseColor("#8B4513")
        val path = Path()
        path.moveTo(width * 0.1f, height * 0.7f)
        path.lineTo(width * 0.5f, height * 0.2f)
        path.lineTo(width * 0.9f, height * 0.7f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.parseColor("#228B22")
        canvas.drawRect(0f, height * 0.7f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
