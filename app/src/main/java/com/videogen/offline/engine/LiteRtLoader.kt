package com.videogen.offline.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, pathOrAsset: String) {

    private val interpreter: Interpreter
    private var activeGpuDelegate: GpuDelegate? = null

    init {
        val model = if (pathOrAsset.startsWith("/")) {
            loadModelFileFromStorage(File(pathOrAsset))
        } else {
            loadModelFileFromAssets(context, pathOrAsset)
        }

        var gpu: GpuDelegate? = null
        var interp: Interpreter? = null

        // Coba GPU dulu
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                delegateOptions.setPrecisionLossAllowed(true)
                gpu = GpuDelegate(delegateOptions)
                val options = Interpreter.Options()
                options.addDelegate(gpu)
                interp = Interpreter(model, options)
            }
        } catch (e: Throwable) {
            gpu?.close(); gpu = null; interp = null
        }

        // Fallback ke CPU Extreme (Hemat RAM)
        if (interp == null) {
            val options = Interpreter.Options()
            options.setNumThreads(1) // 1 Thread agar RAM tidak meledak
            options.setUseXNNPACK(false)
            interp = Interpreter(model, options)
        }

        activeGpuDelegate = gpu
        interpreter = interp
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
            sb.append("Input $i: ${t.name()}\nShape: ${t.shape().contentToString()}\nType: ${t.dataType()}\n\n")
        }
        return sb.toString()
    }

    // FUNGSI FASE 4
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

    // FUNGSI FASE 5 (DiT)
    fun runDitStep(latent: FloatArray, timestep: Float, context: FloatArray): FloatArray {
        val inputBuffers = mutableListOf<ByteBuffer>()
        for (i in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            val requiredLen = tensor.numBytes() / 4
            val fb = buffer.asFloatBuffer()

            when (i) {
                0 -> for (j in 0 until requiredLen) fb.put(if (j < latent.size) latent[j] else 0f)
                1 -> for (j in 0 until requiredLen) fb.put(1.0f)
                2 -> for (j in 0 until requiredLen) fb.put(if (j < context.size) context[j] else 0f)
                3 -> for (j in 0 until requiredLen) fb.put(1.0f)
                4 -> for (j in 0 until requiredLen) fb.put(0.0f)
                5 -> fb.put(timestep)
                else -> for (j in 0 until requiredLen) fb.put(1.0f)
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

    // FUNGSI VAE DECODE (Menjadi Frame Video)
    fun decodeLatentToVideoFrames(latent: FloatArray): List<Bitmap> {
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

        val frames = mutableListOf<Bitmap>()
        val timeDim = outShape[2]
        val height = outShape[3]
        val width = outShape[4]

        for (t in 0 until timeDim) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)

            for (i in 0 until height * width) {
                val r = ((floatPixels[i + (t * 3 * height * width)] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                val g = ((floatPixels[i + (t * 3 * height * width) + (height * width)] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                val b = ((floatPixels[i + (t * 3 * height * width) + (2 * height * width)] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            frames.add(bitmap)
        }
        return frames
    }

    fun close() {
        interpreter.close()
        activeGpuDelegate?.close()
    }
}
