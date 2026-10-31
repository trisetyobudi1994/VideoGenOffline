package com.videogen.offline.engine

import kotlin.math.tanh
import kotlin.random.Random

class NeuralNetwork {
    // Bobot (Weights) Jaringan Saraf
    private val w1 = Array(2) { FloatArray(4) { Random.nextFloat() * 2f - 1f } }
    private val b1 = FloatArray(4) { Random.nextFloat() * 2f - 1f }
    private val w2 = Array(4) { FloatArray(2) { Random.nextFloat() * 2f - 1f } }
    private val b2 = FloatArray(2) { Random.nextFloat() * 2f - 1f }

    // Proses Berpikir (Forward Pass)
    fun think(x: Float, y: Float): Pair<Float, Float> {
        val hidden = FloatArray(4)
        for (i in 0 until 4) {
            val sum = (x * w1[0][i]) + (y * w1[1][i]) + b1[i]
            hidden[i] = tanh(sum.toDouble()).toFloat()
        }
        
        var outX = 0f
        var outY = 0f
        for (i in 0 until 4) {
            outX += hidden[i] * w2[i][0]
            outY += hidden[i] * w2[i][1]
        }
        
        return Pair(
            tanh((outX + b2[0]).toDouble()).toFloat(),
            tanh((outY + b2[1]).toDouble()).toFloat()
        )
    }

    // Mutasi (Evolusi Otak AI)
    fun evolve(mutationRate: Float) {
        for (i in 0 until 4) {
            w1[0][i] += (Random.nextFloat() - 0.5f) * mutationRate
            w1[1][i] += (Random.nextFloat() - 0.5f) * mutationRate
            b1[i] += (Random.nextFloat() - 0.5f) * mutationRate
            w2[i][0] += (Random.nextFloat() - 0.5f) * mutationRate
            w2[i][1] += (Random.nextFloat() - 0.5f) * mutationRate
        }
        b2[0] += (Random.nextFloat() - 0.5f) * mutationRate
        b2[1] += (Random.nextFloat() - 0.5f) * mutationRate
    }
}
