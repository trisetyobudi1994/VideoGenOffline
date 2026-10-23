package com.videogen.offline

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
            text = "VideoGenOffline — Fase 1"
            textSize = 22f
        }

        val btnTest = Button(this).apply {
            text = "Test Load VAE"
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
                } catch (e: java.io.FileNotFoundException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Model tidak ditemukan. Cek URL download model.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Gagal: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(title)
        layout.addView(btnTest)
        setContentView(layout)
    }
}
