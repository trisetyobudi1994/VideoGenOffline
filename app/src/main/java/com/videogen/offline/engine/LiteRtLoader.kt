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

    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)

        // Gunakan numBytes() agar ukurannya PASTI pas dengan yang diminta model
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
        val outShape = outputTensor.shape()
        val outSize = outShape.fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        val inputs = arrayOf<Any>(idBuffer, maskBuffer)
        val outputs = mapOf(0 to outputBuffer)
        
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        val floatBuffer = outputBuffer.asFloatBuffer()
        val result = FloatArray(outSize)
        floatBuffer.get(result)
        
        return result
    }

    fun close() = interpreter.close()
}
