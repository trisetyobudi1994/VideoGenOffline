package com.videogen.offline.engine

import android.content.Context
import org.json.JSONObject
import java.io.File

class TokenizerHelper(context: Context) {
    // Model dt5 menggunakan SentencePiece Unigram. Kita baca tokenizer.json secara manual.
    private val vocab: Map<String, Int>

    init {
        val file = File(context.cacheDir, "tokenizer.json")
        if (!file.exists()) {
            context.assets.open("models/tokenizer.json").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        // Parsing file JSON secara manual tanpa library DJL
        val jsonString = file.readText()
        val root = JSONObject(jsonString)
        val model = root.getJSONObject("model")
        val rawVocab = model.getJSONObject("vocab")
        
        val tempVocab = mutableMapOf<String, Int>()
        val keys = rawVocab.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            tempVocab[key] = rawVocab.getInt(key)
        }
        vocab = tempVocab
    }

    fun encode(text: String): Pair<IntArray, IntArray> {
        val ids = mutableListOf<Int>()
        val mask = mutableListOf<Int>()

        val words = text.trim().split(" ")
        
        // Token BOS (Beginning of Sentence) atau Padding awal (biasanya ID 0)
        ids.add(0) 
        mask.add(1)

        for (word in words) {
            if (word.isEmpty()) continue
            
            // Coba cari kata utuh dulu
            if (vocab.containsKey(word)) {
                ids.add(vocab[word]!!)
                mask.add(1)
            } else {
                // Jika tidak ada, pecah per karakter
                for (char in word) {
                    val charStr = char.toString()
                    if (vocab.containsKey(charStr)) {
                        ids.add(vocab[charStr]!!)
                    } else {
                        ids.add(vocab["<unk>"] ?: 0)
                    }
                    mask.add(1)
                }
            }
        }

        // Tambahkan token EOS (End of Sentence) di akhir (biasanya ID 1 untuk T5)
        ids.add(vocab["</s>"] ?: 1)
        mask.add(1)

        return Pair(ids.toIntArray(), mask.toIntArray())
    }
}
