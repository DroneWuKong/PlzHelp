package com.orqa.chat.sync

import android.util.Base64
import com.orqa.chat.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SyncResult(
    val sourceId: String,
    val filesProcessed: Int,
    val chunksAdded: Int,
    val error: String = ""
)

class SyncManager(
    private val db: AppDatabase,
    private val githubToken: String = "",
    private val githubUser: String = "DroneWuKong"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val CHUNK_SIZE    = 500   // words per chunk
        private const val CHUNK_OVERLAP = 60
        private const val MAX_FILE_KB   = 1500  // skip files over this size

        // Default public sources — editable in settings
        val DEFAULT_SOURCES = listOf(
            SyncSource(
                id       = "public:forge_troubleshooting",
                type     = "public_url",
                label    = "Forge Troubleshooting KB",
                url      = "https://forgeprole.netlify.app/forge_troubleshooting.json"
            ),
            SyncSource(
                id       = "github:drone-integration-handbook",
                type     = "github",
                label    = "Drone Integration Handbook",
                url      = "DroneWuKong/drone-integration-handbook"
            ),
            SyncSource(
                id       = "github:droneclear_Forge",
                type     = "github",
                label    = "DroneClear Forge",
                url      = "DroneWuKong/droneclear_Forge"
            ),
            SyncSource(
                id       = "github:orqa-h7quadcore-px4",
                type     = "github",
                label    = "ORQA PX4 Port",
                url      = "DroneWuKong/orqa-h7quadcore-px4"
            ),
        )
    }

    // ── Main sync entry point ─────────────────────────────────────

    suspend fun syncAll(): List<SyncResult> = withContext(Dispatchers.IO) {
        val sources = db.syncSourceDao().getEnabled()
        sources.map { syncSource(it) }
    }

    suspend fun syncSource(source: SyncSource): SyncResult = withContext(Dispatchers.IO) {
        try {
            val result = when (source.type) {
                "github"     -> syncGitHub(source)
                "public_url" -> syncPublicUrl(source)
                else         -> SyncResult(source.id, 0, 0, "Unknown source type: ${source.type}")
            }
            if (result.error.isEmpty()) {
                db.syncSourceDao().markSynced(
                    source.id, System.currentTimeMillis(),
                    result.filesProcessed, result.chunksAdded
                )
            } else {
                db.syncSourceDao().markError(source.id, result.error)
            }
            result
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown error"
            db.syncSourceDao().markError(source.id, msg)
            SyncResult(source.id, 0, 0, msg)
        }
    }

    // ── GitHub sync ───────────────────────────────────────────────

    private fun syncGitHub(source: SyncSource): SyncResult {
        val repo      = source.url  // e.g. "DroneWuKong/droneclear_Forge"
        val label     = source.label
        val fileList  = getGitHubFileList(repo)

        if (fileList.isEmpty()) {
            return SyncResult(source.id, 0, 0, "No files returned from GitHub API. Check token and repo name.")
        }

        // Clear old chunks for this source
        db.chunkDao().apply { }  // will be called via blocking below
        var filesProcessed = 0
        var chunksAdded    = 0
        val newChunks      = mutableListOf<ChunkEntity>()

        for (file in fileList) {
            val path = file.getString("path")
            val size = file.optInt("size", 0)

            // Filter to useful files
            if (!isUsefulFile(path)) continue
            if (size > MAX_FILE_KB * 1024) continue

            val content = fetchGitHubFile(repo, path) ?: continue
            val chunks  = chunkText(content, path, label, "github")
            newChunks.addAll(chunks)
            filesProcessed++
        }

        // Replace old chunks atomically
        runBlocking {
            db.chunkDao().deleteBySyncSource("github:${repo.substringAfter("/")}")
            db.chunkDao().insertAll(newChunks)
        }
        chunksAdded = newChunks.size

        return SyncResult(source.id, filesProcessed, chunksAdded)
    }

    private fun getGitHubFileList(repo: String): List<JSONObject> {
        // Use git trees API to get full file list in one call
        val url = "https://api.github.com/repos/$repo/git/trees/HEAD?recursive=1"
        val response = githubGet(url)
        return try {
            val tree = JSONObject(response).getJSONArray("tree")
            (0 until tree.length())
                .map { tree.getJSONObject(it) }
                .filter { it.getString("type") == "blob" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchGitHubFile(repo: String, path: String): String? {
        return try {
            // Try raw content first (faster, no base64 decode needed)
            val branch  = "main"   // try main, fall back to master
            val rawUrl  = "https://raw.githubusercontent.com/$repo/$branch/$path"
            val content = httpGet(rawUrl)
            if (content.startsWith("404")) {
                val masterUrl = "https://raw.githubusercontent.com/$repo/master/$path"
                val c2 = httpGet(masterUrl)
                if (c2.startsWith("404")) null else c2
            } else {
                content
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Public URL sync ───────────────────────────────────────────

    private fun syncPublicUrl(source: SyncSource): SyncResult {
        val content = httpGet(source.url)
        if (content.isBlank()) {
            return SyncResult(source.id, 0, 0, "Empty response from ${source.url}")
        }

        val text = when {
            source.url.endsWith(".json") -> flattenJson(content, source.label)
            else                         -> content
        }

        val chunks = chunkText(text, source.label, source.label, "public")

        runBlocking {
            db.chunkDao().deleteBySyncSource("public")
            db.chunkDao().insertAll(chunks)
        }

        return SyncResult(source.id, 1, chunks.size)
    }

    // ── Text chunking ─────────────────────────────────────────────

    private fun chunkText(
        text: String,
        source: String,
        folder: String,
        syncSource: String
    ): List<ChunkEntity> {
        val words  = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = mutableListOf<ChunkEntity>()
        var i      = 0
        while (i < words.size) {
            val chunk = words.subList(i, minOf(i + CHUNK_SIZE, words.size)).joinToString(" ")
            if (chunk.length > 20) {
                result.add(ChunkEntity(
                    text       = chunk.take(2000),
                    source     = source,
                    folder     = folder,
                    syncSource = syncSource
                ))
            }
            i += CHUNK_SIZE - CHUNK_OVERLAP
        }
        return result
    }

    // ── JSON flattening ───────────────────────────────────────────

    private fun flattenJson(json: String, label: String): String {
        return try {
            val lines = mutableListOf("File: $label")
            val arr   = JSONArray(json)
            for (i in 0 until minOf(arr.length(), 500)) {
                val obj = arr.optJSONObject(i) ?: continue
                val line = buildString {
                    obj.keys().forEach { key ->
                        val v = obj.opt(key)?.toString() ?: return@forEach
                        if (v.isNotBlank() && v != "null") append("$key: $v | ")
                    }
                }
                if (line.isNotBlank()) lines.add(line.trimEnd(' ', '|'))
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            json.take(50_000)
        }
    }

    // ── File filter ───────────────────────────────────────────────

    private fun isUsefulFile(path: String): Boolean {
        val ext = path.substringAfterLast(".").lowercase()
        if (ext !in setOf("md", "txt", "json")) return false

        val skipPaths = setOf(
            "node_modules", ".git", "package-lock.json", "yarn.lock",
            "dist/", "build/", ".netlify"
        )
        if (skipPaths.any { it in path }) return false

        // For JSON — only useful ones
        if (ext == "json") {
            val name = path.substringAfterLast("/").lowercase()
            val allowedJson = setOf(
                "forge_troubleshooting.json",
                "session_context", "changelog", "todo",
                "category_map.json"
            )
            return allowedJson.any { it in name }
        }
        return true
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    private fun httpGet(url: String): String {
        val req = Request.Builder().url(url).get().build()
        return try {
            client.newCall(req).execute().body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun githubGet(url: String): String {
        val builder = Request.Builder().url(url)
        if (githubToken.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $githubToken")
        }
        builder.addHeader("Accept", "application/vnd.github+json")
        return try {
            client.newCall(builder.build()).execute().body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

// Blocking helper for use inside sync (already on IO dispatcher)
private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
