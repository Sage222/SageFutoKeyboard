package com.example.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeySpatialProfileEntity
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.InflectionNode
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import com.example.ui.theme.AdaptiveAmber
import com.example.ui.theme.FutoKeyCapBorder
import com.example.ui.theme.FutoKeyCapSpecial
import com.example.ui.theme.FutoKeyCapSurface
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeuralEmerald
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.hypot

enum class ShiftState {
    OFF,
    ONE_SHOT,
    CAPS_LOCK
}

enum class KeyboardLayoutMode {
    ALPHABET,
    SYMBOLS,
    SYMBOLS_EXT
}

enum class ImeActionType {
    RETURN,
    DONE,
    SEARCH,
    SEND,
    GO,
    NEXT
}

private val topRowNumberHints = mapOf(
    'q' to '1', 'w' to '2', 'e' to '3', 'r' to '4', 't' to '5',
    'y' to '6', 'u' to '7', 'i' to '8', 'o' to '9', 'p' to '0'
)

/**
 * Production-grade FUTO Adaptive Keyboard View supporting:
 * - True Tap Typing (character composition, auto-correct, prefix completions, long-press numbers)
 * - Continuous Bezier Swipe Gesture Typing with real-time floating word preview
 * - Full `?123` Symbols & `=\<` Extended Symbols layouts + optional Dedicated Number Row
 * - Learned Per-Key Spatial Drift overlay & Supervised Candidate Correction learning
 */
@Composable
fun FutoGestureKeyboardView(
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    candidates: List<GestureCandidate>,
    composingWord: String,
    lastDecodeResult: GestureDecodeResult?,
    showDriftOverlay: Boolean,
    showNumberRow: Boolean = true,
    shiftState: ShiftState = ShiftState.OFF,
    imeActionType: ImeActionType = ImeActionType.RETURN,
    onToggleShift: () -> Unit,
    onToggleDriftOverlay: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onKeyTap: (Char) -> Unit,
    onGesturePreview: ((List<RawTouchPoint>) -> String?)? = null,
    onGestureCompleted: (List<RawTouchPoint>) -> Unit,
    onCandidateSelected: (GestureCandidate, isCorrection: Boolean) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onPunctuation: (String) -> Unit,
    onEnterAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var layoutMode by remember { mutableStateOf(KeyboardLayoutMode.ALPHABET) }
    val activeTrail = remember { mutableStateListOf<RawTouchPoint>() }
    var hoveredKey by remember { mutableStateOf<Char?>(null) }
    var isSwipingGesture by remember { mutableStateOf(false) }
    var liveSwipePreviewWord by remember { mutableStateOf<String?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FutoSurface)
            .border(width = 1.dp, color = FutoKeyCapBorder.copy(alpha = 0.6f))
            .padding(bottom = 4.dp)
            .testTag("futo_gesture_keyboard")
    ) {
        // 1. Suggestion Strip (Candidates + Composing Word + Settings/Drift Quick Icons)
        CandidateSuggestionToolbar(
            candidates = candidates,
            composingWord = composingWord,
            wasPromotedByAdaptation = lastDecodeResult?.wasPromotedByAdaptation == true,
            isSwiping = isSwipingGesture,
            liveSwipePreviewWord = liveSwipePreviewWord,
            hoveredKey = hoveredKey,
            showDriftOverlay = showDriftOverlay,
            onToggleDriftOverlay = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggleDriftOverlay()
            },
            onOpenSettings = onOpenSettings,
            onCandidateClick = { candidate, index ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCandidateSelected(candidate, index > 0)
            }
        )

        // 2. Optional Dedicated Number Row (1 2 3 4 5 6 7 8 9 0)
        if (showNumberRow && layoutMode == KeyboardLayoutMode.ALPHABET) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val digits = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
                digits.forEach { d ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(FutoKeyCapSpecial)
                            .border(1.dp, FutoKeyCapBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onKeyTap(d)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = d.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Main Keyboard Body (Alphabet Gesture Canvas OR Symbols Grid)
        when (layoutMode) {
            KeyboardLayoutMode.ALPHABET -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(166.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("gesture_letter_canvas")
                        .semantics { contentDescription = "QWERTY keyboard and gesture typing area" }
                        .pointerInput(keyGeometries, spatialProfiles, shiftState) {
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val heightPx = size.height.toFloat().coerceAtLeast(1f)

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                activeTrail.clear()
                                isSwipingGesture = false
                                liveSwipePreviewWord = null

                                val startX = (down.position.x / widthPx).coerceIn(0.01f, 0.99f)
                                val startY = (down.position.y / heightPx).coerceIn(0.01f, 0.99f)
                                val startMs = SystemClock.uptimeMillis()

                                activeTrail.add(RawTouchPoint(startX, startY, startMs))
                                val initialKey = findClosestKey(startX, startY, keyGeometries, spatialProfiles)
                                hoveredKey = initialKey
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                var totalPathDistance = 0f
                                var lastSampleX = startX
                                var lastSampleY = startY

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        val cx = (change.position.x / widthPx).coerceIn(0.01f, 0.99f)
                                        val cy = (change.position.y / heightPx).coerceIn(0.01f, 0.99f)
                                        val nowMs = SystemClock.uptimeMillis()
                                        val stepDist = hypot(cx - lastSampleX, cy - lastSampleY)

                                        if (stepDist >= 0.006f) {
                                            totalPathDistance += stepDist
                                            lastSampleX = cx
                                            lastSampleY = cy
                                            activeTrail.add(RawTouchPoint(cx, cy, nowMs))

                                            if (totalPathDistance >= 0.075f) {
                                                isSwipingGesture = true
                                                if (activeTrail.size % 4 == 0 && onGesturePreview != null) {
                                                    liveSwipePreviewWord = onGesturePreview(activeTrail.toList())
                                                }
                                            }
                                        }
                                        hoveredKey = findClosestKey(cx, cy, keyGeometries, spatialProfiles)
                                        change.consume()
                                    }
                                } while (event.changes.any { it.pressed })

                                val holdDurationMs = SystemClock.uptimeMillis() - startMs
                                val finishedPoints = activeTrail.toList()
                                val finalKey = hoveredKey ?: initialKey

                                isSwipingGesture = false
                                hoveredKey = null
                                liveSwipePreviewWord = null
                                activeTrail.clear()

                                // Distinguish Tap vs. Long-Press Number vs. Continuous Swipe Gesture!
                                if (totalPathDistance < 0.075f) {
                                    val altDigit = topRowNumberHints[finalKey]
                                    if (holdDurationMs >= 360L && altDigit != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        onKeyTap(altDigit)
                                    } else {
                                        val outChar = if (shiftState != ShiftState.OFF) {
                                            finalKey.uppercaseChar()
                                        } else {
                                            finalKey
                                        }
                                        onKeyTap(outChar)
                                    }
                                } else if (finishedPoints.size >= 3) {
                                    onGestureCompleted(finishedPoints)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        for ((ch, geom) in keyGeometries) {
                            val profile = spatialProfiles[ch]
                            val isHovered = hoveredKey == ch
                            val keyPadX = canvasW * 0.0055f
                            val keyPadY = canvasH * 0.022f
                            val left = (geom.canonicalX - geom.width / 2f) * canvasW + keyPadX
                            val top = (geom.canonicalY - geom.height / 2f) * canvasH + keyPadY
                            val kw = geom.width * canvasW - keyPadX * 2f
                            val kh = geom.height * canvasH - keyPadY * 2f

                            // Keycap shadow & surface
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.45f),
                                topLeft = Offset(left, top + 3f),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            drawRoundRect(
                                color = if (isHovered) TelemetryCyan.copy(alpha = 0.28f) else FutoKeyCapSurface,
                                topLeft = Offset(left, top),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            drawRoundRect(
                                color = if (isHovered) TelemetryCyan else FutoKeyCapBorder,
                                topLeft = Offset(left, top),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(14f, 14f),
                                style = Stroke(width = if (isHovered) 3.5f else 1.5f)
                            )

                            // Secondary number hint in top-right corner of Q..P
                            val hintDigit = topRowNumberHints[ch]
                            if (hintDigit != null) {
                                val hintLayout = textMeasurer.measure(
                                    text = hintDigit.toString(),
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                )
                                drawText(
                                    textLayoutResult = hintLayout,
                                    topLeft = Offset(
                                        x = left + kw - hintLayout.size.width - 5f,
                                        y = top + 3f
                                    )
                                )
                            }

                            // Main character label
                            val displayChar = if (shiftState != ShiftState.OFF) ch.uppercase() else ch.toString()
                            val textLayout = textMeasurer.measure(
                                text = displayChar,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isHovered) TelemetryCyan else TextPrimary
                                )
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    x = geom.canonicalX * canvasW - textLayout.size.width / 2f,
                                    y = geom.canonicalY * canvasH - textLayout.size.height / 2f
                                )
                            )

                            // Learned Spatial Drift Vector Overlay on Keycaps
                            if (showDriftOverlay && profile != null && profile.hitCount > 0) {
                                val cx = geom.canonicalX * canvasW
                                val cy = geom.canonicalY * canvasH
                                val shiftedX = (geom.canonicalX + profile.driftX * 2.2f) * canvasW
                                val shiftedY = (geom.canonicalY + profile.driftY * 2.2f) * canvasH

                                drawLine(
                                    color = AdaptiveAmber.copy(alpha = 0.85f),
                                    start = Offset(cx, cy),
                                    end = Offset(shiftedX, shiftedY),
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = AdaptiveAmber,
                                    radius = 5f,
                                    center = Offset(shiftedX, shiftedY)
                                )
                            }
                        }

                        // Key press popup preview when tapping a single key
                        val currentHover = hoveredKey
                        if (currentHover != null && !isSwipingGesture) {
                            val geom = keyGeometries[currentHover]
                            if (geom != null) {
                                val popW = geom.width * canvasW * 1.15f
                                val popH = geom.height * canvasH * 1.1f
                                val popLeft = (geom.canonicalX * canvasW - popW / 2f).coerceIn(4f, canvasW - popW - 4f)
                                val popTop = ((geom.canonicalY - geom.height * 1.25f) * canvasH).coerceAtLeast(2f)

                                drawRoundRect(
                                    color = FutoSlateBg,
                                    topLeft = Offset(popLeft, popTop),
                                    size = Size(popW, popH),
                                    cornerRadius = CornerRadius(16f, 16f)
                                )
                                drawRoundRect(
                                    color = TelemetryCyan,
                                    topLeft = Offset(popLeft, popTop),
                                    size = Size(popW, popH),
                                    cornerRadius = CornerRadius(16f, 16f),
                                    style = Stroke(width = 3f)
                                )
                                val popChar = if (shiftState != ShiftState.OFF) currentHover.uppercase() else currentHover.toString()
                                val popText = textMeasurer.measure(
                                    text = popChar,
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = TelemetryCyan
                                    )
                                )
                                drawText(
                                    textLayoutResult = popText,
                                    topLeft = Offset(
                                        popLeft + popW / 2f - popText.size.width / 2f,
                                        popTop + popH / 2f - popText.size.height / 2f
                                    )
                                )
                            }
                        }

                        // Active Bezier Swipe Trail
                        if (isSwipingGesture && activeTrail.size >= 2) {
                            val trailPath = Path()
                            val first = activeTrail.first()
                            trailPath.moveTo(first.x * canvasW, first.y * canvasH)
                            for (i in 1 until activeTrail.size) {
                                val prev = activeTrail[i - 1]
                                val curr = activeTrail[i]
                                val midX = (prev.x + curr.x) * 0.5f * canvasW
                                val midY = (prev.y + curr.y) * 0.5f * canvasH
                                trailPath.quadraticTo(
                                    prev.x * canvasW,
                                    prev.y * canvasH,
                                    midX,
                                    midY
                                )
                            }
                            val last = activeTrail.last()
                            trailPath.lineTo(last.x * canvasW, last.y * canvasH)

                            drawPath(
                                path = trailPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        TelemetryCyan.copy(alpha = 0.22f),
                                        AdaptiveAmber.copy(alpha = 0.40f),
                                        TelemetryCyan.copy(alpha = 0.52f)
                                    )
                                ),
                                style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = trailPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(TelemetryCyan, AdaptiveAmber, TelemetryCyan)
                                ),
                                style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawCircle(
                                color = TelemetryCyan,
                                radius = 10f,
                                center = Offset(last.x * canvasW, last.y * canvasH)
                            )
                        }
                    }
                }
            }

            KeyboardLayoutMode.SYMBOLS, KeyboardLayoutMode.SYMBOLS_EXT -> {
                SymbolsKeyboardGrid(
                    isExtended = layoutMode == KeyboardLayoutMode.SYMBOLS_EXT,
                    onSymbolTap = { sym ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onKeyTap(sym)
                    },
                    onToggleExtended = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        layoutMode = if (layoutMode == KeyboardLayoutMode.SYMBOLS) {
                            KeyboardLayoutMode.SYMBOLS_EXT
                        } else {
                            KeyboardLayoutMode.SYMBOLS
                        }
                    }
                )
            }
        }

        // 4. Bottom Action Row (?123/ABC, Shift, Comma, Spacebar, Period, Backspace, IME Action/Enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ?123 / ABC mode switch key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("key_mode_switch"),
                isActive = layoutMode != KeyboardLayoutMode.ALPHABET,
                activeColor = AdaptiveAmber,
                contentDesc = "Switch symbols or letters",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    layoutMode = if (layoutMode == KeyboardLayoutMode.ALPHABET) {
                        KeyboardLayoutMode.SYMBOLS
                    } else {
                        KeyboardLayoutMode.ALPHABET
                    }
                }
            ) {
                Text(
                    text = if (layoutMode == KeyboardLayoutMode.ALPHABET) "?123" else "ABC",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (layoutMode != KeyboardLayoutMode.ALPHABET) AdaptiveAmber else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Shift / Caps Lock key (only in Alphabet mode)
            if (layoutMode == KeyboardLayoutMode.ALPHABET) {
                UtilityKeyButton(
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("key_shift"),
                    isActive = shiftState != ShiftState.OFF,
                    activeColor = if (shiftState == ShiftState.CAPS_LOCK) AdaptiveAmber else TelemetryCyan,
                    contentDesc = "Shift key",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onToggleShift()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardCapslock,
                            contentDescription = "Shift",
                            tint = when (shiftState) {
                                ShiftState.CAPS_LOCK -> AdaptiveAmber
                                ShiftState.ONE_SHOT -> TelemetryCyan
                                ShiftState.OFF -> TextPrimary
                            },
                            modifier = Modifier.size(19.dp)
                        )
                        if (shiftState == ShiftState.CAPS_LOCK) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(AdaptiveAmber)
                            )
                        }
                    }
                }
            }

            // Comma key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(0.85f)
                    .testTag("key_comma"),
                isActive = false,
                activeColor = TelemetryCyan,
                contentDesc = "Comma key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPunctuation(",")
                }
            ) {
                Text(
                    text = ",",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Spacebar
            UtilityKeyButton(
                modifier = Modifier
                    .weight(3.4f)
                    .testTag("key_space"),
                isActive = false,
                activeColor = TelemetryCyan,
                contentDesc = "Spacebar",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSpace()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(NeuralEmerald)
                    )
                    Text(
                        text = if (composingWord.isNotEmpty()) "SPACE • \"$composingWord\"" else "English (FUTO Adaptive)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Period key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(0.85f)
                    .testTag("key_period"),
                isActive = false,
                activeColor = TelemetryCyan,
                contentDesc = "Period key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPunctuation(".")
                }
            ) {
                Text(
                    text = ".",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Backspace key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.15f)
                    .testTag("key_backspace"),
                isActive = false,
                activeColor = TelemetryCyan,
                contentDesc = "Backspace key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onBackspace()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Context-aware IME Action / Enter key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("key_enter"),
                isActive = true,
                activeColor = TelemetryCyan,
                contentDesc = "Enter or Action key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onEnterAction()
                }
            ) {
                when (imeActionType) {
                    ImeActionType.SEARCH -> Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    ImeActionType.SEND -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    ImeActionType.DONE -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    ImeActionType.GO, ImeActionType.NEXT -> Text(
                        text = if (imeActionType == ImeActionType.GO) "GO" else "NEXT",
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan,
                        fontWeight = FontWeight.Bold
                    )
                    ImeActionType.RETURN -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = TelemetryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SymbolsKeyboardGrid(
    isExtended: Boolean,
    onSymbolTap: (Char) -> Unit,
    onToggleExtended: () -> Unit
) {
    val rows = if (!isExtended) {
        listOf(
            listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
            listOf('@', '#', '$', '_', '&', '-', '+', '(', ')', '/'),
            listOf('*', '"', '\'', ':', ';', '!', '?', '%', '=', '~')
        )
    } else {
        listOf(
            listOf('~', '`', '|', '•', '√', 'π', '÷', '×', '§', 'Δ'),
            listOf('£', '¢', '€', '¥', '^', '°', '=', '{', '}', '\\'),
            listOf('%', '©', '®', '™', '✓', '[', ']', '<', '>', '…')
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(166.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        rows.forEachIndexed { rowIdx, rowChars ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (rowIdx == 2) {
                    // =\< toggle key on row 3 left
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoKeyCapSpecial)
                            .border(1.dp, AdaptiveAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleExtended),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!isExtended) "=\\<" else "?123",
                            style = MaterialTheme.typography.labelMedium,
                            color = AdaptiveAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                rowChars.forEach { sym ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoKeyCapSurface)
                            .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(8.dp))
                            .clickable { onSymbolTap(sym) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sym.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateSuggestionToolbar(
    candidates: List<GestureCandidate>,
    composingWord: String,
    wasPromotedByAdaptation: Boolean,
    isSwiping: Boolean,
    liveSwipePreviewWord: String?,
    hoveredKey: Char?,
    showDriftOverlay: Boolean,
    onToggleDriftOverlay: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onCandidateClick: (GestureCandidate, Int) -> Unit
) {
    Surface(
        color = FutoSlateBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("candidate_suggestion_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left / Center: Live Gesture Preview OR Candidate Suggestion Strip
            Box(modifier = Modifier.weight(1f)) {
                if (isSwiping) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TelemetryCyan.copy(alpha = 0.2f))
                                .border(1.dp, TelemetryCyan, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = liveSwipePreviewWord ?: hoveredKey?.uppercase() ?: "…",
                                style = MaterialTheme.typography.titleMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Gesture decoding…",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                } else if (candidates.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        candidates.take(5).forEachIndexed { idx, candidate ->
                            val isTop = idx == 0
                            val isPromoted = isTop && wasPromotedByAdaptation
                            val borderColor = when {
                                isPromoted -> AdaptiveAmber
                                isTop -> TelemetryCyan
                                else -> FutoKeyCapBorder
                            }
                            val bgColor = when {
                                isPromoted -> AdaptiveAmber.copy(alpha = 0.16f)
                                isTop -> TelemetryCyan.copy(alpha = 0.14f)
                                else -> FutoKeyCapSurface.copy(alpha = 0.8f)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .clickable { onCandidateClick(candidate, idx) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("candidate_chip_$idx"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isPromoted) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Adapted style prediction",
                                        tint = AdaptiveAmber,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = candidate.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = if (isTop) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = candidate.explanationBadge,
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 8.sp,
                                        color = if (isPromoted) AdaptiveAmber else TextMuted
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (composingWord.isNotEmpty()) "Typing: $composingWord" else "FUTO Adaptive Keyboard",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Right utility icons: Learned Key Drift Vectors Toggle + Settings Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleDriftOverlay,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("key_drift_hud")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Toggle Learned Key Drift Vectors",
                        tint = if (showDriftOverlay) AdaptiveAmber else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                if (onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("ime_open_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Keyboard Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UtilityKeyButton(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    activeColor: Color,
    contentDesc: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.18f) else FutoKeyCapSpecial)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else FutoKeyCapBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun findClosestKey(
    x: Float,
    y: Float,
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>
): Char {
    var bestChar = 'e'
    var bestDist = Float.MAX_VALUE
    for ((ch, geom) in keyGeometries) {
        val drift = spatialProfiles[ch]
        val kx = geom.canonicalX + (drift?.driftX ?: 0f)
        val ky = geom.canonicalY + (drift?.driftY ?: 0f)
        val d = hypot(x - kx, y - ky)
        if (d < bestDist) {
            bestDist = d
            bestChar = ch
        }
    }
    return bestChar
}
