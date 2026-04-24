package com.cappielloantonio.tempo.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.voice.ParsedQuery
import com.cappielloantonio.tempo.voice.QueryType
import com.cappielloantonio.tempo.voice.SearchAndRankUseCase
import com.cappielloantonio.tempo.voice.VoiceQueryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class VoiceSearchState {
    object Idle : VoiceSearchState()
    object Listening : VoiceSearchState()
    object Searching : VoiceSearchState()
    data class Playing(val song: Child) : VoiceSearchState()
    data class Ambiguous(val candidates: List<Child>) : VoiceSearchState()
    data class NoResults(val query: String) : VoiceSearchState()
    data class Error(val message: String) : VoiceSearchState()
}

class VoiceSearchViewModel : ViewModel() {

    private val _state = MutableLiveData<VoiceSearchState>(VoiceSearchState.Idle)
    val state: LiveData<VoiceSearchState> = _state

    private val _transcript = MutableLiveData<String>("")
    val transcript: LiveData<String> = _transcript

    var lastParsed: ParsedQuery? = null
        private set

    fun onTranscript(text: String) {
        _transcript.value = text
        searchAndRank(text)
    }

    fun searchAndRank(query: String) {
        if (query.isBlank()) return
        _state.value = VoiceSearchState.Searching

        viewModelScope.launch {
            val parsed = VoiceQueryParser.parse(query)
            lastParsed = parsed

            val searchQuery = buildSearchQuery(parsed)
            val results = withContext(Dispatchers.IO) {
                SearchAndRankUseCase.search(searchQuery)
            }

            if (results.isEmpty()) {
                _state.value = VoiceSearchState.NoResults(query)
                return@launch
            }

            val ranked = SearchAndRankUseCase.rank(results, parsed)
            val best = ranked.first()
            val bestScore = computeBestScore(best, parsed)

            when {
                bestScore >= 80 -> _state.value = VoiceSearchState.Playing(best)
                ranked.size >= 2 -> _state.value = VoiceSearchState.Ambiguous(ranked.take(5))
                else -> _state.value = VoiceSearchState.Playing(best)
            }
        }
    }

    fun setListening() {
        _state.value = VoiceSearchState.Listening
    }

    fun setIdle() {
        _state.value = VoiceSearchState.Idle
    }

    fun setTranscriptText(text: String) {
        _transcript.value = text
    }

    private fun buildSearchQuery(parsed: ParsedQuery): String = when (parsed.type) {
        QueryType.SONG -> listOfNotNull(parsed.title, parsed.artist).joinToString(" ")
        QueryType.ARTIST -> parsed.artist ?: parsed.rawQuery
        QueryType.ALBUM -> parsed.title ?: parsed.rawQuery
        QueryType.UNKNOWN -> parsed.rawQuery
    }

    private fun computeBestScore(song: Child, parsed: ParsedQuery): Int {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()

        val title = normalize(song.title ?: "")
        val artist = normalize(song.artist ?: "")

        return when (parsed.type) {
            QueryType.SONG -> {
                val qt = normalize(parsed.title ?: parsed.rawQuery)
                val qa = normalize(parsed.artist ?: "")
                var s = 0
                if (title == qt) s += 100
                else if (title.startsWith(qt)) s += 60
                else if (title.contains(qt)) s += 30
                if (qa.isNotEmpty() && artist == qa) s += 50
                s
            }
            QueryType.ARTIST -> {
                val qa = normalize(parsed.artist ?: parsed.rawQuery)
                if (artist == qa) 100 else if (artist.contains(qa)) 60 else 20
            }
            QueryType.ALBUM -> {
                val album = normalize(song.album ?: "")
                val qal = normalize(parsed.title ?: parsed.rawQuery)
                if (album == qal) 100 else if (album.contains(qal)) 60 else 20
            }
            QueryType.UNKNOWN -> {
                val raw = normalize(parsed.rawQuery)
                if (title == raw) 80 else if (title.contains(raw)) 40 else 10
            }
        }
    }
}
