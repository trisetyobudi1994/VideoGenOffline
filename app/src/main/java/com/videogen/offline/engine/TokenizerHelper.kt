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

    fun encode(text: String): Pair<IntArray, IntArray> {
        val encoding = tokenizer.encode(text)
        val ids = encoding.ids.map { it.toInt() }.toIntArray()
        val mask = encoding.attentionMask.map { it.toInt() }.toIntArray()
        return Pair(ids, mask)
    }
}
