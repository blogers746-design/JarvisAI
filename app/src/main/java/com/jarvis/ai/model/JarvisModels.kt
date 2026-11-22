package com.jarvis.ai.model

import android.graphics.Bitmap

enum class JarvisStatus {
    NO_MODEL,
    IMPORTING,
    LOADING,
    READY_VISION,
    READY_TEXT,
    LISTENING,
    THINKING,
    SPEAKING
}

data class JarvisActionPayload(
    val speech: String,
    val action: String?,
    val payloadPretty: String?,
    val rawJson: String
)

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val text: String,
    val attachedPreview: Bitmap? = null,
    val attachedLabel: String? = null,
    val parsed: JarvisActionPayload? = null,
    val isStreaming: Boolean = false
)

data class AttachedMedia(
    val preview: Bitmap,
    val rgbBytes: ByteArray,
    val width: Int,
    val height: Int,
    val label: String
)

data class MemorySettings(
    val contextSize: Int = 1024,
    val batchSize: Int = 128,
    val threads: Int = 4,
    val maxImageSide: Int = 336,
    val enableVision: Boolean = true,
    val autoSpeak: Boolean = true
)

data class RamSnapshot(
    val availMb: Long = 0,
    val totalMb: Long = 0,
    val lowMemory: Boolean = false
)
