package com.cappielloantonio.tempo.voice

data class ParsedQuery(
    val type: QueryType,
    val title: String?,
    val artist: String?,
    val rawQuery: String
)

enum class QueryType { SONG, ARTIST, ALBUM, UNKNOWN }

object VoiceQueryParser {

    private val SONG_BY_PATTERNS = listOf(
        Regex("""(?:play|hear|listen to)\s+(?:song\s+)?(.+?)\s+by\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(.+?)\s+by\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val SONG_PATTERNS = listOf(
        Regex("""(?:play|hear|listen to)\s+(?:song\s+|track\s+)(.+)""", RegexOption.IGNORE_CASE)
    )

    private val ARTIST_PATTERNS = listOf(
        Regex("""(?:play|hear|listen to)\s+(?:artist\s+|music by\s+|songs by\s+|everything by\s+)(.+)""", RegexOption.IGNORE_CASE)
    )

    private val ALBUM_PATTERNS = listOf(
        Regex("""(?:play|hear|listen to)\s+(?:album\s+)(.+)""", RegexOption.IGNORE_CASE)
    )

    fun parse(raw: String): ParsedQuery {
        val q = raw.trim()

        for (pattern in SONG_BY_PATTERNS) {
            val m = pattern.find(q) ?: continue
            if (m.groupValues.size >= 3) {
                return ParsedQuery(QueryType.SONG, m.groupValues[1].trim(), m.groupValues[2].trim(), q)
            }
        }

        for (pattern in ALBUM_PATTERNS) {
            val m = pattern.find(q) ?: continue
            return ParsedQuery(QueryType.ALBUM, m.groupValues[1].trim(), null, q)
        }

        for (pattern in ARTIST_PATTERNS) {
            val m = pattern.find(q) ?: continue
            return ParsedQuery(QueryType.ARTIST, null, m.groupValues[1].trim(), q)
        }

        for (pattern in SONG_PATTERNS) {
            val m = pattern.find(q) ?: continue
            return ParsedQuery(QueryType.SONG, m.groupValues[1].trim(), null, q)
        }

        return ParsedQuery(QueryType.UNKNOWN, null, null, q)
    }
}
