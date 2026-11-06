package com.example.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.AdaptiveStyleDatabase
import com.example.data.AdaptiveStyleRepository
import com.example.engine.DecoderHyperparameters
import com.example.engine.FutoLexicon
import com.example.engine.GestureDecodeResult
import com.example.ui.components.FutoGestureKeyboardView
import com.example.ui.components.ImeActionType
import com.example.ui.components.ShiftState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Full-featured Android System InputMethodService (IME) based on FUTO Keyboard architecture.
 * Supports:
 * - True tap-typing with InputConnection composing text, prefix completions, and typo auto-correction
 * - Continuous gesture swipe typing with live floating preview and single-backspace gesture undo
 * - Continuous on-device style learning (spatial key drift, 16-pt prototypes, and bigrams)
 *   including automatic learning when a user backspaces a swiped word and types/swipes the intended word.
 */
class FutoAdaptiveInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var repository: AdaptiveStyleRepository

    private var currentImeAction by mutableStateOf(ImeActionType.RETURN)
    private var composingBuffer by mutableStateOf("")
    private var prevContextWord by mutableStateOf("")
    private var lastCommittedWord by mutableStateOf("")
    private var justCommittedGestureWord by mutableStateOf(false)
    private var rejectedGestureDecode by mutableStateOf<GestureDecodeResult?>(null)
    private var shiftState by mutableStateOf(ShiftState.ONE_SHOT)
    private var lastShiftTapMs = 0L
    private var lastSpaceTapMs = 0L

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val db = AdaptiveStyleDatabase.getInstance(applicationContext)
        repository = AdaptiveStyleRepository(db.adaptiveStyleDao())
    }

    override fun onStartInputView( editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        composingBuffer = ""
        justCommittedGestureWord = false

        val actionBits = (editorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        currentImeAction = when (actionBits) {
            EditorInfo.IME_ACTION_SEARCH -> ImeActionType.SEARCH
            EditorInfo.IME_ACTION_SEND -> ImeActionType.SEND
            EditorInfo.IME_ACTION_DONE -> ImeActionType.DONE
            EditorInfo.IME_ACTION_GO -> ImeActionType.GO
            EditorInfo.IME_ACTION_NEXT -> ImeActionType.NEXT
            else -> ImeActionType.RETURN
        }

        updateAutoShiftFromInputConnection(editorInfo)
        syncPrevContextFromInputConnection()
    }

    private fun updateAutoShiftFromInputConnection(info: EditorInfo? = currentInputEditorInfo) {
        if (shiftState == ShiftState.CAPS_LOCK) return
        val ic = currentInputConnection ?: return
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
            val capsMode = ic.getCursorCapsMode(inputType)
            shiftState = if (capsMode != 0) ShiftState.ONE_SHOT else ShiftState.OFF
        }
    }

    private fun syncPrevContextFromInputConnection() {
        val before = currentInputConnection?.getTextBeforeCursor(48, 0)?.toString().orEmpty()
        val words = before.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        prevContextWord = words.lastOrNull()?.lowercase()?.filter { it in 'a'..'z' }.orEmpty()
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val decorView = window?.window?.decorView
        decorView?.setViewTreeLifecycleOwner(this)
        decorView?.setViewTreeViewModelStoreOwner(this)
        decorView?.setViewTreeSavedStateRegistryOwner(this)

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FutoAdaptiveInputMethodService)
            setViewTreeViewModelStoreOwner(this@FutoAdaptiveInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@FutoAdaptiveInputMethodService)

            setContent {
                MyApplicationTheme {
                    val scope = rememberCoroutineScope()
                    val profilesList by repository.keySpatialProfilesFlow.collectAsState(initial = emptyList())
                    val wordsList by repository.userWordsFlow.collectAsState(initial = emptyList())
                    val bigramsList by repository.bigramsFlow.collectAsState(initial = emptyList())
                    val prototypesList by repository.prototypesFlow.collectAsState(initial = emptyList())

                    val spatialMap = remember(profilesList) {
                        profilesList.associateBy { it.keyChar.first() }
                    }
                    val wordsMap = remember(wordsList) {
                        wordsList.associateBy { it.word }
                    }
                    val bigramsMap = remember(bigramsList) {
                        bigramsList.associateBy { it.prevWord to it.nextWord }
                    }
                    val prototypesMap = remember(prototypesList) {
                        prototypesList.associateBy { it.word }
                    }

                    var lastDecodeResult by remember { mutableStateOf<GestureDecodeResult?>(null) }
                    var showDriftOverlay by remember { mutableStateOf(false) }
                    val hyperparams = remember { DecoderHyperparameters() }

                    val activeCandidates = remember(
                        composingBuffer,
                        prevContextWord,
                        lastDecodeResult,
                        justCommittedGestureWord,
                        wordsMap,
                        bigramsMap
                    ) {
                        if (justCommittedGestureWord && lastDecodeResult != null) {
                            lastDecodeResult!!.candidates
                        } else {
                            FutoLexicon.getPrefixAndTypoCandidates(
                                composingPrefix = composingBuffer,
                                prevContextWord = prevContextWord,
                                userWords = wordsMap,
                                userBigrams = bigramsMap,
                                keyGeometries = repository.gestureEngine.qwertyKeys
                            )
                        }
                    }

                    FutoGestureKeyboardView(
                        keyGeometries = repository.gestureEngine.qwertyKeys,
                        spatialProfiles = spatialMap,
                        candidates = activeCandidates,
                        composingWord = composingBuffer,
                        lastDecodeResult = if (justCommittedGestureWord) lastDecodeResult else null,
                        showDriftOverlay = showDriftOverlay,
                        showNumberRow = true,
                        shiftState = shiftState,
                        imeActionType = currentImeAction,
                        onToggleShift = {
                            val now = SystemClock.uptimeMillis()
                            shiftState = when {
                                shiftState == ShiftState.ONE_SHOT && (now - lastShiftTapMs) < 380L -> ShiftState.CAPS_LOCK
                                shiftState == ShiftState.OFF -> ShiftState.ONE_SHOT
                                else -> ShiftState.OFF
                            }
                            lastShiftTapMs = now
                        },
                        onToggleDriftOverlay = { showDriftOverlay = !showDriftOverlay },
                        onOpenSettings = {
                            val intent = Intent(this@FutoAdaptiveInputMethodService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        },
                        onKeyTap = { ch ->
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            justCommittedGestureWord = false

                            if (ch.isLetter()) {
                                composingBuffer += ch
                                ic.setComposingText(composingBuffer, 1)
                                if (shiftState == ShiftState.ONE_SHOT) {
                                    shiftState = ShiftState.OFF
                                }
                            } else {
                                // Digit or symbol: commit any composing word first, then commit character
                                if (composingBuffer.isNotEmpty()) {
                                    commitComposingWordAndLearn(
                                        wordToCommit = composingBuffer,
                                        appendSpace = false,
                                        scope = scope
                                    )
                                }
                                ic.commitText(ch.toString(), 1)
                                updateAutoShiftFromInputConnection()
                            }
                        },
                        onGesturePreview = { partialPoints ->
                            repository.gestureEngine.decodeGesture(
                                rawPoints = partialPoints,
                                prevContextWord = prevContextWord,
                                hyperparams = hyperparams,
                                spatialProfiles = spatialMap,
                                userWords = wordsMap,
                                userBigrams = bigramsMap,
                                userPrototypes = prototypesMap
                            ).adaptiveTopWord.ifEmpty { null }
                        },
                        onGestureCompleted = { rawPoints ->
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (composingBuffer.isNotEmpty()) {
                                commitComposingWordAndLearn(
                                    wordToCommit = composingBuffer,
                                    appendSpace = true,
                                    scope = scope
                                )
                            }

                            val result = repository.gestureEngine.decodeGesture(
                                rawPoints = rawPoints,
                                prevContextWord = prevContextWord,
                                hyperparams = hyperparams,
                                spatialProfiles = spatialMap,
                                userWords = wordsMap,
                                userBigrams = bigramsMap,
                                userPrototypes = prototypesMap
                            )
                            lastDecodeResult = result
                            val topWord = result.candidates.firstOrNull()?.word ?: return@FutoGestureKeyboardView
                            val formatted = applyShiftFormatting(topWord, shiftState)

                            // Smart leading space if needed
                            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                            val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace() && before.last() != '(' && before.last() != '"'
                            val prefixSpace = if (needsLeadingSpace) " " else ""

                            ic.commitText("$prefixSpace$formatted ", 1)
                            val contextForLearn = prevContextWord
                            prevContextWord = topWord.lowercase()
                            lastCommittedWord = formatted
                            justCommittedGestureWord = true

                            if (shiftState == ShiftState.ONE_SHOT) {
                                shiftState = ShiftState.OFF
                            }

                            val pendingRejected = rejectedGestureDecode
                            rejectedGestureDecode = null

                            scope.launch {
                                repository.learnFromCommittedGesture(
                                    committedWord = topWord,
                                    prevContextWord = contextForLearn,
                                    decodeResult = pendingRejected ?: result,
                                    hyperparams = hyperparams,
                                    isExplicitUserCorrection = pendingRejected != null
                                )
                            }
                        },
                        onCandidateSelected = { candidate, isCorrection ->
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (composingBuffer.isNotEmpty()) {
                                val formatted = applyShiftFormatting(
                                    candidate.word,
                                    if (composingBuffer.first().isUpperCase()) ShiftState.ONE_SHOT else ShiftState.OFF
                                )
                                ic.commitText("$formatted ", 1)
                                val contextForLearn = prevContextWord
                                prevContextWord = candidate.word.lowercase()
                                lastCommittedWord = formatted
                                composingBuffer = ""
                                justCommittedGestureWord = false

                                scope.launch {
                                    repository.addCustomVocabularyWord(candidate.word, contextForLearn)
                                }
                            } else if (justCommittedGestureWord && lastCommittedWord.isNotEmpty()) {
                                if (isCorrection) {
                                    ic.deleteSurroundingText(lastCommittedWord.length + 1, 0)
                                    val formatted = applyShiftFormatting(
                                        candidate.word,
                                        if (lastCommittedWord.first().isUpperCase()) ShiftState.ONE_SHOT else ShiftState.OFF
                                    )
                                    ic.commitText("$formatted ", 1)
                                    lastCommittedWord = formatted
                                    prevContextWord = candidate.word.lowercase()
                                }
                                lastDecodeResult?.let { decode ->
                                    scope.launch {
                                        repository.learnFromCommittedGesture(
                                            committedWord = candidate.word,
                                            prevContextWord = prevContextWord,
                                            decodeResult = decode,
                                            hyperparams = hyperparams,
                                            isExplicitUserCorrection = isCorrection
                                        )
                                    }
                                }
                            } else {
                                // Next-word suggestion tap when buffer is empty
                                val formatted = applyShiftFormatting(candidate.word, shiftState)
                                ic.commitText("$formatted ", 1)
                                prevContextWord = candidate.word.lowercase()
                                lastCommittedWord = formatted
                                if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
                            }
                        },
                        onBackspace = {
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (composingBuffer.isNotEmpty()) {
                                composingBuffer = composingBuffer.dropLast(1)
                                if (composingBuffer.isNotEmpty()) {
                                    ic.setComposingText(composingBuffer, 1)
                                } else {
                                    ic.commitText("", 1)
                                    updateAutoShiftFromInputConnection()
                                }
                            } else if (justCommittedGestureWord && lastCommittedWord.isNotEmpty()) {
                                // FUTO / LatinIME behavior: single backspace right after a swipe
                                // deletes the entire swiped word and arms correction learning!
                                ic.deleteSurroundingText(lastCommittedWord.length + 1, 0)
                                rejectedGestureDecode = lastDecodeResult
                                lastCommittedWord = ""
                                justCommittedGestureWord = false
                                syncPrevContextFromInputConnection()
                                updateAutoShiftFromInputConnection()
                            } else {
                                ic.deleteSurroundingText(1, 0)
                                syncPrevContextFromInputConnection()
                                updateAutoShiftFromInputConnection()
                            }
                        },
                        onSpace = {
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            val now = SystemClock.uptimeMillis()
                            if (composingBuffer.isNotEmpty()) {
                                commitComposingWordAndLearn(
                                    wordToCommit = composingBuffer,
                                    appendSpace = true,
                                    scope = scope
                                )
                            } else if ((now - lastSpaceTapMs) < 350L) {
                                // Double-space period shortcut
                                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                                if (before.endsWith(" ") && before.length >= 2 && before[0].isLetterOrDigit()) {
                                    ic.deleteSurroundingText(1, 0)
                                    ic.commitText(". ", 1)
                                    shiftState = ShiftState.ONE_SHOT
                                } else {
                                    ic.commitText(" ", 1)
                                }
                            } else {
                                ic.commitText(" ", 1)
                            }
                            justCommittedGestureWord = false
                            lastSpaceTapMs = now
                        },
                        onPunctuation = { punct ->
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (composingBuffer.isNotEmpty()) {
                                commitComposingWordAndLearn(
                                    wordToCommit = composingBuffer,
                                    appendSpace = false,
                                    scope = scope
                                )
                            } else if (justCommittedGestureWord) {
                                // Strip trailing auto-space before punctuation
                                val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                                if (before == " ") {
                                    ic.deleteSurroundingText(1, 0)
                                }
                            }
                            ic.commitText("$punct ", 1)
                            justCommittedGestureWord = false
                            if (punct == "." || punct == "!" || punct == "?") {
                                shiftState = ShiftState.ONE_SHOT
                            }
                        },
                        onEnterAction = {
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (composingBuffer.isNotEmpty()) {
                                commitComposingWordAndLearn(
                                    wordToCommit = composingBuffer,
                                    appendSpace = false,
                                    scope = scope
                                )
                            }
                            justCommittedGestureWord = false
                            when (currentImeAction) {
                                ImeActionType.SEARCH -> ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                                ImeActionType.SEND -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                                ImeActionType.DONE -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                                ImeActionType.GO -> ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                                ImeActionType.NEXT -> ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
                                ImeActionType.RETURN -> {
                                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                                    shiftState = ShiftState.ONE_SHOT
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun commitComposingWordAndLearn(
        wordToCommit: String,
        appendSpace: Boolean,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        val ic = currentInputConnection ?: return
        val suffix = if (appendSpace) " " else ""
        ic.commitText("$wordToCommit$suffix", 1)
        val clean = wordToCommit.lowercase().filter { it in 'a'..'z' }
        val contextForLearn = prevContextWord
        prevContextWord = clean
        lastCommittedWord = wordToCommit
        composingBuffer = ""

        val pendingRejected = rejectedGestureDecode
        rejectedGestureDecode = null

        scope.launch {
            if (pendingRejected != null && clean.isNotEmpty()) {
                // User rejected a gesture via Backspace and typed the intended word manually!
                repository.learnFromCommittedGesture(
                    committedWord = clean,
                    prevContextWord = contextForLearn,
                    decodeResult = pendingRejected,
                    hyperparams = DecoderHyperparameters(),
                    isExplicitUserCorrection = true
                )
            } else if (clean.isNotEmpty()) {
                repository.addCustomVocabularyWord(clean, contextForLearn)
            }
        }
    }

    private fun applyShiftFormatting(word: String, state: ShiftState): String {
        return when (state) {
            ShiftState.CAPS_LOCK -> word.uppercase()
            ShiftState.ONE_SHOT -> word.replaceFirstChar { it.uppercase() }
            ShiftState.OFF -> word
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
