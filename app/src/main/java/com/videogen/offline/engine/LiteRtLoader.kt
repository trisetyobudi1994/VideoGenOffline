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

data class DitRunResult(val output: FloatArray, val report: String)

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

    // Fase 4 (logika yang sudah terbukti jalan di HP)
    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)

        val idBuffer = ByteBuffer.allocateDirect(inputTensor0.numBytes()).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(inputTensor1.numBytes()).order(ByteOrder.nativeOrder())

        val requiredLen0 = inputTensor0.numBytes() / 4
        val requiredLen1 = inputTensor1.numBytes() / 4

        if (inputTensor0.dataType() == DataType.INT32) {
            val ib = idBuffer.asIntBuffer()
            for (i in 0 until requiredLen0) ib.put(if (i < ids.size) ids[i] else 0)
        } else {
            val fb = idBuffer.asFloatBuffer()
            for (i in 0 until requiredLen0) fb.put(if (i < ids.size) ids[i].toFloat() else 0f)
        }

        if (inputTensor1.dataType() == DataType.INT32) {
            val ib = maskBuffer.asIntBuffer()
            for (i in 0 until requiredLen1) ib.put(if (i < mask.size) mask[i] else 0)
        } else {
            val fb = maskBuffer.asFloatBuffer()
            for (i in 0 until requiredLen1) fb.put(if (i < mask.size) mask[i].toFloat() else 0f)
        }

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

    // Fase 5: satu langkah DiT (untuk benchmark). Peran input ditebak dari ukuran
    // tensor, dan hasil tebakan dilaporkan supaya bisa diperiksa.
    fun runOneDitStep(embedding: FloatArray): DitRunResult {
        val n = interpreter.inputTensorCount
        val inputs = arrayOfNulls<Any>(n)
        val report = StringBuilder()
        val rnd = java.util.Random(42)

        for (i in 0 until n) {
            val t = interpreter.getInputTensor(i)
            val buf = ByteBuffer.allocateDirect(t.numBytes()).order(ByteOrder.nativeOrder())
            val count = t.numElements()
            var role = ""

            when (t.dataType()) {
                DataType.FLOAT32 -> {
                    val fb = buf.asFloatBuffer()
                    if (count == embedding.size) {
                        role = "context (embedding teks)"
                        for (j in 0 until count) fb.put(embedding[j])
                    } else if (count <= 4) {
                        role = "timestep (diisi 500)"
                        for (j in 0 until count) fb.put(if (j == 0) 500f else 0f)
                    } else {
                        role = "latent (noise acak)"
                        for (j in 0 until count) fb.put(rnd.nextGaussian().toFloat())
                    }
                }
                DataType.INT32 -> {
                    role = "int32 (diisi 500, sisanya 0)"
                    val ib = buf.asIntBuffer()
                    for (j in 0 until count) ib.put(if (j == 0) 500 else 0)
                }
                DataType.INT64 -> {
                    role = "int64 (diisi 500, sisanya 0)"
                    val lb = buf.asLongBuffer()
                    for (j in 0 until count) lb.put(if (j == 0) 500L else 0L)
                }
                else -> role = "tipe ${t.dataType()} dibiarkan 0"
            }

            buf.rewind()
            inputs[i] = buf
            report.append("Input $i ${t.shape().contentToString()} ${t.dataType()} -> $role\n")
        }

        val out = interpreter.getOutputTensor(0)
        if (out.dataType() != DataType.FLOAT32) {
            throw IllegalStateException("Output 0 bertipe ${out.dataType()}, bukan FLOAT32")
        }
        val outBuf = ByteBuffer.allocateDirect(out.numBytes()).order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to outBuf))

        outBuf.rewind()
        val result = FloatArray(out.numElements())
        outBuf.asFloatBuffer().get(result)
        report.append("Output 0 ${out.shape().contentToString()}\n")
        return DitRunResult(result, report.toString())
    }

    fun close() = interpreter.close()
}
