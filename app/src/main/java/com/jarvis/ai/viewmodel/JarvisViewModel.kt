package com.jarvis.ai.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.engine.JarvisEngine
import com.jarvis.ai.model.AttachedMedia
import com.jarvis.ai.model.ChatMessage
import com.jarvis.ai.model.JarvisStatus
import com.jarvis.ai.model.MemorySettings
import com.jarvis.ai.model.RamSnapshot
import com.jarvis.ai.util.ImageUtils
import com.jarvis.ai.util.JarvisParser
import com.jarvis.ai.voice.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class JarvisUiState(
    val status: JarvisStatus = JarvisStatus.NO_MODEL,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val partialVoiceText: String = "",
    val voiceAmplitude: Float = 0f,
    val attachedMedia: AttachedMedia? = null,
    val modelPath: String = "",
    val modelName: String = "Не выбрана",
    val mmprojPath: String = "",
    val mmprojName: String = "Не выбран",
    val importProgress: Float = 0f,
    val importLabel: String = "",
    val settings: MemorySettings = MemorySettings(),
    val ram: RamSnapshot = RamSnapshot(),
    val bannerMessage: String? = null,
    val isModelLoaded: Boolean = false,
    val isVisionActive: Boolean = false
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
    private val engine = JarvisEngine(application)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    private val voiceManager = VoiceManager(
        context = application,
        onRmsChanged = { rms ->
            _uiState.update { it.copy(voiceAmplitude = rms) }
        },
        onPartialSpeech = { partial ->
            _uiState.update { it.copy(partialVoiceText = partial) }
        },
        onFinalSpeech = { text ->
            _uiState.update {
                it.copy(
                    partialVoiceText = "",
                    status = idleStatus(it)
                )
            }
            sendMessage(text)
        },
        onListeningEnd = { err ->
            _uiState.update {
                it.copy(
                    partialVoiceText = "",
                    voiceAmplitude = 0f,
                    status = if (it.status == JarvisStatus.LISTENING) idleStatus(it) else it.status,
                    bannerMessage = err ?: it.bannerMessage
                )
            }
        },
        onSpeakingStateChanged = { speaking ->
            _uiState.update {
                val nextStatus = when {
                    speaking -> JarvisStatus.SPEAKING
                    it.status == JarvisStatus.SPEAKING -> idleStatus(it)
                    else -> it.status
                }
                it.copy(status = nextStatus)
            }
        }
    )

    init {
        startRamMonitor()
    }

    private fun loadInitialState(): JarvisUiState {
        val mPath = prefs.getString("model_path", "").orEmpty().takeIf { File(it).exists() }.orEmpty()
        val mName = if (mPath.isNotEmpty()) prefs.getString("model_name", File(mPath).name).orEmpty() else "Не выбрана"
        val vPath = prefs.getString("mmproj_path", "").orEmpty().takeIf { File(it).exists() }.orEmpty()
        val vName = if (vPath.isNotEmpty()) prefs.getString("mmproj_name", File(vPath).name).orEmpty() else "Не выбран"

        val settings = MemorySettings(
            contextSize = prefs.getInt("ctx", 1024),
            batchSize = prefs.getInt("batch", 128),
            threads = prefs.getInt("threads", 4),
            maxImageSide = prefs.getInt("img_side", 336),
            enableVision = prefs.getBoolean("enable_vision", true),
            autoSpeak = prefs.getBoolean("auto_speak", true)
        )

        return JarvisUiState(
            modelPath = mPath,
            modelName = mName,
            mmprojPath = vPath,
            mmprojName = vName,
            settings = settings,
            ram = engine.getRamSnapshot()
        )
    }

    private fun idleStatus(state: JarvisUiState): JarvisStatus = when {
        !state.isModelLoaded -> JarvisStatus.NO_MODEL
        state.isVisionActive -> JarvisStatus.READY_VISION
        else -> JarvisStatus.READY_TEXT
    }

    private fun startRamMonitor() {
        viewModelScope.launch {
            while (isActive) {
                val snap = engine.getRamSnapshot()
                _uiState.update { it.copy(ram = snap) }
                delay(2500L)
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun toggleAutoSpeak() {
        val next = !_uiState.value.settings.autoSpeak
        prefs.edit().putBoolean("auto_speak", next).apply()
        if (!next) voiceManager.stopSpeaking()
        _uiState.update { it.copy(settings = it.settings.copy(autoSpeak = next)) }
    }

    fun updateSettings(transform: (MemorySettings) -> MemorySettings) {
        val next = transform(_uiState.value.settings)
        prefs.edit()
            .putInt("ctx", next.contextSize)
            .putInt("batch", next.batchSize)
            .putInt("threads", next.threads)
            .putInt("img_side", next.maxImageSide)
            .putBoolean("enable_vision", next.enableVision)
            .putBoolean("auto_speak", next.autoSpeak)
            .apply()
        _uiState.update { it.copy(settings = next) }
    }

    fun importMainModel(uri: Uri) {
        viewModelScope.launch {
            val originalName = engine.getDisplayFileName(uri)
            _uiState.update {
                it.copy(
                    status = JarvisStatus.IMPORTING,
                    importProgress = 0f,
                    importLabel = "Копирую модель $originalName...",
                    bannerMessage = null
                )
            }
            runCatching {
                engine.importModelFile(uri, "jarvis_main.gguf") { p ->
                    _uiState.update { it.copy(importProgress = p) }
                }
            }.onSuccess { file ->
                prefs.edit()
                    .putString("model_path", file.absolutePath)
                    .putString("model_name", originalName)
                    .apply()
                _uiState.update {
                    it.copy(
                        modelPath = file.absolutePath,
                        modelName = originalName,
                        importProgress = 0f,
                        importLabel = "",
                        status = idleStatus(it),
                        bannerMessage = "Модель готова: $originalName. Нажми «Загрузить в память»."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        importProgress = 0f,
                        importLabel = "",
                        status = idleStatus(it),
                        bannerMessage = "Ошибка импорта: ${err.message}"
                    )
                }
            }
        }
    }

    fun importMmprojModel(uri: Uri) {
        viewModelScope.launch {
            val originalName = engine.getDisplayFileName(uri)
            _uiState.update {
                it.copy(
                    status = JarvisStatus.IMPORTING,
                    importProgress = 0f,
                    importLabel = "Копирую зрение $originalName...",
                    bannerMessage = null
                )
            }
            runCatching {
                engine.importModelFile(uri, "jarvis_mmproj.gguf") { p ->
                    _uiState.update { it.copy(importProgress = p) }
                }
            }.onSuccess { file ->
                prefs.edit()
                    .putString("mmproj_path", file.absolutePath)
                    .putString("mmproj_name", originalName)
                    .apply()
                _uiState.update {
                    it.copy(
                        mmprojPath = file.absolutePath,
                        mmprojName = originalName,
                        importProgress = 0f,
                        importLabel = "",
                        status = idleStatus(it),
                        bannerMessage = "Модуль зрения готов: $originalName"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        importProgress = 0f,
                        importLabel = "",
                        status = idleStatus(it),
                        bannerMessage = "Ошибка импорта mmproj: ${err.message}"
                    )
                }
            }
        }
    }

    fun loadModelIntoRam() {
        val state = _uiState.value
        if (state.modelPath.isBlank()) {
            _uiState.update { it.copy(bannerMessage = "Сначала выбери файл модели (.gguf)") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(status = JarvisStatus.LOADING, bannerMessage = null)
            }
            val result = runCatching {
                engine.load(state.modelPath, state.mmprojPath, state.settings)
            }.getOrElse { "ERR: ${it.message}" }

            when {
                result == "OK_VISION" -> {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            isVisionActive = true,
                            status = JarvisStatus.READY_VISION,
                            bannerMessage = "Джарвис онлайн со зрением"
                        )
                    }
                    if (_uiState.value.settings.autoSpeak) {
                        voiceManager.speak("Джарвис на связи. Зрение активно.")
                    }
                }
                result == "OK_TEXT" -> {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            isVisionActive = false,
                            status = JarvisStatus.READY_TEXT,
                            bannerMessage = "Джарвис онлайн (текстовый режим)"
                        )
                    }
                    if (_uiState.value.settings.autoSpeak) {
                        voiceManager.speak("Джарвис на связи. Готов к работе.")
                    }
                }
                result.startsWith("WARN:") -> {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            isVisionActive = false,
                            status = JarvisStatus.READY_TEXT,
                            bannerMessage = result.removePrefix("WARN:").trim()
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = false,
                            isVisionActive = false,
                            status = JarvisStatus.NO_MODEL,
                            bannerMessage = result.removePrefix("ERR:").trim()
                        )
                    }
                }
            }
        }
    }

    fun unloadModel() {
        activeJob?.cancel()
        voiceManager.stopSpeaking()
        viewModelScope.launch {
            engine.unload()
            _uiState.update {
                it.copy(
                    isModelLoaded = false,
                    isVisionActive = false,
                    status = JarvisStatus.NO_MODEL,
                    bannerMessage = "Модель выгружена, ОЗУ освобождено"
                )
            }
        }
    }

    fun attachMedia(uri: Uri) {
        val maxSide = _uiState.value.settings.maxImageSide
        viewModelScope.launch {
            val media = withContext(Dispatchers.IO) {
                ImageUtils.loadMediaFrame(getApplication(), uri, maxSide)
            }
            if (media != null) {
                _uiState.update { it.copy(attachedMedia = media) }
            } else {
                _uiState.update { it.copy(bannerMessage = "Не удалось прочитать картинку или видео") }
            }
        }
    }

    fun clearAttachedMedia() {
        _uiState.update { it.copy(attachedMedia = null) }
    }

    fun onMainOrbClick() {
        when (_uiState.value.status) {
            JarvisStatus.NO_MODEL -> {
                if (_uiState.value.modelPath.isNotBlank()) {
                    loadModelIntoRam()
                } else {
                    _uiState.update {
                        it.copy(bannerMessage = "Открой настройки (шестерёнка сверху) и выбери файл .gguf")
                    }
                }
            }
            JarvisStatus.LISTENING -> {
                voiceManager.stopListening()
                _uiState.update { it.copy(status = idleStatus(it)) }
            }
            JarvisStatus.THINKING -> {
                engine.cancel()
                activeJob?.cancel()
                _uiState.update { it.copy(status = idleStatus(it)) }
            }
            JarvisStatus.SPEAKING -> {
                voiceManager.stopSpeaking()
            }
            JarvisStatus.READY_VISION, JarvisStatus.READY_TEXT -> {
                _uiState.update { it.copy(status = JarvisStatus.LISTENING, partialVoiceText = "") }
                voiceManager.startListening()
            }
            JarvisStatus.LOADING, JarvisStatus.IMPORTING -> Unit
        }
    }

    fun speakMessage(text: String) {
        voiceManager.speak(text)
    }

    fun clearChat() {
        voiceManager.stopSpeaking()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun sendMessage(customText: String? = null) {
        val state = _uiState.value
        val prompt = (customText ?: state.inputText).trim()
        val media = state.attachedMedia

        if (prompt.isEmpty() && media == null) return
        if (!state.isModelLoaded) {
            _uiState.update { it.copy(bannerMessage = "Сначала загрузи модель Джарвиса в память") }
            return
        }
        if (state.status == JarvisStatus.THINKING) return

        val finalPrompt = prompt.ifEmpty { "Проанализируй этот кадр для Reels и предложи улучшение." }
        val userMsg = ChatMessage(
            isUser = true,
            text = finalPrompt,
            attachedPreview = media?.preview,
            attachedLabel = media?.label
        )
        val assistantMsgId = System.nanoTime() + 1L
        val placeholderMsg = ChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            isStreaming = true
        )

        voiceManager.stopSpeaking()

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + placeholderMsg,
                inputText = if (customText == null) "" else it.inputText,
                attachedMedia = null,
                status = JarvisStatus.THINKING,
                bannerMessage = null
            )
        }

        activeJob = viewModelScope.launch {
            runCatching {
                engine.generate(finalPrompt, media) { streamedRaw ->
                    val parsed = JarvisParser.parse(streamedRaw)
                    _uiState.update { cur ->
                        cur.copy(
                            messages = cur.messages.map { m ->
                                if (m.id == assistantMsgId) {
                                    m.copy(text = parsed.speech, parsed = parsed, isStreaming = true)
                                } else m
                            }
                        )
                    }
                }
            }.onSuccess { finalRaw ->
                val parsed = JarvisParser.parse(finalRaw)
                _uiState.update { cur ->
                    cur.copy(
                        status = idleStatus(cur),
                        messages = cur.messages.map { m ->
                            if (m.id == assistantMsgId) {
                                m.copy(
                                    text = parsed.speech.ifBlank { "(пустой ответ)" },
                                    parsed = parsed,
                                    isStreaming = false
                                )
                            } else m
                        }
                    )
                }
                if (_uiState.value.settings.autoSpeak && parsed.speech.isNotBlank()) {
                    voiceManager.speak(parsed.speech)
                }
            }.onFailure { err ->
                _uiState.update { cur ->
                    cur.copy(
                        status = idleStatus(cur),
                        bannerMessage = err.message,
                        messages = cur.messages.map { m ->
                            if (m.id == assistantMsgId) {
                                m.copy(
                                    text = "Ошибка: ${err.message ?: "сбой генерации"}",
                                    isStreaming = false
                                )
                            } else m
                        }
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
        engine.cancel()
    }
}
