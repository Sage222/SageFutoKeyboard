package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AdaptiveStyleDatabase
import com.example.data.AdaptiveStyleRepository
import com.example.ui.FutoStudioViewModel
import com.example.ui.screens.DecoderLabTabScreen
import com.example.ui.screens.LexiconNGramsTabScreen
import com.example.ui.screens.SandboxTabScreen
import com.example.ui.screens.StyleAdaptationTabScreen
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class NavTabItem(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

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
                FutoGestureStudioApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FutoGestureStudioApp(viewModel: FutoStudioViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spatialProfilesList by viewModel.spatialProfiles.collectAsStateWithLifecycle()
    val userWords by viewModel.userWords.collectAsStateWithLifecycle()
    val userBigrams by viewModel.userBigrams.collectAsStateWithLifecycle()
    val gesturePrototypes by viewModel.gesturePrototypes.collectAsStateWithLifecycle()
    val sessionLogs by viewModel.sessionLogs.collectAsStateWithLifecycle()

    val spatialProfileMap = remember(spatialProfilesList) {
        spatialProfilesList.associateBy { it.keyChar.first() }
    }
    val totalLearnedHits = remember(spatialProfilesList) {
        spatialProfilesList.sumOf { it.hitCount }
    }
    val promotedPredictionsCount = remember(sessionLogs) {
        sessionLogs.count { it.wasPromotedByAdaptation }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val navItems = listOf(
        NavTabItem(
            routeId = "sandbox",
            labelRes = R.string.tab_sandbox,
            selectedIcon = Icons.Filled.Keyboard,
            unselectedIcon = Icons.Outlined.Keyboard,
            testTag = "nav_tab_sandbox"
        ),
        NavTabItem(
            routeId = "decoder_lab",
            labelRes = R.string.tab_telemetry,
            selectedIcon = Icons.Filled.Analytics,
            unselectedIcon = Icons.Outlined.Analytics,
            testTag = "nav_tab_telemetry"
        ),
        NavTabItem(
            routeId = "style_profile",
            labelRes = R.string.tab_adaptation,
            selectedIcon = Icons.Filled.AutoFixHigh,
            unselectedIcon = Icons.Outlined.AutoFixHigh,
            testTag = "nav_tab_adaptation"
        ),
        NavTabItem(
            routeId = "lexicon_ngrams",
            labelRes = R.string.tab_lexicon,
            selectedIcon = Icons.Filled.Hub,
            unselectedIcon = Icons.Outlined.Hub,
            testTag = "nav_tab_lexicon"
        )
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(FutoSlateBg)
    ) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = FutoSlateBg,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = FutoSurface,
                        contentColor = TextPrimary
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected = selectedTab == idx
                            val label = stringResource(id = item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TelemetryCyan,
                                    selectedTextColor = TelemetryCyan,
                                    indicatorColor = TelemetryCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = FutoSurface,
                        contentColor = TextPrimary
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected = selectedTab == idx
                            val label = stringResource(id = item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> SandboxTabScreen(
                            uiState = uiState,
                            keyGeometries = viewModel.keyGeometries,
                            spatialProfiles = spatialProfileMap,
                            totalLearnedHits = totalLearnedHits,
                            learnedPrototypesCount = gesturePrototypes.size,
                            onSelectDrill = viewModel::selectCalibrationDrill,
                            onSwipeNextDrillWord = viewModel::swipeNextDrillWord,
                            onClearEditor = viewModel::clearEditorText,
                            onToggleShift = viewModel::toggleShift,
                            onToggleDriftOverlay = viewModel::toggleDriftOverlay,
                            onGestureCompleted = viewModel::onGestureCompleted,
                            onCandidateSelected = viewModel::onCandidateSelected,
                            onBackspace = viewModel::onBackspace,
                            onSpace = viewModel::onSpace,
                            onPunctuation = viewModel::onPunctuation,
                            onEnter = viewModel::onEnter
                        )
                        1 -> DecoderLabTabScreen(
                            lastDecodeResult = uiState.lastDecodeResult,
                            keyGeometries = viewModel.keyGeometries,
                            spatialProfiles = spatialProfileMap,
                            sessionLogs = sessionLogs,
                            onSimulateWordSwipe = { word ->
                                viewModel.simulateHumanSwipeForWord(word, commitToEditor = true)
                            }
                        )
                        2 -> StyleAdaptationTabScreen(
                            keyGeometries = viewModel.keyGeometries,
                            spatialProfiles = spatialProfileMap,
                            hyperparams = uiState.hyperparams,
                            prototypesCount = gesturePrototypes.size,
                            bigramsCount = userBigrams.size,
                            promotedCount = promotedPredictionsCount,
                            onApplyThumbPreset = viewModel::applyThumbCalibrationPreset,
                            onResetProfile = viewModel::resetLearnedProfile,
                            onUpdateHyperparams = viewModel::updateHyperparameters
                        )
                        3 -> LexiconNGramsTabScreen(
                            userWords = userWords,
                            userBigrams = userBigrams,
                            gesturePrototypes = gesturePrototypes,
                            onAddCustomWord = viewModel::addCustomWord,
                            onRemoveUserWord = viewModel::removeCustomWord
                        )
                    }
                }
            }
        }
    }
}
