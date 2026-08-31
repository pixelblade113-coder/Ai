package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application.applicationContext)

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String>("")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isBlank()) {
                MutableStateFlow(emptyList())
            } else {
                repository.getMessagesForSession(sessionId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastFailedPrompt = MutableStateFlow<String?>(null)

    init {
        // Initialize with default session or load previous active session
        viewModelScope.launch {
            repository.getAllSessions().collect { sessionList ->
                if (_currentSessionId.value.isBlank()) {
                    if (sessionList.isNotEmpty()) {
                        _currentSessionId.value = sessionList.first().id
                    } else {
                        val newId = repository.createNewSession("New Chat")
                        _currentSessionId.value = newId
                    }
                }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun sendMessage(customPrompt: String? = null) {
        val promptToSend = (customPrompt ?: _inputText.value).trim()
        if (promptToSend.isBlank() || _isLoading.value) return

        val sessionId = _currentSessionId.value
        if (sessionId.isBlank()) return

        _inputText.value = ""
        _isLoading.value = true
        _lastFailedPrompt.value = null

        viewModelScope.launch {
            val currentMessages = messages.value
            val result = repository.sendMessage(sessionId, promptToSend, currentMessages)
            _isLoading.value = false
            if (result.isFailure) {
                _lastFailedPrompt.value = promptToSend
            }
        }
    }

    fun retryLastMessage() {
        val failedPrompt = _lastFailedPrompt.value
        if (!failedPrompt.isNullOrBlank()) {
            sendMessage(failedPrompt)
        } else {
            // Find last user message
            val lastUserMsg = messages.value.lastOrNull { it.role == "user" }
            if (lastUserMsg != null) {
                sendMessage(lastUserMsg.content)
            }
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val newId = repository.createNewSession("New Chat")
            _currentSessionId.value = newId
            _inputText.value = ""
            _lastFailedPrompt.value = null
        }
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
        _inputText.value = ""
        _lastFailedPrompt.value = null
    }

    fun clearCurrentChat() {
        val sessionId = _currentSessionId.value
        if (sessionId.isNotBlank()) {
            viewModelScope.launch {
                repository.clearMessagesForSession(sessionId)
                _lastFailedPrompt.value = null
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = ""
            }
        }
    }

    fun getCustomApiKey(): String = repository.getCustomApiKey()

    fun saveCustomApiKey(key: String) {
        repository.setCustomApiKey(key)
    }

    fun hasBuildConfigKey(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }
}
