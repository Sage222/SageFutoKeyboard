package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.KeySpatialProfileEntity
import com.example.engine.DecoderHyperparameters
import com.example.engine.KeyGeometry
import com.example.ui.theme.AdaptiveAmber
import com.example.ui.theme.CoralAlert
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
import kotlin.math.hypot

@Composable
fun StyleAdaptationTabScreen(
    keyGeometries: Map<Char, KeyGeometry>,
    spatialProfiles: Map<Char, KeySpatialProfileEntity>,
    hyperparams: DecoderHyperparameters,
    prototypesCount: Int,
    bigramsCount: Int,
    promotedCount: Int,
    onApplyThumbPreset: (Boolean) -> Unit,
    onResetProfile: () -> Unit,
    onUpdateHyperparams: ((DecoderHyperparameters) -> DecoderHyperparameters) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val adaptedKeysCount = spatialProfiles.values.count { it.hitCount > 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("style_adaptation_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Visual Banner with Telemetry Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(16.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_gesture_hero_1793850892762),
                contentDescription = "FUTO Gesture Telemetry Hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                FutoSlateBg.copy(alpha = 0.35f),
                                FutoSlateBg.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "USER WRITING STYLE & SPATIAL DRIFT ENGINE",
                        style = MaterialTheme.typography.titleMedium,
                        color = TelemetryCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiPill(label = "Adapted Keys", value = "$adaptedKeysCount/26", color = TelemetryCyan)
                        KpiPill(label = "Prototypes", value = "$prototypesCount", color = AdaptiveAmber)
                        KpiPill(label = "N-Grams", value = "$bigramsCount", color = NeuralEmerald)
                        KpiPill(label = "Promotions", value = "+$promotedCount", color = TelemetryCyan)
                    }
                }
            }
        }

        // 26-Key Spatial Drift Heatmap & Thumb Arc Presets
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = AdaptiveAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "26-KEY SPATIAL HITBOX DRIFT MAP",
                            style = MaterialTheme.typography.labelLarge,
                            color = AdaptiveAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "EMA Key Centroid Shift",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FutoSlateBg)
                        .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cw = size.width
                        val ch = size.height

                        for ((keyChar, geom) in keyGeometries) {
                            val profile = spatialProfiles[keyChar]
                            val driftX = profile?.driftX ?: 0f
                            val driftY = profile?.driftY ?: 0f
                            val driftMag = hypot(driftX, driftY)
                            val heatAlpha = (driftMag / 0.045f).coerceIn(0.08f, 0.55f)

                            val padX = cw * 0.006f
                            val padY = ch * 0.025f
                            val left = (geom.canonicalX - geom.width / 2f) * cw + padX
                            val top = (geom.canonicalY - geom.height / 2f) * ch + padY
                            val kw = geom.width * cw - padX * 2f
                            val kh = geom.height * ch - padY * 2f

                            drawRoundRect(
                                color = AdaptiveAmber.copy(alpha = heatAlpha),
                                topLeft = Offset(left, top),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(10f, 10f)
                            )
                            drawRoundRect(
                                color = FutoKeyCapBorder,
                                topLeft = Offset(left, top),
                                size = Size(kw, kh),
                                cornerRadius = CornerRadius(10f, 10f),
                                style = Stroke(width = 1.2f)
                            )

                            val cx = geom.canonicalX * cw
                            val cy = geom.canonicalY * ch
                            // Exaggerate visual shift by 2.8x for clear visual inspection
                            val sx = (geom.canonicalX + driftX * 2.8f) * cw
                            val sy = (geom.canonicalY + driftY * 2.8f) * ch

                            drawCircle(
                                color = TextMuted.copy(alpha = 0.5f),
                                radius = 3f,
                                center = Offset(cx, cy)
                            )
                            drawLine(
                                color = TelemetryCyan,
                                start = Offset(cx, cy),
                                end = Offset(sx, sy),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = AdaptiveAmber,
                                radius = 5f,
                                center = Offset(sx, sy)
                            )

                            val textRes = textMeasurer.measure(
                                text = keyChar.uppercase(),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            drawText(
                                textLayoutResult = textRes,
                                topLeft = Offset(cx - textRes.size.width / 2f, top + 4f)
                            )
                        }
                    }
                }

                // Ergonomic Calibration Preset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "Right-Thumb Arc",
                        color = TelemetryCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_right_thumb"),
                        onClick = { onApplyThumbPreset(true) }
                    )
                    PresetButton(
                        label = "Left-Thumb Arc",
                        color = AdaptiveAmber,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_left_thumb"),
                        onClick = { onApplyThumbPreset(false) }
                    )
                    PresetButton(
                        label = "Zero Baseline",
                        color = CoralAlert,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_profile_button"),
                        onClick = onResetProfile
                    )
                }
            }
        }

        // Decoder Hyperparameters & Learning Controls
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ADAPTIVE DECODER HYPERPARAMETERS",
                        style = MaterialTheme.typography.labelLarge,
                        color = TelemetryCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                HyperparamSlider(
                    title = "Online Style Learning Rate (η)",
                    valueText = "${(hyperparams.learningRateEta * 100).toInt()}%",
                    value = hyperparams.learningRateEta,
                    valueRange = 0.05f..0.35f,
                    color = AdaptiveAmber,
                    onValueChange = { v -> onUpdateHyperparams { it.copy(learningRateEta = v) } }
                )

                HyperparamSlider(
                    title = "DTW Trajectory Shape Weight",
                    valueText = "${(hyperparams.dtwShapeWeight * 100).toInt()}%",
                    value = hyperparams.dtwShapeWeight,
                    valueRange = 0.15f..0.60f,
                    color = TelemetryCyan,
                    onValueChange = { v -> onUpdateHyperparams { it.copy(dtwShapeWeight = v) } }
                )

                HyperparamSlider(
                    title = "Spatial Start/End Anchor Weight",
                    valueText = "${(hyperparams.spatialAnchorWeight * 100).toInt()}%",
                    value = hyperparams.spatialAnchorWeight,
                    valueRange = 0.15f..0.60f,
                    color = NeuralEmerald,
                    onValueChange = { v -> onUpdateHyperparams { it.copy(spatialAnchorWeight = v) } }
                )

                HyperparamSlider(
                    title = "Corner Inflection & Loop Weight",
                    valueText = "${(hyperparams.cornerInflectionWeight * 100).toInt()}%",
                    value = hyperparams.cornerInflectionWeight,
                    valueRange = 0.05f..0.40f,
                    color = AdaptiveAmber,
                    onValueChange = { v -> onUpdateHyperparams { it.copy(cornerInflectionWeight = v) } }
                )

                // Feature switches
                FeatureToggleRow(
                    title = "Personal Key Centroid Drift (Δx, Δy)",
                    subtitle = "Shifts Gaussian key receptors to match your thumb reach",
                    checked = hyperparams.enablePersonalDrift,
                    onCheckedChange = { c -> onUpdateHyperparams { it.copy(enablePersonalDrift = c) } }
                )
                FeatureToggleRow(
                    title = "16-Point Personal Swipe Prototypes",
                    subtitle = "Blends DTW against your personal saved gesture curves",
                    checked = hyperparams.enablePersonalPrototypes,
                    onCheckedChange = { c -> onUpdateHyperparams { it.copy(enablePersonalPrototypes = c) } }
                )
                FeatureToggleRow(
                    title = "Contextual Bigram Language Model",
                    subtitle = "Disambiguates overlapping paths using P(word | prevWord)",
                    checked = hyperparams.enableContextBigrams,
                    onCheckedChange = { c -> onUpdateHyperparams { it.copy(enableContextBigrams = c) } }
                )
                FeatureToggleRow(
                    title = "Double-Letter Dwell & Loop Detector",
                    subtitle = "Detects micro-loops or slowdowns for words like 'coffee', 'hello'",
                    checked = hyperparams.enableDoubleLetterDetection,
                    onCheckedChange = { c -> onUpdateHyperparams { it.copy(enableDoubleLetterDetection = c) } }
                )
            }
        }
    }
}

@Composable
private fun KpiPill(label: String, value: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FutoSurface.copy(alpha = 0.85f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun PresetButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HyperparamSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = FutoKeyCapSurface
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
private fun FeatureToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(FutoSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TelemetryCyan,
                checkedTrackColor = TelemetryCyan.copy(alpha = 0.25f)
            )
        )
    }
}
