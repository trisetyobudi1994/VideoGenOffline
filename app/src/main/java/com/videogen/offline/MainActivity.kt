package com.videogen.offline

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.videogen.offline.engine.LiteRtLoader
import com.videogen.offline.engine.TokenizerHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "VideoGenOffline — Fase 3"
            textSize = 20f
        }

        // --- TOMBOL 1: VAE ---
        val btnTestVae = Button(this).apply {
            text = "1. Test Load VAE"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(
                        this@MainActivity,
                        "models/causal_video_vae_decoder.tflite"
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "VAE decoder berhasil dimuat",
                        Toast.LENGTH_SHORT
                    ).show()
                    loader.close()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal VAE: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // --- TOMBOL 2: TEXT ENCODER ---
        val btnTestText = Button(this).apply {
            text = "2. Test Load Text Encoder (dt5)"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(
                        this@MainActivity,
                        "models/dt5_text_encoder.tflite"
                    )
                    val info =
                        "INPUT:\n${loader.getInputTensorInfo()}\n\nOUTPUT:\n${loader.getOutputTensorInfo()}"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Text Encoder Tensor Info")
                        .setMessage(info)
                        .setPositiveButton("OK", null)
                        .show()
                    loader.close()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // --- TOMBOL 3: TOKENIZER ---
        val btnTestToken = Button(this).apply {
            text = "3. Test Tokenizer (Teks ke Angka)"
            setOnClickListener {
                try {
                    val tokenizer = TokenizerHelper(this@MainActivity)
                    val prompt = "a cat walking"
                    val (ids, mask) = tokenizer.encode(prompt)

                    val nonZeroIds = ids.filter { it != 0 }
                    val resultText = buildString {
                        append("Teks: '$prompt'\n\n")
                        append("IDs (10 pertama):\n")
                        append(ids.take(10).joinToString(", "))
                        append("\n\nMask (10 pertama):\n")
                        append(mask.take(10).joinToString(", "))
                        append("\n\nTotal ID: ${ids.size} (harus 128)")
                        append("\nToken bukan-0: ${nonZeroIds.size}")
                        append("\nID terakhir bukan-0: ${nonZeroIds.lastOrNull() ?: "-"}")
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Hasil Tokenizer")
                        .setMessage(resultText)
                        .setPositiveButton("OK", null)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal Tokenizer: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(btnTestVae)
        layout.addView(btnTestText)
        layout.addView(btnTestToken)
        setContentView(layout)
    }
}
