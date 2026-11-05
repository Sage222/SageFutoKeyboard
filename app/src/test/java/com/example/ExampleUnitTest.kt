package com.example

import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import com.example.engine.DecoderHyperparameters
import com.example.engine.GestureTypingEngine
import com.example.engine.RawTouchPoint
import com.example.engine.WritingStyleLearner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val engine = GestureTypingEngine()
    private val learner = WritingStyleLearner(engine)

    @Test
    fun gestureEngine_recognizesSwipedWordAndInflections() {
        val template = engine.buildWordTemplate32("futo", emptyMap(), targetCount = 24)
        val rawPoints = template.mapIndexed { idx, (x, y) ->
            RawTouchPoint(x = x, y = y, timestampMs = 1000L + idx * 18L)
        }

        val result = engine.decodeGesture(
            rawPoints = rawPoints,
            prevContextWord = "",
            hyperparams = DecoderHyperparameters(),
            spatialProfiles = emptyMap(),
            userWords = emptyMap(),
            userBigrams = emptyMap(),
            userPrototypes = emptyMap()
        )

        assertTrue(result.candidates.isNotEmpty())
        assertEquals("futo", result.adaptiveTopWord)
        assertTrue(result.inflectionNodes.size >= 2)
    }

    @Test
    fun styleLearner_adaptsSpatialDriftAndBoostsPredictionAccuracyOverTime() {
        // Simulate a user who consistently swipes shifted +0.03f right and +0.025f down
        val canonicalTemplate = engine.buildWordTemplate32("gesture", emptyMap(), targetCount = 24)
        val shiftedRawPoints = canonicalTemplate.mapIndexed { idx, (x, y) ->
            RawTouchPoint(
                x = (x + 0.03f).coerceIn(0.02f, 0.98f),
                y = (y + 0.025f).coerceIn(0.02f, 0.98f),
                timestampMs = 2000L + idx * 15L
            )
        }

        val initialDecode = engine.decodeGesture(
            rawPoints = shiftedRawPoints,
            prevContextWord = "your",
            hyperparams = DecoderHyperparameters(),
            spatialProfiles = emptyMap(),
            userWords = emptyMap(),
            userBigrams = emptyMap(),
            userPrototypes = emptyMap()
        )

        val initialTargetScore = initialDecode.candidates.first { it.word == "gesture" }.adaptiveScore

        // Learn from 3 iterations of the user's shifted swipe + bigram + personal prototype
        var profiles = emptyMap<Char, KeySpatialProfileEntity>()
        var proto = learner.computeUpdatedPrototype("gesture", initialDecode.prototype16Path, 360L, null, 0.25f)
        repeat(3) {
            val updatedList = learner.computeUpdatedSpatialProfiles(
                committedWord = "gesture",
                decodeResult = initialDecode,
                currentProfiles = profiles,
                learningRateEta = 0.25f,
                isExplicitUserCorrection = true
            )
            profiles = profiles + updatedList.associateBy { it.keyChar.first() }
            proto = learner.computeUpdatedPrototype("gesture", initialDecode.prototype16Path, 360L, proto, 0.25f)
        }

        val adaptedDecode = engine.decodeGesture(
            rawPoints = shiftedRawPoints,
            prevContextWord = "your",
            hyperparams = DecoderHyperparameters(),
            spatialProfiles = profiles,
            userWords = mapOf("gesture" to UserWordFrequencyEntity("gesture", personalUseCount = 3, correctionSelectCount = 1)),
            userBigrams = mapOf(("your" to "gesture") to UserBigramEntity("your", "gesture", transitionCount = 3)),
            userPrototypes = proto?.let { mapOf("gesture" to it) }.orEmpty()
        )

        val adaptedTargetScore = adaptedDecode.candidates.first { it.word == "gesture" }.adaptiveScore
        assertEquals("gesture", adaptedDecode.adaptiveTopWord)
        assertTrue(
            "Expected adapted score ($adaptedTargetScore) > initial score ($initialTargetScore)",
            adaptedTargetScore > initialTargetScore
        )
    }
}
