package com.videogen.offline.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
    private var activeGpuDelegate: GpuDelegate? = null
    private var activeNnApiDelegate: NnApiDelegate? = null

    init {
        val model = if (pathOrAsset.startsWith("/")) {
            loadModelFileFromStorage(File(pathOrAsset))
        } else {
            loadModelFileFromAssets(context, pathOrAsset)
        }

        var gpu: GpuDelegate? = null
        var nnapi: NnApiDelegate? = null
        var interp: Interpreter? = null

        // --- LAPIS 1: GPU DELEGATE (Paling Cepat) ---
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                delegateOptions.setPrecisionLossAllowed(true) // Hemat RAM (FP16)
                gpu = GpuDelegate(delegateOptions)
                
                val options = Interpreter.Options()
                options.addDelegate(gpu)
                // Proses ini yang bisa error "Failed to apply delegate"
                interp = Interpreter(model, options) 
                Log.d("LiteRtLoader", "SUKSES: Menggunakan GPU Delegate")
            }
        } catch (e: Throwable) {
            Log.w("LiteRtLoader", "GAGAL GPU: ${e.message}. Beralih ke opsi lain.")
            gpu?.close()
            gpu = null
            interp = null
        }

        // --- LAPIS 2: NNAPI / NPU (Jika GPU Gagal) ---
        if (interp == null) {
            try {
                nnapi = NnApiDelegate(NnApiDelegate.Options().apply { setAllowFp16(true) })
                val options = Interpreter.Options()
                options.addDelegate(nnapi)
                interp = Interpreter(model, options)
                Log.d("LiteRtLoader", "SUKSES: Menggunakan NNAPI Delegate")
            } catch (e: Throwable) {
                Log.w("LiteRtLoader", "GAGAL NNAPI: ${e.message}. Beralih ke CPU.")
                nnapi?.close()
                nnapi = null
                interp = null
            }
        }

        // --- LAPIS 3: CPU XNNPACK (Penyelamat Terakhir) ---
        if (interp == null) {
            val options = Interpreter.Options()
            options.setUseXNNPACK(true)
            options.setNumThreads(4)
            interp = Interpreter(model, options)
            Log.d("LiteRtLoader", "SUKSES: Menggunakan CPU XNNPACK")
        }

        activeGpuDelegate = gpu
        activeNnApiDelegate = nnapi
        interpreter = interp!!
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

    fun getInputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.inputTensorCount) {
            val t = interpreter.getInputTensor(i)
            sb.append("Input $i: ${t.name()}\n  Shape: ${t.shape().contentToString()}\n  Type: ${t.dataType()}\n\n")
        }
        return sb.toString()
    }

    fun getOutputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.outputTensorCount) {
            val t = interpreter.getOutputTensor(i)
            sb.append("Output $i: ${t.name()}\n  Shape: ${t.shape().contentToString()}\n  Type: ${t.dataType()}\n\n")
        }
        return sb.toString()
    }

    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)
        val idBuffer = ByteBuffer.allocateDirect(inputTensor0.numBytes()).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(inputTensor1.numBytes()).order(ByteOrder.nativeOrder())
        
        val ib0 = idBuffer.asIntBuffer()
        for (i in 0 until inputTensor0.numBytes() / 4) ib0.put(if (i < ids.size) ids[i] else 0)
        val ib1 = maskBuffer.asIntBuffer()
        for (i in 0 until inputTensor1.numBytes() / 4) ib1.put(if (i < mask.size) mask[i] else 0)

        val outSize = interpreter.getOutputTensor(0).shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())
        
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(idBuffer, maskBuffer), mapOf(0 to outputBuffer))
        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun runContextAdapter(embedding: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(embedding.size * 4).order(ByteOrder.nativeOrder())
        inputBuffer.asFloatBuffer().put(embedding)
        val outSize = interpreter.getOutputTensor(0).shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())
        
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun runDitStep(latent: FloatArray, timestep: Float, context: FloatArray): FloatArray {
        val inputBuffers = mutableListOf<ByteBuffer>()
        val inputCount = interpreter.inputTensorCount

        for (i in 0 until inputCount) {
            val tensor = interpreter.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            val requiredLen = tensor.numBytes() / 4
            val fb = buffer.asFloatBuffer()

            when (i) {
                0 -> { // Latent Noise
                    for (j in 0 until requiredLen) fb.put(if (j < latent.size) latent[j] else 0f)
                }
                1 -> { // Mask / Latent Condition
                    for (j in 0 until requiredLen) fb.put(1.0f) 
                }
                2 -> { // Context (dari Adapter)
                    for (j in 0 until requiredLen) fb.put(if (j < context.size) context[j] else 0f)
                }
                3 -> { // Biasanya mask [1, 128], isi 1.0f
                    for (j in 0 until requiredLen) fb.put(1.0f)
                }
                4 -> { // Biasanya positional/embeddings [1, 2048]
                    for (j in 0 until requiredLen) fb.put(0.0f)
                }
                5 -> { // Timestep [1]
                    fb.put(timestep)
                }
                else -> {
                    for (j in 0 until requiredLen) fb.put(1.0f)
                }
            }
            inputBuffers.add(buffer)
        }

        val outSize = interpreter.getOutputTensor(0).shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(inputBuffers.toTypedArray(), mapOf(0 to outputBuffer))
        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun decodeLatentToImage(latent: FloatArray): Bitmap {
        val inputBuffer = ByteBuffer.allocateDirect(latent.size * 4).order(ByteOrder.nativeOrder())
        inputBuffer.asFloatBuffer().put(latent)

        val outputTensor = interpreter.getOutputTensor(0)
        val outShape = outputTensor.shape()
        val outSize = outShape.fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val floatPixels = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(floatPixels)

        // Asumsi output adalah [1, 3, H, W]
        val height = outShape[2]
        val width = outShape[3]

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in 0 until height * width) {
            val r = ((floatPixels[i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((floatPixels[i + height * width] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((floatPixels[i + 2 * height * width] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun close() {
        interpreter.close()
        activeGpuDelegate?.close()
        activeNnApiDelegate?.close()
    }
}
