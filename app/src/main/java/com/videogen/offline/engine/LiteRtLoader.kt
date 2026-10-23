package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
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
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    // Fungsi baru: Membaca struktur input model
    fun getInputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(i)
            sb.append("Input $i: ${tensor.name()}\n")
            sb.append("  Shape: ${tensor.shape().contentToString()}\n")
            sb.append("  Type: ${tensor.dataType()}\n\n")
        }
        return sb.toString()
    }

    // Fungsi baru: Membaca struktur output model
    fun getOutputTensorInfo(): String {
        val sb = StringBuilder()
        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            sb.append("Output $i: ${tensor.name()}\n")
            sb.append("  Shape: ${tensor.shape().contentToString()}\n")
            sb.append("  Type: ${tensor.dataType()}\n\n")
        }
        return sb.toString()
    }

    fun close() = interpreter.close()
}
