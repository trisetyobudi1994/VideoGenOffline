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
import com.videogen.offline.engine.ParticleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val TOTAL_FRAMES = 120 // 4 detik @ 30FPS
    private val FPS = 30
    private val W = 512
    private val H = 512
    
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sembunyikan pemilih gambar, ubah teks subtitle
        binding.btnPickImage.visibility = View.GONE
        binding.tvSubtitle.text = "Generative Particle Engine"

        binding.btnGenerate.setOnClickListener {
            val input = EditText(this)
            input.hint = "Ketik ide video (misal: fire, water, galaxy)"
            AlertDialog.Builder(this)
                .setTitle("Text to Particle Video")
                .setView(input)
                .setPositiveButton("Buat Video") { _, _ ->
                    val prompt = input.text.toString().ifEmpty { "fire" }
                    generateVideo(prompt)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun generateVideo(prompt: String) {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.btnGenerate.isEnabled = false
        binding.tvLoadingText.text = "Merender Partikel..."

        lifecycleScope.launch {
            try {
                val videoFile = withContext(Dispatchers.Default) {
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Particle_${System.currentTimeMillis()}.mp4")
                    
                    // Inisialisasi Mesin Partikel
                    val engine = ParticleEngine(W, H, prompt)

                    // Render Frame-by-Frame dan simpan ke MP4
                    Mp4Writer(outFile, W, H, FPS).encode(TOTAL_FRAMES) { i ->
                        // Waktu bergerak (semakin lama, partikel bergerak makin jauh)
                        val time = (i.toFloat() / FPS) * 2.0f 
                        engine.renderFrame(time)
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
            .setTitle("Video Berhasil Dibuat!")
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
