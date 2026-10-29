package com.videogen.offline.engine

import android.content.Context
import org.json.JSONObject

class TokenizerHelper(context: Context) {
    private val pieceId = HashMap<String, Int>()
    private val pieceScore = HashMap<String, Double>()
    private var unkId = 2
    private var eosId = 1
    private var maxLen = 1

    init {
        val text = context.assets.open("models/tokenizer.json")
            .bufferedReader().use { it.readText() }
        val vocab = JSONObject(text).getJSONObject("model").getJSONArray("vocab")
        for (i in 0 until vocab.length()) {
            val e = vocab.getJSONArray(i)
            val p = e.getString(0)
            pieceId[p] = i
            pieceScore[p] = e.getDouble(1)
            if (p.length > maxLen) maxLen = p.length
        }
        pieceId["<unk>"]?.let { unkId = it }
        pieceId["</s>"]?.let { eosId = it }
    }

    fun encode(text: String): Pair<IntArray, IntArray> {
        val s = "\u2581" + text.trim().replace(Regex("\\s+"), "\u2581")
        val n = s.length
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val prev = IntArray(n + 1)
        val tok = IntArray(n + 1)
        best[0] = 0.0

        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            for (j in i + 1..minOf(n, i + maxLen)) {
                val sub = s.substring(i, j)
                val id = pieceId[sub] ?: continue
                val sc = best[i] + pieceScore[sub]!!
                if (sc > best[j]) { best[j] = sc; prev[j] = i; tok[j] = id }
            }
            val unkScore = best[i] - 100.0
            if (unkScore > best[i + 1]) { best[i + 1] = unkScore; prev[i + 1] = i; tok[i + 1] = unkId }
        }

        val out = ArrayList<Int>()
        var pos = n
        while (pos > 0) { out.add(tok[pos]); pos = prev[pos] }
        out.reverse()
        out.add(eosId)

        val ids = out.toIntArray()
        return Pair(ids, IntArray(ids.size) { 1 })
    }
}
