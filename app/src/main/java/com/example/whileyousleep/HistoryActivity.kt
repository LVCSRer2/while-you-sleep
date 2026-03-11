package com.example.whileyousleep

import android.app.DatePickerDialog
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

    private var allSegmentsForDate = listOf<AudioSegment>()
    private var maxEnergy = 0.0
    private val selectedCalendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        segmentAdapter = SegmentAdapter { segment -> playSegment(segment) }
        binding.recyclerSegments.layoutManager = LinearLayoutManager(this)
        binding.recyclerSegments.adapter = segmentAdapter

        binding.btnSelectDate.setOnClickListener { showDatePicker() }

        binding.seekBarEnergy.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                applyFilter(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        loadDate(selectedCalendar)

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(year, month, dayOfMonth)
                loadDate(selectedCalendar)
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadDate(cal: Calendar) {
        audioPlayer.stop()
        currentPlayingSegment = null

        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        val displayStr = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.getDefault()).format(cal.time)
        binding.btnSelectDate.text = displayStr

        val segments = SessionManager.getSegmentsForDate(this, dateStr)
        if (segments.isEmpty()) {
            binding.tvDateInfo.text = "기록 없음"
            allSegmentsForDate = emptyList()
            segmentAdapter.submitList(emptyList())
            binding.recyclerSegments.visibility = View.GONE
            binding.layoutFilter.visibility = View.GONE
            return
        }

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
