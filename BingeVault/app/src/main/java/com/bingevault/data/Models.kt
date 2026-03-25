package com.bingevault.data

import androidx.room.*

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class ContentType { MOVIE, SERIES, ANIME }

val ContentType.displayName
    get() = when (this) {
        ContentType.MOVIE -> "Movies"
        ContentType.SERIES -> "Series"
        ContentType.ANIME -> "Anime"
    }

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: ContentType,
    val tags: String = "",          // comma-separated
    val posterPath: String? = null, // filename inside /files/posters/
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "seasons",
    foreignKeys = [ForeignKey(
        entity = MediaItem::class,
        parentColumns = ["id"],
        childColumns = ["mediaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mediaId")]
)
data class Season(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long = 0,
    val name: String,
    val totalEpisodes: Int? = null,  // null = unknown
    val watchedEpisodes: Int = 0,
    val isOva: Boolean = false,
    val isFinished: Boolean = false,
    val sortOrder: Int = 0
)

// ── Relation ──────────────────────────────────────────────────────────────────

data class MediaWithSeasons(
    @Embedded val media: MediaItem,
    @Relation(parentColumn = "id", entityColumn = "mediaId", entity = Season::class)
    val seasons: List<Season>
)
