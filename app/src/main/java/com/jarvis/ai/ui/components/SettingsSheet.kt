package com.jarvis.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.ai.model.JarvisStatus
import com.jarvis.ai.model.MemorySettings
import com.jarvis.ai.ui.theme.JarvisColors
import com.jarvis.ai.viewmodel.JarvisUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: JarvisUiState,
    onDismiss: () -> Unit,
    onPickModel: () -> Unit,
    onPickMmproj: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onUpdateSettings: ((MemorySettings) -> MemorySettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisColors.BgSurface,
        contentColor = JarvisColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Ядро J.A.R.V.I.S. · Офлайн ИИ",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = JarvisColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ОЗУ свободно: ${state.ram.availMb} МБ из ${state.ram.totalMb} МБ",
                fontSize = 13.sp,
                color = if (state.ram.availMb < 1600) JarvisColors.AmberWarn else JarvisColors.EmeraldOk
            )

            Spacer(Modifier.height(16.dp))

            if (state.status == JarvisStatus.IMPORTING) {
                Text(
                    text = state.importLabel,
                    fontSize = 13.sp,
                    color = JarvisColors.CyanPrimary
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.importProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = JarvisColors.CyanPrimary,
                    trackColor = JarvisColors.BgCard
                )
                Spacer(Modifier.height(14.dp))
            }

            // Main LLM GGUF Card
            ModelFileRow(
                title = "Модель мозга (.gguf)",
                subtitle = state.modelName,
                icon = Icons.Rounded.Memory,
                onPick = onPickModel
            )

            Spacer(Modifier.height(10.dp))

            // Vision Projector mmproj GGUF Card
            ModelFileRow(
                title = "Модуль зрения (mmproj.gguf)",
                subtitle = state.mmprojName,
                icon = Icons.Rounded.Visibility,
                onPick = onPickMmproj
            )

            Spacer(Modifier.height(14.dp))

            // Vision Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = JarvisColors.BgCard,
                border = BorderStroke(1.dp, JarvisColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Включить зрение (mmproj)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = JarvisColors.TextPrimary
                        )
                        Text(
                            text = "Выключи, если телефон тормозит от нехватки ОЗУ",
                            fontSize = 11.5.sp,
                            color = JarvisColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = state.settings.enableVision,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(enableVision = enabled) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisColors.BgDeep,
                            checkedTrackColor = JarvisColors.CyanPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Context size selector
            SettingChipGroup(
                label = "Контекст (меньше = легче для ОЗУ)",
                options = listOf(512, 1024, 1536, 2048),
                selected = state.settings.contextSize,
                format = { "$it" },
                onSelect = { ctx -> onUpdateSettings { it.copy(contextSize = ctx) } }
            )

            Spacer(Modifier.height(12.dp))

            // Image scale selector
            SettingChipGroup(
                label = "Размер кадра для зрения",
                options = listOf(252, 336, 448),
                selected = state.settings.maxImageSide,
                format = { "${it}px" },
                onSelect = { px -> onUpdateSettings { it.copy(maxImageSide = px) } }
            )

            Spacer(Modifier.height(12.dp))

            // Threads selector
            SettingChipGroup(
                label = "Потоки процессора",
                options = listOf(2, 4, 6),
                selected = state.settings.threads,
                format = { "$it ядра" },
                onSelect = { t -> onUpdateSettings { it.copy(threads = t) } }
            )

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onLoadModel()
                        onDismiss()
                    },
                    enabled = state.modelPath.isNotBlank() && state.status != JarvisStatus.LOADING && state.status != JarvisStatus.IMPORTING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisColors.CyanPrimary,
                        contentColor = JarvisColors.BgDeep
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isModelLoaded) "Перезагрузить" else "Загрузить ИИ",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.isModelLoaded) {
                    OutlinedButton(
                        onClick = onUnloadModel,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, JarvisColors.RoseAlert.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.RoseAlert),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(Icons.Rounded.PowerOff, contentDescription = "Выгрузить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelFileRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = JarvisColors.BgCard,
        border = BorderStroke(1.dp, JarvisColors.BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JarvisColors.CyanPrimary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    color = JarvisColors.TextSecondary
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = JarvisColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onPick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, JarvisColors.CyanPrimary.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = JarvisColors.CyanPrimary
                )
            }
        }
    }
}

@Composable
private fun <T> SettingChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    format: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = JarvisColors.TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                val isSelected = opt == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(opt) },
                    label = {
                        Text(
                            text = format(opt),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisColors.CyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = JarvisColors.CyanPrimary,
                        containerColor = JarvisColors.BgCard,
                        labelColor = JarvisColors.TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = JarvisColors.BorderSubtle,
                        selectedBorderColor = JarvisColors.CyanPrimary.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}
