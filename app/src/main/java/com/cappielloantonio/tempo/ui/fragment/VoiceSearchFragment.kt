package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.FragmentVoiceSearchBinding
import com.cappielloantonio.tempo.service.MediaManager
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.ui.adapter.VoiceSearchResultAdapter
import com.cappielloantonio.tempo.viewmodel.VoiceSearchState
import com.cappielloantonio.tempo.viewmodel.VoiceSearchViewModel
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
class VoiceSearchFragment : Fragment() {

    private var _bind: FragmentVoiceSearchBinding? = null
    private val bind get() = _bind!!

    private val viewModel: VoiceSearchViewModel by viewModels()
    private lateinit var mediaBrowserFuture: ListenableFuture<MediaBrowser>
    private lateinit var adapter: VoiceSearchResultAdapter
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                bind.voiceSearchTextInput.setText(text)
                viewModel.onTranscript(text)
            } else {
                viewModel.setIdle()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _bind = FragmentVoiceSearchBinding.inflate(inflater, container, false)
        return bind.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VoiceSearchResultAdapter { song -> playNow(listOf(song), 0) }
        bind.voiceSearchFallbackList.layoutManager = LinearLayoutManager(requireContext())
        bind.voiceSearchFallbackList.adapter = adapter

        bind.voiceSearchMicButton.setOnClickListener { startListening() }

        bind.voiceSearchTextInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val q = bind.voiceSearchTextInput.text?.toString() ?: ""
                if (q.isNotBlank()) viewModel.searchAndRank(q)
                true
            } else false
        }

        viewModel.state.observe(viewLifecycleOwner) { state -> render(state) }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserFuture = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), MediaService::class.java))
        ).buildAsync()
    }

    override fun onStop() {
        if (::mediaBrowserFuture.isInitialized) {
            MediaBrowser.releaseFuture(mediaBrowserFuture)
        }
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _bind = null
    }

    private fun startListening() {
        viewModel.setListening()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_speak_now))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.voice_search_speech_unavailable, Toast.LENGTH_SHORT).show()
            viewModel.setIdle()
        }
    }

    private fun render(state: VoiceSearchState) {
        when (state) {
            is VoiceSearchState.Idle -> {
                bind.voiceSearchStatus.visibility = View.GONE
                bind.voiceSearchFallbackLabel.visibility = View.GONE
                bind.voiceSearchFallbackList.visibility = View.GONE
            }
            is VoiceSearchState.Listening -> {
                bind.voiceSearchStatus.text = getString(R.string.voice_search_listening)
                bind.voiceSearchStatus.visibility = View.VISIBLE
                bind.voiceSearchFallbackLabel.visibility = View.GONE
                bind.voiceSearchFallbackList.visibility = View.GONE
            }
            is VoiceSearchState.Searching -> {
                bind.voiceSearchStatus.text = getString(R.string.voice_search_searching)
                bind.voiceSearchStatus.visibility = View.VISIBLE
                bind.voiceSearchFallbackLabel.visibility = View.GONE
                bind.voiceSearchFallbackList.visibility = View.GONE
            }
            is VoiceSearchState.Playing -> {
                val label = formatSongLabel(state.song)
                bind.voiceSearchStatus.text = getString(R.string.voice_search_playing, label)
                bind.voiceSearchStatus.visibility = View.VISIBLE
                bind.voiceSearchFallbackLabel.visibility = View.GONE
                bind.voiceSearchFallbackList.visibility = View.GONE
                playNow(listOf(state.song), 0)
            }
            is VoiceSearchState.Ambiguous -> {
                bind.voiceSearchStatus.visibility = View.GONE
                bind.voiceSearchFallbackLabel.visibility = View.VISIBLE
                bind.voiceSearchFallbackList.visibility = View.VISIBLE
                adapter.submitList(state.candidates)
            }
            is VoiceSearchState.NoResults -> {
                bind.voiceSearchStatus.text = getString(R.string.voice_search_no_results, state.query)
                bind.voiceSearchStatus.visibility = View.VISIBLE
                bind.voiceSearchFallbackLabel.visibility = View.GONE
                bind.voiceSearchFallbackList.visibility = View.GONE
            }
            is VoiceSearchState.Error -> {
                bind.voiceSearchStatus.text = state.message
                bind.voiceSearchStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun playNow(songs: List<Child>, startIndex: Int) {
        MediaManager.startQueue(mediaBrowserFuture, songs, startIndex)
    }

    private fun formatSongLabel(song: Child): String {
        val title = song.title ?: ""
        val artist = song.artist
        return if (artist != null) "$title — $artist" else title
    }
}
