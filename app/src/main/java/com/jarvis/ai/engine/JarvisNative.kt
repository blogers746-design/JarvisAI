package com.jarvis.ai.engine

fun interface TokenCallback {
    fun onTokenBytes(bytes: ByteArray)
}

object JarvisNative {
    init {
        System.loadLibrary("jarvis_native")
    }

    external fun nativeLoadModel(
        modelPath: String,
        mmprojPath: String,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int
    ): String

    external fun nativeUnload()
    external fun nativeCancel()
    external fun nativeHasVision(): Boolean
    external fun nativeMediaMarker(): String

    external fun nativeGenerate(
        prompt: String,
        rgbBytes: ByteArray?,
        imgWidth: Int,
        imgHeight: Int,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback
    ): String
}
