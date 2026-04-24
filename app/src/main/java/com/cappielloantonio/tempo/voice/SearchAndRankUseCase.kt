package com.cappielloantonio.tempo.voice

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.subsonic.models.Child
import java.io.IOException

data class RankedResult(val song: Child, val score: Int)

object SearchAndRankUseCase {

    fun search(query: String): List<Child> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .searchingClient
                .search3(query, 50, 0, 0, 0, 0, 0)
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.searchResult3?.songs ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun rank(songs: List<Child>, parsed: ParsedQuery): List<Child> {
        return songs
            .map { RankedResult(it, score(it, parsed)) }
            .sortedByDescending { it.score }
            .map { it.song }
    }

    private fun score(song: Child, parsed: ParsedQuery): Int {
        var s = 0
        val title = normalize(song.title ?: "")
        val artist = normalize(song.artist ?: "")

        val queryTitle = normalize(parsed.title ?: parsed.rawQuery)
        val queryArtist = normalize(parsed.artist ?: "")

        when (parsed.type) {
            QueryType.SONG -> {
                if (title == queryTitle) s += 100
                else if (title.startsWith(queryTitle)) s += 60
                else if (title.contains(queryTitle)) s += 30

                if (queryArtist.isNotEmpty()) {
                    if (artist == queryArtist) s += 50
                    else if (artist.contains(queryArtist)) s += 20
                }
            }
            QueryType.ARTIST -> {
                val qArtist = normalize(parsed.artist ?: parsed.rawQuery)
                if (artist == qArtist) s += 100
                else if (artist.startsWith(qArtist)) s += 60
                else if (artist.contains(qArtist)) s += 30
            }
            QueryType.ALBUM -> {
                val album = normalize(song.album ?: "")
                val qAlbum = normalize(parsed.title ?: parsed.rawQuery)
                if (album == qAlbum) s += 100
                else if (album.startsWith(qAlbum)) s += 60
                else if (album.contains(qAlbum)) s += 30
            }
            QueryType.UNKNOWN -> {
                val raw = normalize(parsed.rawQuery)
                if (title == raw) s += 80
                else if (title.startsWith(raw)) s += 50
                else if (title.contains(raw)) s += 20
                if (artist.contains(raw)) s += 15
            }
        }

        return s
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
}
