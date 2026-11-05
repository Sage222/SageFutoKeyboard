package com.example.engine

/**
 * Built-in core vocabulary and calibration drills modeled after FUTO LatinIME dictionary weights.
 */
object FutoLexicon {

    data class CalibrationDrill(
        val id: String,
        val title: String,
        val focusDescription: String,
        val targetPhrase: String,
        val focusKeys: List<Char>
    )

    val calibrationDrills: List<CalibrationDrill> = listOf(
        CalibrationDrill(
            id = "thumb_arc_core",
            title = "Thumb Arc & Corner Rounding",
            focusDescription = "Calibrates outer-row reach (Q, P, Z, M) and sharp inner-row inflections.",
            targetPhrase = "the quick brown fox jumps over the lazy dog",
            focusKeys = listOf('q', 'p', 'z', 'm', 'x')
        ),
        CalibrationDrill(
            id = "ambiguous_clusters",
            title = "Ambiguous Trajectory Disambiguation",
            focusDescription = "Trains N-gram context and inflection timing on overlapping swipe paths (from/form, their/there).",
            targetPhrase = "their new form came from there right at night",
            focusKeys = listOf('f', 'r', 'o', 'm', 't', 'h', 'e')
        ),
        CalibrationDrill(
            id = "double_letter_dwell",
            title = "Double-Letter Dwell & Loop Recognition",
            focusDescription = "Teaches the decoder your natural dwell speed or micro-loop on repeated letters.",
            targetPhrase = "hello feel free to keep good coffee and speed",
            focusKeys = listOf('l', 'e', 'o', 'f')
        ),
        CalibrationDrill(
            id = "futo_privacy_manifesto",
            title = "FUTO Adaptive Typing Benchmark",
            focusDescription = "Tests multi-syllable technical words and personal writing rhythm.",
            targetPhrase = "futo adaptive keyboard learns your gesture style locally",
            focusKeys = listOf('f', 'u', 't', 'o', 'g', 's', 'y')
        )
    )

    // Core dictionary with base unigram frequency ratings (50..250)
    val defaultWordsWithFreq: Map<String, Int> = mapOf(
        "the" to 250, "be" to 240, "to" to 245, "of" to 240, "and" to 245,
        "a" to 235, "in" to 240, "that" to 235, "have" to 230, "i" to 235,
        "it" to 235, "for" to 230, "not" to 230, "on" to 228, "with" to 228,
        "he" to 220, "as" to 220, "you" to 235, "do" to 225, "at" to 222,
        "this" to 228, "but" to 222, "his" to 210, "by" to 215, "from" to 225,
        "they" to 222, "we" to 225, "say" to 210, "her" to 208, "she" to 210,
        "or" to 218, "an" to 215, "will" to 220, "my" to 224, "one" to 218,
        "all" to 220, "would" to 215, "there" to 220, "their" to 218, "what" to 222,
        "so" to 218, "up" to 215, "out" to 218, "if" to 215, "about" to 215,
        "who" to 210, "get" to 218, "which" to 210, "go" to 218, "me" to 220,
        "when" to 215, "make" to 218, "can" to 224, "like" to 220, "time" to 220,
        "no" to 215, "just" to 220, "him" to 205, "know" to 218, "take" to 215,
        "people" to 212, "into" to 212, "year" to 205, "your" to 225, "good" to 220,
        "some" to 215, "could" to 212, "them" to 212, "see" to 218, "other" to 212,
        "than" to 210, "then" to 215, "now" to 220, "look" to 215, "only" to 214,
        "come" to 215, "its" to 210, "over" to 215, "think" to 216, "also" to 212,
        "back" to 216, "after" to 212, "use" to 216, "two" to 208, "how" to 218,
        "our" to 215, "work" to 220, "first" to 215, "well" to 216, "way" to 216,
        "even" to 212, "new" to 218, "want" to 218, "because" to 212, "any" to 212,
        "these" to 210, "give" to 212, "day" to 216, "most" to 212, "us" to 212,
        // FUTO & Gesture typing domain words
        "futo" to 215, "keyboard" to 220, "gesture" to 220, "swipe" to 218, "typing" to 218,
        "type" to 214, "adaptive" to 212, "adapt" to 205, "learn" to 215, "learns" to 212,
        "learning" to 216, "style" to 218, "locally" to 210, "local" to 208, "privacy" to 215,
        "private" to 210, "model" to 208, "models" to 200, "neural" to 204, "spatial" to 204,
        "drift" to 202, "vector" to 202, "path" to 210, "curve" to 204, "corner" to 205,
        "accuracy" to 212, "accurate" to 208, "predict" to 210, "prediction" to 214,
        "smart" to 210, "fast" to 215, "speed" to 216, "smooth" to 208, "fluid" to 204,
        "thumb" to 206, "finger" to 206, "touch" to 212, "screen" to 212, "android" to 218,
        "mobile" to 208, "phone" to 214, "device" to 210, "system" to 212, "open" to 215,
        "source" to 210, "code" to 212, "build" to 212, "test" to 214, "train" to 210,
        "training" to 208, "profile" to 210, "user" to 214, "users" to 208, "word" to 216,
        "words" to 214, "text" to 216, "write" to 215, "writing" to 216, "written" to 205,
        "letter" to 208, "letters" to 206, "key" to 214, "keys" to 212, "press" to 206,
        // Ambiguous minimal pairs & pangram words
        "quick" to 215, "quite" to 206, "quiet" to 204, "quit" to 198,
        "brown" to 208, "brain" to 204, "bring" to 210, "bright" to 204,
        "fox" to 205, "fix" to 208, "box" to 206,
        "jumps" to 206, "jump" to 208, "just" to 220,
        "lazy" to 205, "late" to 210, "last" to 214, "later" to 212,
        "dog" to 210, "dogs" to 202, "doing" to 212, "done" to 214,
        "form" to 214, "from" to 225, "firm" to 198, "farm" to 198,
        "night" to 215, "might" to 214, "right" to 222, "sight" to 202, "light" to 212, "fight" to 202,
        "hello" to 222, "help" to 218, "held" to 204, "hell" to 190, "hill" to 198,
        "feel" to 218, "fell" to 204, "fill" to 204, "full" to 212, "fall" to 206,
        "free" to 216, "fire" to 206, "five" to 206, "fine" to 210, "find" to 216,
        "keep" to 218, "kept" to 202, "deep" to 206, "sleep" to 210,
        "coffee" to 214, "code" to 212, "core" to 208, "care" to 210, "case" to 210,
        "book" to 212, "boot" to 198, "both" to 212, "best" to 218, "better" to 218,
        "too" to 216, "top" to 212, "tip" to 202, "tap" to 208, "try" to 216,
        "three" to 212, "tree" to 206, "true" to 214, "trust" to 206,
        "where" to 216, "were" to 214, "we" to 225, "water" to 210, "watch" to 210,
        "world" to 216, "would" to 215, "wound" to 190, "ancora" to 180,
        "great" to 218, "green" to 208, "group" to 208, "grow" to 206,
        "small" to 212, "smart" to 212, "smile" to 204, "still" to 216, "state" to 212,
        "start" to 216, "stop" to 212, "store" to 208, "story" to 208, "study" to 206,
        "every" to 214, "ever" to 210, "never" to 216, "next" to 216, "need" to 218,
        "near" to 208, "name" to 214, "nice" to 212, "note" to 210, "nothing" to 210,
        "always" to 214, "almost" to 208, "along" to 206, "already" to 210, "alone" to 204,
        "change" to 212, "check" to 214, "choice" to 206, "clear" to 212, "close" to 212,
        "clean" to 208, "cloud" to 204, "class" to 206, "click" to 206,
        "place" to 214, "plan" to 212, "play" to 214, "please" to 214, "point" to 214,
        "power" to 212, "part" to 212, "past" to 206, "pass" to 206, "page" to 208,
        "paper" to 204, "person" to 210, "personal" to 210, "pattern" to 206,
        "question" to 210, "quality" to 206, "quickly" to 210,
        "real" to 214, "really" to 216, "read" to 214, "ready" to 212, "reason" to 208,
        "result" to 208, "return" to 208, "run" to 212, "running" to 208, "rule" to 204,
        "same" to 214, "save" to 210, "safe" to 208, "send" to 212, "sense" to 206,
        "service" to 208, "set" to 214, "shift" to 208, "show" to 214, "side" to 210,
        "simple" to 212, "since" to 210, "skill" to 208, "space" to 212, "speak" to 206,
        "special" to 208, "custom" to 208, "context" to 208, "control" to 210,
        "today" to 216, "together" to 210, "tomorrow" to 208, "tonight" to 208,
        "thank" to 214, "thanks" to 216, "thing" to 214, "things" to 214, "thought" to 208,
        "through" to 212, "under" to 210, "until" to 210, "update" to 210, "using" to 212,
        "value" to 208, "very" to 216, "view" to 210, "voice" to 210,
        "walk" to 206, "wait" to 210, "week" to 212, "while" to 212, "white" to 206,
        "whole" to 208, "without" to 212, "wonder" to 204, "working" to 212,
        "yes" to 216, "yet" to 210, "young" to 204, "zero" to 204, "zone" to 204
    )

    // Built-in starter bigrams for immediate context sensitivity before user builds more
    val starterBigrams: Map<Pair<String, String>, Int> = mapOf(
        ("the" to "quick") to 4,
        ("quick" to "brown") to 4,
        ("brown" to "fox") to 4,
        ("fox" to "jumps") to 4,
        ("jumps" to "over") to 4,
        ("over" to "the") to 4,
        ("the" to "lazy") to 4,
        ("lazy" to "dog") to 4,
        ("their" to "new") to 3,
        ("new" to "form") to 3,
        ("came" to "from") to 4,
        ("from" to "there") to 4,
        ("at" to "night") to 4,
        ("feel" to "free") to 4,
        ("free" to "to") to 4,
        ("to" to "keep") to 3,
        ("good" to "coffee") to 3,
        ("futo" to "adaptive") to 4,
        ("adaptive" to "keyboard") to 4,
        ("keyboard" to "learns") to 4,
        ("learns" to "your") to 4,
        ("your" to "gesture") to 4,
        ("gesture" to "style") to 4,
        ("style" to "locally") to 4
    )
}
