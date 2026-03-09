package com.example.whileyousleep

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionManager {

    private const val RECORDINGS_DIR = "recordings"
    private const val METADATA_FILE = "session.json"

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

    fun saveSessionMetadata(
        context: Context,
        session: String,
        loudSegments: List<AudioSegment>,
        totalChunks: Int
    ) {
        val json = JSONObject().apply {
            put("session", session)
            put("totalChunks", totalChunks)
            put("segments", JSONArray().apply {
                for (seg in loudSegments) {
                    put(JSONObject().apply {
                        put("index", seg.index)
                        put("timestampMs", seg.timestampMs)
                        put("rmsEnergy", seg.rmsEnergy)
                        put("filePath", seg.filePath)
                    })
                }
            })
        }
        val file = File(getSessionDir(context, session), METADATA_FILE)
        file.writeText(json.toString())
    }

    fun loadSessionMetadata(context: Context, session: String): List<AudioSegment> {
        val file = File(getSessionDir(context, session), METADATA_FILE)
        if (!file.exists()) return emptyList()
        val json = JSONObject(file.readText())
        val arr = json.getJSONArray("segments")
        val segments = mutableListOf<AudioSegment>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val seg = AudioSegment(
                index = obj.getInt("index"),
                timestampMs = obj.getLong("timestampMs"),
                rmsEnergy = obj.getDouble("rmsEnergy"),
                filePath = obj.getString("filePath")
            )
            if (File(seg.filePath).exists()) {
                segments.add(seg)
            }
        }
        return segments
    }

    fun listSessionDates(context: Context): Set<String> {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) return emptySet()
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dates = mutableSetOf<String>()
        dir.listFiles()?.forEach { sessionDir ->
            val metaFile = File(sessionDir, METADATA_FILE)
            if (metaFile.exists() && sessionDir.isDirectory) {
                val datePart = sessionDir.name.substring(0, 8)
                try {
                    dateFormat.parse(datePart)
                    dates.add(datePart)
                } catch (_: Exception) {}
            }
        }
        return dates
    }

    fun getSessionsForDate(context: Context, dateStr: String): List<String> {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(dateStr) && File(it, METADATA_FILE).exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
}
