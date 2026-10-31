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
import com.videogen.offline.engine.CreatureAnimator
import com.videogen.offline.engine.GeminiBrain
import com.videogen.offline.engine.Mp4Writer
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
        val title = TextView(this).apply { text = "AI Global Brain"; textSize = 24f; setTextColor(Color.BLACK); setPadding(0, 0, 0, 16) }
        val subtitle = TextView(this).apply { text = "Terhubung ke Gemini AI"; textSize = 14f; setTextColor(Color.GRAY); setPadding(0, 0, 0, 32) }
        promptInput = EditText(this).apply { hint = "Ketik (misal: kucing berlajan)"; setPadding(24, 24, 24, 24) }
        btnGenerate = Button(this).apply {
            text = "Pahami & Buat Animasi"
            setOnClickListener { generateVideo(promptInput.text.toString().ifEmpty { "kucing berjalan" }) }
        }
        progressBar = ProgressBar(this).apply { visibility = View.GONE; setPadding(0, 32, 0, 0) }
        layout.addView(title); layout.addView(subtitle); layout.addView(promptInput); layout.addView(btnGenerate); layout.addView(progressBar)
        setContentView(layout)
    }

    private fun generateVideo(prompt: String) {
        btnGenerate.isEnabled = false; progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "AI Berpikir...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    // 1. AI MEMBACA INTERNET (Gemini)
                    val brain = GeminiBrain(BuildConfig.GEMINI_API_KEY)
                    val scene = brain.analyzePrompt(prompt)

                    runOnUiThread { 
                        Toast.makeText(this@MainActivity, "Terdeteksi: ${scene.subject} ${scene.action} (${scene.correctedText})", Toast.LENGTH_LONG).show() 
                    }

                    // 2. RENDER ANIMASI
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AI_${System.currentTimeMillis()}.mp4")
                    val animator = CreatureAnimator(W, H, scene)
                    
                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        animator.renderFrame(i.toFloat() / FPS)
                    }
                    saveToGallery(outFile); outFile
                }
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showResultDialog(result)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showDialog("GAGAL (Cek API Key/Internet)", e.toString())
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
        AlertDialog.Builder(this).setTitle("Animasi Berhasil!").setView(container)
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
