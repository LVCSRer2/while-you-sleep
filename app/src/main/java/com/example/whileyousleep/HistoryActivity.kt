package com.example.whileyousleep

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whileyousleep.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var segmentAdapter: SegmentAdapter
    private val audioPlayer = AudioPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private var currentPlayingSegment: AudioSegment? = null
    private var recordingDates = setOf<String>()

    private var allSegmentsForDate = listOf<AudioSegment>()
    private var maxEnergy = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        segmentAdapter = SegmentAdapter { segment -> playSegment(segment) }
        binding.recyclerSegments.layoutManager = LinearLayoutManager(this)
        binding.recyclerSegments.adapter = segmentAdapter

        recordingDates = SessionManager.listSessionDates(this)

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val dateStr = "%04d%02d%02d".format(year, month + 1, dayOfMonth)
            loadDate(dateStr)
        }

        binding.seekBarEnergy.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applyFilter(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
            Calendar.getInstance().time
        )
        loadDate(today)

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadDate(dateStr: String) {
        audioPlayer.stop()
        currentPlayingSegment = null

        val sessions = SessionManager.getSessionsForDate(this, dateStr)
        if (sessions.isEmpty()) {
            binding.tvDateInfo.text = formatDateDisplay(dateStr) + " - 기록 없음"
            allSegmentsForDate = emptyList()
            segmentAdapter.submitList(emptyList())
            binding.recyclerSegments.visibility = View.GONE
            binding.layoutFilter.visibility = View.GONE
            return
        }

        val segments = mutableListOf<AudioSegment>()
        for (session in sessions) {
            segments.addAll(SessionManager.loadSessionMetadata(this, session))
        }
        segments.sortBy { it.timestampMs }
        allSegmentsForDate = segments
        maxEnergy = segments.maxOfOrNull { it.rmsEnergy } ?: 0.0

        binding.seekBarEnergy.progress = 0
        binding.layoutFilter.visibility = View.VISIBLE
        applyFilter(0)
    }

    private fun applyFilter(progress: Int) {
        val threshold = (progress / 1000.0) * maxEnergy
        val filtered = allSegmentsForDate.filter { it.rmsEnergy >= threshold }

        binding.tvFilterValue.text = "%.4f (%d)".format(threshold, filtered.size)
        binding.tvDateInfo.text = "${filtered.size} / ${allSegmentsForDate.size}개 표시"
        segmentAdapter.submitList(filtered)
        binding.recyclerSegments.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun formatDateDisplay(dateStr: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(dateStr)
            SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault()).format(parsed!!)
        } catch (_: Exception) {
            dateStr
        }
    }

    private fun playSegment(segment: AudioSegment) {
        if (currentPlayingSegment == segment && audioPlayer.isPlaying()) {
            audioPlayer.stop()
            currentPlayingSegment = null
            segmentAdapter.setPlayingIndex(-1)
            return
        }

        audioPlayer.stop()
        currentPlayingSegment = segment

        val position = segmentAdapter.getSegments().indexOfFirst { it.index == segment.index && it.filePath == segment.filePath }
        segmentAdapter.setPlayingIndex(position)

        audioPlayer.play(segment.filePath) {
            handler.post {
                currentPlayingSegment = null
                segmentAdapter.setPlayingIndex(-1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
    }
}
