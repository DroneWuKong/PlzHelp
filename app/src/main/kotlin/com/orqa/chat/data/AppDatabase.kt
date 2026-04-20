package com.orqa.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ── Room Database ─────────────────────────────────────────────────

@Database(
    entities = [ChunkEntity::class, SyncSource::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao
    abstract fun syncSourceDao(): SyncSourceDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "orqa_chat.db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// ── Settings DataStore ────────────────────────────────────────────

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("orqa_settings")

class SettingsStore(private val context: Context) {

    companion object {
        val GEMINI_KEY         = stringPreferencesKey("gemini_key")
        val ANTHROPIC_KEY      = stringPreferencesKey("anthropic_key")
        val PROVIDER           = stringPreferencesKey("provider")         // gemini | anthropic | local
        val MODEL              = stringPreferencesKey("model")
        val LOCAL_SERVER_URL   = stringPreferencesKey("local_server_url") // http://192.168.x.x:5000
        val GITHUB_TOKEN       = stringPreferencesKey("github_token")
        val GITHUB_USERNAME    = stringPreferencesKey("github_username")
        val AUTO_SYNC_ENABLED  = booleanPreferencesKey("auto_sync")
        val CHAT_MODE          = stringPreferencesKey("chat_mode")        // troubleshoot | engcall
    }

    val provider: Flow<String>   = context.dataStore.data.map { it[PROVIDER] ?: "gemini" }
    val model: Flow<String>      = context.dataStore.data.map { it[MODEL]    ?: "gemini-2.0-flash" }
    val chatMode: Flow<String>   = context.dataStore.data.map { it[CHAT_MODE] ?: "troubleshoot" }
    val autoSync: Flow<Boolean>  = context.dataStore.data.map { it[AUTO_SYNC_ENABLED] ?: true }

    suspend fun getString(key: Preferences.Key<String>): String =
        context.dataStore.data.map { it[key] ?: "" }.let { flow ->
            var result = ""
            flow.collect { result = it; return@collect }
            result
        }

    suspend fun set(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    fun observe(key: Preferences.Key<String>): Flow<String> =
        context.dataStore.data.map { it[key] ?: "" }
}
