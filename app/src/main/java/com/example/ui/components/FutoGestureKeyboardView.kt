package com.example.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.data.KeySpatialProfileEntity
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import com.example.ui.theme.FutoKeyCapSpecial
import com.example.ui.theme.FutoKeyCapSurface
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.JetBrainsMonoFamily
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
 * Authentic FUTO Keyboard / LatinIME layout:
 * - 3-slot Suggestion Strip at top (2nd candidate | 1st Primary candidate | 3rd candidate)
 * - Optional Number Row (1..0)
 * - Row 1: q w e r t y u i o p
 * - Row 2: a s d f g h j k l
 * - Row 3: [Shift] z x c v b n m [Backspace]
 * - Row 4: [?123] [,] [     Spacebar     ] [.] [Enter/Action]
 * - Explicit bottom navigation-bar clearance so the keyboard never sits under Android navigation buttons.
 */
@Composable
fun FutoGestureKeyboardView(
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    candidates: List<GestureCandidate>,
    composingWord: String,
    lastDecodeResult: GestureDecodeResult?,
    showDriftOverlay: Boolean = false,
    showNumberRow: Boolean = false,
    shiftState: ShiftState = ShiftState.OFF,
    imeActionType: ImeActionType = ImeActionType.RETURN,
    extraBottomNavClearance: Dp = 0.dp,
    onToggleShift: () -> Unit,
    onToggleDriftOverlay: () -> Unit = {},
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
    var isShiftPressedVisual by remember { mutableStateOf(false) }
    var isBackspacePressedVisual by remember { mutableStateOf(false) }
    var isSwipingGesture by remember { mutableStateOf(false) }
    var liveSwipePreviewWord by remember { mutableStateOf<String?>(null) }

    val textMeasurer = rememberTextMeasurer()

    // Guarantee clearance above Android system navigation bar (3-button or gesture bar)
    val navInsetsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val safeBottomPadding = max(navInsetsBottom, extraBottomNavClearance)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FutoSurface)
            .padding(bottom = safeBottomPadding + 6.dp)
            .testTag("futo_gesture_keyboard")
    ) {
        // 1. Classic FUTO / LatinIME 3-Slot Suggestion Strip
        LatinImeSuggestionStrip(
            candidates = candidates,
            composingWord = composingWord,
            isSwiping = isSwipingGesture,
            liveSwipePreviewWord = liveSwipePreviewWord,
            onOpenSettings = onOpenSettings,
            onCandidateClick = { candidate, index ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCandidateSelected(candidate, index > 0)
            }
        )

        // 2. Optional Number Row (1 2 3 4 5 6 7 8 9 0)
        if (showNumberRow && layoutMode == KeyboardLayoutMode.ALPHABET) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
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
                            .background(FutoKeyCapSurface)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onKeyTap(d)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = d.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Main 3-Row Keyboard Area (Alphabet with Shift/Backspace on Row 3, or Symbols)
        when (layoutMode) {
            KeyboardLayoutMode.ALPHABET -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .testTag("gesture_letter_canvas")
                        .semantics { contentDescription = "QWERTY keyboard area" }
                        .pointerInput(keyGeometries, spatialProfiles, shiftState) {
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val heightPx = size.height.toFloat().coerceAtLeast(1f)

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startX = (down.position.x / widthPx).coerceIn(0.005f, 0.995f)
                                val startY = (down.position.y / heightPx).coerceIn(0.005f, 0.995f)
                                val startMs = SystemClock.uptimeMillis()

                                // Check if touch started on Row 3 Shift [x < 0.145, y > 0.66] or Backspace [x > 0.855, y > 0.66]
                                val startedOnShift = startY >= 0.66f && startX <= 0.145f
                                val startedOnBackspace = startY >= 0.66f && startX >= 0.855f

                                if (startedOnShift) {
                                    isShiftPressedVisual = true
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    do {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    isShiftPressedVisual = false
                                    onToggleShift()
                                    return@awaitEachGesture
                                }

                                if (startedOnBackspace) {
                                    isBackspacePressedVisual = true
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onBackspace()
                                    var lastRepeatMs = startMs
                                    do {
                                        val event = awaitPointerEvent()
                                        val now = SystemClock.uptimeMillis()
                                        if (now - startMs > 400L && now - lastRepeatMs > 75L) {
                                            onBackspace()
                                            lastRepeatMs = now
                                        }
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                    isBackspacePressedVisual = false
                                    return@awaitEachGesture
                                }

                                // Otherwise touch started on one of the 26 letter keys
                                activeTrail.clear()
                                isSwipingGesture = false
                                liveSwipePreviewWord = null

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

                                if (totalPathDistance < 0.075f) {
                                    val altDigit = topRowNumberHints[finalKey]
                                    if (holdDurationMs >= 350L && altDigit != null) {
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
                        val keyPadX = canvasW * 0.006f
                        val keyPadY = canvasH * 0.024f
                        val rowH = canvasH / 3f

                        // Draw Row 3 Left Special Key: SHIFT [0.0 .. 0.135]
                        val shiftW = canvasW * 0.135f - keyPadX * 2f
                        val shiftH = rowH - keyPadY * 2f
                        val shiftLeft = keyPadX
                        val shiftTop = rowH * 2f + keyPadY
                        val shiftActive = shiftState != ShiftState.OFF || isShiftPressedVisual
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(shiftLeft, shiftTop + 2.5f),
                            size = Size(shiftW, shiftH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        drawRoundRect(
                            color = if (shiftActive) TelemetryCyan.copy(alpha = 0.25f) else FutoKeyCapSpecial,
                            topLeft = Offset(shiftLeft, shiftTop),
                            size = Size(shiftW, shiftH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        val shiftSymbol = if (shiftState == ShiftState.CAPS_LOCK) "⇪" else "⇧"
                        val shiftText = textMeasurer.measure(
                            text = shiftSymbol,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (shiftState != ShiftState.OFF) TelemetryCyan else TextPrimary
                            )
                        )
                        drawText(
                            textLayoutResult = shiftText,
                            topLeft = Offset(
                                shiftLeft + shiftW / 2f - shiftText.size.width / 2f,
                                shiftTop + shiftH / 2f - shiftText.size.height / 2f
                            )
                        )

                        // Draw Row 3 Right Special Key: BACKSPACE [0.865 .. 1.0]
                        val bsW = canvasW * 0.135f - keyPadX * 2f
                        val bsH = rowH - keyPadY * 2f
                        val bsLeft = canvasW * 0.865f + keyPadX
                        val bsTop = rowH * 2f + keyPadY
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(bsLeft, bsTop + 2.5f),
                            size = Size(bsW, bsH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        drawRoundRect(
                            color = if (isBackspacePressedVisual) TelemetryCyan.copy(alpha = 0.25f) else FutoKeyCapSpecial,
                            topLeft = Offset(bsLeft, bsTop),
                            size = Size(bsW, bsH),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        val bsText = textMeasurer.measure(
                            text = "⌫",
                            style = TextStyle(
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                        )
                        drawText(
                            textLayoutResult = bsText,
                            topLeft = Offset(
                                bsLeft + bsW / 2f - bsText.size.width / 2f,
                                bsTop + bsH / 2f - bsText.size.height / 2f
                            )
                        )

                        // Draw all 26 QWERTY letter keys
                        for ((ch, geom) in keyGeometries) {
                            val isHovered = hoveredKey == ch
                            val left = (geom.canonicalX - geom.width / 2f) * canvasW + keyPadX
                            val top = (geom.canonicalY - geom.height / 2f) * canvasH + keyPadY
                            val kw = geom.width * canvasW - keyPadX * 2f
                            val kh = geom.height * canvasH - keyPadY * 2f

                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.35f),
                                topLeft = Offset(left, top + 2.5f),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            drawRoundRect(
                                color = if (isHovered) Color(0xFF3E4550) else FutoKeyCapSurface,
                                topLeft = Offset(left, top),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(14f, 14f)
                            )

                            // Subtle top-row number hint (1..0 on q..p)
                            val hintDigit = topRowNumberHints[ch]
                            if (hintDigit != null && !showNumberRow) {
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

                            val displayChar = if (shiftState != ShiftState.OFF) ch.uppercase() else ch.toString()
                            val textLayout = textMeasurer.measure(
                                text = displayChar,
                                style = TextStyle(
                                    fontFamily = SpaceGroteskFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 19.sp,
                                    color = TextPrimary
                                )
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    x = geom.canonicalX * canvasW - textLayout.size.width / 2f,
                                    y = geom.canonicalY * canvasH - textLayout.size.height / 2f
                                )
                            )
                        }

                        // Key press popup preview on single-key tap
                        val currentHover = hoveredKey
                        if (currentHover != null && !isSwipingGesture) {
                            val geom = keyGeometries[currentHover]
                            if (geom != null) {
                                val popW = geom.width * canvasW * 1.15f
                                val popH = geom.height * canvasH * 1.1f
                                val popLeft = (geom.canonicalX * canvasW - popW / 2f).coerceIn(4f, canvasW - popW - 4f)
                                val popTop = ((geom.canonicalY - geom.height * 1.2f) * canvasH).coerceAtLeast(2f)

                                drawRoundRect(
                                    color = Color(0xFF3C424D),
                                    topLeft = Offset(popLeft, popTop),
                                    size = Size(popW, popH),
                                    cornerRadius = CornerRadius(14f, 14f)
                                )
                                val popChar = if (shiftState != ShiftState.OFF) currentHover.uppercase() else currentHover.toString()
                                val popText = textMeasurer.measure(
                                    text = popChar,
                                    style = TextStyle(
                                        fontFamily = SpaceGroteskFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 23.sp,
                                        color = TextPrimary
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

                        // Clean FUTO Gesture Swipe Trail
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
                                color = TelemetryCyan.copy(alpha = 0.28f),
                                style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = trailPath,
                                color = TelemetryCyan,
                                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
                    onBackspace = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onBackspace()
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

        // 4. Authentic FUTO Bottom Row: [ ?123 ]  [ , ]  [      Spacebar      ]  [ . ]  [ Enter ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ?123 / ABC key
            FutoBottomKey(
                modifier = Modifier
                    .weight(1.35f)
                    .testTag("key_mode_switch"),
                backgroundColor = FutoKeyCapSpecial,
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
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Comma key
            FutoBottomKey(
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("key_comma"),
                backgroundColor = FutoKeyCapSpecial,
                contentDesc = "Comma key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPunctuation(",")
                }
            ) {
                Text(
                    text = ",",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            // Spacebar
            FutoBottomKey(
                modifier = Modifier
                    .weight(4.3f)
                    .testTag("key_space"),
                backgroundColor = FutoKeyCapSurface,
                contentDesc = "Spacebar",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSpace()
                }
            ) {
                Text(
                    text = "English",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Period key
            FutoBottomKey(
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("key_period"),
                backgroundColor = FutoKeyCapSpecial,
                contentDesc = "Period key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPunctuation(".")
                }
            ) {
                Text(
                    text = ".",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            // Enter / Action key (FUTO rounded accent pill)
            Box(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TelemetryCyan)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onEnterAction()
                    }
                    .semantics { contentDescription = "Enter key" }
                    .testTag("key_enter"),
                contentAlignment = Alignment.Center
            ) {
                when (imeActionType) {
                    ImeActionType.SEARCH -> Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = FutoSlateBg,
                        modifier = Modifier.size(20.dp)
                    )
                    ImeActionType.SEND -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = FutoSlateBg,
                        modifier = Modifier.size(19.dp)
                    )
                    ImeActionType.DONE -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        tint = FutoSlateBg,
                        modifier = Modifier.size(20.dp)
                    )
                    ImeActionType.GO, ImeActionType.NEXT -> Text(
                        text = if (imeActionType == ImeActionType.GO) "Go" else "Next",
                        style = MaterialTheme.typography.titleMedium,
                        color = FutoSlateBg,
                        fontWeight = FontWeight.Bold
                    )
                    ImeActionType.RETURN -> Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
                        tint = FutoSlateBg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Authentic LatinIME / FUTO 3-Slot Suggestion Strip:
 * [  2nd Candidate  |  1st Primary Candidate (Bold)  |  3rd Candidate  ] [⚙]
 */
@Composable
private fun LatinImeSuggestionStrip(
    candidates: List<GestureCandidate>,
    composingWord: String,
    isSwiping: Boolean,
    liveSwipePreviewWord: String?,
    onOpenSettings: (() -> Unit)?,
    onCandidateClick: (GestureCandidate, Int) -> Unit
) {
    Surface(
        color = FutoSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag("candidate_suggestion_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSwiping && !liveSwipePreviewWord.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = liveSwipePreviewWord,
                        style = MaterialTheme.typography.titleMedium,
                        color = TelemetryCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                val primary = candidates.getOrNull(0)
                val second = candidates.getOrNull(1)
                val third = candidates.getOrNull(2)

                // Left Slot: 2nd candidate
                SuggestionSlot(
                    candidate = second,
                    isPrimary = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("candidate_chip_1"),
                    onClick = { second?.let { onCandidateClick(it, 1) } }
                )

                // Thin vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // Center Slot: 1st Primary Candidate
                SuggestionSlot(
                    candidate = primary,
                    isPrimary = true,
                    modifier = Modifier
                        .weight(1.15f)
                        .testTag("candidate_chip_0"),
                    onClick = { primary?.let { onCandidateClick(it, 0) } }
                )

                // Thin vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // Right Slot: 3rd candidate
                SuggestionSlot(
                    candidate = third,
                    isPrimary = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("candidate_chip_2"),
                    onClick = { third?.let { onCandidateClick(it, 2) } }
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionSlot(
    candidate: GestureCandidate?,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = candidate != null, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (candidate != null) {
            Text(
                text = candidate.word,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPrimary) TextPrimary else TextSecondary,
                fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SymbolsKeyboardGrid(
    isExtended: Boolean,
    onSymbolTap: (Char) -> Unit,
    onBackspace: () -> Unit,
    onToggleExtended: () -> Unit
) {
    val rows = if (!isExtended) {
        listOf(
            listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
            listOf('@', '#', '$', '_', '&', '-', '+', '(', ')', '/'),
            listOf('*', '"', '\'', ':', ';', '!', '?')
        )
    } else {
        listOf(
            listOf('~', '`', '|', '•', '√', 'π', '÷', '×', '§', 'Δ'),
            listOf('£', '¢', '€', '¥', '^', '°', '=', '{', '}', '\\'),
            listOf('%', '©', '®', '™', '✓', '[', ']')
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
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
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(7.dp))
                            .background(FutoKeyCapSpecial)
                            .clickable(onClick = onToggleExtended),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!isExtended) "=\\<" else "?123",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                rowChars.forEach { sym ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(7.dp))
                            .background(FutoKeyCapSurface)
                            .clickable { onSymbolTap(sym) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sym.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
                if (rowIdx == 2) {
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(7.dp))
                            .background(FutoKeyCapSpecial)
                            .clickable(onClick = onBackspace),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⌫",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FutoBottomKey(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentDesc: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .background(backgroundColor)
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
