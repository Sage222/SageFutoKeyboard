package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GestureSessionLogEntity
import com.example.data.KeySpatialProfileEntity
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.InflectionNode
import com.example.engine.KeyGeometry
import com.example.ui.theme.AdaptiveAmber
import com.example.ui.theme.FutoKeyCapBorder
import com.example.ui.theme.FutoKeyCapSurface
import com.example.ui.theme.FutoSlateBg
import com.example.ui.theme.FutoSurface
import com.example.ui.theme.FutoSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeuralEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DecoderLabTabScreen(
    lastDecodeResult: GestureDecodeResult?,
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    sessionLogs: List<GestureSessionLogEntity>,
    onSimulateWordSwipe: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val testWords = listOf("keyboard", "gesture", "from", "form", "their", "hello", "coffee", "quick")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("decoder_lab_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Quick Gesture Test Trigger Strip
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "GESTURE TRAJECTORY & DTW INSPECTOR",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Compares 32-pt Equidistant Curve, Corner Inflection Anchors & Static vs. Adaptive Scoring",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Quick word swipe simulator buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    testWords.forEach { word ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(FutoKeyCapSurface)
                                .border(1.dp, TelemetryCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onSimulateWordSwipe(word) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("inspect_word_$word")
                        ) {
                            Text(
                                text = "Swipe \"$word\"",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Trajectory + Inflection Node Visualizer Canvas
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
                    Text(
                        text = if (lastDecodeResult != null && lastDecodeResult.adaptiveTopWord.isNotEmpty()) {
                            "Decoded Path: \"${lastDecodeResult.adaptiveTopWord.uppercase()}\" (${lastDecodeResult.durationMs} ms)"
                        } else {
                            "No Active Gesture Trajectory"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = TelemetryCyan
                    )
                    if (lastDecodeResult != null) {
                        Text(
                            text = "${lastDecodeResult.inflectionNodes.size} anchors • len ${"%.2f".format(lastDecodeResult.pathLengthNormalized)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AdaptiveAmber
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FutoSlateBg)
                        .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cw = size.width
                        val ch = size.height

                        // Draw subtle key grid centers
                        for ((charKey, geom) in keyGeometries) {
                            val drift = spatialProfiles[charKey]
                            val kx = (geom.canonicalX + (drift?.driftX ?: 0f)) * cw
                            val ky = (geom.canonicalY + (drift?.driftY ?: 0f)) * ch
                            drawCircle(
                                color = FutoKeyCapBorder.copy(alpha = 0.6f),
                                radius = 14f,
                                center = Offset(kx, ky),
                                style = Stroke(width = 1.2f)
                            )
                            val labelLayout = textMeasurer.measure(
                                text = charKey.uppercase(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            )
                            drawText(
                                textLayoutResult = labelLayout,
                                topLeft = Offset(
                                    kx - labelLayout.size.width / 2f,
                                    ky - labelLayout.size.height / 2f
                                )
                            )
                        }

                        // Draw user smoothed trajectory + inflection nodes
                        val pts = lastDecodeResult?.smoothedPath.orEmpty()
                        if (pts.size >= 2) {
                            val curvePath = Path()
                            curvePath.moveTo(pts.first().x * cw, pts.first().y * ch)
                            for (i in 1 until pts.size) {
                                curvePath.lineTo(pts[i].x * cw, pts[i].y * ch)
                            }
                            drawPath(
                                path = curvePath,
                                color = TelemetryCyan.copy(alpha = 0.30f),
                                style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawPath(
                                path = curvePath,
                                color = TelemetryCyan,
                                style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )

                            // Draw 32 resampled sample ticks
                            for (pt in pts) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.6f),
                                    radius = 2.5f,
                                    center = Offset(pt.x * cw, pt.y * ch)
                                )
                            }

                            // Draw Inflection Anchors (Start, Corner, Dwell/Loop, End)
                            for (node in lastDecodeResult?.inflectionNodes.orEmpty()) {
                                val c = when (node.nodeType) {
                                    InflectionNode.NodeType.START -> NeuralEmerald
                                    InflectionNode.NodeType.CORNER -> AdaptiveAmber
                                    InflectionNode.NodeType.DWELL_LOOP -> Color(0xFFE040FB)
                                    InflectionNode.NodeType.END -> TelemetryCyan
                                }
                                drawCircle(
                                    color = c,
                                    radius = 8.5f,
                                    center = Offset(node.x * cw, node.y * ch)
                                )
                            }
                        }
                    }
                }

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendDot(color = NeuralEmerald, label = "Start Anchor")
                    LegendDot(color = AdaptiveAmber, label = "Corner Inflection")
                    LegendDot(color = Color(0xFFE040FB), label = "2x-Letter Loop")
                    LegendDot(color = TelemetryCyan, label = "End Anchor")
                }
            }
        }

        // Candidate Multi-Stage Score Breakdown (Static Baseline vs. Adaptive Score)
        Surface(
            color = FutoSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = AdaptiveAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "STATIC BASELINE VS. USER-ADAPTED DECODER SCORES",
                        style = MaterialTheme.typography.labelLarge,
                        color = AdaptiveAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                val candidates = lastDecodeResult?.candidates.orEmpty().take(5)
                if (candidates.isEmpty()) {
                    Text(
                        text = "Swipe a word in the Sandbox or tap a Quick Test word above to inspect candidate scores.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    candidates.forEachIndexed { idx, cand ->
                        CandidateBreakdownCard(rank = idx + 1, candidate = cand)
                    }
                }
            }
        }

        // Recent Gesture Recognition Telemetry History
        if (sessionLogs.isNotEmpty()) {
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
                    Text(
                        text = "RECENT ADAPTATION TELEMETRY LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    sessionLogs.take(6).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(FutoSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "\"${log.committedWord}\"",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (log.wasPromotedByAdaptation) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = AdaptiveAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Promoted over '${log.staticTopPrediction}'",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AdaptiveAmber
                                        )
                                    }
                                }
                                Text(
                                    text = "Context: '${log.prevContextWord.ifEmpty { "none" }}' • ${log.inflectionCount} corner anchors",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = "${log.staticConfidence.toInt()}% → ${log.adaptiveConfidence.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (log.adaptiveConfidence >= log.staticConfidence) NeuralEmerald else TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateBreakdownCard(
    rank: Int,
    candidate: GestureCandidate
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FutoSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (rank == 1) TelemetryCyan.copy(alpha = 0.6f) else FutoKeyCapBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
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
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rank == 1) TelemetryCyan else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = candidate.word,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AdaptiveAmber.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = candidate.explanationBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = AdaptiveAmber
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Base: ${"%.1f".format(candidate.baselineStaticScore)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = "Adapted: ${"%.1f".format(candidate.adaptiveScore)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rank == 1) TelemetryCyan else NeuralEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Sub-metric progress bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricMiniBar(
                label = "Anchor ${candidate.spatialAnchorScore.toInt()}%",
                progress = (candidate.spatialAnchorScore / 100f).coerceIn(0f, 1f),
                color = TelemetryCyan,
                modifier = Modifier.weight(1f)
            )
            MetricMiniBar(
                label = "DTW ${candidate.dtwShapeScore.toInt()}%",
                progress = (candidate.dtwShapeScore / 100f).coerceIn(0f, 1f),
                color = NeuralEmerald,
                modifier = Modifier.weight(1f)
            )
            MetricMiniBar(
                label = "Style +${"%.1f".format(candidate.personalStyleBoost)}",
                progress = (candidate.personalStyleBoost / 20f).coerceIn(0.05f, 1f),
                color = AdaptiveAmber,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricMiniBar(
    label: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = FutoKeyCapSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
