package com.videogen.offline.engine

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class DepthEstimator(context: Context, modelPath: String) {
    private val interpreter: Interpreter

    init {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val model = inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        interpreter = Interpreter(model)
    }

    fun estimateDepth(image: Bitmap): FloatArray {
        // MiDaS meminta input 256x256
        val resized = Bitmap.createScaledBitmap(image, 256, 256, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 3 * 4).order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(256 * 256)
        resized.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        inputBuffer.rewind()

        // Output berupa matriks kedalaman 256x256
        val outputBuffer = ByteBuffer.allocateDirect(1 * 256 * 256 * 4).order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        
        outputBuffer.rewind()
        val depthMap = FloatArray(256 * 256)
        outputBuffer.asFloatBuffer().get(depthMap)
        
        return depthMap
    }

    fun close() = interpreter.close()
}
