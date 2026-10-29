package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, pathOrAsset: String) {

    private val interpreter: Interpreter

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

        val options = Interpreter.Options()
        options.setNumThreads(4)
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

    // FUNGSI 1: Text Encoder (Output 524.288 angka)
    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)

        val idBuffer = ByteBuffer.allocateDirect(inputTensor0.numBytes()).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(inputTensor1.numBytes()).order(ByteOrder.nativeOrder())

        val requiredLen0 = inputTensor0.numBytes() / 4
        val requiredLen1 = inputTensor1.numBytes() / 4

        // PAKSA INTEGER UNTUK MENGHINDARI ERROR EMBEDDING_LOOKUP
        val ib0 = idBuffer.asIntBuffer()
        for (i in 0 until requiredLen0) ib0.put(if (i < ids.size) ids[i] else 0)

        val ib1 = maskBuffer.asIntBuffer()
        for (i in 0 until requiredLen1) ib1.put(if (i < mask.size) mask[i] else 0)

        val outputTensor = interpreter.getOutputTensor(0)
        val outSize = outputTensor.shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        val inputs = arrayOf<Any>(idBuffer, maskBuffer)
        val outputs = mapOf(0 to outputBuffer)
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    // FUNGSI 2: Context Adapter (Mengubah 4096 -> 1536)
    fun runContextAdapter(embedding: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(embedding.size * 4).order(ByteOrder.nativeOrder())
        inputBuffer.asFloatBuffer().put(embedding)

        val outputTensor = interpreter.getOutputTensor(0)
        val outSize = outputTensor.shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    // FUNGSI 3: Uji 1 Langkah DiT (BENCHMARK)
    fun runDitStep(context: FloatArray): FloatArray {
        val inputBuffers = mutableListOf<ByteBuffer>()
        val inputCount = interpreter.inputTensorCount

        for (i in 0 until inputCount) {
            val tensor = interpreter.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            val requiredLen = tensor.numBytes() / 4
            val fb = buffer.asFloatBuffer()

            // Isi data berdasarkan urutan Input dari Screenshot Info Tensor:
            if (i == 0 || i == 1) {
                // Input 0 & 1: Latent (Noise). Shape [1, 16, 1, 10, 16] = 2560 floats
                for (j in 0 until requiredLen) fb.put((Math.random().toFloat() * 2f) - 1f) // Isi noise acak
            } else if (i == 2) {
                // Input 2: Context (dari Adapter). Shape [1, 128, 1536] = 196.608 floats
                for (j in 0 until requiredLen) fb.put(if (j < context.size) context[j] else 0f)
            } else {
                // Input 3, 4, 5: Mask, dll. Isi dengan 1.0f atau 0.0f
                for (j in 0 until requiredLen) fb.put(1.0f)
            }
            inputBuffers.add(buffer)
        }

        val outputTensor = interpreter.getOutputTensor(0)
        val outSize = outputTensor.shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(inputBuffers.toTypedArray(), mapOf(0 to outputBuffer))

        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun close() = interpreter.close()
}
