package com.jarvis.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.jarvis.ai.model.JarvisStatus
import com.jarvis.ai.ui.JarvisScreen
import com.jarvis.ai.ui.theme.JarvisTheme
import com.jarvis.ai.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            JarvisTheme {
                val state by viewModel.uiState.collectAsState()

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.onMainOrbClick()
                    }
                }

                val modelPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.importMainModel(uri)
                }

                val mmprojPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.importMmprojModel(uri)
                }

                val mediaPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.attachMedia(uri)
                }

                JarvisScreen(
                    state = state,
                    onOrbClick = {
                        val needsMic = state.status == JarvisStatus.READY_VISION ||
                            state.status == JarvisStatus.READY_TEXT
                        val hasMic = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (needsMic && !hasMic) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.onMainOrbClick()
                        }
                    },
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onPickMedia = {
                        mediaPicker.launch(arrayOf("image/*", "video/*"))
                    },
                    onClearMedia = viewModel::clearAttachedMedia,
                    onPickModel = {
                        modelPicker.launch(arrayOf("*/*"))
                    },
                    onPickMmproj = {
                        mmprojPicker.launch(arrayOf("*/*"))
                    },
                    onLoadModel = viewModel::loadModelIntoRam,
                    onUnloadModel = viewModel::unloadModel,
                    onUpdateSettings = viewModel::updateSettings,
                    onToggleAutoSpeak = viewModel::toggleAutoSpeak,
                    onSpeakMessage = viewModel::speakMessage,
                    onClearChat = viewModel::clearChat,
                    onClearBanner = viewModel::clearBanner
                )
            }
        }
    }
}
