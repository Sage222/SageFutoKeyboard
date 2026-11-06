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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import com.example.engine.FutoLexicon
import com.example.engine.GestureCandidate
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import com.example.ui.StudioUiState
import com.example.ui.components.FutoGestureKeyboardView
import com.example.ui.theme.FutoKeyCapBorder
import com.example.ui.theme.FutoKeyCapSurface
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.FutoSurfaceElevated
import com.example.ui.theme.NeuralEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Clean FUTO Keyboard Settings & Input Screen:
 * - System IME Setup (Enable in Settings & Select Input Method)
 * - Test Typing & Swipe Area
 * - Number Row Toggle & Learned Used-Words History
 * - Docked FUTO Keyboard sitting cleanly above Android system navigation buttons
 */
@Composable
fun SandboxTabScreen(
    uiState: StudioUiState,
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    userWords: List<UserWordFrequencyEntity>,
    userWordsMap: Map<String, UserWordFrequencyEntity>,
    userBigramsMap: Map<Pair<String, String>, UserBigramEntity>,
    onExternalTextChange: (String) -> Unit,
    onClearEditor: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleNumberRow: (Boolean) -> Unit,
    onClearLearnedWords: () -> Unit,
    onKeyTap: (Char) -> Unit,
    onGesturePreview: (List<RawTouchPoint>) -> String?,
    onGestureCompleted: (List<RawTouchPoint>) -> Unit,
    onCandidateSelected: (GestureCandidate, Boolean) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onPunctuation: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isImeEnabledInSystem by remember { mutableStateOf(checkImeEnabled(context)) }
    var isImeSelectedAsDefault by remember { mutableStateOf(checkImeSelected(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isImeEnabledInSystem = checkImeEnabled(context)
                isImeSelectedAsDefault = checkImeSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeCandidates = remember(
        uiState.composingWord,
        uiState.prevContextWord,
        uiState.lastDecodeResult,
        uiState.justCommittedGestureWord,
        userWordsMap,
        userBigramsMap
    ) {
        if (uiState.justCommittedGestureWord && uiState.lastDecodeResult != null) {
            uiState.lastDecodeResult.candidates
        } else {
            FutoLexicon.getPrefixAndTypoCandidates(
                composingPrefix = uiState.composingWord,
                prevContextWord = uiState.prevContextWord,
                userWords = userWordsMap,
                userBigrams = userBigramsMap,
                keyGeometries = keyGeometries
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("sandbox_tab_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "FUTO Keyboard",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isImeSelectedAsDefault) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active IME",
                            tint = NeuralEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Default Keyboard",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeuralEmerald
                        )
                    }
                }
            }

            // System IME Setup Card
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "System Keyboard Setup",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                } catch (_: Exception) {
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isImeEnabledInSystem) FutoSurfaceElevated else TelemetryCyan,
                                contentColor = if (isImeEnabledInSystem) NeuralEmerald else FutoSlateBg
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("enable_ime_settings_button")
                        ) {
                            Icon(
                                imageVector = if (isImeEnabledInSystem) Icons.Default.CheckCircle else Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = if (isImeEnabledInSystem) "1. Enabled" else "1. Enable IME",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showInputMethodPicker()
                                    isImeEnabledInSystem = checkImeEnabled(context)
                                    isImeSelectedAsDefault = checkImeSelected(context)
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("select_ime_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "2. Switch IME",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Test Input Field
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(12.dp))
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
                        Text(
                            text = "Test Typing & Gesture Swiping",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
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

                    if (isImeSelectedAsDefault) {
                        OutlinedTextField(
                            value = uiState.editorText,
                            onValueChange = onExternalTextChange,
                            placeholder = {
                                Text(
                                    "Tap here to type or swipe with FUTO Keyboard…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TelemetryCyan,
                                unfocusedBorderColor = FutoKeyCapBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .testTag("system_ime_test_field")
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(FutoSurfaceElevated)
                                .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .testTag("live_keyboard_text_box")
                        ) {
                            if (uiState.editorText.isEmpty() && uiState.composingWord.isEmpty()) {
                                Text(
                                    text = "Swipe across keys or tap letters on the keyboard below…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            } else {
                                val annotated = buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextPrimary)) {
                                        append(uiState.editorText)
                                    }
                                    if (uiState.composingWord.isNotEmpty()) {
                                        withStyle(
                                            SpanStyle(
                                                color = TelemetryCyan,
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = TextDecoration.Underline
                                            )
                                        ) {
                                            append(uiState.composingWord)
                                        }
                                    }
                                    withStyle(SpanStyle(color = TelemetryCyan)) {
                                        append("▎")
                                    }
                                }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Keyboard Preferences & Prioritized Used Words
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(12.dp))
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
                        Column {
                            Text(
                                text = "Show Number Row",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Display dedicated 1–0 number keys above top row",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = uiState.showNumberRow,
                            onCheckedChange = onToggleNumberRow,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TelemetryCyan,
                                checkedTrackColor = TelemetryCyan.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.testTag("toggle_number_row_switch")
                        )
                    }

                    if (userWords.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prioritized Used Words (${userWords.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onClearLearnedWords() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear history",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            userWords.take(15).forEach { entry ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(FutoKeyCapSurface)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${entry.word} (${entry.personalUseCount}x)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Show docked keyboard when system IME is not already replacing it, sitting cleanly above navigation buttons
        if (!isImeSelectedAsDefault) {
            FutoGestureKeyboardView(
                keyGeometries = keyGeometries,
                spatialProfiles = spatialProfiles,
                candidates = activeCandidates,
                composingWord = uiState.composingWord,
                lastDecodeResult = if (uiState.justCommittedGestureWord) uiState.lastDecodeResult else null,
                showDriftOverlay = false,
                showNumberRow = uiState.showNumberRow,
                shiftState = uiState.shiftState,
                onToggleShift = onToggleShift,
                onKeyTap = onKeyTap,
                onGesturePreview = onGesturePreview,
                onGestureCompleted = onGestureCompleted,
                onCandidateSelected = onCandidateSelected,
                onBackspace = onBackspace,
                onSpace = onSpace,
                onPunctuation = onPunctuation,
                onEnterAction = onEnter
            )
        }
    }
}

private fun checkImeEnabled(context: Context): Boolean {
    return try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
    } catch (_: Exception) {
        false
    }
}

private fun checkImeSelected(context: Context): Boolean {
    return try {
        val defaultIme = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ).orEmpty()
        defaultIme.contains(context.packageName) || defaultIme.contains("FutoAdaptiveInputMethodService")
    } catch (_: Exception) {
        false
    }
}
