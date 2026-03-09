package com.example.whileyousleep

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.whileyousleep.databinding.ItemSegmentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SegmentAdapter(
    private val onPlayClick: (AudioSegment) -> Unit
) : RecyclerView.Adapter<SegmentAdapter.ViewHolder>() {

    private val segments = mutableListOf<AudioSegment>()
    private var playingIndex: Int = -1

    inner class ViewHolder(val binding: ItemSegmentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSegmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val segment = segments[position]
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date(segment.timestampMs))

        holder.binding.tvTime.text = timeStr
        holder.binding.tvEnergy.text = "%.4f".format(segment.rmsEnergy)

        val isPlaying = position == playingIndex
        holder.binding.btnPlay.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        holder.binding.root.alpha = if (isPlaying) 1.0f else 0.8f

        holder.binding.btnPlay.setOnClickListener {
            onPlayClick(segment)
        }
    }

    override fun getItemCount(): Int = segments.size

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<AudioSegment>) {
        segments.clear()
        segments.addAll(list)
        playingIndex = -1
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setPlayingIndex(index: Int) {
        playingIndex = index
        notifyDataSetChanged()
    }

    fun getSegments(): List<AudioSegment> = segments.toList()
}
