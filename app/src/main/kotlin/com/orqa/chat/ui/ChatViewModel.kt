package com.orqa.chat.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orqa.chat.data.*
import com.orqa.chat.llm.ChatMessage
import com.orqa.chat.llm.LlmService
import com.orqa.chat.search.CategoryMap
import com.orqa.chat.search.SearchEngine
import com.orqa.chat.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db       = AppDatabase.get(app)
    val settings         = SettingsStore(app)
    private val search   = SearchEngine(db.chunkDao())
    private val llm      = LlmService(settings)

    // ── UI State ──────────────────────────────────────────────────

    data class UiState(
        val messages: List<MessageEntity>  = emptyList(),
        val isLoading: Boolean             = false,
        val inputText: String              = "",
        val attachedImageUri: Uri?         = null,
        val attachedImageB64: String       = "",
        val attachedImageMime: String      = "",
        val chunkCount: Int                = 0,
        val syncStatus: String             = "",
        val isSyncing: Boolean             = false,
        val chatMode: String               = "troubleshoot",
        val provider: String               = "gemini",
        val model: String                  = "gemini-2.0-flash",
        val error: String                  = ""
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe messages
            db.messageDao().observeAll().collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch {
            // Observe settings
            combine(
                settings.provider,
                settings.model,
                settings.chatMode
            ) { provider, model, mode ->
                Triple(provider, model, mode)
            }.collect { (provider, model, mode) ->
                _state.update { it.copy(provider = provider, model = model, chatMode = mode) }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(chunkCount = db.chunkDao().totalCount()) }
        }
        // Seed default sync sources if empty
        viewModelScope.launch {
            if (db.syncSourceDao().getAll().isEmpty()) {
                SyncManager.DEFAULT_SOURCES.forEach { db.syncSourceDao().upsert(it) }
            }
        }
    }

    // ── Input handling ────────────────────────────────────────────

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text) }

    fun onModeToggle() {
        val newMode = if (_state.value.chatMode == "troubleshoot") "engcall" else "troubleshoot"
        viewModelScope.launch { settings.set(SettingsStore.CHAT_MODE, newMode) }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                val bytes  = stream?.readBytes() ?: return@launch
                val b64    = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime   = getApplication<Application>().contentResolver.getType(uri) ?: "image/jpeg"
                _state.update { it.copy(
                    attachedImageUri  = uri,
                    attachedImageB64  = b64,
                    attachedImageMime = mime
                )}
            } catch (e: Exception) {
                _state.update { it.copy(error = "Image error: ${e.message}") }
            }
        }
    }

    fun clearImage() = _state.update { it.copy(
        attachedImageUri  = null,
        attachedImageB64  = "",
        attachedImageMime = ""
    )}

    fun clearError() = _state.update { it.copy(error = "") }

    // ── Send message ──────────────────────────────────────────────

    fun send() {
        val text  = _state.value.inputText.trim()
        val imgB64  = _state.value.attachedImageB64
        val imgMime = _state.value.attachedImageMime
        if (text.isEmpty() && imgB64.isEmpty()) return
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, inputText = "", error = "") }
            clearImage()

            // Save user message
            val userMsg = MessageEntity(
                role        = "user",
                content     = text,
                imageBase64 = imgB64,
                imageMime   = imgMime
            )
            db.messageDao().insert(userMsg)

            // Search docs
            val hits = search.search(text.ifEmpty { "image analysis" })
            val category = CategoryMap.detect(text)

            // Build context
            val docContext = if (hits.isNotEmpty()) {
                "Retrieved from local docs:\n\n" + hits.joinToString("\n\n---\n\n") {
                    "[${it.chunk.folder} / ${it.chunk.source}]\n${it.chunk.text}"
                }
            } else {
                "No matching docs found. Answer from general ORQA knowledge."
            }

            val mode = _state.value.chatMode
            val modeInstruction = if (mode == "engcall") {
                "ENGINEER CALL MODE: max 6 lines, lead with the answer, plain lists only."
            } else {
                "TROUBLESHOOT MODE: detailed step-by-step answers, cite source files."
            }

            val system = """
You are an expert assistant for ORQA FPV flight controllers — QuadCore H7, WingCore H7, 3030 Lite F405, 3030 ESC, DTK APB, and the H503 OSD firmware rewrite project.

$modeInstruction

${if (category.isNotEmpty()) "Query category: $category\n\n" else ""}$docContext
            """.trimIndent()

            // Build message history for LLM (last 10 turns)
            val history = db.messageDao().getRecent()
                .takeLast(10)
                .map { msg ->
                    ChatMessage(
                        role        = msg.role,
                        content     = msg.content,
                        imageBase64 = msg.imageBase64,
                        imageMime   = msg.imageMime
                    )
                }

            // Call LLM
            val response = llm.chat(
                systemPrompt = system,
                messages     = history,
                provider     = _state.value.provider,
                model        = _state.value.model
            )

            val sourcesJson = JSONArray(hits.map { "[${it.chunk.folder}] ${it.chunk.source}" }).toString()

            // Save assistant message
            db.messageDao().insert(MessageEntity(
                role     = "assistant",
                content  = if (response.error.isEmpty()) response.text else "Error: ${response.error}",
                sources  = sourcesJson,
                category = category
            ))

            _state.update { it.copy(
                isLoading = false,
                error     = response.error
            )}
        }
    }

    fun clearChat() {
        viewModelScope.launch { db.messageDao().clearAll() }
    }

    // ── Sync ──────────────────────────────────────────────────────

    val syncSources: StateFlow<List<SyncSource>> = db.syncSourceDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSyncing = true, syncStatus = "Syncing all sources...") }
            val token = settings.getString(SettingsStore.GITHUB_TOKEN)
            val mgr   = SyncManager(db, token)
            val results = mgr.syncAll()
            val totalChunks = results.sumOf { it.chunksAdded }
            val errors = results.filter { it.error.isNotEmpty() }.map { it.error }
            _state.update { it.copy(
                isSyncing   = false,
                syncStatus  = "Synced: $totalChunks chunks added" +
                    if (errors.isNotEmpty()) "\nErrors: ${errors.joinToString("; ")}" else "",
                chunkCount  = db.chunkDao().totalCount()
            )}
        }
    }

    fun syncOne(source: SyncSource) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSyncing = true, syncStatus = "Syncing ${source.label}...") }
            val token  = settings.getString(SettingsStore.GITHUB_TOKEN)
            val mgr    = SyncManager(db, token)
            val result = mgr.syncSource(source)
            _state.update { it.copy(
                isSyncing  = false,
                syncStatus = if (result.error.isEmpty())
                    "${source.label}: ${result.chunksAdded} chunks" else result.error,
                chunkCount = db.chunkDao().totalCount()
            )}
        }
    }

    fun addSyncSource(source: SyncSource) {
        viewModelScope.launch { db.syncSourceDao().upsert(source) }
    }

    fun deleteSyncSource(source: SyncSource) {
        viewModelScope.launch {
            db.syncSourceDao().delete(source)
            db.chunkDao().deleteBySyncSource(source.syncSource())
        }
    }

    fun toggleSyncSource(source: SyncSource) {
        viewModelScope.launch {
            db.syncSourceDao().upsert(source.copy(enabled = !source.enabled))
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            db.chunkDao().deleteAll()
            _state.update { it.copy(chunkCount = 0, syncStatus = "Cache cleared") }
        }
    }

    private fun SyncSource.syncSource(): String = when (type) {
        "github" -> "github:${url.substringAfter("/")}"
        else     -> "public"
    }
}
