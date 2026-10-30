package com.videogen.offline.engine

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// values: 0..1, makin besar = makin dekat ke kamera
class DepthMap(val width: Int, val height: Int, val values: FloatArray, val rawMin: Float, val rawMax: Float)

class DepthEstimator(context: Context, assetPath: String) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.openFd(assetPath).use { fd ->
            FileInputStream(fd.fileDescriptor).use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(model, options)
    }

    fun inputInfo(): String {
        val t = interpreter.getInputTensor(0)
        val o = interpreter.getOutputTensor(0)
        return "Input ${t.shape().contentToString()} ${t.dataType()} | Output ${o.shape().contentToString()} ${o.dataType()}"
    }

    fun estimateDepth(src: Bitmap): DepthMap {
        val inT = interpreter.getInputTensor(0)
        require(inT.dataType() == DataType.FLOAT32) { "Input bukan FLOAT32: ${inT.dataType()}" }
        val s = inT.shape()
        require(s.size == 4) { "Bentuk input tak terduga: ${s.contentToString()}" }
        val nhwc = s[3] == 3
        val nchw = s[1] == 3
        require(nhwc || nchw) { "Bentuk input tak terduga: ${s.contentToString()}" }

        val h = if (nhwc) s[1] else s[2]
        val w = if (nhwc) s[2] else s[3]

        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val inBuf = ByteBuffer.allocateDirect(inT.numBytes()).order(ByteOrder.nativeOrder())
        val fb = inBuf.asFloatBuffer()
        if (nhwc) {
            for (p in px) {
                fb.put((((p shr 16) and 0xFF) / 255f - mean[0]) / std[0])
                fb.put((((p shr 8) and 0xFF) / 255f - mean[1]) / std[1])
                fb.put(((p and 0xFF) / 255f - mean[2]) / std[2])
            }
        } else {
            for (c in 0 until 3) {
                val shift = when (c) { 0 -> 16; 1 -> 8; else -> 0 }
                for (p in px) fb.put((((p shr shift) and 0xFF) / 255f - mean[c]) / std[c])
            }
        }
        inBuf.rewind()

        val outT = interpreter.getOutputTensor(0)
        require(outT.dataType() == DataType.FLOAT32) { "Output bukan FLOAT32: ${outT.dataType()}" }
        val outBuf = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())

        interpreter.run(inBuf, outBuf)

        outBuf.rewind()
        val n = outT.numElements()
        val raw = FloatArray(n)
        outBuf.asFloatBuffer().get(raw)

        val dims = outT.shape().filter { it > 1 }
        require(dims.size >= 2) { "Bentuk output tak terduga: ${outT.shape().contentToString()}" }
        val oh = dims[dims.size - 2]
        val ow = dims[dims.size - 1]
        require(oh * ow == n) { "Output bukan peta 2D: ${outT.shape().contentToString()}" }

        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in raw) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val range = if (mx - mn < 1e-6f) 1f else (mx - mn)
        val norm = FloatArray(n) { (raw[it] - mn) / range }

        return DepthMap(ow, oh, norm, mn, mx)
    }

    fun close() = interpreter.close()
}
