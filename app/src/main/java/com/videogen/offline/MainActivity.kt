package com.videogen.offline

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
            text = "VideoGenOffline — Fase 4"
            textSize = 20f
        }

        val btnTestVae = Button(this).apply {
            text = "1. Test Load VAE"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                    Toast.makeText(this@MainActivity, "VAE berhasil dimuat", Toast.LENGTH_SHORT).show()
                    loader.close()
                } catch (e: Throwable) {
                    Toast.makeText(this@MainActivity, "Gagal VAE: ${e.toString()}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val btnTestText = Button(this).apply {
            text = "2. Info Text Encoder"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                    val info = "INPUT:\n${loader.getInputTensorInfo()}\nOUTPUT:\n${loader.getOutputTensorInfo()}"
                    AlertDialog.Builder(this@MainActivity).setTitle("Info").setMessage(info).setPositiveButton("OK", null).show()
                    loader.close()
                } catch (e: Throwable) {
                    Toast.makeText(this@MainActivity, "Gagal: ${e.toString()}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val btnTestToken = Button(this).apply {
            text = "3. Test Tokenizer"
            setOnClickListener {
                try {
                    val tokenizer = TokenizerHelper(this@MainActivity)
                    val (ids, _) = tokenizer.encode("a cat walking")
                    val resultText = "IDs (10 pertama):\n${ids.take(10).joinToString(", ")}...\n\nTotal: ${ids.size}"
                    AlertDialog.Builder(this@MainActivity).setTitle("Hasil Tokenizer").setMessage(resultText).setPositiveButton("OK", null).show()
                } catch (e: Throwable) {
                    Toast.makeText(this@MainActivity, "Gagal: ${e.toString()}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val btnRunEncoder = Button(this).apply {
            text = "4. JALANKAN AI (TEKS -> MAKNA)"
            setOnClickListener {
                isEnabled = false
                text = "AI SEDANG BERPIKIR... MOHON TUNGGU"

                // Jalankan di Background Thread agar layar tidak macet
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

                            "BERHASIL!\n\n" +
                            "Waktu proses: $duration ms\n" +
                            "Ukuran output: ${embedding.size} angka\n\n" +
                            "Contoh 5 angka pertama:\n" + embedding.take(5).joinToString(", ")
                        }

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Hasil AI (Embedding)")
                            .setMessage(resultText)
                            .setPositiveButton("OK", null)
                            .show()

                    } catch (e: Throwable) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Error Detail")
                            .setMessage("Gagal AI: ${e.toString()}")
                            .setPositiveButton("OK", null)
                            .show()
                    } finally {
                        isEnabled = true
                        text = "4. JALANKAN AI (TEKS -> MAKNA)"
                    }
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
}
