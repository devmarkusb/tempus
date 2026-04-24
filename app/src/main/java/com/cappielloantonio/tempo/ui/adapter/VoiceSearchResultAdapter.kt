package com.cappielloantonio.tempo.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.databinding.ItemVoiceSearchResultBinding
import com.cappielloantonio.tempo.subsonic.models.Child

class VoiceSearchResultAdapter(
    private val onPick: (Child) -> Unit
) : RecyclerView.Adapter<VoiceSearchResultAdapter.ViewHolder>() {

    private val items = mutableListOf<Child>()

    fun submitList(list: List<Child>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVoiceSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemVoiceSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Child) {
            binding.voiceResultTitle.text = song.title ?: ""
            binding.voiceResultSubtitle.text = listOfNotNull(song.artist, song.album)
                .joinToString(" • ")
            binding.root.setOnClickListener { onPick(song) }
        }
    }
}
