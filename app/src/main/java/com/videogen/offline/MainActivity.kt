package com.videogen.offline

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
import com.videogen.offline.engine.LiteRtLoader
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.ModelDownloader
import com.videogen.offline.engine.TokenizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Random

class MainActivity : AppCompatActivity() {
    
    private val DIT_STAGE0_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/dit_stage0_10x16_int4.tflite"
    private val DIT_STAGE0_FILENAME = "dit_stage0.tflite"
    private val ADAPTER_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/context_adapter_dynamic_int8.tflite"
    private val ADAPTER_FILENAME = "context_adapter.tflite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 96, 48, 48)

        val title = TextView(this)
        title.text = "VideoGenOffline — Final Video"
        title.textSize = 18f

        val progressAi = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressAi.max = 100
        progressAi.visibility = View.GONE

        // 1. TOMBOL DOWNLOAD DIT
        val btnDownloadDit = Button(this)
        btnDownloadDit.text = "1. DOWNLOAD MODEL DIT (816 MB)"
        btnDownloadDit.setOnClickListener {
            btnDownloadDit.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(listOf(DIT_STAGE0_URL), DIT_STAGE0_FILENAME) { p ->
                        runOnUiThread { btnDownloadDit.text = "DiT: $p%" }
                    }
                    btnDownloadDit.text = "Unduhan DiT Selesai"
                } catch (e: Throwable) { showDialog("Error", e.toString()) }
            }
        }

        // 2. TOMBOL DOWNLOAD ADAPTER
        val btnDownloadAdapter = Button(this)
        btnDownloadAdapter.text = "2. DOWNLOAD ADAPTER (124 MB)"
        btnDownloadAdapter.setOnClickListener {
            btnDownloadAdapter.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(listOf(ADAPTER_URL), ADAPTER_FILENAME) { p ->
                        runOnUiThread { btnDownloadAdapter.text = "Adapter: $p%" }
                    }
                    btnDownloadAdapter.text = "Unduhan Adapter Selesai"
                } catch (e: Throwable) { showDialog("Error", e.toString()) }
            }
        }

        // 3. TOMBOL GENERATE VIDEO
        val btnGenerate = Button(this)
        btnGenerate.text = "3. BUAT VIDEO AI"
        btnGenerate.setOnClickListener {
            val fileDit = File(filesDir, DIT_STAGE0_FILENAME)
            val fileAdapter = File(filesDir, ADAPTER_FILENAME)
            if (!fileDit.exists() || !fileAdapter.exists()) {
                return@setOnClickListener showDialog("Error", "Unduh Model DiT & Adapter dulu!")
            }

            btnGenerate.isEnabled = false
            progressAi.visibility = View.VISIBLE
            progressAi.progress = 0

            lifecycleScope.launch {
                try {
                    val videoPath = withContext(Dispatchers.Default) {
                        // 1. Tokenisasi
                        runOnUiThread { progressAi.progress = 10; btnGenerate.text = "Tokenisasi..." }
                        val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a cat walking")
                        val textLoader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val rawEmbedding = textLoader.runTextEncoder(ids, mask)
                        textLoader.close()

                        // 2. Adapter
                        runOnUiThread { progressAi.progress = 20; btnGenerate.text = "Adapter..." }
                        val adapterLoader = LiteRtLoader(this@MainActivity, fileAdapter.absolutePath)
                        val context = adapterLoader.runContextAdapter(rawEmbedding)
                        adapterLoader.close()

                        // 3. Denoising Loop (3 Langkah)
                        runOnUiThread { progressAi.progress = 30; btnGenerate.text = "Memuat DiT..." }
                        val ditLoader = LiteRtLoader(this@MainActivity, fileDit.absolutePath)
                        var latent = FloatArray(2560) { Random().nextFloat() * 2f - 1f }
                        
                        val totalSteps = 3 
                        for (step in 1..totalSteps) {
                            val timestep = (1000 - (step * (1000 / totalSteps))).toFloat()
                            runOnUiThread { 
                                progressAi.progress = 30 + (step * 20)
                                btnGenerate.text = "Berpikir (Step $step/$totalSteps)..." 
                            }
                            latent = ditLoader.runDitStep(latent, timestep, context)
                            System.gc()
                        }
                        
                        ditLoader.close()
                        System.gc()
                        runOnUiThread { progressAi.progress = 80; btnGenerate.text = "Memuat VAE..." }

                        // 4. Decode ke Frame Video
                        val vaeLoader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                        val frames = vaeLoader.decodeLatentToVideoFrames(latent)
                        vaeLoader.close()
                        runOnUiThread { progressAi.progress = 90; btnGenerate.text = "Menyimpan Video..." }

                        // 5. Simpan ke MP4
                        val videoFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoGen_${System.currentTimeMillis()}.mp4")
                        if (frames.isNotEmpty()) {
                            val writer = Mp4Writer(videoFile, frames[0].width, frames[0].height, 8)
                            writer.encodeFrames(frames)
                        }
                        
                        videoFile.absolutePath
                    }

                    progressAi.progress = 100
                    btnGenerate.text = "3. BUAT VIDEO AI"
                    btnGenerate.isEnabled = true
                    progressAi.visibility = View.GONE
                    showDialog("Sukses!", "Video MP4 berhasil dibuat di:\n$videoPath")

                } catch (e: Throwable) {
                    btnGenerate.text = "3. BUAT VIDEO AI"
                    btnGenerate.isEnabled = true
                    progressAi.visibility = View.GONE
                    showDialog("GAGAL / OOM", "HP tidak kuat: ${e.toString()}")
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
