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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.GesturePrototypeEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
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
fun LexiconNGramsTabScreen(
    userWords: List<UserWordFrequencyEntity>,
    userBigrams: List<UserBigramEntity>,
    gesturePrototypes: List<GesturePrototypeEntity>,
    onAddCustomWord: (word: String, prevWord: String) -> Unit,
    onRemoveUserWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newWordInput by remember { mutableStateOf("") }
    var prevContextInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("lexicon_ngrams_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Add Custom Vocabulary & Bigram Transition Card
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
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "TEACH CUSTOM WORD OR PHRASE TRANSITION",
                        style = MaterialTheme.typography.labelLarge,
                        color = TelemetryCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = prevContextInput,
                        onValueChange = { prevContextInput = it },
                        label = { Text("Prev word (optional)", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AdaptiveAmber,
                            unfocusedBorderColor = FutoKeyCapBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("prev_context_input")
                    )

                    OutlinedTextField(
                        value = newWordInput,
                        onValueChange = { newWordInput = it },
                        label = { Text("Target word *", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TelemetryCyan,
                            unfocusedBorderColor = FutoKeyCapBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_word_input")
                    )
                }

                Button(
                    onClick = {
                        if (newWordInput.isNotBlank()) {
                            onAddCustomWord(newWordInput, prevContextInput)
                            newWordInput = ""
                            prevContextInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelemetryCyan,
                        contentColor = FutoSlateBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_custom_word_button")
                ) {
                    Text(
                        text = "Add to Gesture Dictionary & N-Gram Model",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. 16-Point Learned Personal Swipe Prototype Gallery
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = AdaptiveAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "16-POINT PERSONAL SWIPE PROTOTYPES (${gesturePrototypes.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = AdaptiveAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Each card shows the EMA-blended normalized swipe trajectory learned from your fingers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gesturePrototypes.forEach { proto ->
                        PrototypeSparklineCard(proto = proto)
                    }
                }
            }
        }

        // 3. Learned Word-Pair Bigrams P(nextWord | prevWord)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = NeuralEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LEARNED CONTEXTUAL BIGRAMS (${userBigrams.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuralEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }

                userBigrams.take(10).forEach { bigram ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bigram.prevWord,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.titleMedium,
                                color = TelemetryCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = bigram.nextWord,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeuralEmerald.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${bigram.transitionCount}x context boost",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeuralEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Personal Lexicon & Unigram Boost Table
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
                    text = "PERSONAL VOCABULARY & CORRECTION WEIGHTS (${userWords.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                userWords.take(15).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(FutoSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (entry.isCustomWord) {
                                    Text(
                                        text = "CUSTOM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryCyan
                                    )
                                }
                            }
                            Text(
                                text = "Swiped ${entry.personalUseCount}x • Corrected ${entry.correctionSelectCount}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = { onRemoveUserWord(entry.word) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete learned word ${entry.word}",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrototypeSparklineCard(proto: GesturePrototypeEntity) {
    val points = remember(proto.encodedNormalizedPath) {
        proto.encodedNormalizedPath.split(";").mapNotNull { pair ->
            val coords = pair.split(",")
            if (coords.size == 2) {
                val x = coords[0].toFloatOrNull()
                val y = coords[1].toFloatOrNull()
                if (x != null && y != null) x to y else null
            } else null
        }
    }

    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(FutoSurfaceElevated)
            .border(1.dp, FutoKeyCapBorder, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = proto.word,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${proto.sampleCount}x",
                style = MaterialTheme.typography.labelSmall,
                color = AdaptiveAmber
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(FutoSlateBg)
                .padding(4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (points.size >= 2) {
                    val path = Path()
                    path.moveTo(points.first().first * size.width, points.first().second * size.height)
                    for (i in 1 until points.size) {
                        path.lineTo(points[i].first * size.width, points[i].second * size.height)
                    }
                    drawPath(
                        path = path,
                        color = TelemetryCyan,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawCircle(
                        color = NeuralEmerald,
                        radius = 4f,
                        center = Offset(points.first().first * size.width, points.first().second * size.height)
                    )
                    drawCircle(
                        color = AdaptiveAmber,
                        radius = 4f,
                        center = Offset(points.last().first * size.width, points.last().second * size.height)
                    )
                }
            }
        }
        Text(
            text = "Avg ${proto.avgDurationMs} ms",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
