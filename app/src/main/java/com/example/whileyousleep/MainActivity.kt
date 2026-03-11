package com.example.whileyousleep

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.whileyousleep.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), RecordingService.RecordingListener {

    private lateinit var binding: ActivityMainBinding
    private var recordingService: RecordingService? = null
    private var bound = false

    private lateinit var segmentAdapter: SegmentAdapter
    private val audioPlayer = AudioPlayer()
    private val handler = Handler(Looper.getMainLooper())

    private var recordingStartTime = 0L
    private var timerRunnable: Runnable? = null
    private var currentPlayingSegment: AudioSegment? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startRecording()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            recordingService?.setListener(this@MainActivity)
            bound = true

            if (recordingService?.isCurrentlyRecording() == true) {
                recordingStartTime = recordingService!!.getRecordingStartTime()
                updateUiRecording()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        segmentAdapter = SegmentAdapter { segment ->
            playSegment(segment)
        }
        binding.recyclerSegments.layoutManager = LinearLayoutManager(this)
        binding.recyclerSegments.adapter = segmentAdapter

        binding.btnToggle.setOnClickListener {
            if (recordingService?.isCurrentlyRecording() == true) {
                stopRecording()
            } else {
                checkPermissionsAndStart()
            }
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        val intent = Intent(this, RecordingService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            startRecording()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startRecording() {
        recordingService?.startRecording()
        recordingStartTime = System.currentTimeMillis()
        updateUiRecording()
    }

    private fun updateUiRecording() {
        binding.btnToggle.text = "STOP"
        binding.tvStatus.text = "녹음 중..."
        binding.tvChunkCount.text = ""
        segmentAdapter.submitList(emptyList())

        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val hours = (elapsed / 3600000).toInt()
                val minutes = ((elapsed % 3600000) / 60000).toInt()
                val seconds = ((elapsed % 60000) / 1000).toInt()
                binding.tvTimer.text = "%02d:%02d:%02d".format(hours, minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopRecording() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        binding.tvStatus.text = "분석 중..."
        recordingService?.stopRecording()
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

        val position = segmentAdapter.getSegments().indexOfFirst { it.index == segment.index }
        segmentAdapter.setPlayingIndex(position)

        audioPlayer.play(segment.filePath) {
            handler.post {
                currentPlayingSegment = null
                segmentAdapter.setPlayingIndex(-1)
            }
        }
    }

    // RecordingListener callbacks

    override fun onChunkRecorded(segment: AudioSegment, totalChunks: Int) {
        handler.post {
            binding.tvChunkCount.text = "청크: $totalChunks"
        }
    }

    override fun onRecordingStopped(loudSegments: List<AudioSegment>, totalChunks: Int) {
        handler.post {
            binding.btnToggle.text = "START"
            binding.tvStatus.text = "총 ${totalChunks}개 중 ${loudSegments.size}개의 큰 소리 감지"
            segmentAdapter.submitList(loudSegments)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
        audioPlayer.stop()
        recordingService?.setListener(null)
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }
}
