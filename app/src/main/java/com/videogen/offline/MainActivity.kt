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
import com.videogen.offline.engine.CharacterMemory
import com.videogen.offline.engine.GeminiBrain
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.TemporalRenderer
import com.videogen.offline.engine.TemporalScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val TOTAL_FRAMES = 150
    private val FPS = 30
    private val W = 720
    private val H = 720

    private var exoPlayer: ExoPlayer? = null
    private var currentScene: TemporalScene? = null
    private lateinit var promptInput: EditText
    private lateinit var btnGenerate: Button
    private lateinit var btnEvolve: Button
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
            text = "🎬 Temporal AI Animator"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Ketik prompt, AI akan melukis & mengingat karakter"
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, 32)
        }
        promptInput = EditText(this).apply {
            hint = "Contoh: naga merah terbang di gunung"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8B949E"))
            setPadding(24, 24, 24, 24)
        }
        statusText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.parseColor("#4ECDC4"))
            setPadding(0, 8, 0, 8)
        }
        btnGenerate = Button(this).apply {
            text = "✨ Generate / Muat dari Memori"
            setOnClickListener {
                generate(promptInput.text.toString().ifEmpty { "naga terbang" })
            }
        }
        btnEvolve = Button(this).apply {
            text = "🔄 Evolve (AI Lihat Sebelumnya)"
            setBackgroundColor(Color.parseColor("#6200EE"))
            setTextColor(Color.WHITE)
            setOnClickListener { evolve() }
        }
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(0, 32, 0, 0)
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(promptInput)
        layout.addView(statusText)
        layout.addView(btnGenerate)
        layout.addView(btnEvolve)
        layout.addView(progressBar)
        setContentView(layout)
    }

    private fun generate(prompt: String) {
        btnGenerate.isEnabled = false
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val memory = CharacterMemory(this@MainActivity)
                val cached = memory.load(prompt)
                val scene = if (cached != null) {
                    runOnUiThread { statusText.text = "🧠 Dimuat dari memori (karakter sama persis)" }
                    cached
                } else {
                    runOnUiThread { statusText.text = "🎨 AI mendesain keyframe baru..." }
                    val brain = GeminiBrain(BuildConfig.GEMINI_API_KEY)
                    val s = brain.analyzePrompt(prompt)
                    memory.save(s)
                    runOnUiThread { statusText.text = "✅ ${s.keyframes.size} keyframe tersimpan di memori" }
                    s
                }
                currentScene = scene
                renderScene(scene)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                showDialog("GAGAL", e.message ?: "error")
            }
        }
    }

    private fun evolve() {
        val scene = currentScene ?: return showDialog("Info", "Generate dulu!")
        btnEvolve.isEnabled = false
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                runOnUiThread { statusText.text = "🔄 AI mempelajari karakter sebelumnya..." }
                val brain = GeminiBrain(BuildConfig.GEMINI_API_KEY)
                val newPrompt = "${scene.prompt} versi evolusi berbeda pose dan gerakan"
                val newScene = brain.analyzePrompt(newPrompt)
                CharacterMemory(this@MainActivity).save(newScene)
                currentScene = newScene
                runOnUiThread { statusText.text = "✨ Evolusi selesai!" }
                renderScene(newScene)
            } catch (e: Throwable) {
                progressBar.visibility = View.GONE
                btnEvolve.isEnabled = true
                showDialog("GAGAL", e.message ?: "error")
            }
        }
    }

    private suspend fun renderScene(scene: TemporalScene) {
        val file = withContext(Dispatchers.Default) {
            val f = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Temporal_${System.currentTimeMillis()}.mp4")
            val renderer = TemporalRenderer(W, H)
            Mp4Writer(f, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                renderer.renderFrame(scene, i.toFloat() / TOTAL_FRAMES, i)
            }
            saveToGallery(f)
            f
        }
        progressBar.visibility = View.GONE
        btnGenerate.isEnabled = true
        btnEvolve.isEnabled = true
        showResult(file)
    }

    private fun showResult(file: File) {
        val pv = PlayerView(this)
        pv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        val c = LinearLayout(this).apply {
            addView(pv)
            setPadding(16, 16, 16, 16)
        }
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
        pv.player = exoPlayer
        AlertDialog.Builder(this)
            .setTitle("🎉 Animasi Berhasil!")
            .setView(c)
            .setPositiveButton("Tutup") { _, _ -> exoPlayer?.release() }
            .setOnDismissListener { exoPlayer?.release() }
            .show()
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
