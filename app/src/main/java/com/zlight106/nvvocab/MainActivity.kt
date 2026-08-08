package com.zlight106.nvvocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.NvvocabApp
import com.zlight106.nvvocab.ui.theme.NvvocabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = application as NvvocabApplication
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(application))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NvvocabTheme(
                themeMode = state.themeMode,
                dynamicColor = state.dynamicColor,
                themePresetId = state.themePresetId,
            ) {
                NvvocabApp(viewModel = viewModel, state = state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as NvvocabApplication).studyTimeTracker.start()
    }

    override fun onStop() {
        (application as NvvocabApplication).studyTimeTracker.stop()
        super.onStop()
    }
}
