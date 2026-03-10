package com.example.whileyousleep

data class AudioSegment(
    val index: Int,
    val timestampMs: Long,
    val rmsEnergy: Double,
    val filePath: String,
    val durationMs: Int = 5000
)
