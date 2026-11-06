package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AdaptiveStyleDatabase
import com.example.data.AdaptiveStyleRepository
import com.example.ui.FutoStudioViewModel
import com.example.ui.screens.SandboxTabScreen
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val repository = remember(context) {
                    val db = AdaptiveStyleDatabase.getInstance(context)
                    AdaptiveStyleRepository(db.adaptiveStyleDao())
                }
                val viewModel: FutoStudioViewModel = viewModel(
                    factory = FutoStudioViewModel.Factory(repository)
                )
                FutoKeyboardApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FutoKeyboardApp(viewModel: FutoStudioViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spatialProfilesList by viewModel.spatialProfiles.collectAsStateWithLifecycle()
    val userWords by viewModel.userWords.collectAsStateWithLifecycle()
    val userBigrams by viewModel.userBigrams.collectAsStateWithLifecycle()

    val spatialProfileMap = remember(spatialProfilesList) {
        spatialProfilesList.associateBy { it.keyChar.first() }
    }
    val userWordsMap = remember(userWords) {
        userWords.associateBy { it.word }
    }
    val userBigramsMap = remember(userBigrams) {
        userBigrams.associateBy { it.prevWord to it.nextWord }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = FutoSlateBg,
        modifier = Modifier
            .fillMaxSize()
            .background(FutoSlateBg)
    ) { innerPadding ->
        SandboxTabScreen(
            uiState = uiState,
            keyGeometries = viewModel.keyGeometries,
            spatialProfiles = spatialProfileMap,
            userWords = userWords,
            userWordsMap = userWordsMap,
            userBigramsMap = userBigramsMap,
            onExternalTextChange = viewModel::onExternalEditorTextChanged,
            onClearEditor = viewModel::clearEditorText,
            onToggleShift = viewModel::toggleShift,
            onToggleNumberRow = viewModel::toggleNumberRow,
            onClearLearnedWords = viewModel::resetLearnedProfile,
            onKeyTap = viewModel::onKeyTap,
            onGesturePreview = viewModel::previewGestureWord,
            onGestureCompleted = viewModel::onGestureCompleted,
            onCandidateSelected = viewModel::onCandidateSelected,
            onBackspace = viewModel::onBackspace,
            onSpace = viewModel::onSpace,
            onPunctuation = viewModel::onPunctuation,
            onEnter = viewModel::onEnter,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
