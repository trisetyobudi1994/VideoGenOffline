package com.videogen.offline

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videogen.offline.databinding.ActivityMainBinding
import com.videogen.offline.engine.Mp4Writer
import com.videogen.offline.engine.ProceduralMathEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val TOTAL_FRAMES = 90 // 3 detik
    private val FPS = 30
    private val W = 512
    private val H = 512
    
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan tombol pilih gambar
        binding.btnPickImage.visibility = View.GONE
        binding.tvSubtitle.text = "Neuro-Symbolic Engine (0 MB Model)"

        binding.btnGenerate.setOnClickListener {
            // Kita gunakan kotak dialog untuk input teks
            val input = EditText(this)
            input.hint = "Ketik teks (contoh: fire, ocean, forest)"
            AlertDialog.Builder(this)
                .setTitle("Text to Video")
                .setView(input)
                .setPositiveButton("Buat") { _, _ ->
                    val prompt = input.text.toString().ifEmpty { "ocean" }
                    generateVideo(prompt)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun generateVideo(prompt: String) {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false

        lifecycleScope.launch {
            try {
                val videoFile = withContext(Dispatchers.Default) {
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Math_${System.currentTimeMillis()}.mp4")
                    val engine = ProceduralMathEngine()
                    
                    // Ubah teks jadi parameter matematika
                    val params = engine.textToParameters(prompt)

                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        val time = (i.toFloat() / FPS) * 2f // Waktu berjalan
                        engine.generateFrame(W, H, params, time)
                    }

                    saveToGallery(outFile)
                    outFile
                }
                
                binding.loadingLayout.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
                showResultDialog(videoFile)
            } catch (e: Throwable) {
                binding.loadingLayout.visibility = View.GONE
                binding.btnGenerate.isEnabled = true
                showDialog("GAGAL", e.toString())
            }
        }
    }

    private fun showResultDialog(file: File) {
        val playerView = PlayerView(this)
        playerView.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 800
        )
        val container = android.widget.FrameLayout(this).apply {
            addView(playerView)
            setPadding(16, 16, 16, 16)
        }

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare(); playWhenReady = true
        }
        playerView.player = exoPlayer

        AlertDialog.Builder(this)
            .setTitle("Video Matematika Berhasil!")
            .setView(container)
            .setPositiveButton("Tutup") { _,_ -> exoPlayer?.release() }
            .setOnDismissListener { exoPlayer?.release() }
            .show()
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
