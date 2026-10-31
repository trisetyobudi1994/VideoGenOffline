package com.videogen.offline

import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.ParticleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TOTAL_FRAMES = 120; private val FPS = 30; private val W = 512; private val H = 512
    private var exoPlayer: ExoPlayer? = null
    private lateinit var promptInput: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48); setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        val title = TextView(this).apply { text = "Generative Particle Engine"; textSize = 24f; setTextColor(Color.BLACK); setPadding(0, 0, 0, 16) }
        val subtitle = TextView(this).apply { text = "Teks -> Video (100% Matematika Murni)"; textSize = 14f; setTextColor(Color.GRAY); setPadding(0, 0, 0, 32) }
        promptInput = EditText(this).apply { hint = "Ketik ide video (misal: fire, water, galaxy)"; setPadding(24, 24, 24, 24) }
        btnGenerate = Button(this).apply {
            text = "Buat Video Partikel"
            setOnClickListener { generateVideo(promptInput.text.toString().ifEmpty { "fire" }) }
        }
        progressBar = ProgressBar(this).apply { visibility = View.GONE; setPadding(0, 32, 0, 0) }
        layout.addView(title); layout.addView(subtitle); layout.addView(promptInput); layout.addView(btnGenerate); layout.addView(progressBar)
        setContentView(layout)
    }

    private fun generateVideo(prompt: String) {
        btnGenerate.isEnabled = false; progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Merender Partikel...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val videoFile = withContext(Dispatchers.Default) {
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Particle_${System.currentTimeMillis()}.mp4")
                    val engine = ParticleEngine(W, H, prompt)
                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        engine.renderFrame((i.toFloat() / FPS) * 2.0f)
                    }
                    saveToGallery(outFile); outFile
                }
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showResultDialog(videoFile)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showDialog("GAGAL", e.toString())
            }
        }
    }

    private fun showResultDialog(file: File) {
        val playerView = PlayerView(this)
        playerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800)
        val container = LinearLayout(this).apply { addView(playerView); setPadding(16, 16, 16, 16) }
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); prepare(); playWhenReady = true
        }
        playerView.player = exoPlayer
        AlertDialog.Builder(this).setTitle("Video Berhasil!").setView(container)
            .setPositiveButton("Tutup") { _,_ -> exoPlayer?.release() }
            .setOnDismissListener { exoPlayer?.release() }.show()
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoGen")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        contentResolver.openOutputStream(uri!!)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
