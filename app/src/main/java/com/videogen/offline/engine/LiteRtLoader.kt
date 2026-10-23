package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, assetPath: String) : AutoCloseable {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, assetPath)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)
    }

    fun getInputTensorInfo(): String {
        val t = interpreter.getInputTensor(0)
        return "shape=${t.shape().contentToString()} type=${t.dataType()}"
    }

    fun getOutputTensorInfo(): String {
        val t = interpreter.getOutputTensor(0)
        return "shape=${t.shape().contentToString()} type=${t.dataType()}"
    }

    override fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetPath)
        FileInputStream(fd.fileDescriptor).use { input ->
            val channel = input.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }
}
