package com.orqa.chat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────────────────

@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val source: String,       // filename
    val folder: String,       // label (forge, manuals, handbook, etc.)
    val syncSource: String,   // "github", "public", "local", "manual"
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_sources")
data class SyncSource(
    @PrimaryKey val id: String,            // e.g. "github:droneclear_Forge"
    val type: String,                      // "github" | "public_url" | "local_server"
    val label: String,                     // display name
    val url: String,                       // base URL or API path
    val enabled: Boolean = true,
    val lastSyncAt: Long = 0L,
    val fileCount: Int = 0,
    val chunkCount: Int = 0,
    val errorMsg: String = ""
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,          // "user" | "assistant"
    val content: String,
    val imageBase64: String = "",
    val imageMime: String = "",
    val sources: String = "",  // JSON array of source strings
    val category: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ── DAOs ──────────────────────────────────────────────────────────

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE folder = :folder")
    suspend fun getByFolder(folder: String): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM chunks WHERE syncSource = :src")
    suspend fun countBySource(src: String): Int

    @Query("DELETE FROM chunks WHERE folder = :folder")
    suspend fun deleteByFolder(folder: String)

    @Query("DELETE FROM chunks WHERE syncSource = :src")
    suspend fun deleteBySyncSource(src: String)

    @Query("DELETE FROM chunks")
    suspend fun deleteAll()

    // Simple keyword search — returns chunks containing any of the terms
    @Query("""
        SELECT * FROM chunks 
        WHERE text LIKE '%' || :term || '%'
        LIMIT 200
    """)
    suspend fun searchByTerm(term: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<ChunkEntity>
}

@Dao
interface SyncSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SyncSource)

    @Query("SELECT * FROM sync_sources ORDER BY label ASC")
    fun observeAll(): Flow<List<SyncSource>>

    @Query("SELECT * FROM sync_sources ORDER BY label ASC")
    suspend fun getAll(): List<SyncSource>

    @Query("SELECT * FROM sync_sources WHERE enabled = 1")
    suspend fun getEnabled(): List<SyncSource>

    @Delete
    suspend fun delete(source: SyncSource)

    @Query("UPDATE sync_sources SET lastSyncAt = :ts, fileCount = :files, chunkCount = :chunks, errorMsg = '' WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long, files: Int, chunks: Int)

    @Query("UPDATE sync_sources SET errorMsg = :msg WHERE id = :id")
    suspend fun markError(id: String, msg: String)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecent(): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
