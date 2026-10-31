package com.videogen.offline.engine

import android.content.Context
import android.content.SharedPreferences

class AdaptiveBrain(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AI_BRAIN", Context.MODE_PRIVATE)
    
    // Kamus bawaan (Vocab awal)
    private val baseVocab = listOf("kucing", "anjing", "berjalan", "berlari", "terbang", "api", "air", "galaxy", "hutan", "gunung")

    /**
     * Mencari kata terdekat jika ada typo (Levenshtein Distance)
     */
    private fun findClosestWord(input: String): String {
        // Cek memori dulu (apakah user pernah mengajari AI ini sebelumnya?)
        val learned = prefs.getString(input, null)
        if (learned != null) return learned

        // Jika tidak, cari yang paling mirip dari kamus
        var bestMatch = input
        var minDistance = Int.MAX_VALUE

        for (word in baseVocab) {
            val dist = levenshtein(input, word)
            if (dist < minDistance && dist <= 2) { // Toleransi typo maks 2 huruf
                minDistance = dist
                bestMatch = word
            }
        }

        // AI BELAJAR: Simpan typo ini agar besok langsung dikenali
        if (bestMatch != input) {
            prefs.edit().putString(input, bestMatch).apply()
        }
        return bestMatch
    }

    fun parsePrompt(prompt: String): ParsedScene {
        val words = prompt.lowercase().split(" ")
        val correctedWords = words.map { findClosestWord(it) }
        
        var subject = "kucing"
        var action = "berjalan"
        
        // Parsing tata bahasa sederhana
        for (w in correctedWords) {
            if (w in listOf("kucing", "anjing")) subject = w
            if (w in listOf("berjalan", "berlari")) action = w
        }

        return ParsedScene(subject, action, correctedWords.joinToString(" "))
    }

    // Algoritma Levenshtein untuk deteksi typo
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return dp[a.length][b.length]
    }
}

data class ParsedScene(val subject: String, val action: String, val correctedText: String)
