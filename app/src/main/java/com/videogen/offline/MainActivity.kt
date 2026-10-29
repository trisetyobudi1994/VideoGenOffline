package com.videogen.offline

import android.os.Bundle
import android.os.Debug
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
        title.text = "VideoGenOffline — Fase 5 (Benchmark DiT)"
        title.textSize = 18f

        // 1. VAE
        val btnTestVae = Button(this)
        btnTestVae.text = "1. Test Load VAE"
        btnTestVae.setOnClickListener {
            try {
                val loader = LiteRtLoader(this, "models/causal_video_vae_decoder.tflite")
                showDialog("Sukses", "VAE berhasil dimuat")
                loader.close()
            } catch (e: Throwable) { showDialog("Error VAE", e.toString()) }
        }

        // 2. Info Text Encoder
        val btnTestText = Button(this)
        btnTestText.text = "2. Info Text Encoder"
        btnTestText.setOnClickListener {
            try {
                val loader = LiteRtLoader(this, "models/dt5_text_encoder.tflite")
                showDialog("Info Tensor", "INPUT:\n${loader.getInputTensorInfo()}\nOUTPUT:\n${loader.getOutputTensorInfo()}")
                loader.close()
            } catch (e: Throwable) { showDialog("Error", e.toString()) }
        }

        // 3. Tokenizer
        val btnTestToken = Button(this)
        btnTestToken.text = "3. Test Tokenizer"
        btnTestToken.setOnClickListener {
            try {
                val tokenizer = TokenizerHelper(this)
                val (ids, _) = tokenizer.encode("a cat walking")
                showDialog("Hasil Tokenizer", "IDs: ${ids.take(5).joinToString(", ")}...")
            } catch (e: Throwable) { showDialog("Error", e.toString()) }
        }

        // 4. Embedding
        val btnRunEncoder = Button(this)
        btnRunEncoder.text = "4. Generate Embedding (Teks -> Makna)"
        btnRunEncoder.setOnClickListener {
            btnRunEncoder.isEnabled = false
            lifecycleScope.launch {
                try {
                    val resultText = withContext(Dispatchers.Default) {
                        val tokenizer = TokenizerHelper(this@MainActivity)
                        val (ids, mask) = tokenizer.encode("a cat walking")
                        val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val embedding = loader.runTextEncoder(ids, mask)
                        loader.close()
                        "Berhasil! Embedding size: ${embedding.size}"
                    }
                    btnRunEncoder.isEnabled = true
                    showDialog("Sukses", resultText)
                } catch (e: Throwable) {
                    btnRunEncoder.isEnabled = true
                    showDialog("Error", e.toString())
                }
            }
        }

        // 5. Download DiT
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
                    showDialog("Error Unduhan", e.toString())
                }
            }
        }
        
        // 5b. Download Adapter
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
                    showDialog("Error Unduhan", e.toString())
                }
            }
        }

        // 6. Test Load DiT
        val btnTestDit = Button(this)
        btnTestDit.text = "6. Info Tensor DiT"
        btnTestDit.setOnClickListener {
            val fileDit = File(filesDir, DIT_STAGE0_FILENAME)
            if (!fileDit.exists()) {
                showDialog("Error", "Unduh model dulu!")
                return@setOnClickListener
            }
            
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

        // 7. BENCHMARK LENGKAP
        val btnBenchmarkDit = Button(this)
        btnBenchmarkDit.text = "7. UJI DIT (BENCHMARK)"
        btnBenchmarkDit.setOnClickListener {
            val fileDit = File(filesDir, DIT_STAGE0_FILENAME)
            val fileAdapter = File(filesDir, ADAPTER_FILENAME)
            if (!fileDit.exists() || !fileAdapter.exists()) {
                showDialog("Error", "Unduh Model DiT dan Adapter (Tombol 5 & 5b) dulu!")
                return@setOnClickListener
            }

            btnBenchmarkDit.isEnabled = false
            btnBenchmarkDit.text = "MENGUJI DIT... (Bisa makan waktu)"

            lifecycleScope.launch {
                try {
                    val resultText = withContext(Dispatchers.Default) {
                        // 1. Embedding
                        val tokenizer = TokenizerHelper(this@MainActivity)
                        val (ids, mask) = tokenizer.encode("a cat walking")
                        val textLoader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val rawEmbedding = textLoader.runTextEncoder(ids, mask)
                        textLoader.close()

                        // 2. Context Adapter
                        val adapterLoader = LiteRtLoader(this@MainActivity, fileAdapter.absolutePath)
                        val context = adapterLoader.runContextAdapter(rawEmbedding)
                        adapterLoader.close()

                        // 3. Uji DiT
                        val startTime = System.currentTimeMillis()
                        val startRam = Debug.getPss() / 1024 

                        val ditLoader = LiteRtLoader(this@MainActivity, fileDit.absolutePath)
                        val outputLatent = ditLoader.runDitStep(context)
                        ditLoader.close()

                        val endTime = System.currentTimeMillis()
                        val endRam = Debug.getPss() / 1024 

                        "BENCHMARK SELESAI\n\n" +
                        "Waktu 1 langkah DiT: ${(endTime - startTime) / 1000.0} detik\n" +
                        "RAM terpakai: ${endRam - startRam} MB\n" +
                        "Output Latent size: ${outputLatent.size}\n\n" +
                        "Total langkah (misal 20x): ${((endTime - startTime) * 20 / 1000)} detik"
                    }

                    btnBenchmarkDit.text = "7. UJI DIT (BENCHMARK)"
                    btnBenchmarkDit.isEnabled = true
                    showDialog("Hasil Benchmark", resultText)

                } catch (e: Throwable) {
                    btnBenchmarkDit.text = "7. UJI DIT (BENCHMARK)"
                    btnBenchmarkDit.isEnabled = true
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
        layout.addView(btnBenchmarkDit)
        setContentView(layout)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
