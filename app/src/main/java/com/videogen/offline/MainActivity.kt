package com.videogen.offline

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.videogen.offline.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random

class MainActivity : AppCompatActivity() {
    
    private val DIT_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/dit_stage0_10x16_int4.tflite"
    private val ADAPTER_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/context_adapter_dynamic_int8.tflite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 96, 48, 48)
        
        val title = TextView(this)
        title.text = "VideoGenOffline — 2.5D Parallax"
        title.textSize = 18f

        val progressAi = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressAi.max = 100
        progressAi.visibility = View.GONE

        val btnDownloadDit = Button(this)
        btnDownloadDit.text = "1. Download Model DiT"
        btnDownloadDit.setOnClickListener {
            btnDownloadDit.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(listOf(DIT_URL), "dit_stage0.tflite") { p -> 
                        runOnUiThread { btnDownloadDit.text = "DiT: $p%" } 
                    }
                    btnDownloadDit.text = "DiT Selesai"
                } catch (e: Throwable) { showDialog("Error", e.toString()) }
            }
        }

        val btnDownloadAdapter = Button(this)
        btnDownloadAdapter.text = "2. Download Adapter"
        btnDownloadAdapter.setOnClickListener {
            btnDownloadAdapter.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(listOf(ADAPTER_URL), "context_adapter.tflite") { p -> 
                        runOnUiThread { btnDownloadAdapter.text = "Adapter: $p%" } 
                    }
                    btnDownloadAdapter.text = "Adapter Selesai"
                } catch (e: Throwable) { showDialog("Error", e.toString()) }
            }
        }

        val btnGenerate = Button(this)
        btnGenerate.text = "3. BUAT VIDEO PARALLAX (RINGAN)"
        btnGenerate.setOnClickListener {
            val fileDit = File(filesDir, "dit_stage0.tflite")
            val fileAdapter = File(filesDir, "context_adapter.tflite")
            if (!fileDit.exists() || !fileAdapter.exists()) {
                showDialog("Error", "Unduh Model DiT & Adapter dulu!")
                return@setOnClickListener
            }

            btnGenerate.isEnabled = false
            progressAi.visibility = View.VISIBLE
            progressAi.progress = 0

            lifecycleScope.launch {
                try {
                    val videoPath = withContext(Dispatchers.Default) {
                        runOnUiThread { progressAi.progress = 10; btnGenerate.text = "AI Membuat Gambar..." }
                        val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a beautiful mountain landscape")
                        val textLoader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val rawEmbedding = textLoader.runTextEncoder(ids, mask)
                        textLoader.close()

                        val adapterLoader = LiteRtLoader(this@MainActivity, fileAdapter.absolutePath)
                        val context = adapterLoader.runContextAdapter(rawEmbedding)
                        adapterLoader.close()

                        val ditLoader = LiteRtLoader(this@MainActivity, fileDit.absolutePath)
                        var latent = FloatArray(2560) { Random().nextFloat() * 2f - 1f }
                        latent = ditLoader.runDitStep(latent, 500f, context)
                        ditLoader.close()
                        System.gc()

                        runOnUiThread { progressAi.progress = 40; btnGenerate.text = "Membuat Gambar..." }
                        val vaeLoader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                        val frames = vaeLoader.decodeLatentToVideoFrames(latent)
                        vaeLoader.close()
                        
                        if (frames.isEmpty()) throw Exception("Gagal membuat gambar dasar.")
                        val baseImage = frames[0]

                        runOnUiThread { progressAi.progress = 60; btnGenerate.text = "Menganalisis Kedalaman..." }
                        val depthEstimator = DepthEstimator(this@MainActivity, "models/midas_v2.tflite")
                        val depthMap = depthEstimator.estimateDepth(baseImage)
                        depthEstimator.close()

                        runOnUiThread { progressAi.progress = 80; btnGenerate.text = "Merender Video 3D..." }
                        val renderer = ParallaxRenderer()
                        val videoFrames = renderer.generateFrames(baseImage, depthMap, baseImage.width, baseImage.height)

                        runOnUiThread { progressAi.progress = 90; btnGenerate.text = "Menyimpan MP4..." }
                        val videoFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Parallax_${System.currentTimeMillis()}.mp4")
                        val writer = Mp4Writer(videoFile, baseImage.width, baseImage.height, 30)
                        writer.encodeFrames(videoFrames)
                        
                        videoFile.absolutePath
                    }

                    progressAi.progress = 100
                    btnGenerate.text = "3. BUAT VIDEO PARALLAX"
                    btnGenerate.isEnabled = true
                    progressAi.visibility = View.GONE
                    showDialog("Sukses!", "Video Parallax berhasil dibuat:\n$videoPath")

                } catch (e: Throwable) {
                    btnGenerate.text = "3. BUAT VIDEO PARALLAX"
                    btnGenerate.isEnabled = true
                    progressAi.visibility = View.GONE
                    showDialog("GAGAL", "Error: ${e.toString()}")
                }
            }
        }

        layout.addView(title)
        layout.addView(btnDownloadDit)
        layout.addView(btnDownloadAdapter)
        layout.addView(btnGenerate)
        layout.addView(progressAi)
        setContentView(layout)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
