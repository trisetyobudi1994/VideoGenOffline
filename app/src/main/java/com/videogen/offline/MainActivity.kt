package com.videogen.offline

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.videogen.offline.engine.LiteRtLoader
import com.videogen.offline.engine.TokenizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "VideoGenOffline — Fase 4 (Encoder)"
            textSize = 20f
        }

        // 1. TOMBOL VAE
        val btnTestVae = Button(this)
        btnTestVae.text = "1. Test Load VAE"
        btnTestVae.setOnClickListener {
            try {
                val loader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                showDialog("Sukses", "VAE berhasil dimuat")
                loader.close()
            } catch (e: Throwable) {
                showDialog("Error VAE", e.toString())
            }
        }

        // 2. TOMBOL INFO TEXT ENCODER
        val btnTestText = Button(this)
        btnTestText.text = "2. Info Text Encoder"
        btnTestText.setOnClickListener {
            try {
                val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                val info = "INPUT:\n${loader.getInputTensorInfo()}\nOUTPUT:\n${loader.getOutputTensorInfo()}"
                showDialog("Info Tensor", info)
                loader.close()
            } catch (e: Throwable) {
                showDialog("Error Info", e.toString())
            }
        }

        // 3. TOMBOL TEST TOKENIZER
        val btnTestToken = Button(this)
        btnTestToken.text = "3. Test Tokenizer"
        btnTestToken.setOnClickListener {
            try {
                val tokenizer = TokenizerHelper(this@MainActivity)
                val (ids, mask) = tokenizer.encode("a cat walking")
                val resultText = "IDs (10 pertama):\n${ids.take(10).joinToString(", ")}...\n\nTotal ID: ${ids.size}\nTotal Mask: ${mask.size}"
                showDialog("Hasil Tokenizer", resultText)
            } catch (e: Throwable) {
                showDialog("Error Tokenizer", e.toString())
            }
        }

        // 4. TOMBOL JALANKAN AI (BACKGROUND THREAD)
        val btnRunEncoder = Button(this)
        btnRunEncoder.text = "4. JALANKAN AI (Teks -> Makna)"
        
        btnRunEncoder.setOnClickListener {
            btnRunEncoder.text = "AI SEDANG BERPIKIR... MOHON TUNGGU"
            btnRunEncoder.isEnabled = false

            lifecycleScope.launch {
                try {
                    val resultText = withContext(Dispatchers.Default) {
                        val tokenizer = TokenizerHelper(this@MainActivity)
                        val (ids, mask) = tokenizer.encode("a cat walking")
                        
                        val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                        val startTime = System.currentTimeMillis()
                        
                        val embedding = loader.runTextEncoder(ids, mask)
                        
                        val duration = System.currentTimeMillis() - startTime
                        loader.close()

                        "Berhasil!\n\n" +
                        "Waktu proses: $duration ms\n" +
                        "Ukuran output: ${embedding.size} angka\n\n" +
                        "Contoh 5 angka pertama:\n" +
                        embedding.take(5).joinToString(", ")
                    }

                    btnRunEncoder.text = "4. JALANKAN AI (Teks -> Makna)"
                    btnRunEncoder.isEnabled = true
                    showDialog("Hasil AI (Embedding)", resultText)

                } catch (e: Throwable) {
                    btnRunEncoder.text = "4. JALANKAN AI (Teks -> Makna)"
                    btnRunEncoder.isEnabled = true
                    showDialog("Error Detail", "Gagal AI: ${e.toString()}")
                }
            }
        }

        layout.addView(title)
        layout.addView(btnTestVae)
        layout.addView(btnTestText)
        layout.addView(btnTestToken)
        layout.addView(btnRunEncoder)
        setContentView(layout)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
