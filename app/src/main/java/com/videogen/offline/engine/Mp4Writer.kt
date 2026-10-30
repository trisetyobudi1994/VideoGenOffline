package com.videogen.offline.engine

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

class Mp4Writer(
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30
) {
    init {
        require(width % 2 == 0 && height % 2 == 0) { "Lebar dan tinggi harus genap: ${width}x$height" }
    }

    fun encode(frameCount: Int, frameProvider: (Int) -> Bitmap) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()

        fun drain(untilEos: Boolean) {
            var idleTries = 0
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!untilEos) return
                    idleTries++
                    if (idleTries > 500) throw IllegalStateException("Encoder tidak selesai")
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (idx >= 0) {
                    val buf = codec.getOutputBuffer(idx)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                    if (info.size > 0 && started) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            for (f in 0 until frameCount) {
                var inIdx = codec.dequeueInputBuffer(10_000)
                while (inIdx < 0) {
                    drain(false)
                    inIdx = codec.dequeueInputBuffer(10_000)
                }
                val image = codec.getInputImage(inIdx) ?: throw IllegalStateException("Input image null")
                val bmp = frameProvider(f)
                fillImage(image, bmp)
                bmp.recycle()
                val pts = f * 1_000_000L / fps
                codec.queueInputBuffer(inIdx, 0, width * height * 3 / 2, pts, 0)
                drain(false)
            }

            var eosIdx = codec.dequeueInputBuffer(10_000)
            while (eosIdx < 0) {
                drain(false)
                eosIdx = codec.dequeueInputBuffer(10_000)
            }
            codec.queueInputBuffer(
                eosIdx, 0, 0, frameCount * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            drain(true)
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release()
            try { if (started) muxer.stop() } catch (_: Throwable) {}
            muxer.release()
        }
    }

    private fun fillImage(image: Image, bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)

        val yP = image.planes[0]
        val uP = image.planes[1]
        val vP = image.planes[2]
        val yBuf = yP.buffer
        val uBuf = uP.buffer
        val vBuf = vP.buffer

        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuf.put(j * yP.rowStride + i * yP.pixelStride, y.coerceIn(0, 255).toByte())
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val row = j / 2
                    val col = i / 2
                    uBuf.put(row * uP.rowStride + col * uP.pixelStride, u.coerceIn(0, 255).toByte())
                    vBuf.put(row * vP.rowStride + col * vP.pixelStride, v.coerceIn(0, 255).toByte())
                }
            }
        }
    }
}
