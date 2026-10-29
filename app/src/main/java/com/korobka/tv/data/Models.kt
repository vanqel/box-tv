package com.korobka.tv.data

data class MediaItem(
    val id: String,
    val title: String,
    val origTitle: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val ratingKp: Float? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    val isSeries: Boolean = false,
    val seasonsCount: Int? = null
)

data class Episode(
    val episodeNumber: Int,
    val title: String,
    val hlsUrl: String? = null,
    val posterUrl: String? = null
)

data class Season(
    val seasonNumber: Int,
    val episodes: List<Episode>
)

data class Translation(
    val id: String,
    val name: String
)

data class StreamSource(
    val streamUrl: String,
    val isHls: Boolean,
    val referer: String,
    val seasons: List<Season>,
    val translations: List<Translation>,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,
    val currentTranslation: String = "Русский дубляж"
)

data class PlaybackHistoryItem(
    val itemId: String,
    val title: String,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val translation: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val streamUrl: String? = null
)
