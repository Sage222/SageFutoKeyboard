package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-key spatial adaptation profile learned from the user's swipe trajectories.
 * Coordinates (driftX, driftY) are stored in normalized keyboard space [0f..1f]
 * so they remain invariant across portrait/landscape or container dimensions.
 */
@Entity(tableName = "key_spatial_profiles")
data class KeySpatialProfileEntity(
    @PrimaryKey val keyChar: String, // "a".."z"
    val driftX: Float = 0f,          // Learned EMA horizontal offset in normalized keyboard width
    val driftY: Float = 0f,          // Learned EMA vertical offset in normalized keyboard height
    val radiusScale: Float = 1.0f,   // Learned Gaussian tolerance multiplier
    val hitCount: Int = 0,           // Total confirmed anchor alignments on this key
    val avgCornerSharpness: Float = 0.5f
)

/**
 * Personal lexicon word entry with adaptive unigram frequency and custom word support.
 */
@Entity(tableName = "user_word_frequencies")
data class UserWordFrequencyEntity(
    @PrimaryKey val word: String,
    val baseFrequency: Int = 100,
    val personalUseCount: Int = 0,
    val correctionSelectCount: Int = 0,
    val isCustomWord: Boolean = false,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Contextual N-Gram (Bigram) transition learned from the user's writing sequence:
 * P(nextWord | prevWord)
 */
@Entity(tableName = "user_bigrams", primaryKeys = ["prevWord", "nextWord"])
data class UserBigramEntity(
    val prevWord: String,
    val nextWord: String,
    val transitionCount: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Personal Gesture Trajectory Prototype for a word.
 * Stores a 16-point equidistant resampled path ("x0,y0;x1,y1;...;x15,y15")
 * blended via Exponential Moving Average whenever the user swipes or confirms this word.
 */
@Entity(tableName = "gesture_prototypes")
data class GesturePrototypeEntity(
    @PrimaryKey val word: String,
    val encodedNormalizedPath: String,
    val sampleCount: Int = 1,
    val avgDurationMs: Long = 450L,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Telemetry log entry for each completed gesture, comparing static baseline decoding
 * vs. user-adapted decoding so accuracy gains can be tracked over time.
 */
@Entity(tableName = "gesture_session_logs")
data class GestureSessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val committedWord: String,
    val prevContextWord: String,
    val staticTopPrediction: String,
    val adaptiveTopPrediction: String,
    val staticConfidence: Float,
    val adaptiveConfidence: Float,
    val inflectionCount: Int,
    val wasPromotedByAdaptation: Boolean,
    val wasManualCorrection: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
