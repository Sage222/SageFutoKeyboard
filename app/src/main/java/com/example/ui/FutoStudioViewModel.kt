package com.example.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AdaptiveStyleRepository
import com.example.data.GesturePrototypeEntity
import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import com.example.engine.DecoderHyperparameters
import com.example.engine.GestureCandidate
import com.example.engine.GestureDecodeResult
import com.example.engine.KeyGeometry
import com.example.engine.RawTouchPoint
import com.example.ui.components.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val editorText: String = "",
    val composingWord: String = "",
    val lastCommittedWord: String = "",
    val prevContextWord: String = "",
    val justCommittedGestureWord: Boolean = false,
    val shiftState: ShiftState = ShiftState.ONE_SHOT,
    val showNumberRow: Boolean = false,
    val hyperparams: DecoderHyperparameters = DecoderHyperparameters(),
    val lastDecodeResult: GestureDecodeResult? = null,
    val rejectedGestureDecode: GestureDecodeResult? = null
)

class FutoStudioViewModel(
    private val repository: AdaptiveStyleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val keyGeometries: Map<Char, KeyGeometry> = repository.gestureEngine.qwertyKeys

    private var lastShiftTapMs = 0L
    private var lastSpaceTapMs = 0L

    val spatialProfiles: StateFlow<List<KeySpatialProfileEntity>> = repository.keySpatialProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userWords: StateFlow<List<UserWordFrequencyEntity>> = repository.userWordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userBigrams: StateFlow<List<UserBigramEntity>> = repository.bigramsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gesturePrototypes: StateFlow<List<GesturePrototypeEntity>> = repository.prototypesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onExternalEditorTextChanged(newText: String) {
        val words = extractWords(newText)
        _uiState.update {
            it.copy(
                editorText = newText,
                composingWord = "",
                justCommittedGestureWord = false,
                lastCommittedWord = words.lastOrNull().orEmpty(),
                prevContextWord = if (words.size >= 2) words[words.size - 2] else ""
            )
        }
    }

    fun onKeyTap(ch: Char) {
        val state = _uiState.value
        if (ch.isLetter()) {
            val newComposing = state.composingWord + ch
            val nextShift = if (state.shiftState == ShiftState.ONE_SHOT) ShiftState.OFF else state.shiftState
            _uiState.update {
                it.copy(
                    composingWord = newComposing,
                    justCommittedGestureWord = false,
                    shiftState = nextShift
                )
            }
        } else {
            val flushed = flushComposingWordIfNeeded(state, appendSpace = false)
            _uiState.update {
                flushed.copy(
                    editorText = flushed.editorText + ch,
                    justCommittedGestureWord = false
                )
            }
        }
    }

    fun previewGestureWord(partialPoints: List<RawTouchPoint>): String? {
        if (partialPoints.size < 3) return null
        val state = _uiState.value
        return repository.gestureEngine.decodeGesture(
            rawPoints = partialPoints,
            prevContextWord = state.prevContextWord,
            hyperparams = state.hyperparams,
            spatialProfiles = spatialProfiles.value.associateBy { it.keyChar.first() },
            userWords = userWords.value.associateBy { it.word },
            userBigrams = userBigrams.value.associateBy { it.prevWord to it.nextWord },
            userPrototypes = gesturePrototypes.value.associateBy { it.word }
        ).adaptiveTopWord.ifEmpty { null }
    }

    fun onGestureCompleted(rawPoints: List<RawTouchPoint>) {
        if (rawPoints.isEmpty()) return
        val preFlushedState = flushComposingWordIfNeeded(_uiState.value, appendSpace = true)
        val currentWords = extractWords(preFlushedState.editorText)
        val contextWord = currentWords.lastOrNull() ?: ""

        val profileMap = spatialProfiles.value.associateBy { it.keyChar.first() }
        val wordsMap = userWords.value.associateBy { it.word }
        val bigramsMap = userBigrams.value.associateBy { it.prevWord to it.nextWord }
        val protoMap = gesturePrototypes.value.associateBy { it.word }

        val decodeResult = repository.gestureEngine.decodeGesture(
            rawPoints = rawPoints,
            prevContextWord = contextWord,
            hyperparams = preFlushedState.hyperparams,
            spatialProfiles = profileMap,
            userWords = wordsMap,
            userBigrams = bigramsMap,
            userPrototypes = protoMap
        )

        val topCandidate = decodeResult.candidates.firstOrNull() ?: return
        val committed = applyShift(topCandidate.word, preFlushedState.shiftState)
        val newEditorText = appendWordToText(preFlushedState.editorText, committed)
        val nextShift = if (preFlushedState.shiftState == ShiftState.ONE_SHOT) ShiftState.OFF else preFlushedState.shiftState
        val rejected = preFlushedState.rejectedGestureDecode

        _uiState.update {
            preFlushedState.copy(
                editorText = newEditorText,
                composingWord = "",
                prevContextWord = topCandidate.word.lowercase(),
                lastCommittedWord = committed,
                justCommittedGestureWord = true,
                shiftState = nextShift,
                lastDecodeResult = decodeResult,
                rejectedGestureDecode = null
            )
        }

        viewModelScope.launch {
            repository.learnFromCommittedGesture(
                committedWord = topCandidate.word,
                prevContextWord = contextWord,
                decodeResult = rejected ?: decodeResult,
                hyperparams = preFlushedState.hyperparams,
                isExplicitUserCorrection = rejected != null
            )
        }
    }

    fun onCandidateSelected(candidate: GestureCandidate, isCorrection: Boolean) {
        val state = _uiState.value
        if (state.composingWord.isNotEmpty()) {
            val formatted = if (state.composingWord.first().isUpperCase()) {
                candidate.word.replaceFirstChar { it.uppercase() }
            } else {
                candidate.word
            }
            val newText = appendWordToText(state.editorText, formatted)
            val contextForLearn = state.prevContextWord
            val pendingRejected = state.rejectedGestureDecode

            _uiState.update {
                it.copy(
                    editorText = newText,
                    composingWord = "",
                    prevContextWord = candidate.word.lowercase(),
                    lastCommittedWord = formatted,
                    justCommittedGestureWord = false,
                    rejectedGestureDecode = null
                )
            }

            viewModelScope.launch {
                if (pendingRejected != null) {
                    repository.learnFromCommittedGesture(
                        committedWord = candidate.word,
                        prevContextWord = contextForLearn,
                        decodeResult = pendingRejected,
                        hyperparams = state.hyperparams,
                        isExplicitUserCorrection = true
                    )
                } else {
                    repository.recordUsedWord(candidate.word, contextForLearn)
                }
            }
        } else if (state.justCommittedGestureWord && state.lastDecodeResult != null) {
            val capitalize = state.lastCommittedWord.firstOrNull()?.isUpperCase() == true
            val updatedText = if (isCorrection && state.lastCommittedWord.isNotEmpty()) {
                replaceLastWordInText(state.editorText, candidate.word, capitalize)
            } else {
                state.editorText
            }
            val formatted = if (capitalize) candidate.word.replaceFirstChar { it.uppercase() } else candidate.word

            _uiState.update {
                it.copy(
                    editorText = updatedText,
                    lastCommittedWord = formatted,
                    prevContextWord = candidate.word.lowercase()
                )
            }

            viewModelScope.launch {
                repository.learnFromCommittedGesture(
                    committedWord = candidate.word,
                    prevContextWord = state.prevContextWord,
                    decodeResult = state.lastDecodeResult,
                    hyperparams = state.hyperparams,
                    isExplicitUserCorrection = isCorrection
                )
            }
        } else {
            val formatted = applyShift(candidate.word, state.shiftState)
            val newText = appendWordToText(state.editorText, formatted)
            val contextForLearn = state.prevContextWord
            val nextShift = if (state.shiftState == ShiftState.ONE_SHOT) ShiftState.OFF else state.shiftState
            _uiState.update {
                it.copy(
                    editorText = newText,
                    lastCommittedWord = formatted,
                    prevContextWord = candidate.word.lowercase(),
                    shiftState = nextShift
                )
            }
            viewModelScope.launch {
                repository.recordUsedWord(candidate.word, contextForLearn)
            }
        }
    }

    fun onBackspace() {
        val state = _uiState.value
        if (state.composingWord.isNotEmpty()) {
            val dropped = state.composingWord.dropLast(1)
            _uiState.update { it.copy(composingWord = dropped) }
            return
        }

        if (state.justCommittedGestureWord && state.lastCommittedWord.isNotEmpty()) {
            val trimmed = state.editorText.trimEnd()
            val lastSpace = trimmed.lastIndexOfAny(charArrayOf(' ', '\n'))
            val nextText = if (lastSpace >= 0) trimmed.substring(0, lastSpace + 1) else ""
            val remainingWords = extractWords(nextText)
            _uiState.update {
                it.copy(
                    editorText = nextText,
                    lastCommittedWord = remainingWords.lastOrNull().orEmpty(),
                    prevContextWord = remainingWords.lastOrNull().orEmpty(),
                    justCommittedGestureWord = false,
                    rejectedGestureDecode = state.lastDecodeResult,
                    shiftState = if (nextText.isEmpty()) ShiftState.ONE_SHOT else state.shiftState
                )
            }
            return
        }

        if (state.editorText.isNotEmpty()) {
            val nextText = state.editorText.dropLast(1)
            val remainingWords = extractWords(nextText)
            _uiState.update {
                it.copy(
                    editorText = nextText,
                    lastCommittedWord = remainingWords.lastOrNull().orEmpty(),
                    prevContextWord = remainingWords.lastOrNull().orEmpty(),
                    shiftState = if (nextText.isEmpty()) ShiftState.ONE_SHOT else state.shiftState
                )
            }
        }
    }

    fun onSpace() {
        val now = SystemClock.uptimeMillis()
        val state = _uiState.value
        if (state.composingWord.isNotEmpty()) {
            val flushed = flushComposingWordIfNeeded(state, appendSpace = true)
            _uiState.update { flushed.copy(justCommittedGestureWord = false) }
        } else if ((now - lastSpaceTapMs) < 350L && state.editorText.endsWith(" ") && state.editorText.length >= 2) {
            val withoutSpace = state.editorText.dropLast(1) + ". "
            _uiState.update {
                it.copy(
                    editorText = withoutSpace,
                    justCommittedGestureWord = false,
                    shiftState = ShiftState.ONE_SHOT
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    editorText = it.editorText + " ",
                    justCommittedGestureWord = false
                )
            }
        }
        lastSpaceTapMs = now
    }

    fun onPunctuation(punct: String) {
        val state = flushComposingWordIfNeeded(_uiState.value, appendSpace = false)
        val base = state.editorText.trimEnd()
        val nextShift = if (punct == "." || punct == "!" || punct == "?") ShiftState.ONE_SHOT else state.shiftState
        _uiState.update {
            state.copy(
                editorText = if (base.isEmpty()) "$punct " else "$base$punct ",
                justCommittedGestureWord = false,
                shiftState = nextShift
            )
        }
    }

    fun onEnter() {
        val state = flushComposingWordIfNeeded(_uiState.value, appendSpace = false)
        _uiState.update {
            state.copy(
                editorText = state.editorText + "\n",
                justCommittedGestureWord = false,
                shiftState = ShiftState.ONE_SHOT
            )
        }
    }

    fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        _uiState.update { state ->
            val next = when {
                state.shiftState == ShiftState.ONE_SHOT && (now - lastShiftTapMs) < 380L -> ShiftState.CAPS_LOCK
                state.shiftState == ShiftState.OFF -> ShiftState.ONE_SHOT
                else -> ShiftState.OFF
            }
            state.copy(shiftState = next)
        }
        lastShiftTapMs = now
    }

    fun toggleNumberRow(enabled: Boolean) {
        _uiState.update { it.copy(showNumberRow = enabled) }
    }

    fun clearEditorText() {
        _uiState.update {
            it.copy(
                editorText = "",
                composingWord = "",
                lastCommittedWord = "",
                prevContextWord = "",
                justCommittedGestureWord = false,
                shiftState = ShiftState.ONE_SHOT
            )
        }
    }

    fun addCustomWord(word: String, prevContextWord: String = "") {
        viewModelScope.launch {
            repository.addCustomVocabularyWord(word, prevContextWord)
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
        }
    }

    private fun flushComposingWordIfNeeded(state: StudioUiState, appendSpace: Boolean): StudioUiState {
        if (state.composingWord.isEmpty()) return state
        val word = state.composingWord
        val clean = word.lowercase().filter { it in 'a'..'z' }
        val contextForLearn = state.prevContextWord
        val pendingRejected = state.rejectedGestureDecode

        viewModelScope.launch {
            if (pendingRejected != null && clean.isNotEmpty()) {
                repository.learnFromCommittedGesture(
                    committedWord = clean,
                    prevContextWord = contextForLearn,
                    decodeResult = pendingRejected,
                    hyperparams = state.hyperparams,
                    isExplicitUserCorrection = true
                )
            } else if (clean.isNotEmpty()) {
                repository.recordUsedWord(clean, contextForLearn)
            }
        }

        val nextEditor = if (appendSpace) {
            appendWordToText(state.editorText, word)
        } else {
            val base = if (state.editorText.isEmpty() || state.editorText.endsWith(" ") || state.editorText.endsWith("\n")) {
                state.editorText
            } else {
                "${state.editorText} "
            }
            base + word
        }

        return state.copy(
            editorText = nextEditor,
            composingWord = "",
            lastCommittedWord = word,
            prevContextWord = clean,
            rejectedGestureDecode = null
        )
    }

    private fun applyShift(word: String, shift: ShiftState): String {
        return when (shift) {
            ShiftState.CAPS_LOCK -> word.uppercase()
            ShiftState.ONE_SHOT -> word.replaceFirstChar { it.uppercase() }
            ShiftState.OFF -> word
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
