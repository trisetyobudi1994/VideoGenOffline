package com.videogen.offline.engine

import android.content.Context
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

    // Fungsi untuk Fase 2 (Info Tensor)
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

    // FUNGSI BARU: Fase 4 (Menjalankan Text Encoder)
    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        // 1. Siapkan buffer untuk input IDs (1, 128) tipe INT32
        val idBuffer = ByteBuffer.allocateDirect(1 * 128 * 4).order(ByteOrder.nativeOrder())
        idBuffer.asIntBuffer().put(ids)

        // 2. Siapkan buffer untuk input Mask (1, 128) tipe INT32
        val maskBuffer = ByteBuffer.allocateDirect(1 * 128 * 4).order(ByteOrder.nativeOrder())
        maskBuffer.asIntBuffer().put(mask)

        // 3. Siapkan buffer untuk output (1, 128, 4096) tipe FLOAT32
        val outputSize = 1 * 128 * 4096
        val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())

        // 4. PERBAIKAN: Input pakai Array, Output pakai Map
        val inputs = arrayOf<Any>(idBuffer, maskBuffer)
        val outputs = mapOf(0 to outputBuffer)

        // 5. Jalankan inferensi
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        // 6. Ambil hasilnya dalam bentuk FloatArray
        val floatBuffer = outputBuffer.asFloatBuffer()
        val result = FloatArray(outputSize)
        floatBuffer.get(result)
        
        return result
    }

    fun close() = interpreter.close()
}
