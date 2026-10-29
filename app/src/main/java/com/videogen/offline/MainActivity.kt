package com.videogen.offline

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.videogen.offline.engine.LiteRtLoader
import com.videogen.offline.engine.ModelDownloader
import com.videogen.offline.engine.TokenizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class MainActivity : AppCompatActivity() {
    
    private val DIT_STAGE0_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/dit_stage0_10x16_int4.tflite"
    private val DIT_STAGE0_FILENAME = "dit_stage0.tflite"
    private val ADAPTER_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/context_adapter_dynamic_int8.tflite"
    private val ADAPTER_FILENAME = "context_adapter.tflite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply { text = "VideoGenOffline — The Final Pipeline"; textSize = 18f }

        val btnTestVae = Button(this)
        btnTestVae.text = "1. Test Load VAE"
        btnTestVae.setOnClickListener {
            try {
                val loader = LiteRtLoader(this, "models/causal_video_vae_decoder.tflite")
                showDialog("Sukses", "VAE dimuat")
                loader.close()
            } catch (e: Throwable) { showDialog("Error", e.toString()) }
        }

        val btnTestText = Button(this)
        btnTestText.text = "2. Info Text Encoder"
        btnTestText.setOnClickListener {
            try {
                val loader = LiteRtLoader(this, "models/dt5_text_encoder.tflite")
                showDialog("Info", loader.getInputTensorInfo())
                loader.close()
            } catch (e: Throwable) { showDialog("Error", e.toString()) }
        }

        val btnTestToken = Button(this)
        btnTestToken.text = "3. Test Tokenizer"
        btnTestToken.setOnClickListener {
            try {
                val (ids, _) = TokenizerHelper(this).encode("a cat walking")
                showDialog("Sukses", "IDs: ${ids.take(5).joinToString(", ")}...")
            } catch (e: Throwable) { showDialog("Error", e.toString()) }
        }

        val btnRunEncoder = Button(this)
        btnRunEncoder.text = "4. Generate Embedding"
        btnRunEncoder.setOnClickListener {
            btnRunEncoder.isEnabled = false
            lifecycleScope.launch {
                try {
                    val res = withContext(Dispatchers.Default) {
                        val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a cat walking")
                        val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val emb = loader.runTextEncoder(ids, mask)
                        loader.close()
                        "Berhasil! Size: ${emb.size}"
                    }
                    btnRunEncoder.isEnabled = true
                    showDialog("Sukses", res)
                } catch (e: Throwable) {
                    btnRunEncoder.isEnabled = true
                    showDialog("Error", e.toString())
                }
            }
        }

        val btnDownloadDit = Button(this)
        btnDownloadDit.text = "5. DOWNLOAD MODEL DiT (816 MB)"
        btnDownloadDit.setOnClickListener {
            btnDownloadDit.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(DIT_STAGE0_URL, DIT_STAGE0_FILENAME) { p ->
                        btnDownloadDit.text = "Mengunduh DiT: $p%"
                    }
                    btnDownloadDit.text = "Unduhan DiT Selesai"
                } catch (e: Throwable) {
                    btnDownloadDit.isEnabled = true
                    showDialog("Error", e.toString())
                }
            }
        }

        val btnDownloadAdapter = Button(this)
        btnDownloadAdapter.text = "5b. DOWNLOAD ADAPTER (124 MB)"
        btnDownloadAdapter.setOnClickListener {
            btnDownloadAdapter.isEnabled = false
            lifecycleScope.launch {
                try {
                    ModelDownloader(this@MainActivity).downloadFile(ADAPTER_URL, ADAPTER_FILENAME) { p ->
                        btnDownloadAdapter.text = "Mengunduh Adapter: $p%"
                    }
                    btnDownloadAdapter.text = "Unduhan Adapter Selesai"
                } catch (e: Throwable) {
                    btnDownloadAdapter.isEnabled = true
                    showDialog("Error", e.toString())
                }
            }
        }

        val btnTestDit = Button(this)
        btnTestDit.text = "6. Info Tensor DiT"
        btnTestDit.setOnClickListener {
            val fileDit = File(filesDir, DIT_STAGE0_FILENAME)
            if (!fileDit.exists()) return@setOnClickListener showDialog("Error", "Unduh model dulu!")
            btnTestDit.isEnabled = false
            lifecycleScope.launch {
                try {
                    val info = withContext(Dispatchers.Default) {
                        val loader = LiteRtLoader(this@MainActivity, fileDit.absolutePath)
                        val res = "INPUT:\n${loader.getInputTensorInfo()}\nOUTPUT:\n${loader.getOutputTensorInfo()}"
                        loader.close()
                        res
                    }
                    btnTestDit.isEnabled = true
                    showDialog("Info Tensor DiT", info)
                } catch (e: Throwable) {
                    btnTestDit.isEnabled = true
                    showDialog("Error", e.toString())
                }
            }
        }

        // TOMBOL 8: THE ULTIMATE PIPELINE
        val btnGenerate = Button(this)
        btnGenerate.text = "8. PROSES AI VIDEO (UJI COBA)"
        btnGenerate.setOnClickListener {
            val fileDit = File(filesDir, DIT_STAGE0_FILENAME)
            val fileAdapter = File(filesDir, ADAPTER_FILENAME)
            if (!fileDit.exists() || !fileAdapter.exists()) {
                return@setOnClickListener showDialog("Error", "Unduh Model DiT & Adapter dulu!")
            }

            btnGenerate.isEnabled = false
            btnGenerate.text = "AI BEKERJA... (Jangan tutup)"

            lifecycleScope.launch {
                try {
                    val resultText = withContext(Dispatchers.Default) {
                        val log = StringBuilder()
                        val startTime = System.currentTimeMillis()
                        val startRam = Debug.getPss() / 1024

                        // 1. Teks -> Embedding
                        log.append("1. Tokenisasi...\n")
                        val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a beautiful mountain landscape, 4k")
                        val textLoader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val rawEmbedding = textLoader.runTextEncoder(ids, mask)
                        textLoader.close()

                        // 2. Context Adapter
                        log.append("2. Adapter...\n")
                        val adapterLoader = LiteRtLoader(this@MainActivity, fileAdapter.absolutePath)
                        val context = adapterLoader.runContextAdapter(rawEmbedding)
                        adapterLoader.close()

                        // 3. Latent Awal (2560 floats)
                        var latent = FloatArray(2560) { Random().nextFloat() * 2f - 1f }
                        
                        // 4. Denoising
                        log.append("3. Memuat DiT...\n")
                        val ditLoader = LiteRtLoader(this@MainActivity, fileDit.absolutePath)
                        val totalSteps = 10
                        
                        for (step in 1..totalSteps) {
                            val timestep = (1000 - (step * (1000 / totalSteps))).toFloat()
                            log.append("   Step $step/$totalSteps...\n")
                            latent = ditLoader.runDitStep(latent, timestep, context)
                        }
                        
                        log.append("4. Tutup DiT & Muat VAE...\n")
                        ditLoader.close()
                        System.gc()

                        // 5. VAE Decode
                        val vaeLoader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                        val bitmap = vaeLoader.decodeLatentToImage(latent)
                        vaeLoader.close()

                        // 6. Simpan
                        log.append("5. Simpan Gambar...\n")
                        val filePath = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "VideoGen_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(filePath).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }

                        val endTime = System.currentTimeMillis()
                        val endRam = Debug.getPss() / 1024

                        log.append("\nSELESAI!\n")
                        log.append("Waktu: ${(endTime - startTime) / 1000.0} detik\n")
                        log.append("RAM: ${endRam - startRam} MB\n")
                        log.append("Gambar: ${filePath.absolutePath}")
                        log.toString()
                    }

                    btnGenerate.text = "8. PROSES AI VIDEO"
                    btnGenerate.isEnabled = true
                    showDialog("Hasil Pipeline", resultText)

                } catch (e: Throwable) {
                    btnGenerate.text = "8. PROSES AI VIDEO"
                    btnGenerate.isEnabled = true
                    showDialog("GAGAL / OOM", "HP tidak kuat: ${e.toString()}")
                }
            }
        }

        layout.addView(title)
        layout.addView(btnTestVae)
        layout.addView(btnTestText)
        layout.addView(btnTestToken)
        layout.addView(btnRunEncoder)
        layout.addView(btnDownloadDit)
        layout.addView(btnDownloadAdapter)
        layout.addView(btnTestDit)
        layout.addView(btnGenerate)
        setContentView(layout)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
