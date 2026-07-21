package com.cappielloantonio.tempo.voice

import android.os.Bundle
import android.provider.MediaStore
import com.cappielloantonio.tempo.subsonic.models.Child

/**
 * Resolves Google Assistant / MEDIA_PLAY_FROM_SEARCH queries into a playable queue
 * using the same parser + search/rank pipeline as in-app voice search.
 */
object PlayFromSearchUseCase {

    private const val STRONG_MATCH_SCORE = 80
    private const val STRONG_NAME_SCORE = 70

    data class Result(
        val songs: List<Child>,
        val startIndex: Int = 0
    )

    fun resolveQueue(query: String?, extras: Bundle? = null): Result {
        val parsed = parseAssistantQuery(query, extras) ?: return Result(emptyList())
        val resolution = resolvePreferringCollections(parsed)
        val songs = resolution.songs
        if (songs.isEmpty()) return Result(emptyList())

        if (resolution.isCollection) {
            return Result(songs, startIndex = 0)
        }

        val ranked = if (resolution.preserveOrder) {
            songs
        } else {
            SearchAndRankUseCase.rank(songs, parsed)
        }

        val best = ranked.first()
        val bestScore = scoreBest(best, parsed)

        return when {
            bestScore >= STRONG_MATCH_SCORE -> Result(listOf(best))
            ranked.size >= 2 -> Result(listOf(best))
            else -> Result(listOf(best))
        }
    }

    private fun resolvePreferringCollections(parsed: ParsedQuery): SearchResolution {
        if (parsed.type != QueryType.UNKNOWN && parsed.type != QueryType.SONG) {
            return SearchAndRankUseCase.resolve(parsed)
        }

        // Bare Assistant queries like "michael jackson" often omit "artist"/"album".
        // Prefer a strong artist/album collection match before falling back to song search.
        if (parsed.type == QueryType.UNKNOWN) {
            val artistQuery = ParsedQuery(
                type = QueryType.ARTIST,
                title = null,
                artist = parsed.rawQuery,
                rawQuery = parsed.rawQuery
            )
            val artistResolution = SearchAndRankUseCase.resolve(artistQuery)
            if (artistResolution.isCollection && artistResolution.songs.isNotEmpty()) {
                val artistName = artistResolution.songs.first().artist
                if (scoreName(artistName, parsed.rawQuery) >= STRONG_NAME_SCORE) {
                    return artistResolution
                }
            }

            val albumQuery = ParsedQuery(
                type = QueryType.ALBUM,
                title = parsed.rawQuery,
                artist = null,
                rawQuery = parsed.rawQuery
            )
            val albumResolution = SearchAndRankUseCase.resolve(albumQuery)
            if (albumResolution.isCollection && albumResolution.songs.isNotEmpty()) {
                val albumName = albumResolution.songs.first().album
                if (scoreName(albumName, parsed.rawQuery) >= STRONG_NAME_SCORE) {
                    return albumResolution
                }
            }
        }

        return SearchAndRankUseCase.resolve(parsed)
    }

    fun parseAssistantQuery(query: String?, extras: Bundle?): ParsedQuery? {
        val structured = parseFromMediaExtras(extras)
        if (structured != null) return structured

        val cleaned = cleanQuery(query)
        if (cleaned.isNullOrBlank()) return null
        return VoiceQueryParser.parse(cleaned)
    }

    private fun parseFromMediaExtras(extras: Bundle?): ParsedQuery? {
        if (extras == null) return null

        val focus = extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        val artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
        val album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.trim().orEmpty()
        val title = extras.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
        val genre = extras.getString(MediaStore.EXTRA_MEDIA_GENRE)?.trim().orEmpty()

        return when (focus) {
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                if (artist.isEmpty()) null
                else ParsedQuery(QueryType.ARTIST, null, artist, artist)
            }
            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                if (album.isEmpty()) null
                else ParsedQuery(QueryType.ALBUM, album, artist.ifEmpty { null }, album)
            }
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
                when {
                    title.isNotEmpty() && artist.isNotEmpty() ->
                        ParsedQuery(QueryType.SONG, title, artist, "$title by $artist")
                    title.isNotEmpty() ->
                        ParsedQuery(QueryType.SONG, title, null, title)
                    artist.isNotEmpty() ->
                        ParsedQuery(QueryType.ARTIST, null, artist, artist)
                    else -> null
                }
            }
            MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                if (genre.isEmpty()) null
                else ParsedQuery(QueryType.UNKNOWN, null, null, genre)
            }
            else -> {
                when {
                    title.isNotEmpty() && artist.isNotEmpty() ->
                        ParsedQuery(QueryType.SONG, title, artist, "$title by $artist")
                    album.isNotEmpty() ->
                        ParsedQuery(QueryType.ALBUM, album, artist.ifEmpty { null }, album)
                    artist.isNotEmpty() && title.isEmpty() ->
                        ParsedQuery(QueryType.ARTIST, null, artist, artist)
                    title.isNotEmpty() ->
                        ParsedQuery(QueryType.SONG, title, null, title)
                    else -> null
                }
            }
        }
    }

    private fun cleanQuery(query: String?): String? {
        if (query.isNullOrBlank()) return query
        return query
            .trim()
            .replace(
                Regex("""^(?:play|hear|listen to)\s+""", RegexOption.IGNORE_CASE),
                ""
            )
            .replace(
                Regex(
                    """\s+(?:on|in)\s+(?:the\s+)?(?:tempus|tempo)(?:\s+app)?\s*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
            .ifBlank { query.trim() }
    }

    private fun scoreBest(song: Child, parsed: ParsedQuery): Int {
        fun normalize(s: String) =
            s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()

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
            QueryType.PLAYLIST -> {
                val raw = normalize(parsed.title ?: parsed.rawQuery)
                if (title == raw) 80 else if (title.contains(raw)) 40 else 10
            }
            QueryType.UNKNOWN -> {
                val raw = normalize(parsed.rawQuery)
                if (title == raw) 80 else if (title.contains(raw)) 40 else 10
            }
        }
    }

    private fun scoreName(value: String?, query: String): Int {
        val name = normalize(value ?: "")
        val q = normalize(query)
        if (q.isEmpty() || name.isEmpty()) return 0
        return when {
            name == q -> 100
            name.startsWith(q) -> 70
            name.contains(q) -> 50
            q.contains(name) -> 40
            else -> 0
        }
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()
}
