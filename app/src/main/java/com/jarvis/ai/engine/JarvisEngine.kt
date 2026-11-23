package com.jarvis.ai.engine

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jarvis.ai.model.AttachedMedia
import com.jarvis.ai.model.MemorySettings
import com.jarvis.ai.model.RamSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class JarvisEngine(private val context: Context) {

    companion object {
        const val SYSTEM_PROMPT =
            "Ты — Джарвис, ИИ-тимлид Instagram-канала. Отвечай строго в формате JSON " +
                "с полями speech (краткий голосовой ответ для локального TTS), action и payload."
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    fun getRamSnapshot(): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamSnapshot(
            availMb = mi.availMem / (1024L * 1024L),
            totalMb = mi.totalMem / (1024L * 1024L),
            lowMemory = mi.lowMemory
        )
    }

    suspend fun importModelFile(
        uri: Uri,
        targetFileName: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val totalSize = queryFileSize(uri)
        val outFile = File(modelsDir, targetFileName)
        val tmpFile = File(modelsDir, "$targetFileName.tmp")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buf = ByteArray(1024 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    copied += read
                    if (totalSize > 0) {
                        onProgress((copied.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f))
                    }
                }
                output.flush()
            }
        } ?: error("Не удалось открыть выбранный файл")

        if (outFile.exists()) outFile.delete()
        if (!tmpFile.renameTo(outFile)) {
            error("Не удалось сохранить модель во внутреннюю память")
        }
        onProgress(1f)
        outFile
    }

    private fun queryFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) {
                return c.getLong(idx)
            }
        }
        return -1L
    }

    fun getDisplayFileName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                return c.getString(idx) ?: "model.gguf"
            }
        }
        return "model.gguf"
    }

    suspend fun load(
        modelPath: String,
        mmprojPath: String?,
        settings: MemorySettings
    ): String = withContext(Dispatchers.IO) {
        val visionPath = if (settings.enableVision && !mmprojPath.isNullOrBlank() && File(mmprojPath).exists()) {
            mmprojPath
        } else {
            ""
        }
        JarvisNative.nativeLoadModel(
            modelPath = modelPath,
            mmprojPath = visionPath,
            nCtx = settings.contextSize,
            nBatch = settings.batchSize,
            nThreads = settings.threads
        )
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        JarvisNative.nativeUnload()
    }

    fun cancel() {
        JarvisNative.nativeCancel()
    }

    suspend fun generate(
        userPrompt: String,
        media: AttachedMedia?,
        onChunk: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val hasVision = JarvisNative.nativeHasVision()
        val attachImage = media != null && hasVision
        val mediaMarker = if (attachImage) JarvisNative.nativeMediaMarker() else ""

        val userBlock = if (attachImage) {
            "$mediaMarker\n$userPrompt"
        } else {
            userPrompt
        }

        val fullPrompt = buildString {
            append("<|im_start|>system\n")
            append(SYSTEM_PROMPT)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userBlock)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        val acc = StringBuilder()
        val status = JarvisNative.nativeGenerate(
            prompt = fullPrompt,
            rgbBytes = if (attachImage) media?.rgbBytes else null,
            imgWidth = if (attachImage) media?.width ?: 0 else 0,
            imgHeight = if (attachImage) media?.height ?: 0 else 0,
            maxTokens = 256,
            temperature = 0.28f,
            callback = TokenCallback { bytes ->
                val text = String(bytes, Charsets.UTF_8)
                acc.append(text)
                onChunk(acc.toString())
            }
        )

        if (status.startsWith("ERR:")) {
            error(status.removePrefix("ERR:").trim())
        }
        acc.toString()
    }
}
