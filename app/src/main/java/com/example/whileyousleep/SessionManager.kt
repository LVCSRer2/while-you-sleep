package com.example.whileyousleep

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionManager {

    private const val RECORDINGS_DIR = "recordings"

    fun createSession(context: Context): String {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "$RECORDINGS_DIR/$name")
        dir.mkdirs()
        return name
    }

    fun getSessionDir(context: Context, name: String): File {
        return File(context.filesDir, "$RECORDINGS_DIR/$name")
    }

    fun getChunkPath(context: Context, session: String, index: Int): String {
        val dir = getSessionDir(context, session)
        return File(dir, "chunk_%04d.pcm".format(index)).absolutePath
    }

    fun writeChunk(path: String, data: ByteArray) {
        FileOutputStream(path).use { it.write(data) }
    }

    fun deleteNonLoudChunks(segments: List<AudioSegment>, threshold: Double) {
        for (segment in segments) {
            if (segment.rmsEnergy < threshold) {
                File(segment.filePath).delete()
            }
        }
    }

    fun deleteSession(context: Context, session: String) {
        val dir = getSessionDir(context, session)
        dir.deleteRecursively()
    }
}
