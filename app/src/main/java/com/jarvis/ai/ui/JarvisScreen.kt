package com.jarvis.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.model.JarvisStatus
import com.jarvis.ai.model.MemorySettings
import com.jarvis.ai.ui.components.ChatMessageItem
import com.jarvis.ai.ui.components.JarvisOrbButton
import com.jarvis.ai.ui.components.SettingsSheet
import com.jarvis.ai.ui.theme.JarvisColors
import com.jarvis.ai.viewmodel.JarvisUiState

private val QUICK_PROMPTS = listOf(
    "Разбери этот кадр для Reels",
    "Подгони реплику под субтитры",
    "Придумай хук на первые 2 секунды",
    "Проверь статус и план контента"
)

@Composable
fun JarvisScreen(
    state: JarvisUiState,
    onOrbClick: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: (String?) -> Unit,
    onPickMedia: () -> Unit,
    onClearMedia: () -> Unit,
    onPickModel: () -> Unit,
    onPickMmproj: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onUpdateSettings: ((MemorySettings) -> MemorySettings) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onSpeakMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onClearBanner: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060A12),
                        Color(0xFF091121),
                        Color(0xFF060A12)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 1. Clean Top Header
            TopStatusBar(
                state = state,
                onToggleAutoSpeak = onToggleAutoSpeak,
                onClearChat = onClearChat,
                onOpenSettings = { showSettings = true }
            )

            // 2. Notification Banner (if any)
            AnimatedVisibility(visible = !state.bannerMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisColors.BgCard,
                    border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.bannerMessage.orEmpty(),
                            color = JarvisColors.TextPrimary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearBanner,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = JarvisColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Center Workspace: Welcome Core or Chat Feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.messages.isEmpty()) {
                    WelcomeHero(
                        state = state,
                        onOpenSettings = { showSettings = true },
                        onQuickPrompt = { prompt ->
                            if (state.isModelLoaded) onSend(prompt)
                            else showSettings = true
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            ChatMessageItem(
                                message = msg,
                                onSpeak = onSpeakMessage
                            )
                        }
                    }
                }

                // Loading overlay badge
                if (state.status == JarvisStatus.LOADING || state.status == JarvisStatus.IMPORTING) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JarvisColors.BgSurface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = JarvisColors.CyanPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (state.status == JarvisStatus.IMPORTING) {
                                    "${state.importLabel} ${(state.importProgress * 100).toInt()}%"
                                } else {
                                    "Загружаю нейросеть в память..."
                                },
                                color = JarvisColors.TextPrimary,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }

            // 4. Live Voice Transcription Pill
            AnimatedVisibility(visible = state.status == JarvisStatus.LISTENING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.partialVoiceText.ifBlank { "Слушаю тебя..." },
                        color = JarvisColors.CyanPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 5. Attached Frame Preview Strip
            AnimatedVisibility(visible = state.attachedMedia != null) {
                state.attachedMedia?.let { media ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisColors.BgCard,
                        border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = media.preview.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.label,
                                    color = JarvisColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (state.isVisionActive) "Готов к анализу зрением" else "Внимание: зрение выключено",
                                    color = if (state.isVisionActive) JarvisColors.EmeraldOk else JarvisColors.AmberWarn,
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(onClick = onClearMedia) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Убрать кадр",
                                    tint = JarvisColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 6. Bottom Dock: Text + Media Bar + Central Glowing Voice Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = JarvisColors.BgCard,
                        border = BorderStroke(1.dp, JarvisColors.BorderSubtle),
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(onClick = onPickMedia) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Прикрепить фото или видео",
                                tint = JarvisColors.CyanPrimary
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = onInputChange,
                        placeholder = {
                            Text(
                                text = "Команда Джарвису...",
                                color = JarvisColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisColors.CyanPrimary.copy(alpha = 0.7f),
                            unfocusedBorderColor = JarvisColors.BorderSubtle,
                            focusedContainerColor = JarvisColors.BgCard,
                            unfocusedContainerColor = JarvisColors.BgCard,
                            focusedTextColor = JarvisColors.TextPrimary,
                            unfocusedTextColor = JarvisColors.TextPrimary,
                            cursorColor = JarvisColors.CyanPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend(null) }),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    val canSend = state.inputText.isNotBlank() || state.attachedMedia != null
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) JarvisColors.CyanPrimary else JarvisColors.BgCard,
                        border = BorderStroke(
                            1.dp,
                            if (canSend) JarvisColors.CyanPrimary else JarvisColors.BorderSubtle
                        ),
                        modifier = Modifier.size(46.dp)
                    ) {
                        IconButton(
                            onClick = { onSend(null) },
                            enabled = canSend && state.status != JarvisStatus.THINKING
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Send,
                                contentDescription = "Отправить",
                                tint = if (canSend) JarvisColors.BgDeep else JarvisColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Main Glowing Jarvis Voice Button
                JarvisOrbButton(
                    status = state.status,
                    amplitude = state.voiceAmplitude,
                    size = 78.dp,
                    onClick = onOrbClick
                )

                Text(
                    text = when (state.status) {
                        JarvisStatus.NO_MODEL -> if (state.modelPath.isNotBlank()) "Нажми, чтобы запустить ИИ" else "Выбери модель в настройках"
                        JarvisStatus.LISTENING -> "Говори · нажми для стопа"
                        JarvisStatus.THINKING -> "Думает · нажми для отмены"
                        JarvisStatus.SPEAKING -> "Говорит · нажми для тишины"
                        JarvisStatus.LOADING -> "Загрузка ядра..."
                        JarvisStatus.IMPORTING -> "Копирование файла..."
                        else -> "Нажми и говори"
                    },
                    fontSize = 11.5.sp,
                    color = JarvisColors.TextSecondary
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            state = state,
            onDismiss = { showSettings = false },
            onPickModel = onPickModel,
            onPickMmproj = onPickMmproj,
            onLoadModel = onLoadModel,
            onUnloadModel = onUnloadModel,
            onUpdateSettings = onUpdateSettings
        )
    }
}

@Composable
private fun TopStatusBar(
    state: JarvisUiState,
    onToggleAutoSpeak: () -> Unit,
    onClearChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val dotColor = when (state.status) {
        JarvisStatus.READY_VISION, JarvisStatus.READY_TEXT, JarvisStatus.SPEAKING -> JarvisColors.EmeraldOk
        JarvisStatus.THINKING, JarvisStatus.LOADING, JarvisStatus.IMPORTING -> JarvisColors.CyanPrimary
        JarvisStatus.LISTENING -> JarvisColors.RoseAlert
        JarvisStatus.NO_MODEL -> JarvisColors.TextSecondary
    }

    val statusLabel = when (state.status) {
        JarvisStatus.READY_VISION -> "Офлайн · Зрение"
        JarvisStatus.READY_TEXT -> "Офлайн · Текст"
        JarvisStatus.THINKING -> "Генерация..."
        JarvisStatus.LISTENING -> "Слушаю..."
        JarvisStatus.SPEAKING -> "Озвучка..."
        JarvisStatus.LOADING -> "Загрузка..."
        JarvisStatus.IMPORTING -> "Импорт..."
        JarvisStatus.NO_MODEL -> "Ожидание модели"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "J.A.R.V.I.S.",
                    color = JarvisColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
            }
            Text(
                text = "$statusLabel  ·  ОЗУ ${state.ram.availMb} МБ",
                color = JarvisColors.TextSecondary,
                fontSize = 11.5.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleAutoSpeak) {
                Icon(
                    imageVector = if (state.settings.autoSpeak) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = "Голос Джарвиса",
                    tint = if (state.settings.autoSpeak) JarvisColors.CyanPrimary else JarvisColors.TextSecondary
                )
            }
            if (state.messages.isNotEmpty()) {
                IconButton(onClick = onClearChat) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Очистить чат",
                        tint = JarvisColors.TextSecondary
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = JarvisColors.BgCard,
                border = BorderStroke(1.dp, JarvisColors.BorderSubtle)
            ) {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Модели и память",
                        tint = JarvisColors.CyanPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHero(
    state: JarvisUiState,
    onOpenSettings: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Локальный ИИ-Тимлид",
            color = JarvisColors.CyanPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.isModelLoaded) "Готов к разбору Reels" else "Подключи свою модель GGUF",
            color = JarvisColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.isModelLoaded) {
                "Прикрепи фото или видео Reels, либо нажми на кнопку голоса внизу"
            } else {
                "Выбери Qwen3.5-2B.Q4_K_M.gguf и mmproj.gguf — модель скопируется во внутреннюю память для быстрого запуска"
            },
            color = JarvisColors.TextSecondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        if (!state.isModelLoaded) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = JarvisColors.CyanPrimary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.65f)),
                modifier = Modifier.clickable { onOpenSettings() }
            ) {
                Text(
                    text = "Выбрать и загрузить модель",
                    color = JarvisColors.CyanPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_PROMPTS.forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = JarvisColors.BgCard,
                        border = BorderStroke(1.dp, JarvisColors.BorderSubtle),
                        modifier = Modifier.clickable { onQuickPrompt(chip) }
                    ) {
                        Text(
                            text = chip,
                            color = JarvisColors.TextPrimary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}
