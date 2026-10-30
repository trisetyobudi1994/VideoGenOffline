package com.videogen.offline.engine

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

class Mp4Writer(private val outputFile: File, private val width: Int, private val height: Int, private val fps: Int = 8) {
    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    fun encodeFrames(frames: List<Bitmap>) {
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        for ((index, bitmap) in frames.withIndex()) {
            val yuv = bitmapToYuv(bitmap)
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val inBuf = codec.getInputBuffer(inIndex)!!
                inBuf.clear()
                inBuf.put(yuv)
                val presentationTimeUs = (index * 1000000L) / fps
                codec.queueInputBuffer(inIndex, 0, yuv.size, presentationTimeUs, 0)
            }
            drainEncoder(false)
        }

        val inIndex = codec.dequeueInputBuffer(10000)
        if (inIndex >= 0) {
            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainEncoder(true)
        codec.stop(); codec.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }

    private fun drainEncoder(endOfStream: Boolean) {
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start(); muxerStarted = true
            } else if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                if (muxerStarted && bufferInfo.size > 0) {
                    muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
    }

    private fun bitmapToYuv(bitmap: Bitmap): ByteArray {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val yuv = ByteArray(bitmap.width * bitmap.height * 3 / 2)
        var yIndex = 0
        var uvIndex = bitmap.width * bitmap.height
        for (j in 0 until bitmap.height) {
            for (i in 0 until bitmap.width) {
                val color = argb[yIndex++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex - 1] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
