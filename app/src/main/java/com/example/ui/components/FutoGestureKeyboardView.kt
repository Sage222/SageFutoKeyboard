package com.example.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
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

/**
 * Interactive FUTO Adaptive Gesture Keyboard component supporting:
 * - Fluid continuous Bezier swipe gesture recognition across all 26 QWERTY keys
 * - Live inflection node & learned spatial drift overlay on keycaps
 * - Interactive candidate suggestion strip where tapping an alternate candidate immediately
 *   triggers supervised style learning (updating key offsets, bigrams, and gesture prototypes)
 */
@Composable
fun FutoGestureKeyboardView(
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    lastDecodeResult: GestureDecodeResult?,
    showDriftOverlay: Boolean,
    isShiftActive: Boolean,
    onToggleShift: () -> Unit,
    onToggleDriftOverlay: () -> Unit,
    onGestureCompleted: (List<RawTouchPoint>) -> Unit,
    onCandidateSelected: (GestureCandidate, isCorrection: Boolean) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onPunctuation: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val activeTrail = remember { mutableStateListOf<RawTouchPoint>() }
    var hoveredKey by remember { mutableStateOf<Char?>(null) }
    var isSwiping by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FutoSurface)
            .border(width = 1.dp, color = FutoKeyCapBorder.copy(alpha = 0.6f))
            .padding(bottom = 4.dp)
            .testTag("futo_gesture_keyboard")
    ) {
        // 1. Candidate Prediction & Adaptation Telemetry Strip
        CandidateSuggestionBar(
            decodeResult = lastDecodeResult,
            hoveredKey = hoveredKey,
            isSwiping = isSwiping,
            activePointCount = activeTrail.size,
            onCandidateClick = { candidate, index ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCandidateSelected(candidate, index > 0)
            }
        )

        // 2. Main 26-Key Gesture Canvas (3 rows of QWERTY letters)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("gesture_letter_canvas")
                .semantics { contentDescription = "Gesture typing QWERTY key area" }
                .pointerInput(keyGeometries, spatialProfiles) {
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val heightPx = size.height.toFloat().coerceAtLeast(1f)

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        activeTrail.clear()
                        isSwiping = true
                        val normX = (down.position.x / widthPx).coerceIn(0.01f, 0.99f)
                        val normY = (down.position.y / heightPx).coerceIn(0.01f, 0.99f)
                        val startMs = SystemClock.uptimeMillis()

                        activeTrail.add(RawTouchPoint(normX, normY, startMs))
                        val initialKey = findClosestKey(normX, normY, keyGeometries, spatialProfiles)
                        hoveredKey = initialKey
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                        var lastKey = initialKey
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val cx = (change.position.x / widthPx).coerceIn(0.01f, 0.99f)
                                val cy = (change.position.y / heightPx).coerceIn(0.01f, 0.99f)
                                val nowMs = SystemClock.uptimeMillis()
                                val prev = activeTrail.lastOrNull()
                                if (prev == null || hypot(cx - prev.x, cy - prev.y) >= 0.006f) {
                                    activeTrail.add(RawTouchPoint(cx, cy, nowMs))
                                }
                                val currentKey = findClosestKey(cx, cy, keyGeometries, spatialProfiles)
                                hoveredKey = currentKey
                                if (currentKey != lastKey) {
                                    lastKey = currentKey
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        val finishedPoints = activeTrail.toList()
                        isSwiping = false
                        hoveredKey = null
                        activeTrail.clear()
                        if (finishedPoints.isNotEmpty()) {
                            onGestureCompleted(finishedPoints)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // Draw each of the 26 QWERTY keys
                for ((ch, geom) in keyGeometries) {
                    val profile = spatialProfiles[ch]
                    val isHovered = hoveredKey == ch
                    val keyPadX = canvasW * 0.006f
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
                        color = if (isHovered) TelemetryCyan.copy(alpha = 0.26f) else FutoKeyCapSurface,
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

                    // Key character label
                    val displayChar = if (isShiftActive) ch.uppercase() else ch.toString()
                    val textLayout = textMeasurer.measure(
                        text = displayChar,
                        style = TextStyle(
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isHovered) TelemetryCyan else TextPrimary
                        )
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(
                            x = geom.canonicalX * canvasW - textLayout.size.width / 2f,
                            y = geom.canonicalY * canvasH - textLayout.size.height / 2f - 2f
                        )
                    )

                    // Optional Spatial Drift Overlay: shows learned receptor center & shift vector
                    if (showDriftOverlay && profile != null && profile.hitCount > 0) {
                        val cx = geom.canonicalX * canvasW
                        val cy = geom.canonicalY * canvasH
                        // Amplify visual vector by 2.2x so subtle thumb offsets are easy to inspect
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
                            radius = 5.5f,
                            center = Offset(shiftedX, shiftedY)
                        )
                        drawCircle(
                            color = AdaptiveAmber.copy(alpha = 0.18f),
                            radius = 16f * profile.radiusScale,
                            center = Offset(shiftedX, shiftedY)
                        )
                    }
                }

                // Draw Ghost Overlay of Last Completed Gesture + Inflection Anchors when Drift HUD is active
                if (!isSwiping && showDriftOverlay && lastDecodeResult != null && lastDecodeResult.smoothedPath.size > 1) {
                    val ghostPath = Path()
                    val pts = lastDecodeResult.smoothedPath
                    ghostPath.moveTo(pts.first().x * canvasW, pts.first().y * canvasH)
                    for (i in 1 until pts.size) {
                        ghostPath.lineTo(pts[i].x * canvasW, pts[i].y * canvasH)
                    }
                    drawPath(
                        path = ghostPath,
                        color = TelemetryCyan.copy(alpha = 0.35f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    for (node in lastDecodeResult.inflectionNodes) {
                        val nodeColor = when (node.nodeType) {
                            InflectionNode.NodeType.START -> NeuralEmerald
                            InflectionNode.NodeType.CORNER -> AdaptiveAmber
                            InflectionNode.NodeType.DWELL_LOOP -> Color(0xFFE040FB)
                            InflectionNode.NodeType.END -> TelemetryCyan
                        }
                        drawCircle(
                            color = nodeColor,
                            radius = 7f,
                            center = Offset(node.x * canvasW, node.y * canvasH)
                        )
                    }
                }

                // Draw Active Live Gesture Swipe Trail (Bezier smoothed glowing ribbon)
                if (activeTrail.size >= 2) {
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

                    // Outer glow
                    drawPath(
                        path = trailPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                TelemetryCyan.copy(alpha = 0.20f),
                                AdaptiveAmber.copy(alpha = 0.38f),
                                TelemetryCyan.copy(alpha = 0.50f)
                            )
                        ),
                        style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Core laser trail
                    drawPath(
                        path = trailPath,
                        brush = Brush.linearGradient(
                            colors = listOf(TelemetryCyan, AdaptiveAmber, TelemetryCyan)
                        ),
                        style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Pointer head glow
                    drawCircle(
                        color = TelemetryCyan,
                        radius = 10f,
                        center = Offset(last.x * canvasW, last.y * canvasH)
                    )
                }
            }
        }

        // 3. Bottom Utility Row (Shift, Drift HUD Toggle, Spacebar, Punctuation, Backspace, Enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.1f)
                    .testTag("key_shift"),
                isActive = isShiftActive,
                activeColor = TelemetryCyan,
                contentDesc = "Shift key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleShift()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardCapslock,
                    contentDescription = "Toggle Shift",
                    tint = if (isShiftActive) TelemetryCyan else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Spatial Drift HUD overlay toggle
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("key_drift_hud"),
                isActive = showDriftOverlay,
                activeColor = AdaptiveAmber,
                contentDesc = "Toggle Spatial Drift Overlay",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onToggleDriftOverlay()
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Drift HUD",
                        tint = if (showDriftOverlay) AdaptiveAmber else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "DRIFT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showDriftOverlay) AdaptiveAmber else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Spacebar with FUTO Adaptive status pill
            UtilityKeyButton(
                modifier = Modifier
                    .weight(3.6f)
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
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NeuralEmerald)
                    )
                    Text(
                        text = "FUTO ADAPTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.SpaceBar,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Period / Punctuation key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(0.9f)
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

            // Enter key
            UtilityKeyButton(
                modifier = Modifier
                    .weight(1.15f)
                    .testTag("key_enter"),
                isActive = true,
                activeColor = TelemetryCyan,
                contentDesc = "Enter key",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onEnter()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = TelemetryCyan,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun CandidateSuggestionBar(
    decodeResult: GestureDecodeResult?,
    hoveredKey: Char?,
    isSwiping: Boolean,
    activePointCount: Int,
    onCandidateClick: (GestureCandidate, Int) -> Unit
) {
    Surface(
        color = FutoSlateBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("candidate_suggestion_bar")
    ) {
        if (isSwiping && hoveredKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TelemetryCyan.copy(alpha = 0.2f))
                            .border(1.dp, TelemetryCyan, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hoveredKey.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = TelemetryCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Capturing gesture trajectory ($activePointCount samples)...",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                Text(
                    text = "DTW + DRIFT ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = AdaptiveAmber
                )
            }
        } else if (decodeResult != null && decodeResult.candidates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                decodeResult.candidates.take(5).forEachIndexed { idx, candidate ->
                    val isTop = idx == 0
                    val isPromoted = isTop && decodeResult.wasPromotedByAdaptation
                    val borderColor = when {
                        isPromoted -> AdaptiveAmber
                        isTop -> TelemetryCyan
                        else -> FutoKeyCapBorder
                    }
                    val bgColor = when {
                        isPromoted -> AdaptiveAmber.copy(alpha = 0.16f)
                        isTop -> TelemetryCyan.copy(alpha = 0.15f)
                        else -> FutoKeyCapSurface.copy(alpha = 0.8f)
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onCandidateClick(candidate, idx) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("candidate_chip_$idx"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isPromoted) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Promoted by style learning",
                                tint = AdaptiveAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = candidate.word,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = if (isTop) FontWeight.Bold else FontWeight.Medium
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${candidate.adaptiveScore.toInt()}%",
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPromoted) AdaptiveAmber else if (isTop) TelemetryCyan else TextSecondary
                                )
                            )
                            Text(
                                text = candidate.explanationBadge,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 8.sp,
                                    color = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Swipe across keys to gesture-type • Tap alternate chips to train your style",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
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
