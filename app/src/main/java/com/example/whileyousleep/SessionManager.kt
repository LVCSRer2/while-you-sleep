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
        totalChunks: Int,
        endTimeMs: Long = System.currentTimeMillis()
    ) {
        val json = JSONObject().apply {
            put("session", session)
            put("totalChunks", totalChunks)
            put("endTimeMs", endTimeMs)
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
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dates = mutableSetOf<String>()
        for (session in listAllSessions(context)) {
            val endTime = getSessionEndTime(context, session) ?: continue
            dates.add(dateFormat.format(Date(endTime)))
        }
        return dates
    }

    fun getSegmentsForDate(context: Context, dateStr: String): List<AudioSegment> {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val segments = mutableListOf<AudioSegment>()
        for (session in listAllSessions(context)) {
            val endTime = getSessionEndTime(context, session) ?: continue
            if (dateFormat.format(Date(endTime)) == dateStr) {
                segments.addAll(loadSessionMetadata(context, session))
            }
        }
        segments.sortBy { it.timestampMs }
        return segments
    }

    private fun getSessionEndTime(context: Context, session: String): Long? {
        val file = File(getSessionDir(context, session), METADATA_FILE)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        return if (json.has("endTimeMs")) {
            json.getLong("endTimeMs")
        } else {
            // Fallback for old sessions: use last segment timestamp
            val arr = json.getJSONArray("segments")
            if (arr.length() > 0) arr.getJSONObject(arr.length() - 1).getLong("timestampMs")
            else null
        }
    }

    private fun listAllSessions(context: Context): List<String> {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, METADATA_FILE).exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
}
