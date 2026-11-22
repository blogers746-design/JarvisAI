package com.jarvis.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.model.ChatMessage
import com.jarvis.ai.ui.theme.JarvisColors

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onSpeak: (String) -> Unit
) {
    if (message.isUser) {
        UserBubble(message)
    } else {
        JarvisCard(message, onSpeak)
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = JarvisColors.IndigoAccent.copy(alpha = 0.24f),
            border = BorderStroke(1.dp, JarvisColors.IndigoAccent.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                message.attachedPreview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = message.attachedLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    message.attachedLabel?.let { lbl ->
                        Text(
                            text = lbl,
                            fontSize = 11.sp,
                            color = JarvisColors.CyanSoft
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Text(
                    text = message.text,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun JarvisCard(
    message: ChatMessage,
    onSpeak: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showPayload by remember { mutableStateOf(false) }
    val parsed = message.parsed

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            color = JarvisColors.BgCard,
            border = BorderStroke(
                1.dp,
                if (message.isStreaming) JarvisColors.CyanPrimary.copy(alpha = 0.55f)
                else JarvisColors.BorderSubtle
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(JarvisColors.CyanPrimary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = JarvisColors.CyanPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (message.isStreaming) "J.A.R.V.I.S. · думает..." else "J.A.R.V.I.S.",
                            color = JarvisColors.CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    if (!message.isStreaming && message.text.isNotBlank()) {
                        IconButton(
                            onClick = { onSpeak(message.text) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Озвучить",
                                tint = JarvisColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = message.text.ifBlank { "..." },
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                // Action & JSON Payload Pill
                if (parsed?.action != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisColors.BgDeep.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPayload = !showPayload }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(JarvisColors.EmeraldOk)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "ACTION · ${parsed.action}",
                                        color = JarvisColors.CyanSoft,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(parsed.rawJson))
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Копировать JSON",
                                            tint = JarvisColors.TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showPayload) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        tint = JarvisColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showPayload && !parsed.payloadPretty.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = parsed.payloadPretty,
                                    color = Color(0xFFA5F3FC),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
