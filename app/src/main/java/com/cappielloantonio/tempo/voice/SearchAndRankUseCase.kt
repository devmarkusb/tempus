package com.cappielloantonio.tempo.voice

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.subsonic.models.AlbumID3
import com.cappielloantonio.tempo.subsonic.models.ArtistID3
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.Playlist
import java.io.IOException

data class RankedResult(val song: Child, val score: Int)
data class SearchResolution(
    val songs: List<Child>,
    val isCollection: Boolean = false,
    val preserveOrder: Boolean = false
)

object SearchAndRankUseCase {

    private const val SONG_PAGE_SIZE = 500
    private const val MAX_SONG_SEARCH_RESULTS = 1_000
    private const val COLLECTION_SEARCH_LIMIT = 50

    fun resolve(parsed: ParsedQuery): SearchResolution {
        return when (parsed.type) {
            QueryType.ARTIST -> resolveArtist(parsed) ?: SearchResolution(search(queryFor(parsed)))
            QueryType.ALBUM -> resolveAlbum(parsed) ?: SearchResolution(search(queryFor(parsed)))
            QueryType.PLAYLIST -> resolvePlaylist(parsed) ?: SearchResolution(emptyList())
            QueryType.SONG,
            QueryType.UNKNOWN -> SearchResolution(search(queryFor(parsed)))
        }
    }

    fun search(query: String): List<Child> {
        val songs = mutableListOf<Child>()
        var offset = 0

        while (songs.size < MAX_SONG_SEARCH_RESULTS) {
            val page = searchSongPage(query, SONG_PAGE_SIZE, offset)
            if (page.isEmpty()) break

            val remaining = MAX_SONG_SEARCH_RESULTS - songs.size
            songs.addAll(page.take(remaining))
            if (page.size < SONG_PAGE_SIZE) break

            offset += page.size
        }

        return songs
    }

    private fun searchSongPage(query: String, count: Int, offset: Int): List<Child> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .searchingClient
                .search3(query, count, offset, 0, 0, 0, 0)
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

    private fun resolveArtist(parsed: ParsedQuery): SearchResolution? {
        val query = parsed.artist ?: parsed.rawQuery
        val artist = searchArtists(query)
            .map { RankedArtist(it, scoreName(it.name, query)) }
            .filter { it.score > 0 }
            .maxByOrNull { it.score }
            ?.artist
            ?: return null

        val artistId = artist.id ?: return null
        val songs = getArtistSongs(artistId)
        return songs.takeIf { it.isNotEmpty() }?.let {
            SearchResolution(it, isCollection = true, preserveOrder = true)
        }
    }

    private fun resolveAlbum(parsed: ParsedQuery): SearchResolution? {
        val query = parsed.title ?: parsed.rawQuery
        val album = searchAlbums(query)
            .map { RankedAlbum(it, scoreName(it.name, query)) }
            .filter { it.score > 0 }
            .maxByOrNull { it.score }
            ?.album
            ?: return null

        val albumId = album.id ?: return null
        val songs = getAlbumSongs(albumId)
        return songs.takeIf { it.isNotEmpty() }?.let {
            SearchResolution(it, isCollection = true, preserveOrder = true)
        }
    }

    private fun resolvePlaylist(parsed: ParsedQuery): SearchResolution? {
        val query = parsed.title ?: parsed.rawQuery
        val playlist = getPlaylists()
            .map { RankedPlaylist(it, scoreName(it.name, query)) }
            .filter { it.score > 0 }
            .maxByOrNull { it.score }
            ?.playlist
            ?: return null

        val songs = getPlaylistSongs(playlist.id)
        return songs.takeIf { it.isNotEmpty() }?.let {
            SearchResolution(it, isCollection = true, preserveOrder = true)
        }
    }

    private fun searchArtists(query: String): List<ArtistID3> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .searchingClient
                .search3(query, 0, 0, 0, 0, COLLECTION_SEARCH_LIMIT, 0)
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.searchResult3?.artists ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun searchAlbums(query: String): List<AlbumID3> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .searchingClient
                .search3(query, 0, 0, COLLECTION_SEARCH_LIMIT, 0, 0, 0)
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.searchResult3?.albums ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun getArtistSongs(artistId: String): List<Child> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .browsingClient
                .getArtist(artistId)
                .execute()

            if (!response.isSuccessful) return emptyList()

            response.body()
                ?.subsonicResponse
                ?.artist
                ?.albums
                ?.flatMap { album ->
                    album.id?.let { getAlbumSongs(it) } ?: emptyList()
                }
                ?: emptyList()
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun getAlbumSongs(albumId: String): List<Child> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .browsingClient
                .getAlbum(albumId)
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.album?.songs ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun getPlaylists(): List<Playlist> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .playlistClient
                .getPlaylists()
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.playlists?.playlists ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun getPlaylistSongs(playlistId: String): List<Child> {
        return try {
            val response = App.getSubsonicClientInstance(false)
                .playlistClient
                .getPlaylist(playlistId)
                .execute()
            if (response.isSuccessful) {
                response.body()?.subsonicResponse?.playlist?.entries ?: emptyList()
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
            QueryType.PLAYLIST -> {
                val raw = normalize(parsed.title ?: parsed.rawQuery)
                if (title == raw) s += 40
                else if (title.startsWith(raw)) s += 20
                else if (title.contains(raw)) s += 10
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

    private fun scoreName(value: String?, query: String): Int {
        val name = normalize(value ?: "")
        val q = normalize(query)
        if (q.isEmpty()) return 0
        if (name.isEmpty()) return 0

        return when {
            name == q -> 100
            name.startsWith(q) -> 70
            name.contains(q) -> 50
            q.contains(name) -> 40
            else -> 0
        }
    }

    private fun queryFor(parsed: ParsedQuery): String = when (parsed.type) {
        QueryType.SONG -> listOfNotNull(parsed.title, parsed.artist).joinToString(" ")
        QueryType.ARTIST -> parsed.artist ?: parsed.rawQuery
        QueryType.ALBUM,
        QueryType.PLAYLIST -> parsed.title ?: parsed.rawQuery
        QueryType.UNKNOWN -> parsed.rawQuery
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()

    private data class RankedArtist(val artist: ArtistID3, val score: Int)
    private data class RankedAlbum(val album: AlbumID3, val score: Int)
    private data class RankedPlaylist(val playlist: Playlist, val score: Int)
}
