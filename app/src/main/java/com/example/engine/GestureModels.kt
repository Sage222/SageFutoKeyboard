package com.example.engine

/**
 * Raw pointer sample captured during a swipe gesture on the keyboard canvas.
 */
data class RawTouchPoint(
    val x: Float,          // Normalized keyboard X [0f..1f]
    val y: Float,          // Normalized keyboard Y [0f..1f]
    val timestampMs: Long  // SystemClock / uptime millis
)

/**
 * Resampled & smoothed point along the gesture trajectory with kinematic derivatives.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val progress: Float,       // [0f..1f] arc-length progress
    val velocity: Float,       // Normalized speed
    val curvatureDeg: Float,   // Turning angle in degrees [0f..180f]
    val isInflection: Boolean, // True if sharp corner or dwell anchor
    val isLoopOrDwell: Boolean // True if micro-loop or low-velocity dwell for double letters
)

/**
 * Detected anchor node along a user's swipe trajectory (start, corner turn, dwell, or end).
 */
data class InflectionNode(
    val index: Int,
    val x: Float,
    val y: Float,
    val turningAngleDeg: Float,
    val nearestKey: Char,
    val nodeType: NodeType
) {
    enum class NodeType { START, CORNER, DWELL_LOOP, END }
}

/**
 * Canonical QWERTY key geometry in normalized keyboard space [0f..1f].
 */
data class KeyGeometry(
    val char: Char,
    val row: Int,
    val col: Int,
    val canonicalX: Float,
    val canonicalY: Float,
    val width: Float,
    val height: Float
)

/**
 * User-tunable and automatically self-tuning weights for the FUTO Adaptive Gesture Decoder.
 */
data class DecoderHyperparameters(
    val spatialAnchorWeight: Float = 0.34f,
    val dtwShapeWeight: Float = 0.36f,
    val cornerInflectionWeight: Float = 0.18f,
    val contextBigramWeight: Float = 0.12f,
    val learningRateEta: Float = 0.18f,
    val enablePersonalDrift: Boolean = true,
    val enablePersonalPrototypes: Boolean = true,
    val enableContextBigrams: Boolean = true,
    val enableDoubleLetterDetection: Boolean = true
)

/**
 * Detailed score breakdown for a single candidate word so the user can inspect
 * why the adaptive engine ranked it higher than the static baseline.
 */
data class GestureCandidate(
    val word: String,
    val adaptiveScore: Float,          // Final score [0f..100f] with user style adaptation
    val baselineStaticScore: Float,    // Score [0f..100f] without user style adaptation
    val spatialAnchorScore: Float,     // Start/end + shifted key centroid alignment [0f..100f]
    val dtwShapeScore: Float,          // 32-point Dynamic Time Warping path match [0f..100f]
    val inflectionMatchScore: Float,   // Corner & double-letter anchor match [0f..100f]
    val contextBigramBoost: Float,     // Bonus from P(word | prevWord)
    val personalStyleBoost: Float,     // Combined boost from shifted centroids, personal freq & prototype
    val explanationBadge: String       // e.g. "+Drift & Bigram", "+Prototype Match", "+Corner Anchor"
)

/**
 * Complete output from decoding a gesture or tap sequence.
 */
data class GestureDecodeResult(
    val candidates: List<GestureCandidate>,
    val smoothedPath: List<NormalizedPoint>,
    val prototype16Path: List<Pair<Float, Float>>,
    val inflectionNodes: List<InflectionNode>,
    val detectedDoubleLetterZone: Boolean,
    val durationMs: Long,
    val pathLengthNormalized: Float,
    val staticTopWord: String,
    val adaptiveTopWord: String,
    val wasPromotedByAdaptation: Boolean
)
