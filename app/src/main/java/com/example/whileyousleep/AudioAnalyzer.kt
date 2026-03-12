package com.example.whileyousleep

import kotlin.math.sqrt

object AudioAnalyzer {

    fun computeRmsEnergy(samples: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / 32768.0
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / count)
    }

    fun computeMadThreshold(energies: List<Double>, k: Double = 5.0): Double {
        if (energies.isEmpty()) return 0.0
        val sorted = energies.sorted()
        val median = median(sorted)
        val deviations = energies.map { kotlin.math.abs(it - median) }.sorted()
        val mad = median(deviations)
        return median + k * mad
    }

    private fun median(sorted: List<Double>): Double {
        val n = sorted.size
        return if (n % 2 == 0) {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        } else {
            sorted[n / 2]
        }
    }
}
