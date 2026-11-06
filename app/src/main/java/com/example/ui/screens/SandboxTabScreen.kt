package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Numbers
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
import com.example.ui.theme.AdaptiveAmber
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

@Composable
fun SandboxTabScreen(
    uiState: StudioUiState,
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    userWordsMap: Map<String, UserWordFrequencyEntity>,
    userBigramsMap: Map<Pair<String, String>, UserBigramEntity>,
    totalLearnedHits: Int,
    learnedPrototypesCount: Int,
    onExternalTextChange: (String) -> Unit,
    onClearEditor: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleDriftOverlay: () -> Unit,
    onToggleNumberRow: (Boolean) -> Unit,
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. System Keyboard Activation & Status Card (FUTO Keyboard Onboarding)
            Surface(
                color = FutoSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isImeSelectedAsDefault) NeuralEmerald.copy(alpha = 0.6f) else FutoKeyCapBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            Icon(
                                imageVector = if (isImeSelectedAsDefault) Icons.Default.CheckCircle else Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = if (isImeSelectedAsDefault) NeuralEmerald else TelemetryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isImeSelectedAsDefault) {
                                        "FUTO ADAPTIVE KEYBOARD ACTIVE"
                                    } else {
                                        "SYSTEM ANDROID KEYBOARD SETUP"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isImeSelectedAsDefault) NeuralEmerald else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$totalLearnedHits learned key alignments • $learnedPrototypesCount personal swipe prototypes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step 1: Enable in Android System Settings
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
                            shape = RoundedCornerShape(10.dp),
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
                                text = if (isImeEnabledInSystem) "1. IME Enabled" else "1. Enable IME",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Step 2: Select Active Input Method via System Picker
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
                            shape = RoundedCornerShape(10.dp),
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
                                text = "2. Select Keyboard",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Live Keyboard Input Field + Quick Layout Toggles
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
                            Text(
                                text = "TEST KEYBOARD & GESTURE INPUT",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Number row toggle chip
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (uiState.showNumberRow) TelemetryCyan.copy(alpha = 0.16f) else FutoKeyCapSurface)
                                    .border(
                                        1.dp,
                                        if (uiState.showNumberRow) TelemetryCyan else FutoKeyCapBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleNumberRow(!uiState.showNumberRow) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("toggle_number_row_chip"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Numbers,
                                    contentDescription = "Number Row",
                                    tint = if (uiState.showNumberRow) TelemetryCyan else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "123 Row",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.showNumberRow) TelemetryCyan else TextSecondary
                                )
                            }

                            // Drift HUD toggle chip
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (uiState.showDriftOverlay) AdaptiveAmber.copy(alpha = 0.16f) else FutoKeyCapSurface)
                                    .border(
                                        1.dp,
                                        if (uiState.showDriftOverlay) AdaptiveAmber else FutoKeyCapBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onToggleDriftOverlay() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("toggle_drift_chip"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Key Drift Overlay",
                                    tint = if (uiState.showDriftOverlay) AdaptiveAmber else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Drift HUD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.showDriftOverlay) AdaptiveAmber else TextSecondary
                                )
                            }

                            IconButton(
                                onClick = onClearEditor,
                                modifier = Modifier
                                    .size(30.dp)
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
                    }

                    // Active Composing + Committed Text Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(FutoSurfaceElevated)
                            .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("live_keyboard_text_box")
                    ) {
                        if (uiState.editorText.isEmpty() && uiState.composingWord.isEmpty()) {
                            Text(
                                text = "Type individual letters or swipe across keys on the keyboard below. Backspace undoes swiped words and teaches your personal style.",
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

                    // Also provide a native Android OutlinedTextField when the user wants to test the system-wide IME popup
                    if (isImeSelectedAsDefault) {
                        OutlinedTextField(
                            value = uiState.editorText,
                            onValueChange = onExternalTextChange,
                            label = {
                                Text(
                                    "Tap here to invoke system-wide FUTO IME popup",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeuralEmerald,
                                unfocusedBorderColor = FutoKeyCapBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_ime_test_field")
                        )
                    }
                }
            }
        }

        // Docked Real FUTO Adaptive Keyboard View (supports both tap-composing and gesture swiping)
        FutoGestureKeyboardView(
            keyGeometries = keyGeometries,
            spatialProfiles = spatialProfiles,
            candidates = activeCandidates,
            composingWord = uiState.composingWord,
            lastDecodeResult = if (uiState.justCommittedGestureWord) uiState.lastDecodeResult else null,
            showDriftOverlay = uiState.showDriftOverlay,
            showNumberRow = uiState.showNumberRow,
            shiftState = uiState.shiftState,
            onToggleShift = onToggleShift,
            onToggleDriftOverlay = onToggleDriftOverlay,
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
