package com.korenskoy.yandexmusic.data.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/** Track metadata, stored once however many lists contain the track. [json] is the serialized DTO. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val json: String
)

/**
 * One position of a track list: likes, a playlist, the wave queue, charts, history.
 * [owner] is the account's cache folder, or [CacheDatabase.SHARED] for lists common to all accounts.
 */
@Entity(
    tableName = "list_items",
    primaryKeys = ["owner", "list", "position"],
    indices = [Index("trackId")]
)
data class ListItemEntity(
    val owner: String,
    val list: String,
    val position: Int,
    val trackId: String
)

/** Small per-owner values: revisions, the playlist list. */
@Entity(tableName = "cache_values", primaryKeys = ["owner", "key"])
data class ValueEntity(
    val owner: String,
    val key: String,
    val value: String
)

@Dao
interface CacheDao {
    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Insert
    suspend fun insertItems(items: List<ListItemEntity>)

    @Query("DELETE FROM list_items WHERE owner = :owner AND list = :list")
    suspend fun deleteList(owner: String, list: String)

    /** Replaces a list in one transaction: a crash never leaves it half written. */
    @Transaction
    suspend fun replaceList(owner: String, list: String, tracks: List<TrackEntity>, ids: List<String>) {
        upsertTracks(tracks)
        deleteList(owner, list)
        insertItems(ids.mapIndexed { i, id -> ListItemEntity(owner, list, i, id) })
        deleteOrphanTracks() // e.g. tracks that left the wave queue
    }

    @Query(
        "SELECT t.json FROM list_items i JOIN tracks t ON t.id = i.trackId " +
            "WHERE i.owner = :owner AND i.list = :list ORDER BY i.position"
    )
    suspend fun loadTracks(owner: String, list: String): List<String>

    @Query("SELECT trackId FROM list_items WHERE owner = :owner AND list = :list ORDER BY position")
    suspend fun loadIds(owner: String, list: String): List<String>

    @Upsert
    suspend fun putValue(value: ValueEntity)

    @Query("SELECT value FROM cache_values WHERE owner = :owner AND `key` = :key")
    suspend fun getValue(owner: String, key: String): String?

    @Query("DELETE FROM cache_values WHERE owner = :owner AND `key` = :key")
    suspend fun deleteValue(owner: String, key: String)

    @Query("DELETE FROM list_items WHERE owner = :owner")
    suspend fun deleteOwnerItems(owner: String)

    @Query("DELETE FROM cache_values WHERE owner = :owner")
    suspend fun deleteOwnerValues(owner: String)

    @Transaction
    suspend fun deleteOwner(owner: String) {
        deleteOwnerItems(owner)
        deleteOwnerValues(owner)
        deleteOrphanTracks()
    }

    @Query("DELETE FROM list_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM cache_values")
    suspend fun deleteAllValues()

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Transaction
    suspend fun deleteAll() {
        deleteAllItems()
        deleteAllValues()
        deleteAllTracks()
    }

    /** Metadata no list refers to any more. */
    @Query("DELETE FROM tracks WHERE id NOT IN (SELECT trackId FROM list_items)")
    suspend fun deleteOrphanTracks()
}

@Database(entities = [TrackEntity::class, ListItemEntity::class, ValueEntity::class], version = 1)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun dao(): CacheDao

    companion object {
        /** Owner of the lists every account shares (charts). Never a sanitized folder name. */
        const val SHARED = "~shared"

        @Volatile
        private var instance: CacheDatabase? = null

        /** One database per process, also for tests that build their own cache manager. */
        fun get(context: Context): CacheDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CacheDatabase::class.java, "music_cache.db")
                .build()
                .also { instance = it }
        }
    }
}
