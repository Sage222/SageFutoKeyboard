package com.example.data

import com.example.engine.DecoderHyperparameters
import com.example.engine.FutoLexicon
import com.example.engine.GestureDecodeResult
import com.example.engine.GestureTypingEngine
import com.example.engine.WritingStyleLearner
import kotlinx.coroutines.flow.Flow

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
     * Records a committed gesture word (or candidate selection) and updates:
     * 1. Used-Word Priority (`UserWordFrequencyEntity`: increments `personalUseCount` & `correctionSelectCount`)
     * 2. Contextual Bigram (`UserBigramEntity`: increments `P(word | prevWord)`)
     * 3. Per-Key Spatial Hitbox Offsets (`KeySpatialProfileEntity`: EMA drift alignment)
     * 4. 16-Point Personal Swipe Prototype (`GesturePrototypeEntity`)
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

        // 2. Update Used-Word Frequency & Priority
        val existingWord = dao.getUserWord(cleanWord)
        val baseFreq = FutoLexicon.defaultWordsWithFreq[cleanWord] ?: 200
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
    }

    /**
     * Records a word typed via tap-typing or added to personal dictionary so it is prioritized
     * in future swipe gestures and predictions.
     */
    suspend fun recordUsedWord(rawWord: String, optionalPrevWord: String = "", isCustom: Boolean = false) {
        val clean = rawWord.trim().lowercase().filter { it in 'a'..'z' }
        if (clean.isEmpty()) return
        val existing = dao.getUserWord(clean)
        val baseFreq = FutoLexicon.defaultWordsWithFreq[clean] ?: 205
        dao.upsertUserWord(
            UserWordFrequencyEntity(
                word = clean,
                baseFrequency = existing?.baseFrequency ?: baseFreq,
                personalUseCount = (existing?.personalUseCount ?: 0) + 1,
                correctionSelectCount = existing?.correctionSelectCount ?: 0,
                isCustomWord = isCustom || (existing?.isCustomWord == true) || !FutoLexicon.defaultWordsWithFreq.containsKey(clean),
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
                    transitionCount = (existingBigram?.transitionCount ?: 0) + 1,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun addCustomVocabularyWord(rawWord: String, optionalPrevWord: String = "") {
        recordUsedWord(rawWord, optionalPrevWord, isCustom = true)
    }

    suspend fun removeUserWord(word: String) {
        dao.deleteUserWord(word.trim().lowercase())
    }

    suspend fun ensureStarterSeedIfEmpty() {
        // Keep clean by default so only real user words and swipes are prioritized
    }

    suspend fun applyErgonomicThumbPreset(rightHanded: Boolean) {
        // Optional helper if user resets or calibrates
    }

    suspend fun resetAllLearnedStyleData() {
        dao.clearSpatialProfiles()
        dao.clearUserWords()
        dao.clearBigrams()
        dao.clearGesturePrototypes()
        dao.clearSessionLogs()
    }
}
