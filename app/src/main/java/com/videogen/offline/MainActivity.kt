package com.videogen.offline

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.videogen.offline.engine.AutoregressiveGenerator
import com.videogen.offline.engine.MotionPredictor
import com.videogen.offline.engine.Mp4Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private val TOTAL_FRAMES = 60
    private val FPS = 30
    private val UPSCALE_WIDTH = 720 // Resolusi HD

    private var selectedPhoto: Bitmap? = null
    private lateinit var imagePreview: ImageView
    private var exoPlayer: ExoPlayer? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedPhoto = loadScaled(uri)
            imagePreview.setImageBitmap(selectedPhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply { text = "Autoregressive AI Video"; textSize = 22f }
        imagePreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
        }
        val btnPick = Button(this).apply {
            text = "1. Pilih Foto"
            setOnClickListener { pickImage.launch("image/*") }
        }
        val btnGenerate = Button(this).apply {
            text = "2. BUAT VIDEO (Frame-by-Frame)"
            setOnClickListener { generateVideo() }
        }

        layout.addView(title)
        layout.addView(imagePreview)
        layout.addView(btnPick)
        layout.addView(btnGenerate)
        setContentView(layout)
    }

    private fun generateVideo() {
        val base = selectedPhoto ?: return showDialog("Error", "Pilih foto dulu!")
        val progress = ProgressBar(this).apply { visibility = View.VISIBLE }
        (window.decorView as android.view.ViewGroup).addView(progress)

        lifecycleScope.launch {
            try {
                val videoFile = withContext(Dispatchers.Default) {
                    val outFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AI_${System.currentTimeMillis()}.mp4")
                    val predictor = MotionPredictor(this@MainActivity, "models/flownet.tflite")
                    val generator = AutoregressiveGenerator(predictor)

                    val framesDir = File(cacheDir, "frames")
                    if (framesDir.exists()) framesDir.deleteRecursively()
                    framesDir.mkdirs()

                    var currentFrame = base
                    val frameFiles = mutableListOf<File>()

                    // LOOP FRAME-BY-FRAME (Simpan ke disk untuk hemat RAM)
                    for (i in 0 until TOTAL_FRAMES) {
                        runOnUiThread { progress.progress = ((i.toFloat() / TOTAL_FRAMES) * 100).toInt() }
                        currentFrame = generator.generateNextFrame(currentFrame, i.toFloat() / TOTAL_FRAMES)
                        
                        val frameFile = File(framesDir, "frame_$i.jpg")
                        FileOutputStream(frameFile).use { out -> currentFrame.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                        frameFiles.add(frameFile)
                    }

                    predictor.close()

                    // GABUNG & UPSCALE KE MP4
                    runOnUiThread { progress.progress = 90 }
                    val firstFrame = BitmapFactory.decodeFile(frameFiles.first().absolutePath)
                    val upscaledHeight = (firstFrame.height * (UPSCALE_WIDTH.toFloat() / firstFrame.width)).toInt()

                    Mp4Writer(outFile, UPSCALE_WIDTH, upscaledHeight, FPS).encode(TOTAL_FRAMES) { i ->
                        val frame = BitmapFactory.decodeFile(frameFiles[i].absolutePath)
                        generator.upscale(frame, UPSCALE_WIDTH)
                    }

                    framesDir.deleteRecursively()
                    saveToGallery(outFile)
                    outFile
                }
                progress.visibility = View.GONE
                showResultDialog(videoFile)
            } catch (e: Throwable) {
                progress.visibility = View.GONE
                showDialog("GAGAL", e.toString())
            }
        }
    }

    private fun showResultDialog(file: File) {
        val dialogView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val playerView = PlayerView(this)
        playerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800)
        dialogView.addView(playerView)
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare(); playWhenReady = true
        }
        playerView.player = exoPlayer
        AlertDialog.Builder(this).setTitle("Video Berhasil! (Tersimpan di Galeri)").setView(dialogView)
            .setPositiveButton("Tutup") { _,_ -> exoPlayer?.release() }.show()
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

    private fun loadScaled(uri: Uri): Bitmap {
        val opts = BitmapFactory.Options()
        val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }!!
        val scale = minOf(1f, 512f / maxOf(raw.width, raw.height))
        return Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
