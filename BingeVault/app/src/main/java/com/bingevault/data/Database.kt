package com.bingevault.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── DAOs ──────────────────────────────────────────────────────────────────────

@Dao
interface MediaDao {

    @Transaction
    @Query("SELECT * FROM media_items ORDER BY title ASC")
    fun getAllWithSeasons(): Flow<List<MediaWithSeasons>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItem): Long

    @Update
    suspend fun updateMedia(item: MediaItem)

    @Delete
    suspend fun deleteMediaList(items: List<MediaItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<Season>)

    @Update
    suspend fun updateSeason(season: Season)

    @Query("DELETE FROM seasons WHERE mediaId = :mediaId")
    suspend fun deleteSeasonsForMedia(mediaId: Long)
}

// ── Type Converters ───────────────────────────────────────────────────────────

class Converters {
    @TypeConverter fun fromType(v: ContentType): String = v.name
    @TypeConverter fun toType(v: String): ContentType = ContentType.valueOf(v)
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [MediaItem::class, Season::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bingevault.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
