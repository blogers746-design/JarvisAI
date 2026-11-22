package com.jarvis.ai.util

import com.jarvis.ai.model.JarvisActionPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object JarvisParser {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(rawOutput: String): JarvisActionPayload {
        val cleaned = rawOutput
            .replace("<|im_end|>", "")
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val jsonCandidate = extractFirstJsonObject(cleaned)
        if (jsonCandidate != null) {
            runCatching {
                val obj = prettyJson.parseToJsonElement(jsonCandidate) as? JsonObject
                if (obj != null) {
                    val speech = (obj["speech"] as? JsonPrimitive)?.contentOrNull?.trim()
                    val action = (obj["action"] as? JsonPrimitive)?.contentOrNull?.trim()
                    val payloadEl: JsonElement? = obj["payload"]
                    val payloadText = payloadEl?.let {
                        prettyJson.encodeToString(JsonElement.serializer(), it)
                    }
                    return JarvisActionPayload(
                        speech = speech?.ifBlank { null } ?: cleaned,
                        action = action?.ifBlank { null },
                        payloadPretty = payloadText,
                        rawJson = jsonCandidate
                    )
                }
            }
        }

        // Streaming/partial JSON fallback: extract "speech": "..." if already visible
        val speechRegex = Regex(""speech"\s*:\s*"([^"]*)")
        val partialSpeech = speechRegex.find(cleaned)?.groupValues?.getOrNull(1)
            ?.replace("\n", "
")
            ?.replace("\"", """)

        return JarvisActionPayload(
            speech = partialSpeech?.takeIf { it.isNotBlank() } ?: cleaned,
            action = null,
            payloadPretty = null,
            rawJson = cleaned
        )
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }
}
