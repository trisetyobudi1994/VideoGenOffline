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
import com.videogen.offline.engine.GeminiBrain
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.UniversalRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TOTAL_FRAMES = 150; private val FPS = 30
    private val W = 720; private val H = 720
    private var exoPlayer: ExoPlayer? = null
    private lateinit var promptInput: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        val title = TextView(this).apply {
            text = "🎨 Universal AI Painter"
            textSize = 26f; setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Gambar APA SAJA — Naga, Robot, Alien, Apapun!"
            textSize = 12f; setTextColor(Color.parseColor("#8B949E")); setPadding(0, 0, 0, 32)
        }
        promptInput = EditText(this).apply {
            hint = "Contoh: naga merah terbang di gunung saat hujan"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#8B949E"))
            setPadding(24, 24, 24, 24)
        }
        btnGenerate = Button(this).apply {
            text = "✨ Gambar Apapun dari Teks"
            setOnClickListener { generate(promptInput.text.toString().ifEmpty { "naga terbang di langit malam" }) }
        }
        progressBar = ProgressBar(this).apply { visibility = View.GONE; setPadding(0, 32, 0, 0) }
        layout.addView(title); layout.addView(subtitle); layout.addView(promptInput)
        layout.addView(btnGenerate); layout.addView(progressBar)
        setContentView(layout)
    }

    private fun generate(prompt: String) {
        btnGenerate.isEnabled = false; progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "AI sedang melukis...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val brain = GeminiBrain(BuildConfig.GEMINI_API_KEY)
                    val scene = brain.analyzePrompt(prompt)

                    runOnUiThread {
                        Toast.makeText(this@MainActivity,
                            "🎬 ${scene.shapes.size} bentuk digambar!", Toast.LENGTH_LONG).show()
                    }

                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                        "Universal_${System.currentTimeMillis()}.mp4")
                    val renderer = UniversalRenderer(W, H)
                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        renderer.renderFrame(scene, i)
                    }
                    saveToGallery(outFile); outFile
                }
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showResult(result)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE; btnGenerate.isEnabled = true
                showDialog("GAGAL", "Error: ${e.message}")
            }
        }
    }

    private fun showResult(file: File) {
        val playerView = PlayerView(this)
        playerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        val container = LinearLayout(this).apply { addView(playerView); setPadding(16,16,16,16) }
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare(); playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
        playerView.player = exoPlayer
        AlertDialog.Builder(this).setTitle("🎉 Berhasil!")
            .setView(container)
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
