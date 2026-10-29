package com.videogen.offline.engine

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, pathOrAsset: String) {

    private val interpreter: Interpreter
    private val gpuDelegate: GpuDelegate?
    private val nnApiDelegate: NnApiDelegate?

    init {
        val model = when {
            pathOrAsset.startsWith("http") ->
                throw IllegalArgumentException("Gunakan ModelDownloader untuk URL")
            pathOrAsset.startsWith("/") -> {
                val f = File(pathOrAsset)
                if (!f.exists()) throw IllegalArgumentException("File tidak ditemukan: $pathOrAsset")
                loadModelFileFromStorage(f)
            }
            else -> loadModelFileFromAssets(context, pathOrAsset)
        }

        var gpu: GpuDelegate? = null
        var nnapi: NnApiDelegate? = null
        val options = Interpreter.Options()

        // Coba gunakan GPU Delegate
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                delegateOptions.setPrecisionLossAllowed(true) // Izinkan FP16 untuk performa
                gpu = GpuDelegate(delegateOptions)
                options.addDelegate(gpu)
                Log.d("LiteRtLoader", "Menggunakan GPU Delegate (FP16 Mode)")
            }
        } catch (e: Exception) {
            Log.w("LiteRtLoader", "GPU Delegate gagal, beralih ke opsi lain: ${e.message}")
            gpu?.close()
            gpu = null
        }

        // Jika GPU tidak tersedia atau gagal, coba NNAPI
        if (gpu == null) {
            try {
                nnapi = NnApiDelegate(NnApiDelegate.Options().apply {
                    setAllowFp16(true)
                })
                options.addDelegate(nnapi)
                Log.d("LiteRtLoader", "Menggunakan NNAPI Delegate (NPU Mode)")
            } catch (e: Exception) {
                Log.w("LiteRtLoader", "NNAPI gagal, beralih ke CPU: ${e.message}")
                nnapi?.close()
                nnapi = null
            }
        }

        // Fallback terakhir: Optimasi CPU dengan XNNPACK
        if (gpu == null && nnapi == null) {
            options.setUseXNNPACK(true)
            options.setNumThreads(4)
            Log.d("LiteRtLoader", "Menggunakan CPU XNNPACK (4 Threads)")
        }

        gpuDelegate = gpu
        nnApiDelegate = nnapi

        // Membuat interpreter dengan opsi yang sudah dipilih
        interpreter = Interpreter(model, options)
    }

    private fun loadModelFileFromAssets(context: Context, path: String): MappedByteBuffer {
        context.assets.openFd(path).use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    private fun loadModelFileFromStorage(file: File): MappedByteBuffer {
        FileInputStream(file).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }

    // ... (fungsi getInputTensorInfo, getOutputTensorInfo, runTextEncoder, runContextAdapter, runDitStep tetap sama seperti sebelumnya)
    // Anda bisa menyalinnya dari file yang sudah Anda miliki.

    fun close() {
        interpreter.close()
        gpuDelegate?.close()
        nnApiDelegate?.close()
    }
}
