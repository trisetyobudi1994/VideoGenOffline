package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loader untuk satu model .tflite di folder assets.
 * Memakai TensorFlow Lite Interpreter (stabil & terdokumentasi).
 */
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

    fun close() = interpreter.close()
}
