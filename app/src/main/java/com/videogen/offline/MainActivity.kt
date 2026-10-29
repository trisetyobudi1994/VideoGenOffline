package com.videogen.offline

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.videogen.offline.engine.DitRunResult
import com.videogen.offline.engine.LiteRtLoader
import com.videogen.offline.engine.ModelDownloader
import com.videogen.offline.engine.TokenizerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val DIT_URL = "https://huggingface.co/litert-community/Neo_Dragon/resolve/main/dit_stage0_10x16_int4.tflite"
    private val DIT_FILE = "dit_stage0.tflite"
    private val DL_TEXT = "5. DOWNLOAD MODEL DiT (816 MB)"

    private val progressFile by lazy { File(filesDir, "progress.txt") }
    private val crashFile by lazy { File(filesDir, "last_crash.txt") }

    private fun step(msg: String) {
        try { progressFile.appendText("${System.currentTimeMillis() % 100000} $msg\n") } catch (_: Throwable) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try { crashFile.writeText(Log.getStackTraceString(e)) } catch (_: Throwable) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        val title = TextView(this).apply {
            text = "VideoGenOffline — Fase 5 (Benchmark DiT v3)"
            textSize = 18f
        }

        // 1. VAE
        val btn1 = Button(this)
        btn1.text = "1. Test Load VAE"
        btn1.setOnClickListener {
            runSafely(btn1, "1. Test Load VAE", "Error VAE") {
                val l = LiteRtLoader(this@MainActivity, "models/causal_video_vae_decoder.tflite")
                try {
                    "VAE berhasil dimuat\n\nINPUT:\n${l.getInputTensorInfo()}\nOUTPUT:\n${l.getOutputTensorInfo()}"
                } finally { l.close() }
            }
        }

        // 2. Info text encoder
        val btn2 = Button(this)
        btn2.text = "2. Info Text Encoder"
        btn2.setOnClickListener {
            runSafely(btn2, "2. Info Text Encoder", "Error Info") {
                val l = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                try {
                    "INPUT:\n${l.getInputTensorInfo()}\nOUTPUT:\n${l.getOutputTensorInfo()}"
                } finally { l.close() }
            }
        }

        // 3. Tokenizer
        val btn3 = Button(this)
        btn3.text = "3. Test Tokenizer"
        btn3.setOnClickListener {
            runSafely(btn3, "3. Test Tokenizer", "Error Tokenizer") {
                val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a cat walking")
                "IDs: ${ids.joinToString(", ")}\nTotal: ${ids.size} / mask ${mask.size}"
            }
        }

        // 4. Embedding
        val btn4 = Button(this)
        btn4.text = "4. Generate Embedding"
        btn4.setOnClickListener {
            runSafely(btn4, "4. Generate Embedding", "Error Encoder") {
                val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a cat walking")
                val l = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    val e = l.runTextEncoder(ids, mask)
                    "Berhasil! ${e.size} angka, ${SystemClock.elapsedRealtime() - t0} ms"
                } finally { l.close() }
            }
        }

        // 5. Download DiT
        val btn5 = Button(this)
        btn5.text = DL_TEXT
        btn5.setOnClickListener {
            btn5.isEnabled = false
            lifecycleScope.launch {
                try {
                    val f = ModelDownloader(this@MainActivity)
                        .downloadFile(DIT_URL, DIT_FILE) { p -> btn5.text = "Mengunduh: $p%" }
                    btn5.text = "5. Unduhan selesai (${f.length() / 1048576} MB)"
                } catch (e: Throwable) {
                    btn5.text = DL_TEXT
                    btn5.isEnabled = true
                    showDialog("Error Unduhan", e.toString())
                }
            }
        }

        // 6. Info tensor DiT
        val btn6 = Button(this)
        btn6.text = "6. Info Tensor DiT"
        btn6.setOnClickListener {
            runSafely(btn6, "6. Info Tensor DiT", "Error DiT") {
                val f = File(filesDir, DIT_FILE)
                check(f.exists() && f.length() > 0) { "Unduh model dulu (tombol 5)" }
                step("DIT-INFO: muat")
                val l = LiteRtLoader(this@MainActivity, f.absolutePath)
                try {
                    step("DIT-INFO: SELESAI")
                    "Ukuran file: ${f.length() / 1048576} MB\n\nINPUT:\n${l.getInputTensorInfo()}\nOUTPUT:\n${l.getOutputTensorInfo()}"
                } finally { l.close() }
            }
        }

        // 7. Benchmark
        val btn7 = Button(this)
        btn7.text = "7. UJI DIT (3x langkah)"
        btn7.setOnClickListener {
            runSafely(btn7, "7. UJI DIT (3x langkah)", "GAGAL", "MENGUJI DIT... (bisa beberapa menit)") {
                val f = File(filesDir, DIT_FILE)
                check(f.exists() && f.length() > 0) { "Unduh model dulu (tombol 5)" }

                step("BENCH: embedding")
                val (ids, mask) = TokenizerHelper(this@MainActivity).encode("a cat walking")
                val enc = LiteRtLoader(this@MainActivity, "models/dt5_text_encoder.tflite")
                val embedding = try { enc.runTextEncoder(ids, mask) } finally { enc.close() }

                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val totalMb = mi.totalMem / 1048576
                val availMb = mi.availMem / 1048576

                val pssBefore = Debug.getPss() / 1024
                step("BENCH: muat DiT")
                val t0 = SystemClock.elapsedRealtime()
                val dit = LiteRtLoader(this@MainActivity, f.absolutePath)
                val loadMs = SystemClock.elapsedRealtime() - t0
                step("BENCH: DiT dimuat ${loadMs}ms")

                try {
                    val times = ArrayList<Long>()
                    var last: DitRunResult? = null
                    for (r in 1..3) {
                        step("BENCH: langkah $r mulai")
                        val a = SystemClock.elapsedRealtime()
                        last = dit.runOneDitStep(embedding)
                        times.add(SystemClock.elapsedRealtime() - a)
                        step("BENCH: langkah $r selesai ${times.last()}ms")
                    }
                    val pssAfter = Debug.getPss() / 1024
                    val res = last!!

                    var mn = Float.MAX_VALUE
                    var mx = -Float.MAX_VALUE
                    var sum = 0.0
                    var bad = 0
                    for (v in res.output) {
                        if (v.isNaN() || v.isInfinite()) bad++ else {
                            if (v < mn) mn = v
                            if (v > mx) mx = v
                            sum += v
                        }
                    }
                    val perStep = times.last()
                    step("BENCH: SELESAI")

                    "BENCHMARK DIT (stage 0)\n\n" +
                        "RAM HP: total $totalMb MB, bebas $availMb MB\n" +
                        "Waktu muat model: ${loadMs / 1000.0} dtk\n" +
                        "Waktu per langkah: ${times.joinToString(" / ") { "${it / 1000.0} dtk" }}\n" +
                        "(langkah 1 biasanya paling lambat)\n" +
                        "PSS proses: $pssBefore -> $pssAfter MB (saat model aktif)\n\n" +
                        "Proyeksi 20 langkah: ${perStep * 20 / 1000} dtk per stage\n" +
                        "(jika pakai guidance, kira-kira 2x lipat)\n\n" +
                        "Output: min $mn, maks $mx, rata-rata ${sum / res.output.size}, NaN/Inf: $bad\n\n" +
                        "PERAN INPUT:\n${res.report}"
                } finally {
                    dit.close()
                }
            }
        }

        layout.addView(title)
        listOf(btn1, btn2, btn3, btn4, btn5, btn6, btn7).forEach { layout.addView(it) }
        setContentView(ScrollView(this).apply { addView(layout) })

        showPreviousRunInfo()
    }

    private fun runSafely(
        button: Button,
        normalText: String,
        errorTitle: String,
        busyText: String = "Memproses...",
        work: () -> String
    ) {
        button.text = busyText
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { work() }
                showDialog("Hasil", result)
            } catch (e: Throwable) {
                step("ERROR: $e")
                showDialog(errorTitle, e.toString())
            } finally {
                button.text = normalText
                button.isEnabled = true
            }
        }
    }

    private fun showPreviousRunInfo() {
        val sb = StringBuilder()
        if (crashFile.exists()) {
            sb.append("CRASH SEBELUMNYA:\n").append(crashFile.readText().take(2500)).append("\n\n")
            crashFile.delete()
        }
        if (progressFile.exists()) {
            val log = progressFile.readText()
            val lastLine = log.trim().lines().lastOrNull() ?: ""
            if (!lastLine.contains("SELESAI") || sb.isNotEmpty()) {
                sb.append("LANGKAH TERAKHIR SESI LALU:\n").append(log.takeLast(1200))
            }
            progressFile.delete()
        }
        if (sb.isNotEmpty()) showDialog("Info sesi sebelumnya", sb.toString())
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
