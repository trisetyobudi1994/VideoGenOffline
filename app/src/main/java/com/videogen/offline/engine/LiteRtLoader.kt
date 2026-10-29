package com.videogen.offline.engine

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LiteRtLoader(context: Context, pathOrAsset: String) {

    private val interpreter: Interpreter
    private val gpuDelegate: GpuDelegate?
    private val nnApiDelegate: NnApiDelegate?

    init {
        val model = when {
            pathOrAsset.startsWith("http") ->
                throw IllegalArgumentException("Gunakan ModelDownloader untuk URL")
            pathOrAsset.startsWith("/") -> {
                val f = File(pathOrAsset)
                if (!f.exists()) throw IllegalArgumentException("File tidak ditemukan: $pathOrAsset")
                // TRIK 1: ZERO-COPY MMAP (Baca langsung dari disk, hemat RAM)
                loadModelFileFromStorage(f)
            }
            else -> loadModelFileFromAssets(context, pathOrAsset)
        }

        val options = Interpreter.Options()
        
        // TRIK 2: AKTIFKAN GPU DELEGATE (Lempar beban ke GPU)
        val compatList = CompatibilityList()
        var gpu: GpuDelegate? = null
        var nnapi: NnApiDelegate? = null

        if (compatList.isDelegateSupportedOnThisDevice) {
            val delegateOptions = compatList.bestOptionsForThisDevice
            delegateOptions.setPrecisionLossAllowed(true) // TRIK 3: IZINKAN FP16 (Hemat RAM 50%)
            gpu = GpuDelegate(delegateOptions)
            options.addDelegate(gpu)
            Log.d("LiteRtLoader", "Menggunakan GPU Delegate (FP16 Mode)")
        } else {
            // Fallback ke NNAPI (NPU) jika GPU tidak mendukung
            try {
                nnapi = NnApiDelegate(NnApiDelegate.Options().apply {
                    setAllowFp16(true)
                })
                options.addDelegate(nnapi)
                Log.d("LiteRtLoader", "Menggunakan NNAPI Delegate (NPU Mode)")
            } catch (e: Exception) {
                // Fallback ke CPU (XNNPACK - Optimasi CPU)
                options.setUseXNNPACK(true)
                options.setNumThreads(4)
                Log.d("LiteRtLoader", "Menggunakan CPU XNNPACK (4 Threads)")
            }
        }

        gpuDelegate = gpu
        nnApiDelegate = nnapi

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
            // Mmap: Memetakan file langsung ke memori virtual tanpa memuat semuanya ke RAM fisik
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

    fun runTextEncoder(ids: IntArray, mask: IntArray): FloatArray {
        val inputTensor0 = interpreter.getInputTensor(0)
        val inputTensor1 = interpreter.getInputTensor(1)

        val idBuffer = ByteBuffer.allocateDirect(inputTensor0.numBytes()).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(inputTensor1.numBytes()).order(ByteOrder.nativeOrder())

        val requiredLen0 = inputTensor0.numBytes() / 4
        val requiredLen1 = inputTensor1.numBytes() / 4

        val ib0 = idBuffer.asIntBuffer()
        for (i in 0 until requiredLen0) ib0.put(if (i < ids.size) ids[i] else 0)

        val ib1 = maskBuffer.asIntBuffer()
        for (i in 0 until requiredLen1) ib1.put(if (i < mask.size) mask[i] else 0)

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

    fun runContextAdapter(embedding: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(embedding.size * 4).order(ByteOrder.nativeOrder())
        inputBuffer.asFloatBuffer().put(embedding)

        val outputTensor = interpreter.getOutputTensor(0)
        val outSize = outputTensor.shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun runDitStep(context: FloatArray): FloatArray {
        val inputBuffers = mutableListOf<ByteBuffer>()
        val inputCount = interpreter.inputTensorCount

        for (i in 0 until inputCount) {
            val tensor = interpreter.getInputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
            val requiredLen = tensor.numBytes() / 4
            val fb = buffer.asFloatBuffer()

            if (i == 0 || i == 1) {
                for (j in 0 until requiredLen) fb.put((Math.random().toFloat() * 2f) - 1f)
            } else if (i == 2) {
                for (j in 0 until requiredLen) fb.put(if (j < context.size) context[j] else 0f)
            } else {
                for (j in 0 until requiredLen) fb.put(1.0f)
            }
            inputBuffers.add(buffer)
        }

        val outputTensor = interpreter.getOutputTensor(0)
        val outSize = outputTensor.shape().fold(1) { acc, i -> acc * i }
        val outputBuffer = ByteBuffer.allocateDirect(outSize * 4).order(ByteOrder.nativeOrder())

        interpreter.runForMultipleInputsOutputs(inputBuffers.toTypedArray(), mapOf(0 to outputBuffer))

        outputBuffer.rewind()
        val result = FloatArray(outSize)
        outputBuffer.asFloatBuffer().get(result)
        return result
    }

    fun close() {
        interpreter.close()
        gpuDelegate?.close()
        nnApiDelegate?.close()
    }
}
