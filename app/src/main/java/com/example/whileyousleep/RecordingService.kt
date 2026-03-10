package com.example.whileyousleep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class RecordingService : Service() {

    companion object {
        private const val CHANNEL_ID = "SleepRecordingChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "RecordingService"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_DURATION_SEC = 5
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_DURATION_SEC
    }

    private val binder = RecordingBinder()
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRecording = false

    private var sessionName: String = ""
    private val allSegments = mutableListOf<AudioSegment>()
    private var chunkIndex = 0
    private var recordingStartTime = 0L

    interface RecordingListener {
        fun onChunkRecorded(segment: AudioSegment, totalChunks: Int)
        fun onRecordingStopped(loudSegments: List<AudioSegment>, totalChunks: Int)
    }

    private var listener: RecordingListener? = null

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    fun setListener(l: RecordingListener?) {
        this.listener = l
    }

    fun startRecording() {
        if (isRecording) return

        sessionName = SessionManager.createSession(this)
        allSegments.clear()
        chunkIndex = 0
        recordingStartTime = System.currentTimeMillis()

        val chunkBytes = CHUNK_SAMPLES * 2
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ),
            chunkBytes * 2
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "AudioRecord SecurityException: ${e.message}")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            return
        }

        isRecording = true
        audioRecord?.startRecording()
        startForeground(NOTIFICATION_ID, createNotification("수면 중 녹음 중..."))

        recordingThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

            val chunkBuffer = ShortArray(CHUNK_SAMPLES)
            var chunkOffset = 0
            val readBuffer = ShortArray(1024)

            try {
                while (isRecording) {
                    val read = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (read <= 0) continue

                    val remaining = CHUNK_SAMPLES - chunkOffset
                    val toCopy = minOf(read, remaining)
                    System.arraycopy(readBuffer, 0, chunkBuffer, chunkOffset, toCopy)
                    chunkOffset += toCopy

                    if (chunkOffset >= CHUNK_SAMPLES) {
                        processChunk(chunkBuffer, CHUNK_SAMPLES)

                        // Handle leftover samples from readBuffer
                        val leftover = read - toCopy
                        if (leftover > 0) {
                            System.arraycopy(readBuffer, toCopy, chunkBuffer, 0, leftover)
                            chunkOffset = leftover
                        } else {
                            chunkOffset = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}")
            }
        }, "RecordingThread")
        recordingThread?.start()
    }

    private fun processChunk(samples: ShortArray, count: Int) {
        val energy = AudioAnalyzer.computeRmsEnergy(samples, count)
        val path = SessionManager.getChunkPath(this, sessionName, chunkIndex)

        val byteBuffer = ByteArray(count * 2)
        for (i in 0 until count) {
            byteBuffer[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            byteBuffer[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        SessionManager.writeChunk(path, byteBuffer)

        val segment = AudioSegment(
            index = chunkIndex,
            timestampMs = recordingStartTime + (chunkIndex * CHUNK_DURATION_SEC * 1000L),
            rmsEnergy = energy,
            filePath = path
        )

        synchronized(allSegments) {
            allSegments.add(segment)
        }
        chunkIndex++

        listener?.onChunkRecorded(segment, chunkIndex)
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        recordingThread?.join(2000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stopForeground(STOP_FOREGROUND_REMOVE)

        val segments: List<AudioSegment>
        synchronized(allSegments) {
            segments = allSegments.toList()
        }

        val energies = segments.map { it.rmsEnergy }
        val threshold = AudioAnalyzer.computeMadThreshold(energies, 3.0)
        val loudSegments = segments.filter { it.rmsEnergy >= threshold }

        // Save metadata and delete non-loud chunk files
        val sessionCopy = sessionName
        Thread({
            SessionManager.saveSessionMetadata(this, sessionCopy, loudSegments, segments.size)
            SessionManager.deleteNonLoudChunks(segments, threshold)
        }, "CleanupThread").start()

        listener?.onRecordingStopped(loudSegments, segments.size)
    }

    fun isCurrentlyRecording() = isRecording

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("While You Sleep")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "수면 녹음",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
    }
}
