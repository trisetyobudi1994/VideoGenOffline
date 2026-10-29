package com.videogen.offline.engine

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, assetPath: String) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, assetPath)
        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(model, options)
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

    private fun fill(tensor: Tensor, data: IntArray): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        val n = tensor.numElements()
        when (tensor.dataType()) {
            DataType.INT32 -> { val b = buf.asIntBuffer(); for (i in 0 until n) b.put(if (i < data.size) data[i] else 0) }
            DataType.INT64 -> { val b = buf.asLongBuffer(); for (i in 0 until n) b.put(if (i < data.size) data[i].toLong() else 0L) }
            DataType.FLOAT32 -> { val b = buf.asFloatBuffer(); for (i in 0 until n) b.put(if (i < data.size) data[i].toFloat() else 0f) }
            else -> throw IllegalStateException("Tipe input tidak didukung: ${tensor.dataType()}")
        }
        buf.rewind()
        return buf
    }

    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        check(interpreter.inputTensorCount == 2) {
            "Model punya ${interpreter.inputTensorCount} input, diharapkan 2"
        }

        var idIdx = 0
        var maskIdx = 1
        for (i in 0 until 2) {
            if (interpreter.getInputTensor(i).name().lowercase().contains("mask")) {
                maskIdx = i
                idIdx = 1 - i
            }
        }

        val inputs = arrayOfNulls<Any>(2)
        inputs[idIdx] = fill(interpreter.getInputTensor(idIdx), ids)
        inputs[maskIdx] = fill(interpreter.getInputTensor(maskIdx), mask)

        val out = interpreter.getOutputTensor(0)
        check(out.dataType() == DataType.FLOAT32) { "Output bukan FLOAT32: ${out.dataType()}" }
        val outBuf = ByteBuffer.allocateDirect(out.numBytes()).order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to outBuf))

        outBuf.rewind()
        val result = FloatArray(out.numElements())
        outBuf.asFloatBuffer().get(result)
        return result
    }

    fun close() = interpreter.close()
}
