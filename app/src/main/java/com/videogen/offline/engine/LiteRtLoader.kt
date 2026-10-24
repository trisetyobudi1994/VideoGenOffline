package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, assetPath: String) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, assetPath)
        interpreter = Interpreter(model)
    }

    private fun loadModelFile(context: Context, path: String): MappedByteBuffer {
        val fd = context.assets.openFd(path)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun getInputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(i)
            sb.append("Input $i: ${tensor.name()}\n  Shape: ${tensor.shape().contentToString()}\n  Type: ${tensor.dataType()}\n\n")
        }
        return sb.toString()
    }

    fun getOutputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            sb.append("Output $i: ${tensor.name()}\n  Shape: ${tensor.shape().contentToString()}\n  Type: ${tensor.dataType()}\n\n")
        }
        return sb.toString()
    }

    // FUNGSI BARU: Fase 4 (Menjalankan Text Encoder secara Dinamis)
    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)

        // 1. Alokasikan buffer sesuai ukuran ASLI yang diminta model
        val idBuffer = ByteBuffer.allocateDirect(inputTensor0.numBytes()).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(inputTensor1.numBytes()).order(ByteOrder.nativeOrder())

        // 2. Isi buffer ID (Cek apakah model minta INT32 atau FLOAT32)
        if (inputTensor0.dataType() == DataType.INT32) {
            val ib = idBuffer.asIntBuffer()
            for (i in 0 until ib.capacity()) {
                ib.put(if (i < ids.size) ids[i] else 0) // Isi 0 jika array kita lebih pendek
            }
        } else if (inputTensor0.dataType() == DataType.FLOAT32) {
            val fb = idBuffer.asFloatBuffer()
            for (i in 0 until fb.capacity()) {
                fb.put(if (i < ids.size) ids[i].toFloat() else 0f)
            }
        }

        // 3. Isi buffer Mask
        if (inputTensor1.dataType() == DataType.INT32) {
            val ib = maskBuffer.asIntBuffer()
            for (i in 0 until ib.capacity()) {
                ib.put(if (i < mask.size) mask[i] else 0)
            }
        } else if (inputTensor1.dataType() == DataType.FLOAT32) {
            val fb = maskBuffer.asFloatBuffer()
            for (i in 0 until fb.capacity()) {
                fb.put(if (i < mask.size) mask[i].toFloat() else 0f)
            }
        }

        // 4. Siapkan Output
        val outputTensor = interpreter.getOutputTensor(0)
        val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())

        // 5. Jalankan
        val inputs = arrayOf<Any>(idBuffer, maskBuffer)
        val outputs = mapOf(0 to outputBuffer)
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        // 6. Ambil hasilnya
        val floatBuffer = outputBuffer.asFloatBuffer()
        val result = FloatArray(floatBuffer.capacity())
        floatBuffer.get(result)
        
        return result
    }

    fun close() = interpreter.close()
}
