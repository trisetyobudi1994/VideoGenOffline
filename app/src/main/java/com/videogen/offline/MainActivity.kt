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
import com.videogen.offline.engine.CinematicAnimator
import com.videogen.offline.engine.ImageFetcher
import com.videogen.offline.engine.Mp4Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TOTAL_FRAMES = 150
    private val FPS = 30
    private val W = 768
    private val H = 768

    private var exoPlayer: ExoPlayer? = null
    private lateinit var promptInput: EditText
    private lateinit var btnGenerate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(Color.parseColor("#0D1117"))
        }
        val title = TextView(this).apply {
            text = "🎬 Cinematic AI Generator"
            textSize = 24f; setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0,0,0,8)
        }
        val subtitle = TextView(this).apply {
            text = "Gambar AI Nyata + Animasi Sinematik + Efek Cuaca"
            textSize = 12f; setTextColor(Color.parseColor("#8B949E")); setPadding(0,0,0,32)
        }
        promptInput = EditText(this).apply {
            hint = "Contoh: naga merah terbang di gunung saat sunset"
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#8B949E"))
            setPadding(24,24,24,24)
        }
        statusText = TextView(this).apply {
            text = ""; textSize = 12f; setTextColor(Color.parseColor("#4ECDC4"))
            setPadding(0,8,0,8)
        }
        btnGenerate = Button(this).apply {
            text = "✨ Generate Cinematic Video"
            setOnClickListener {
                generate(promptInput.text.toString().ifEmpty { "cinematic mountain landscape at sunset" })
            }
        }
        progressBar = ProgressBar(this).apply { visibility = View.GONE; setPadding(0,32,0,0) }

        layout.addView(title); layout.addView(subtitle)
        layout.addView(promptInput); layout.addView(statusText)
        layout.addView(btnGenerate); layout.addView(progressBar)
        setContentView(layout)
    }

    private fun generate(prompt: String) {
        btnGenerate.isEnabled = false
        progressBar.visibility = View.VISIBLE
        statusText.text = "🎨 AI sedang melukis gambar..."

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    // 1. Ambil gambar AI sungguhan
                    val image = ImageFetcher().fetchImage(prompt)
                    runOnUiThread { statusText.text = "🎬 Animasi sinematik..." }

                    // 2. Tentukan cuaca & waktu dari prompt
                    val lp = prompt.lowercase()
                    val weather = when {
                        lp.contains("rain") || lp.contains("hujan") -> "rain"
                        lp.contains("snow") || lp.contains("salju") -> "snow"
                        lp.contains("fire") || lp.contains("api") -> "fire"
                        lp.contains("star") || lp.contains("bintang") || lp.contains("malam") -> "stars"
                        else -> "none"
                    }
                    val tod = when {
                        lp.contains("night") || lp.contains("malam") -> "night"
                        lp.contains("sunset") || lp.contains("senja") -> "sunset"
                        lp.contains("dawn") || lp.contains("subuh") -> "dawn"
                        else -> "day"
                    }

                    // 3. Render sinematik
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                        "Cinematic_${System.currentTimeMillis()}.mp4")
                    val animator = CinematicAnimator(image, W, H, weather, tod)
                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        animator.renderFrame(i, TOTAL_FRAMES)
                    }
                    saveToGallery(outFile)
                    outFile
                }
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                statusText.text = "✅ Video sinematik selesai!"
                showResult(result)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                statusText.text = "❌ ${e.message}"
                showDialog("GAGAL", e.message ?: "error")
            }
        }
    }

    private fun showResult(file: File) {
        val pv = PlayerView(this)
        pv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        val c = LinearLayout(this).apply { addView(pv); setPadding(16,16,16,16) }
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare(); playWhenReady = true; repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
        pv.player = exoPlayer
        AlertDialog.Builder(this).setTitle("🎉 Video Cinematic Berhasil!")
            .setView(c)
            .setPositiveButton("Tutup") { _,_ -> exoPlayer?.release() }
            .setOnDismissListener { exoPlayer?.release() }.show()
    }

    private fun saveToGallery(file: File) {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoGen")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
        contentResolver.openOutputStream(uri!!)?.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    private fun showDialog(t: String, m: String) {
        AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK", null).show()
    }
}
