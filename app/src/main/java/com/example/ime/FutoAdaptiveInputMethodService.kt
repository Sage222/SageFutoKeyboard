package com.example.ime

import android.inputmethodservice.InputMethodService
import android.view.View
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
import com.example.data.AdaptiveStyleDatabase
import com.example.data.AdaptiveStyleRepository
import com.example.engine.DecoderHyperparameters
import com.example.engine.GestureDecodeResult
import com.example.ui.components.FutoGestureKeyboardView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * System-wide Android InputMethodService (IME) powered by the FUTO Adaptive Gesture Engine
 * and shared Room style database.
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

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val db = AdaptiveStyleDatabase.getInstance(applicationContext)
        repository = AdaptiveStyleRepository(db.adaptiveStyleDao())
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
                    var lastCommittedWord by remember { mutableStateOf("") }
                    var prevContextWord by remember { mutableStateOf("") }
                    var showDriftOverlay by remember { mutableStateOf(true) }
                    var isShiftActive by remember { mutableStateOf(false) }
                    val hyperparams = remember { DecoderHyperparameters() }

                    FutoGestureKeyboardView(
                        keyGeometries = repository.gestureEngine.qwertyKeys,
                        spatialProfiles = spatialMap,
                        lastDecodeResult = lastDecodeResult,
                        showDriftOverlay = showDriftOverlay,
                        isShiftActive = isShiftActive,
                        onToggleShift = { isShiftActive = !isShiftActive },
                        onToggleDriftOverlay = { showDriftOverlay = !showDriftOverlay },
                        onGestureCompleted = { rawPoints ->
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
                            val formatted = if (isShiftActive) {
                                topWord.replaceFirstChar { it.uppercase() }
                            } else {
                                topWord
                            }
                            currentInputConnection?.commitText("$formatted ", 1)
                            val contextForLearn = prevContextWord
                            prevContextWord = lastCommittedWord
                            lastCommittedWord = topWord
                            isShiftActive = false
                            scope.launch {
                                repository.learnFromCommittedGesture(
                                    committedWord = topWord,
                                    prevContextWord = contextForLearn,
                                    decodeResult = result,
                                    hyperparams = hyperparams,
                                    isExplicitUserCorrection = false
                                )
                            }
                        },
                        onCandidateSelected = { candidate, isCorrection ->
                            val ic = currentInputConnection ?: return@FutoGestureKeyboardView
                            if (isCorrection && lastCommittedWord.isNotEmpty()) {
                                ic.deleteSurroundingText(lastCommittedWord.length + 1, 0)
                            }
                            val formatted = if (isShiftActive) {
                                candidate.word.replaceFirstChar { it.uppercase() }
                            } else {
                                candidate.word
                            }
                            ic.commitText("$formatted ", 1)
                            lastCommittedWord = candidate.word
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
                        },
                        onBackspace = {
                            currentInputConnection?.deleteSurroundingText(1, 0)
                        },
                        onSpace = {
                            currentInputConnection?.commitText(" ", 1)
                        },
                        onPunctuation = { punct ->
                            currentInputConnection?.commitText("$punct ", 1)
                        },
                        onEnter = {
                            currentInputConnection?.commitText("\n", 1)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
