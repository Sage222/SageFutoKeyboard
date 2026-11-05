package com.example.data

import com.example.engine.DecoderHyperparameters
import com.example.engine.FutoLexicon
import com.example.engine.GestureDecodeResult
import com.example.engine.GestureTypingEngine
import com.example.engine.WritingStyleLearner
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Repository coordinating Room persistence with the FUTO Adaptive Gesture Engine
 * and Writing Style Learner.
 */
class AdaptiveStyleRepository(
    private val dao: AdaptiveStyleDao,
    val gestureEngine: GestureTypingEngine = GestureTypingEngine(),
    val styleLearner: WritingStyleLearner = WritingStyleLearner(gestureEngine)
) {
    val keySpatialProfilesFlow: Flow<List<KeySpatialProfileEntity>> = dao.observeKeySpatialProfiles()
    val userWordsFlow: Flow<List<UserWordFrequencyEntity>> = dao.observeUserWords()
    val bigramsFlow: Flow<List<UserBigramEntity>> = dao.observeBigrams()
    val prototypesFlow: Flow<List<GesturePrototypeEntity>> = dao.observeGesturePrototypes()
    val recentLogsFlow: Flow<List<GestureSessionLogEntity>> = dao.observeRecentLogs()

    /**
     * Records a committed gesture word (either auto-accepted top prediction or an explicit
     * candidate correction chip tap) and updates all four adaptive learning pillars:
     * 1. Key Spatial Drift Profile (EMA key centroid offsets)
     * 2. Personal Word Unigram & Correction Frequency
     * 3. Contextual Bigram P(committedWord | prevContextWord)
     * 4. 16-Point Personal Gesture Trajectory Prototype
     */
    suspend fun learnFromCommittedGesture(
        committedWord: String,
        prevContextWord: String,
        decodeResult: GestureDecodeResult,
        hyperparams: DecoderHyperparameters,
        isExplicitUserCorrection: Boolean
    ) {
        val cleanWord = committedWord.trim().lowercase().filter { it in 'a'..'z' }
        if (cleanWord.isEmpty()) return

        // 1. Update Key Spatial Profiles
        val currentProfiles = dao.getKeySpatialProfilesSnapshot().associateBy { it.keyChar.first() }
        val updatedProfiles = styleLearner.computeUpdatedSpatialProfiles(
            committedWord = cleanWord,
            decodeResult = decodeResult,
            currentProfiles = currentProfiles,
            learningRateEta = hyperparams.learningRateEta,
            isExplicitUserCorrection = isExplicitUserCorrection
        )
        if (updatedProfiles.isNotEmpty()) {
            dao.upsertKeySpatialProfiles(updatedProfiles)
        }

        // 2. Update User Word Unigram & Correction Frequency
        val existingWord = dao.getUserWord(cleanWord)
        val baseFreq = FutoLexicon.defaultWordsWithFreq[cleanWord] ?: 165
        val updatedWord = if (existingWord != null) {
            existingWord.copy(
                personalUseCount = existingWord.personalUseCount + 1,
                correctionSelectCount = existingWord.correctionSelectCount + (if (isExplicitUserCorrection) 1 else 0),
                lastUsedTimestamp = System.currentTimeMillis()
            )
        } else {
            UserWordFrequencyEntity(
                word = cleanWord,
                baseFrequency = baseFreq,
                personalUseCount = 1,
                correctionSelectCount = if (isExplicitUserCorrection) 1 else 0,
                isCustomWord = !FutoLexicon.defaultWordsWithFreq.containsKey(cleanWord),
                lastUsedTimestamp = System.currentTimeMillis()
            )
        }
        dao.upsertUserWord(updatedWord)

        // 3. Update Contextual Bigram Transition P(cleanWord | prevNorm)
        val prevNorm = prevContextWord.trim().lowercase().filter { it in 'a'..'z' }
        if (prevNorm.isNotEmpty()) {
            val existingBigram = dao.getBigram(prevNorm, cleanWord)
            val updatedBigram = if (existingBigram != null) {
                existingBigram.copy(
                    transitionCount = existingBigram.transitionCount + (if (isExplicitUserCorrection) 2 else 1),
                    lastUpdated = System.currentTimeMillis()
                )
            } else {
                UserBigramEntity(
                    prevWord = prevNorm,
                    nextWord = cleanWord,
                    transitionCount = if (isExplicitUserCorrection) 2 else 1,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            dao.upsertBigram(updatedBigram)
        }

        // 4. Update 16-Point Personal Gesture Prototype
        if (decodeResult.prototype16Path.size == 16 && decodeResult.pathLengthNormalized >= 0.05f) {
            val existingProto = dao.getGesturePrototype(cleanWord)
            val updatedProto = styleLearner.computeUpdatedPrototype(
                committedWord = cleanWord,
                current16Path = decodeResult.prototype16Path,
                durationMs = decodeResult.durationMs,
                existingPrototype = existingProto,
                learningRateEta = hyperparams.learningRateEta
            )
            if (updatedProto != null) {
                dao.upsertGesturePrototype(updatedProto)
            }
        }

        // 5. Log session telemetry for accuracy tracking
        val topStaticScore = decodeResult.candidates.maxOfOrNull { it.baselineStaticScore } ?: 0f
        val topAdaptiveScore = decodeResult.candidates.firstOrNull()?.adaptiveScore ?: 0f
        dao.insertSessionLog(
            GestureSessionLogEntity(
                committedWord = cleanWord,
                prevContextWord = prevNorm,
                staticTopPrediction = decodeResult.staticTopWord.ifEmpty { cleanWord },
                adaptiveTopPrediction = decodeResult.adaptiveTopWord.ifEmpty { cleanWord },
                staticConfidence = topStaticScore,
                adaptiveConfidence = topAdaptiveScore,
                inflectionCount = decodeResult.inflectionNodes.count {
                    it.nodeType == com.example.engine.InflectionNode.NodeType.CORNER
                },
                wasPromotedByAdaptation = decodeResult.wasPromotedByAdaptation ||
                    (isExplicitUserCorrection && decodeResult.staticTopWord != cleanWord),
                wasManualCorrection = isExplicitUserCorrection,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun addCustomVocabularyWord(rawWord: String, optionalPrevWord: String = "") {
        val clean = rawWord.trim().lowercase().filter { it in 'a'..'z' }
        if (clean.isEmpty()) return
        val existing = dao.getUserWord(clean)
        dao.upsertUserWord(
            UserWordFrequencyEntity(
                word = clean,
                baseFrequency = existing?.baseFrequency ?: 205,
                personalUseCount = (existing?.personalUseCount ?: 0) + 3,
                correctionSelectCount = (existing?.correctionSelectCount ?: 0) + 1,
                isCustomWord = true,
                lastUsedTimestamp = System.currentTimeMillis()
            )
        )
        val cleanPrev = optionalPrevWord.trim().lowercase().filter { it in 'a'..'z' }
        if (cleanPrev.isNotEmpty()) {
            val existingBigram = dao.getBigram(cleanPrev, clean)
            dao.upsertBigram(
                UserBigramEntity(
                    prevWord = cleanPrev,
                    nextWord = clean,
                    transitionCount = (existingBigram?.transitionCount ?: 0) + 3,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeUserWord(word: String) {
        dao.deleteUserWord(word.trim().lowercase())
    }

    /**
     * Seeds realistic initial right-thumb ergonomic drift and starter learned words if the database
     * is completely empty on first launch, so all charts/heatmaps have immediate data.
     */
    suspend fun ensureStarterSeedIfEmpty() {
        val existingProfiles = dao.getKeySpatialProfilesSnapshot()
        if (existingProfiles.isNotEmpty()) return

        applyErgonomicThumbPreset(rightHanded = true)
    }

    /**
     * Applies a realistic right-thumb or left-thumb ergonomic arc calibration preset,
     * simulating how a user's thumb naturally pulls far-edge keys slightly inward/downward
     * and rounds inner corners.
     */
    suspend fun applyErgonomicThumbPreset(rightHanded: Boolean) {
        val profiles = mutableListOf<KeySpatialProfileEntity>()
        for ((ch, geom) in gestureEngine.qwertyKeys) {
            // Right-thumb users slightly undershoot left-side keys (pulling +X toward center)
            // and slightly drop top-row keys (+Y downward)
            val anchorX = if (rightHanded) 0.78f else 0.22f
            val anchorY = 0.92f
            val pullX = ((anchorX - geom.canonicalX) * 0.042f).coerceIn(-0.038f, 0.038f)
            val pullY = ((anchorY - geom.canonicalY) * 0.034f).coerceIn(-0.028f, 0.032f)
            val harmonic = sin(ch.code.toDouble() * 0.7).toFloat() * 0.005f
            val cosHarmonic = cos(ch.code.toDouble() * 0.5).toFloat() * 0.004f

            profiles.add(
                KeySpatialProfileEntity(
                    keyChar = ch.toString(),
                    driftX = pullX + harmonic,
                    driftY = pullY + cosHarmonic,
                    radiusScale = if (geom.col in 0..2 || geom.col >= 7) 1.12f else 1.02f,
                    hitCount = 6 + (ch.code % 9),
                    avgCornerSharpness = 0.58f + (ch.code % 5) * 0.05f
                )
            )
        }
        dao.upsertKeySpatialProfiles(profiles)

        // Seed starter personal words & bigrams if empty
        if (dao.getUserWordsSnapshot().isEmpty()) {
            val starterWords = listOf(
                UserWordFrequencyEntity("futo", baseFrequency = 225, personalUseCount = 8, correctionSelectCount = 2, isCustomWord = true),
                UserWordFrequencyEntity("gesture", baseFrequency = 220, personalUseCount = 7, correctionSelectCount = 1, isCustomWord = false),
                UserWordFrequencyEntity("keyboard", baseFrequency = 220, personalUseCount = 6, correctionSelectCount = 1, isCustomWord = false),
                UserWordFrequencyEntity("adaptive", baseFrequency = 215, personalUseCount = 5, correctionSelectCount = 2, isCustomWord = false),
                UserWordFrequencyEntity("from", baseFrequency = 225, personalUseCount = 6, correctionSelectCount = 1, isCustomWord = false),
                UserWordFrequencyEntity("their", baseFrequency = 218, personalUseCount = 5, correctionSelectCount = 1, isCustomWord = false)
            )
            for (w in starterWords) {
                dao.upsertUserWord(w)
            }
        }

        if (dao.getBigramsSnapshot().isEmpty()) {
            val starterBigrams = listOf(
                UserBigramEntity("futo", "keyboard", transitionCount = 5),
                UserBigramEntity("gesture", "typing", transitionCount = 5),
                UserBigramEntity("adaptive", "style", transitionCount = 4),
                UserBigramEntity("came", "from", transitionCount = 4),
                UserBigramEntity("from", "their", transitionCount = 3),
                UserBigramEntity("the", "quick", transitionCount = 4)
            )
            for (b in starterBigrams) {
                dao.upsertBigram(b)
            }
        }

        if (dao.getGesturePrototypesSnapshot().isEmpty()) {
            val sampleProtoWords = listOf("futo", "gesture", "the", "quick", "keyboard")
            val profileMap = profiles.associateBy { it.keyChar.first() }
            for (w in sampleProtoWords) {
                val template16 = gestureEngine.buildWordTemplate32(w, profileMap, targetCount = 16)
                dao.upsertGesturePrototype(
                    GesturePrototypeEntity(
                        word = w,
                        encodedNormalizedPath = gestureEngine.encodePrototypePath(template16),
                        sampleCount = 4,
                        avgDurationMs = 390L
                    )
                )
            }
        }
    }

    suspend fun resetAllLearnedStyleData() {
        dao.clearSpatialProfiles()
        dao.clearUserWords()
        dao.clearBigrams()
        dao.clearGesturePrototypes()
        dao.clearSessionLogs()
    }
}
