package com.videogen.offline.engine

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import android.content.Context
import java.io.File

class TokenizerHelper(context: Context) {
    private val tokenizer: HuggingFaceTokenizer

    init {
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
     * Mengubah teks menjadi IDs (128) dan Mask (128) dalam bentuk IntArray
     */
    fun encode(text: String): Pair<IntArray, IntArray> {
        val encoding = tokenizer.encode(text)
        val rawIds = encoding.ids          // Ini bertipe LongArray
        val rawMask = encoding.attentionMask // Ini bertipe LongArray

        val targetLength = 128
        
        // Siapkan array dengan ukuran 128 dan isi default 0 (padding)
        val ids = IntArray(targetLength) { 0 }
        val mask = IntArray(targetLength) { 0 }

        // Berapa banyak yang akan disalin? (Maksimal 128)
        val copyLength = if (rawIds.size > targetLength) targetLength else rawIds.size

        // Salin dan konversi dari Long ke Int
        for (i in 0 until copyLength) {
            ids[i] = rawIds[i].toInt()
            mask[i] = rawMask[i].toInt()
        }

        return Pair(ids, mask)
    }
}
