package com.videogen.offline.engine

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import android.content.Context
import java.io.File

class TokenizerHelper(context: Context) {
    private val tokenizer: HuggingFaceTokenizer

    init {
        // Android tidak bisa membaca file JSON langsung dari assets, 
        // jadi kita salin dulu ke cacheDir sementara.
        val file = File(context.cacheDir, "tokenizer.json")
        if (!file.exists()) {
            context.assets.open("models/tokenizer.json").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        tokenizer = HuggingFaceTokenizer.newInstance(file.toPath())
    }

    /**
     * Mengubah teks menjadi IDs (128) dan Mask (128)
     */
    fun encode(text: String): Pair<IntArray, IntArray> {
        val encoding = tokenizer.encode(text)
        var ids = encoding.ids
        var mask = encoding.attentionMask

        val targetLength = 128
        // Potong jika kepanjangan
        if (ids.size > targetLength) {
            ids = ids.copyOf(targetLength)
            mask = mask.copyOf(targetLength)
        } 
        // Tambahkan padding (0) jika terlalu pendek
        else if (ids.size < targetLength) {
            val padLength = targetLength - ids.size
            ids = ids + IntArray(padLength) { 0 }
            mask = mask + IntArray(padLength) { 0 }
        }

        return Pair(ids, mask)
    }
}
