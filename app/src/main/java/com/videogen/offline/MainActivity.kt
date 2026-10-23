package com.videogen.offline

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.videogen.offline.engine.LiteRtLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "VideoGenOffline — Fase 2"
            textSize = 22f
        }

        // --- TOMBOL 1: VAE ---
        val btnTestVae = Button(this).apply {
            text = "Test Load VAE"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                    Toast.makeText(this@MainActivity, "VAE decoder berhasil dimuat", Toast.LENGTH_SHORT).show()
                    loader.close()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Gagal VAE: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // --- TOMBOL 2: TEXT ENCODER ---
        val btnTestText = Button(this).apply {
            text = "Test Load Text Encoder (dt5)"
            setOnClickListener {
                try {
                    val loader = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                    val info = "INPUT:\n${loader.getInputTensorInfo()}\nOUTPUT:\n${loader.getOutputTensorInfo()}"
                    
                    // Tampilkan popup
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Text Encoder Tensor Info")
                        .setMessage(info)
                        .setPositiveButton("OK", null)
                        .show()
                        
                    loader.close()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(btnTestVae)
        layout.addView(btnTestText)
        setContentView(layout)
    }
}
