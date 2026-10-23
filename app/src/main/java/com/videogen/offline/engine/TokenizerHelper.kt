package com.videogen.offline.engine

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Tokenizer T5 murni Kotlin (Viterbi Unigram).
 * Membaca tokenizer.json hasil konversi dari t5_spiece.model.
 * Tidak butuh library native, jalan di semua ABI Android.
 */
class TokenizerHelper(context: Context) {

    private val tokenToId = HashMap<String, Int>()
    private val tokenToScore = HashMap<String, Double>()
    private val unkId: Int
    private val maxLen = 128
    private val maxPieceLen = 32

    init {
        val file = File(context.cacheDir, "tokenizer.json")
        if (!file.exists()) {
            context.assets.open("models/tokenizer.json").use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
        }
        val root = JSONObject(file.readText())
        val model = root.getJSONObject("model")
        val vocabArr = model.getJSONArray("vocab")

        for (i in 0 until vocabArr.length()) {
            val pair = vocabArr.getJSONArray(i)
            val tok = pair.getString(0)
            val score = pair.getDouble(1)
            tokenToId[tok] = i
            tokenToScore[tok] = score
        }
        unkId = model.optInt("unk_id", 2)
    }

    /** Hasil: Pair(ids[128], mask[128]), sudah di-pad dengan 0 dan diakhiri EOS (id 1). */
    fun encode(text: String): Pair<IntArray, IntArray> {
        val pieces = unigramTokenize(normalize(text))

        val ids = IntArray(maxLen) { 0 }
        val mask = IntArray(maxLen) { 0 }

        val body = pieces.take(maxLen - 1)
        for (i in body.indices) {
            ids[i] = tokenToId[body[i]] ?: unkId
            mask[i] = 1
        }
        // EOS token T5 = 1
        if (body.size < maxLen) {
            ids[body.size] = 1
            mask[body.size] = 1
        }
        return Pair(ids, mask)
    }

    /** T5/SentencePiece: lowercase, spasi → '▁', prepend '▁'. */
    private fun normalize(text: String): String {
        val lowered = text.lowercase().trim()
        return "▁" + lowered.replace(" ", "▁")
    }

    /** Viterbi Unigram (algoritma SentencePiece). */
    private fun unigramTokenize(s: String): List<String> {
        val n = s.length
        if (n == 0) return emptyList()
        val best = DoubleArray(n + 1) { -1e18 }
        val bestPos = IntArray(n + 1) { -1 }
        best[0] = 0.0

        for (i in 0 until n) {
            if (best[i] < -1e17) continue
            val limit = minOf(maxPieceLen, n - i)
            for (len in 1..limit) {
                val piece = s.substring(i, i + len)
                val score = tokenToScore[piece] ?: continue
                val cand = best[i] + score
                if (cand > best[i + len]) {
                    best[i + len] = cand
                    bestPos[i + len] = i
                }
            }
        }

        val out = ArrayList<String>()
        var pos = n
        while (pos > 0) {
            val prev = bestPos[pos]
            if (prev < 0) {
                out.add(s.substring(pos - 1, pos))
                pos -= 1
            } else {
                out.add(s.substring(prev, pos))
                pos = prev
            }
        }
        return out.reversed()
    }
}
