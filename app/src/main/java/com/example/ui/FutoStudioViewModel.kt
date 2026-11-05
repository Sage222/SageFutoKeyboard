package com.example.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AdaptiveStyleRepository
import com.example.data.GesturePrototypeEntity
import com.example.data.GestureSessionLogEntity
import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import com.example.engine.DecoderHyperparameters
import com.example.engine.FutoLexicon
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.sin

data class StudioUiState(
    val editorText: String = "futo adaptive ",
    val lastCommittedWord: String = "adaptive",
    val prevContextWord: String = "futo",
    val activeDrillIndex: Int = 0,
    val isShiftActive: Boolean = false,
    val showDriftOverlay: Boolean = true,
    val hyperparams: DecoderHyperparameters = DecoderHyperparameters(),
    val lastDecodeResult: GestureDecodeResult? = null,
    val statusBannerText: String = "Swipe across the keys below or tap 'Simulate Thumb Swipe' to test recognition & style learning."
)

class FutoStudioViewModel(
    private val repository: AdaptiveStyleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val keyGeometries: Map<Char, KeyGeometry> = repository.gestureEngine.qwertyKeys

    val spatialProfiles: StateFlow<List<KeySpatialProfileEntity>> = repository.keySpatialProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userWords: StateFlow<List<UserWordFrequencyEntity>> = repository.userWordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userBigrams: StateFlow<List<UserBigramEntity>> = repository.bigramsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gesturePrototypes: StateFlow<List<GesturePrototypeEntity>> = repository.prototypesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionLogs: StateFlow<List<GestureSessionLogEntity>> = repository.recentLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureStarterSeedIfEmpty()
            // Perform an initial synthetic gesture decode for "keyboard" after "futo adaptive"
            // so the Decoder Lab & Candidate Strip have rich live data immediately on launch.
            simulateHumanSwipeForWord("keyboard", commitToEditor = true)
        }
    }

    fun onGestureCompleted(rawPoints: List<RawTouchPoint>) {
        if (rawPoints.isEmpty()) return
        val state = _uiState.value
        val currentWords = extractWords(state.editorText)
        val contextWord = currentWords.lastOrNull() ?: ""

        val profileMap = spatialProfiles.value.associateBy { it.keyChar.first() }
        val wordsMap = userWords.value.associateBy { it.word }
        val bigramsMap = userBigrams.value.associateBy { it.prevWord to it.nextWord }
        val protoMap = gesturePrototypes.value.associateBy { it.word }

        val decodeResult = repository.gestureEngine.decodeGesture(
            rawPoints = rawPoints,
            prevContextWord = contextWord,
            hyperparams = state.hyperparams,
            spatialProfiles = profileMap,
            userWords = wordsMap,
            userBigrams = bigramsMap,
            userPrototypes = protoMap
        )

        val topCandidate = decodeResult.candidates.firstOrNull() ?: return
        val committed = if (state.isShiftActive) {
            topCandidate.word.replaceFirstChar { it.uppercase() }
        } else {
            topCandidate.word
        }

        val newEditorText = appendWordToText(state.editorText, committed)
        val statusMsg = if (decodeResult.wasPromotedByAdaptation) {
            "Style Learning promoted '${topCandidate.word}' (${topCandidate.adaptiveScore.toInt()}%) over static baseline '${decodeResult.staticTopWord}'!"
        } else {
            "Recognized '${topCandidate.word}' (${topCandidate.adaptiveScore.toInt()}% confidence • ${topCandidate.explanationBadge})"
        }

        _uiState.update {
            it.copy(
                editorText = newEditorText,
                prevContextWord = contextWord,
                lastCommittedWord = topCandidate.word,
                isShiftActive = false,
                lastDecodeResult = decodeResult,
                statusBannerText = statusMsg
            )
        }

        viewModelScope.launch {
            repository.learnFromCommittedGesture(
                committedWord = topCandidate.word,
                prevContextWord = contextWord,
                decodeResult = decodeResult,
                hyperparams = state.hyperparams,
                isExplicitUserCorrection = false
            )
        }
    }

    /**
     * Triggered when the user taps one of the candidate chips in the suggestion bar.
     * If `isCorrection` is true (chip index > 0), replaces the last committed word in the editor
     * and applies higher-weight supervised style learning to key drift, bigrams, and prototypes!
     */
    fun onCandidateSelected(candidate: GestureCandidate, isCorrection: Boolean) {
        val state = _uiState.value
        val decodeResult = state.lastDecodeResult ?: return

        val updatedText = if (isCorrection && state.lastCommittedWord.isNotEmpty()) {
            replaceLastWordInText(state.editorText, candidate.word, state.isShiftActive)
        } else {
            appendWordToText(state.editorText, candidate.word)
        }

        val banner = if (isCorrection) {
            "Learned correction -> '${candidate.word}': shifted key offsets, boosted bigram '${state.prevContextWord} -> ${candidate.word}', and updated 16-pt prototype!"
        } else {
            "Confirmed '${candidate.word}' — reinforced personal gesture prototype & key offsets."
        }

        _uiState.update {
            it.copy(
                editorText = updatedText,
                lastCommittedWord = candidate.word,
                isShiftActive = false,
                statusBannerText = banner
            )
        }

        viewModelScope.launch {
            repository.learnFromCommittedGesture(
                committedWord = candidate.word,
                prevContextWord = state.prevContextWord,
                decodeResult = decodeResult,
                hyperparams = state.hyperparams,
                isExplicitUserCorrection = isCorrection
            )
        }
    }

    /**
     * Generates a realistic human thumb swipe trajectory for `targetWord` (with slight ergonomic
     * thumb drift and corner rounding) and runs it through the full gesture recognition pipeline.
     */
    fun simulateHumanSwipeForWord(targetWord: String, commitToEditor: Boolean = true) {
        val clean = targetWord.trim().lowercase().filter { it in 'a'..'z' }
        if (clean.isEmpty()) return

        val profileMap = spatialProfiles.value.associateBy { it.keyChar.first() }
        val baseVertices = mutableListOf<Pair<Float, Float>>()
        for (i in clean.indices) {
            val ch = clean[i]
            val geom = keyGeometries[ch] ?: continue
            val drift = profileMap[ch]
            // Simulate user's natural thumb offset + slight corner rounding on interior letters
            val dx = (drift?.driftX ?: 0.014f) * 0.9f
            val dy = (drift?.driftY ?: 0.012f) * 0.9f
            val x = (geom.canonicalX + dx).coerceIn(0.03f, 0.97f)
            val y = (geom.canonicalY + dy).coerceIn(0.05f, 0.95f)
            baseVertices.add(x to y)
            // If consecutive duplicate letter (e.g. 'll' in hello, 'ee' in feel/coffee), add a micro-dwell loop
            if (i > 0 && clean[i] == clean[i - 1]) {
                baseVertices.add((x + 0.018f) to (y - 0.018f))
                baseVertices.add(x to y)
            }
        }
        if (baseVertices.isEmpty()) return

        val densePairs = repository.gestureEngine.buildWordTemplate32(clean, profileMap, targetCount = 28)
        val startClock = SystemClock.uptimeMillis()
        val rawPoints = densePairs.mapIndexed { idx, (px, py) ->
            val t = idx.toFloat() / (densePairs.size - 1).coerceAtLeast(1)
            val microWave = sin(t * 9.0).toFloat() * 0.004f
            RawTouchPoint(
                x = (px + microWave).coerceIn(0.02f, 0.98f),
                y = (py - microWave * 0.8f).coerceIn(0.04f, 0.96f),
                timestampMs = startClock + (idx * 16L)
            )
        }

        if (commitToEditor) {
            onGestureCompleted(rawPoints)
        } else {
            val state = _uiState.value
            val currentWords = extractWords(state.editorText)
            val contextWord = currentWords.lastOrNull() ?: ""
            val decodeResult = repository.gestureEngine.decodeGesture(
                rawPoints = rawPoints,
                prevContextWord = contextWord,
                hyperparams = state.hyperparams,
                spatialProfiles = profileMap,
                userWords = userWords.value.associateBy { it.word },
                userBigrams = userBigrams.value.associateBy { it.prevWord to it.nextWord },
                userPrototypes = gesturePrototypes.value.associateBy { it.word }
            )
            _uiState.update {
                it.copy(
                    lastDecodeResult = decodeResult,
                    statusBannerText = "Simulated thumb trajectory for '$clean' -> Top match '${decodeResult.adaptiveTopWord}'"
                )
            }
        }
    }

    /**
     * Simulates next word in the active calibration drill so the user can test rapid phrase progression.
     */
    fun swipeNextDrillWord() {
        val state = _uiState.value
        val drill = FutoLexicon.calibrationDrills[state.activeDrillIndex % FutoLexicon.calibrationDrills.size]
        val targetWords = extractWords(drill.targetPhrase)
        val typedWords = extractWords(state.editorText)
        val nextIdx = typedWords.size % targetWords.size
        if (typedWords.size >= targetWords.size) {
            _uiState.update { it.copy(editorText = "") }
        }
        simulateHumanSwipeForWord(targetWords[nextIdx], commitToEditor = true)
    }

    fun selectCalibrationDrill(index: Int) {
        val safeIdx = index.coerceIn(0, FutoLexicon.calibrationDrills.lastIndex)
        val drill = FutoLexicon.calibrationDrills[safeIdx]
        _uiState.update {
            it.copy(
                activeDrillIndex = safeIdx,
                editorText = "",
                prevContextWord = "",
                lastCommittedWord = "",
                statusBannerText = "Loaded Calibration Drill: \"${drill.title}\""
            )
        }
    }

    fun toggleShift() {
        _uiState.update { it.copy(isShiftActive = !it.isShiftActive) }
    }

    fun toggleDriftOverlay() {
        _uiState.update { it.copy(showDriftOverlay = !it.showDriftOverlay) }
    }

    fun onBackspace() {
        _uiState.update { state ->
            val trimmed = state.editorText.trimEnd()
            if (trimmed.isEmpty()) {
                state.copy(editorText = "", lastCommittedWord = "")
            } else {
                // Delete last word if preceded by space, or last char
                val lastSpace = trimmed.lastIndexOf(' ')
                val nextText = if (lastSpace >= 0) {
                    trimmed.substring(0, lastSpace + 1)
                } else {
                    ""
                }
                val remainingWords = extractWords(nextText)
                state.copy(
                    editorText = nextText,
                    lastCommittedWord = remainingWords.lastOrNull() ?: "",
                    prevContextWord = if (remainingWords.size >= 2) remainingWords[remainingWords.size - 2] else ""
                )
            }
        }
    }

    fun onSpace() {
        _uiState.update { state ->
            if (state.editorText.endsWith(" ")) state
            else state.copy(editorText = state.editorText + " ")
        }
    }

    fun onPunctuation(punct: String) {
        _uiState.update { state ->
            val base = state.editorText.trimEnd()
            state.copy(editorText = if (base.isEmpty()) "$punct " else "$base$punct ")
        }
    }

    fun onEnter() {
        _uiState.update { state ->
            state.copy(editorText = state.editorText.trimEnd() + "\n")
        }
    }

    fun clearEditorText() {
        _uiState.update {
            it.copy(
                editorText = "",
                lastCommittedWord = "",
                prevContextWord = "",
                statusBannerText = "Cleared sandbox editor."
            )
        }
    }

    fun updateHyperparameters(transform: (DecoderHyperparameters) -> DecoderHyperparameters) {
        _uiState.update { state ->
            state.copy(hyperparams = transform(state.hyperparams))
        }
    }

    fun applyThumbCalibrationPreset(rightHanded: Boolean) {
        viewModelScope.launch {
            repository.applyErgonomicThumbPreset(rightHanded)
            _uiState.update {
                it.copy(
                    statusBannerText = if (rightHanded) {
                        "Applied Right-Thumb Arc Ergonomic Drift Profile across all 26 keys."
                    } else {
                        "Applied Left-Thumb Arc Ergonomic Drift Profile across all 26 keys."
                    }
                )
            }
        }
    }

    fun addCustomWord(word: String, prevContextWord: String) {
        viewModelScope.launch {
            repository.addCustomVocabularyWord(word, prevContextWord)
            _uiState.update {
                it.copy(statusBannerText = "Added '$word' to personal gesture dictionary.")
            }
        }
    }

    fun removeCustomWord(word: String) {
        viewModelScope.launch {
            repository.removeUserWord(word)
        }
    }

    fun resetLearnedProfile() {
        viewModelScope.launch {
            repository.resetAllLearnedStyleData()
            _uiState.update {
                it.copy(
                    statusBannerText = "Reset all learned spatial offsets, bigrams, and gesture prototypes to zero baseline."
                )
            }
        }
    }

    private fun appendWordToText(current: String, word: String): String {
        val base = if (current.isEmpty() || current.endsWith(" ") || current.endsWith("\n")) {
            current
        } else {
            "$current "
        }
        return "$base$word "
    }

    private fun replaceLastWordInText(current: String, replacement: String, capitalize: Boolean): String {
        val formatted = if (capitalize) replacement.replaceFirstChar { it.uppercase() } else replacement
        val trimmed = current.trimEnd()
        val lastSpaceIdx = trimmed.lastIndexOfAny(charArrayOf(' ', '\n'))
        return if (lastSpaceIdx >= 0) {
            trimmed.substring(0, lastSpaceIdx + 1) + "$formatted "
        } else {
            "$formatted "
        }
    }

    private fun extractWords(text: String): List<String> {
        return text.lowercase()
            .split(Regex("\\s+"))
            .map { it.filter { ch -> ch in 'a'..'z' } }
            .filter { it.isNotEmpty() }
    }

    class Factory(private val repository: AdaptiveStyleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FutoStudioViewModel(repository) as T
        }
    }
}
