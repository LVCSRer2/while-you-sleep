package com.example.whileyousleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.FileInputStream

class AudioPlayer(private val sampleRate: Int = 16000) {

    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile
    private var playing = false

    fun play(pcmFilePath: String, onComplete: () -> Unit) {
        stop()

        val file = File(pcmFilePath)
        if (!file.exists()) {
            onComplete()
            return
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        playing = true
        audioTrack?.play()

        playThread = Thread({
            try {
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(bufferSize)
                    while (playing) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        audioTrack?.write(buffer, 0, read)
                    }
                }
            } catch (_: Exception) {
            } finally {
                playing = false
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                onComplete()
            }
        }, "AudioPlayThread")
        playThread?.start()
    }

    fun stop() {
        playing = false
        playThread?.join(500)
        playThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun isPlaying(): Boolean = playing
}
