package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.KeySpatialProfileEntity
import com.example.engine.FutoLexicon
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import com.example.ui.StudioUiState
import com.example.ui.components.FutoGestureKeyboardView
import com.example.ui.theme.AdaptiveAmber
import com.example.ui.theme.FutoKeyCapBorder
import com.example.ui.theme.FutoKeyCapSurface
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.FutoSurfaceElevated
import com.example.ui.theme.NeuralEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SandboxTabScreen(
    uiState: StudioUiState,
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    totalLearnedHits: Int,
    learnedPrototypesCount: Int,
    onSelectDrill: (Int) -> Unit,
    onSwipeNextDrillWord: () -> Unit,
    onClearEditor: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleDriftOverlay: () -> Unit,
    onGestureCompleted: (List<RawTouchPoint>) -> Unit,
    onCandidateSelected: (GestureCandidate, Boolean) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onPunctuation: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeDrill = FutoLexicon.calibrationDrills[uiState.activeDrillIndex % FutoLexicon.calibrationDrills.size]
    val targetWords = activeDrill.targetPhrase.split(" ").filter { it.isNotBlank() }
    val typedWords = uiState.editorText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("sandbox_tab_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper scrollable workspace
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Telemetry & System IME Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FutoSurfaceElevated)
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(NeuralEmerald)
                    )
                    Column {
                        Text(
                            text = "FUTO ADAPTIVE ENGINE v2.4",
                            style = MaterialTheme.typography.labelMedium,
                            color = TelemetryCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalLearnedHits key alignments • $learnedPrototypesCount gesture prototypes",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // System IME Picker / Settings shortcut
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(FutoKeyCapSurface)
                        .border(1.dp, TelemetryCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("system_ime_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Switch System IME",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "IME Picker",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Calibration Phrase Selector & Word-by-Word Interactive Tracker
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = null,
                                tint = AdaptiveAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "STYLE CALIBRATION DRILL",
                                style = MaterialTheme.typography.labelMedium,
                                color = AdaptiveAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Auto-Swipe Next Word button to test gesture recognition effortlessly
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TelemetryCyan.copy(alpha = 0.16f))
                                .border(1.dp, TelemetryCyan, RoundedCornerShape(8.dp))
                                .clickable { onSwipeNextDrillWord() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("simulate_swipe_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Simulate Thumb Swipe",
                                tint = TelemetryCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Simulate Thumb Swipe",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Drill pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FutoLexicon.calibrationDrills.forEachIndexed { idx, drill ->
                            val selected = idx == uiState.activeDrillIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AdaptiveAmber.copy(alpha = 0.2f) else FutoKeyCapSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) AdaptiveAmber else FutoKeyCapBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectDrill(idx) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("drill_chip_$idx")
                            ) {
                                Text(
                                    text = drill.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) AdaptiveAmber else TextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Word-by-word highlighted target phrase
                    val annotatedPhrase = buildAnnotatedString {
                        targetWords.forEachIndexed { idx, word ->
                            val typedMatch = typedWords.getOrNull(idx)?.lowercase()?.filter { it in 'a'..'z' }
                            val isCurrentTarget = idx == typedWords.size
                            val isMatched = typedMatch == word.lowercase()
                            val color = when {
                                isMatched -> NeuralEmerald
                                isCurrentTarget -> TelemetryCyan
                                idx < typedWords.size -> AdaptiveAmber
                                else -> TextSecondary
                            }
                            withStyle(
                                SpanStyle(
                                    color = color,
                                    fontWeight = if (isCurrentTarget || isMatched) FontWeight.Bold else FontWeight.Normal
                                )
                            ) {
                                append(word)
                            }
                            if (idx < targetWords.lastIndex) append(" ")
                        }
                    }

                    Text(
                        text = annotatedPhrase,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // Live Sandbox Text Output Card + Context Telemetry
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LIVE GESTURE BUFFER",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.lastCommittedWord.isNotEmpty()) {
                                Text(
                                    text = "Context: \"${uiState.lastCommittedWord}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                            }
                        }

                        IconButton(
                            onClick = onClearEditor,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("clear_editor_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear text",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FutoSurfaceElevated)
                            .border(1.dp, FutoKeyCapBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("sandbox_editor_box")
                    ) {
                        if (uiState.editorText.isBlank()) {
                            Text(
                                text = "Swipe across letters on the FUTO keyboard below...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        } else {
                            Text(
                                text = uiState.editorText + "▎",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    // Adaptation status feedback banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoKeyCapSurface.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AdaptiveAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = uiState.statusBannerText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Docked Interactive FUTO Adaptive Gesture Keyboard
        FutoGestureKeyboardView(
            keyGeometries = keyGeometries,
            spatialProfiles = spatialProfiles,
            lastDecodeResult = uiState.lastDecodeResult,
            showDriftOverlay = uiState.showDriftOverlay,
            isShiftActive = uiState.isShiftActive,
            onToggleShift = onToggleShift,
            onToggleDriftOverlay = onToggleDriftOverlay,
            onGestureCompleted = onGestureCompleted,
            onCandidateSelected = onCandidateSelected,
            onBackspace = onBackspace,
            onSpace = onSpace,
            onPunctuation = onPunctuation,
            onEnter = onEnter
        )
    }
}
