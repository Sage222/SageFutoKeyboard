package com.example.engine

/**
 * Comprehensive built-in English lexicon and starter bigrams for the FUTO Adaptive Keyboard IME.
 * Words are paired with unigram log-frequency weights (50..255) modeled on LatinIME dictionaries.
 */
object FutoLexicon {

    val defaultWordsWithFreq: Map<String, Int> by lazy {
        val map = HashMap<String, Int>(2400)

        // Tier 1: Ultra-high frequency core words (230..255)
        val tier1 = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
            "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
            "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
            "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
            "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
            "is", "are", "was", "were", "been", "has", "had", "did", "does", "am"
        )
        tier1.forEachIndexed { idx, w -> map[w] = (255 - idx / 4).coerceAtLeast(230) }

        // Tier 2: High-frequency everyday conversation & messaging words (200..229)
        val tier2 = listOf(
            "find", "here", "thing", "many", "well", "only", "those", "tell", "very", "even",
            "back", "any", "good", "woman", "through", "life", "child", "there", "work", "down",
            "may", "after", "should", "call", "world", "over", "school", "still", "try", "in",
            "last", "ask", "need", "too", "feel", "three", "when", "state", "never", "become",
            "between", "high", "really", "something", "most", "another", "much", "family", "own", "out",
            "leave", "put", "old", "while", "mean", "on", "keep", "student", "why", "let",
            "great", "same", "big", "group", "begin", "seem", "country", "help", "talk", "where",
            "turn", "problem", "every", "start", "hand", "might", "american", "show", "part", "about",
            "against", "place", "over", "such", "again", "few", "case", "most", "week", "company",
            "where", "system", "each", "right", "program", "hear", "so", "question", "during", "work",
            "play", "government", "run", "small", "number", "off", "always", "move", "like", "night",
            "live", "mr", "point", "believe", "hold", "today", "bring", "happen", "next", "without",
            "before", "large", "all", "million", "must", "home", "under", "water", "room", "write",
            "mother", "area", "national", "money", "story", "young", "fact", "month", "different", "lot",
            "study", "book", "eye", "job", "word", "though", "business", "issue", "side", "kind",
            "four", "head", "far", "black", "long", "both", "little", "house", "yes", "after",
            "since", "long", "provide", "service", "around", "friend", "important", "father", "sit", "away",
            "until", "power", "hour", "game", "often", "yet", "line", "political", "end", "among",
            "ever", "stand", "bad", "lose", "however", "member", "pay", "law", "meet", "car",
            "city", "almost", "include", "continue", "set", "later", "community", "much", "name", "five",
            "once", "white", "least", "president", "learn", "real", "change", "team", "minute", "best",
            "several", "idea", "kid", "body", "information", "nothing", "ago", "right", "lead", "social",
            "understand", "whether", "watch", "together", "follow", "around", "parent", "only", "stop", "face",
            "anything", "create", "public", "already", "speak", "others", "read", "level", "allow", "add",
            "office", "spend", "door", "health", "person", "art", "sure", "such", "war", "history",
            "party", "within", "grow", "result", "open", "change", "morning", "walk", "reason", "low",
            "win", "research", "girl", "guy", "early", "food", "moment", "himself", "air", "teacher",
            "force", "offer", "enough", "education", "across", "although", "remember", "foot", "second", "boy",
            "maybe", "toward", "able", "age", "policy", "everything", "love", "process", "music", "including",
            "consider", "appear", "actually", "buy", "probably", "human", "wait", "serve", "market", "die",
            "send", "expect", "sense", "build", "stay", "fall", "oh", "nation", "plan", "cut",
            "college", "interest", "death", "course", "someone", "experience", "behind", "reach", "local", "kill",
            "six", "remain", "effect", "yeah", "suggest", "class", "control", "raise", "care", "perhaps",
            "little", "late", "hard", "field", "else", "pass", "former", "sell", "major", "sometimes",
            "require", "along", "development", "themselves", "report", "role", "better", "economic", "effort", "decide",
            "rate", "strong", "possible", "heart", "drug", "show", "leader", "light", "voice", "wife",
            "whole", "police", "mind", "finally", "pull", "return", "free", "military", "price", "less",
            "according", "decision", "explain", "son", "hope", "even", "develop", "view", "relationship", "carry",
            "town", "road", "drive", "arm", "true", "federal", "break", "better", "difference", "thank",
            "receive", "value", "international", "building", "action", "full", "model", "join", "season", "society",
            "tax", "director", "position", "player", "agree", "especially", "record", "pick", "wear", "paper",
            "special", "space", "ground", "form", "support", "event", "official", "whose", "matter", "everyone",
            "center", "couple", "site", "project", "hit", "base", "activity", "star", "table", "need",
            "court", "produce", "eat", "teach", "oil", "half", "situation", "easy", "cost", "industry",
            "figure", "street", "image", "itself", "phone", "either", "data", "cover", "quite", "picture",
            "clear", "practice", "piece", "land", "recent", "describe", "product", "doctor", "wall", "patient",
            "worker", "news", "test", "movie", "certain", "north", "personal", "simply", "third", "technology",
            "catch", "step", "baby", "computer", "type", "attention", "draw", "film", "republican", "tree",
            "source", "red", "nearly", "organization", "choose", "cause", "hair", "look", "point", "century",
            "evidence", "window", "difficult", "listen", "soon", "culture", "billion", "chance", "brother", "energy",
            "period", "summer", "realize", "hundred", "available", "plant", "likely", "opportunity", "term", "short",
            "letter", "condition", "choice", "single", "rule", "daughter", "administration", "south", "husband", "congress",
            "floor", "campaign", "material", "population", "economy", "medical", "hospital", "church", "close", "thousand",
            "risk", "current", "fire", "future", "wrong", "involve", "defense", "anyone", "increase", "security",
            "bank", "myself", "certainly", "west", "sport", "board", "seek", "per", "subject", "officer",
            "private", "rest", "behavior", "deal", "performance", "fight", "throw", "top", "quickly", "past",
            "goal", "bed", "order", "author", "fill", "represent", "focus", "foreign", "drop", "blood",
            "upon", "agency", "push", "nature", "color", "recently", "store", "reduce", "sound", "note",
            "fine", "near", "movement", "page", "enter", "share", "common", "poor", "natural", "race",
            "concern", "series", "significant", "similar", "hot", "language", "usually", "response", "dead", "rise",
            "animal", "factor", "decade", "article", "shoot", "east", "save", "seven", "artist", "scene",
            "stock", "career", "despite", "central", "eight", "thus", "treatment", "beyond", "happy", "exactly",
            "protect", "approach", "lie", "size", "dog", "fund", "serious", "occur", "media", "ready",
            "sign", "thought", "list", "individual", "simple", "quality", "pressure", "accept", "answer", "resource",
            "identify", "left", "meeting", "determine", "prepare", "disease", "whatever", "success", "argue", "cup",
            "particularly", "amount", "ability", "staff", "recognize", "indicate", "character", "growth", "loss", "degree",
            "wonder", "attack", "herself", "region", "television", "box", "training", "pretty", "trade", "election",
            "everybody", "physical", "lay", "general", "feeling", "standard", "bill", "message", "fail", "outside",
            "arrive", "analysis", "benefit", "sex", "forward", "lawyer", "present", "section", "environmental", "glass",
            "skill", "sister", "professor", "operation", "financial", "crime", "stage", "ok", "compare", "authority",
            "miss", "design", "sort", "act", "ten", "knowledge", "gun", "station", "blue", "strategy",
            "clearly", "discuss", "indeed", "truth", "song", "example", "democratic", "check", "environment", "leg",
            "dark", "various", "rather", "laugh", "guess", "executive", "prove", "hang", "entire", "rock",
            "forget", "claim", "remove", "manager", "enjoy", "network", "legal", "religious", "cold", "final",
            "main", "science", "green", "memory", "card", "above", "seat", "cell", "establish", "nice",
            "trial", "expert", "spring", "firm", "radio", "visit", "management", "avoid", "imagine", "tonight",
            "huge", "ball", "finish", "yourself", "theory", "impact", "respond", "statement", "maintain", "charge",
            "popular", "traditional", "onto", "reveal", "direction", "weapon", "employee", "cultural", "contain", "peace",
            "pain", "apply", "measure", "wide", "shake", "fly", "interview", "manage", "chair", "fish",
            "particular", "camera", "structure", "politics", "perform", "bit", "weight", "suddenly", "discover", "candidate",
            "production", "treat", "trip", "evening", "affect", "inside", "conference", "unit", "style", "adult",
            "worry", "range", "mention", "deep", "edge", "specific", "writer", "trouble", "necessary", "throughout",
            "challenge", "fear", "shoulder", "institution", "middle", "sea", "dream", "bar", "beautiful", "property",
            "instead", "improve", "stuff", "detail", "method", "somebody", "magazine", "hotel", "soldier", "reflect",
            "heavy", "sexual", "bag", "heat", "marriage", "tough", "sing", "surface", "purpose", "exist",
            "pattern", "whom", "skin", "agent", "owner", "machine", "gas", "ahead", "generation", "commercial",
            "address", "cancer", "item", "reality", "coach", "mrs", "yard", "beat", "violence", "total",
            "tend", "investment", "discussion", "finger", "garden", "notice", "collection", "modern", "task", "partner",
            "positive", "civil", "kitchen", "consumer", "shot", "budget", "wish", "painting", "scientist", "safe",
            "agreement", "capital", "mouth", "nor", "victim", "newspaper", "threat", "responsibility", "smile", "attorney",
            "score", "account", "interesting", "audience", "rich", "dinner", "vote", "western", "relate", "travel",
            "debate", "prevent", "citizen", "majority", "none", "front", "born", "admit", "senior", "assume",
            "wind", "key", "professional", "mission", "fast", "alone", "customer", "suffer", "speech", "successful",
            "option", "participant", "southern", "fresh", "eventually", "forest", "video", "global", "senate", "reform",
            "access", "restaurant", "judge", "publish", "relation", "release", "bird", "opinion", "credit", "critical",
            "corner", "concerned", "recall", "version", "stare", "safety", "effective", "neighborhood", "original", "troop",
            "income", "directly", "hurt", "species", "immediately", "track", "basic", "strike", "sky", "freedom",
            "absolutely", "plane", "nobody", "achieve", "object", "attitude", "labor", "refer", "concept", "client",
            "powerful", "perfect", "nine", "therefore", "conduct", "announce", "conversation", "examine", "touch", "please",
            "attend", "completely", "variety", "sleep", "involved", "investigation", "nuclear", "researcher", "press", "conflict",
            "spirit", "replace", "british", "encourage", "argument", "camp", "brain", "feature", "afternoon", "weekend",
            "dozen", "possibility", "insurance", "department", "battle", "beginning", "date", "generally", "african", "sorry",
            "crisis", "complete", "fan", "stick", "define", "easily", "hole", "element", "vision", "status",
            "normal", "chinese", "ship", "solution", "stone", "slowly", "scale", "university", "introduce", "driver",
            "attempt", "park", "spot", "lack", "ice", "boat", "drink", "sun", "distance", "wood",
            "handle", "truck", "return", "mountain", "survey", "supposed", "tradition", "winter", "village", "soviet",
            "refuse", "sales", "roll", "communication", "screen", "gain", "resident", "hide", "gold", "club",
            "farm", "potential", "european", "presence", "independent", "district", "shape", "reader", "contract", "crowd",
            "christian", "express", "apartment", "willing", "strength", "previous", "band", "obviously", "horse", "interested",
            "target", "prison", "ride", "guard", "terms", "demand", "reporter", "deliver", "text", "tool",
            "wild", "vehicle", "observe", "flight", "facility", "understanding", "average", "emerge", "advantage", "quick",
            "light", "leadership", "earn", "pound", "basis", "bright", "operate", "guest", "sample", "contribute",
            "tiny", "block", "protection", "settle", "feed", "collect", "additional", "highly", "identity", "title",
            "mostly", "lesson", "faith", "river", "promote", "living", "count", "unless", "marry", "tomorrow",
            "technique", "path", "ear", "shop", "folk", "principle", "survive", "lift", "border", "competition",
            "jump", "gather", "limit", "fit", "cry", "equipment", "worth", "associate", "critic", "warm",
            "aspect", "insist", "failure", "annual", "french", "christmas", "comment", "responsible", "affair", "procedure",
            "regular", "spread", "chairman", "baseball", "soft", "ignore", "egg", "belief", "demonstrate", "anybody",
            "murder", "gift", "religion", "review", "editor", "engage", "coffee", "document", "speed", "cross",
            "influence", "anyway", "threaten", "commit", "female", "youth", "wave", "afraid", "quarter", "background",
            "native", "broad", "wonderful", "deny", "apparently", "slightly", "reaction", "twice", "suit", "perspective",
            "growing", "blow", "construction", "intelligence", "destroy", "cook", "connection", "burn", "shoe", "grade",
            "context", "committee", "hey", "mistake", "focus", "smile", "location", "clothes", "quiet", "dress",
            "promise", "aware", "neighbor", "function", "bone", "active", "extend", "chief", "combine", "wine",
            "below", "cool", "voter", "learning", "bus", "hell", "dangerous", "remind", "moral", "united",
            "category", "relatively", "victory", "academic", "internet", "healthy", "negative", "following", "historical", "medicine",
            "tour", "depend", "photo", "finding", "grab", "direct", "classroom", "contact", "justice", "participate",
            "daily", "fair", "pair", "famous", "exercise", "knee", "flower", "tape", "hire", "familiar",
            "appropriate", "supply", "fully", "actor", "birth", "search", "tie", "democracy", "eastern", "primary",
            "yesterday", "circle", "device", "progress", "bottom", "island", "exchange", "clean", "studio", "train",
            "lady", "colleague", "application", "neck", "lean", "damage", "plastic", "tall", "plate", "hate",
            "otherwise", "writing", "male", "alive", "expression", "football", "intend", "chicken", "army", "abuse",
            "theater", "shut", "map", "extra", "session", "danger", "welcome", "domestic", "lots", "literature",
            "rain", "desire", "assessment", "injury", "respect", "northern", "nod", "paint", "fuel", "leaf",
            "dry", "russian", "instruction", "pool", "climb", "sweet", "engine", "fourth", "salt", "expand",
            "importance", "metal", "fat", "ticket", "software", "disappear", "corporate", "strange", "lip", "reading",
            "urban", "mental", "increasingly", "lunch", "educational", "somewhere", "farmer", "sugar", "planet", "favorite",
            "explore", "obtain", "enemy", "greatest", "complex", "surround", "athlete", "invite", "repeat", "carefully",
            "soul", "scientific", "impossible", "panel", "meaning", "mom", "married", "instrument", "predict", "weather",
            "presidential", "emotional", "commitment", "supreme", "bear", "pocket", "thin", "temperature", "surprise", "poll",
            "proposal", "consequence", "breath", "sight", "balance", "adopt", "minority", "straight", "connect", "works",
            "teaching", "belong", "aid", "advice", "okay", "photograph", "empty", "regional", "trail", "novel",
            "code", "somehow", "organize", "jury", "breast", "iraqi", "acknowledge", "theme", "storm", "union",
            "desk", "thanks", "fruit", "expensive", "yellow", "conclusion", "prime", "shadow", "struggle", "conclude",
            "analyst", "dance", "regulation", "being", "ring", "largely", "shift", "revenue", "mark", "locate",
            "county", "appearance", "package", "difficulty", "bridge", "recommend", "obvious", "basically", "generate", "anymore",
            "propose", "thinking", "possibly", "trend", "visitor", "loan", "currently", "comfortable", "investor", "profit",
            "angry", "crew", "deeply", "accident", "male", "meal", "hearing", "traffic", "muscle", "notion",
            "capture", "prefer", "truly", "earth", "japanese", "chest", "thick", "cash", "museum", "beauty",
            "emergency", "unique", "internal", "ethnic", "link", "stress", "content", "select", "root", "nose",
            "declare", "appreciate", "actual", "bottle", "hardly", "setting", "launch", "file", "sick", "outcome",
            "ad", "defend", "matter", "sheet", "ought", "ensure", "catholic", "extremely", "extent", "component",
            "mix", "long-term", "slow", "contrast", "zone", "wake", "airport", "brown", "shirt", "pilot",
            "warn", "ultimately", "cat", "contribution", "capacity", "ourselves", "estate", "guide", "circumstance", "snow",
            "english", "politician", "steal", "pursue", "slip", "percentage", "meat", "funny", "neither", "soil",
            "surgery", "correct", "jewish", "blame", "estimate", "due", "basketball", "golf", "investigate", "crazy",
            "significantly", "chain", "branch", "combination", "frequently", "governor", "relief", "user", "dad", "kick",
            "manner", "ancient", "silence", "rating", "golden", "motion", "german", "gender", "solve", "fee",
            "landscape", "used", "bowl", "equal", "forth", "frame", "typical", "except", "conservative", "eliminate",
            "host", "hall", "trust", "ocean", "row", "producer", "afford", "meanwhile", "regime", "division",
            "confirm", "fix", "appeal", "mirror", "tooth", "smart", "length", "entirely", "rely", "topic",
            "complain", "issue", "variable", "telephone", "perception", "attract", "confidence", "bedroom", "secret", "debt",
            "rare", "tank", "nurse", "coverage", "opposition", "aside", "anywhere", "bond", "pleasure", "master",
            "era", "requirement", "fun", "expectation", "wing", "separate", "somewhat", "pour", "stir", "judgment",
            "beer", "reference", "tear", "doubt", "grant", "seriously", "minister", "totally", "hero", "industrial",
            "cloud", "stretch", "winner", "volume", "seed", "surprised", "fashion", "pepper", "busy", "cheap",
            "beach", "divide", "oppose", "route", "league", "core", "mail", "futo", "keyboard", "gesture",
            "swipe", "typing", "adaptive", "android", "hello", "hey", "awesome", "great", "perfect", "thanks",
            "please", "sorry", "yes", "no", "maybe", "tonight", "tomorrow", "yesterday", "morning", "afternoon",
            "evening", "weekend", "message", "email", "reply", "send", "delete", "space", "enter", "shift",
            "fox", "jumps", "lazy", "dogs", "learns", "locally", "privacy", "spatial", "drift", "vector",
            "curve", "accuracy", "accurate", "prediction", "smooth", "fluid", "thumb", "keys", "written"
        )
        for (w in tier2) {
            if (!map.containsKey(w)) {
                map[w] = 212
            }
        }
        map
    }

    // Common word-to-word transitions for immediate context disambiguation
    val starterBigrams: Map<Pair<String, String>, Int> = mapOf(
        ("i" to "am") to 6,
        ("i" to "have") to 5,
        ("i" to "will") to 5,
        ("i" to "think") to 5,
        ("i" to "want") to 5,
        ("i" to "need") to 5,
        ("you" to "are") to 6,
        ("you" to "can") to 5,
        ("you" to "have") to 5,
        ("it" to "is") to 6,
        ("it" to "was") to 5,
        ("that" to "is") to 5,
        ("there" to "is") to 5,
        ("there" to "are") to 5,
        ("this" to "is") to 6,
        ("going" to "to") to 6,
        ("want" to "to") to 6,
        ("need" to "to") to 6,
        ("have" to "to") to 6,
        ("used" to "to") to 5,
        ("able" to "to") to 5,
        ("thank" to "you") to 6,
        ("thanks" to "for") to 5,
        ("looking" to "for") to 5,
        ("wait" to "for") to 4,
        ("come" to "from") to 5,
        ("came" to "from") to 5,
        ("away" to "from") to 5,
        ("new" to "form") to 5,
        ("good" to "morning") to 5,
        ("good" to "night") to 5,
        ("right" to "now") to 6,
        ("at" to "night") to 5,
        ("of" to "the") to 6,
        ("in" to "the") to 6,
        ("to" to "the") to 6,
        ("on" to "the") to 6,
        ("for" to "the") to 6,
        ("and" to "the") to 5,
        ("with" to "the") to 5,
        ("from" to "the") to 5,
        ("by" to "the") to 4,
        ("at" to "the") to 5,
        ("the" to "quick") to 4,
        ("quick" to "brown") to 4,
        ("brown" to "fox") to 4,
        ("fox" to "jumps") to 4,
        ("jumps" to "over") to 4,
        ("over" to "the") to 4,
        ("the" to "lazy") to 4,
        ("lazy" to "dog") to 4,
        ("feel" to "free") to 5,
        ("let" to "me") to 5,
        ("me" to "know") to 5,
        ("talk" to "to") to 4,
        ("see" to "you") to 5,
        ("how" to "are") to 5,
        ("are" to "you") to 5,
        ("what" to "is") to 5
    )

    /**
     * Fast prefix + edit-distance lookup for standard tap-typing word composition.
     */
    fun getPrefixAndTypoCandidates(
        composingPrefix: String,
        prevContextWord: String,
        userWords: Map<String, com.example.data.UserWordFrequencyEntity>,
        userBigrams: Map<Pair<String, String>, com.example.data.UserBigramEntity>,
        keyGeometries: Map<Char, KeyGeometry>
    ): List<GestureCandidate> {
        val cleanPrefix = composingPrefix.trim().lowercase().filter { it in 'a'..'z' }
        val prevNorm = prevContextWord.trim().lowercase().filter { it in 'a'..'z' }

        // If composing buffer is empty, return next-word predictions based on prevContextWord!
        if (cleanPrefix.isEmpty()) {
            val nextWordScores = mutableListOf<GestureCandidate>()
            if (prevNorm.isNotEmpty()) {
                for ((pair, entity) in userBigrams) {
                    if (pair.first == prevNorm) {
                        val score = (80f + entity.transitionCount * 4.5f).coerceAtMost(98f)
                        nextWordScores.add(
                            GestureCandidate(
                                word = pair.second,
                                adaptiveScore = score,
                                baselineStaticScore = 72f,
                                spatialAnchorScore = 90f,
                                dtwShapeScore = 90f,
                                inflectionMatchScore = 90f,
                                contextBigramBoost = entity.transitionCount * 4.5f,
                                personalStyleBoost = entity.transitionCount * 4.5f,
                                explanationBadge = "Next Word"
                            )
                        )
                    }
                }
                for ((pair, count) in starterBigrams) {
                    if (pair.first == prevNorm && nextWordScores.none { it.word == pair.second }) {
                        val score = (75f + count * 3f).coerceAtMost(94f)
                        nextWordScores.add(
                            GestureCandidate(
                                word = pair.second,
                                adaptiveScore = score,
                                baselineStaticScore = 75f,
                                spatialAnchorScore = 90f,
                                dtwShapeScore = 90f,
                                inflectionMatchScore = 90f,
                                contextBigramBoost = count * 3f,
                                personalStyleBoost = count * 3f,
                                explanationBadge = "Next Word"
                            )
                        )
                    }
                }
            }
            if (nextWordScores.isEmpty()) {
                listOf("the", "i", "you", "to", "and").forEachIndexed { idx, w ->
                    nextWordScores.add(
                        GestureCandidate(
                            word = w,
                            adaptiveScore = 88f - idx * 3f,
                            baselineStaticScore = 88f - idx * 3f,
                            spatialAnchorScore = 90f,
                            dtwShapeScore = 90f,
                            inflectionMatchScore = 90f,
                            contextBigramBoost = 0f,
                            personalStyleBoost = 0f,
                            explanationBadge = "Suggested"
                        )
                    )
                }
            }
            return nextWordScores.sortedByDescending { it.adaptiveScore }.take(5)
        }

        val results = mutableListOf<GestureCandidate>()

        // Always include the exact typed literal as a candidate so the user can commit custom words
        val exactInLexicon = defaultWordsWithFreq.containsKey(cleanPrefix) || userWords.containsKey(cleanPrefix)
        val exactBigramBoost = if (prevNorm.isNotEmpty()) {
            ((userBigrams[prevNorm to cleanPrefix]?.transitionCount ?: 0) * 3f +
                (starterBigrams[prevNorm to cleanPrefix] ?: 0) * 1.8f)
        } else 0f

        results.add(
            GestureCandidate(
                word = cleanPrefix,
                adaptiveScore = if (exactInLexicon) (91f + exactBigramBoost).coerceAtMost(99f) else 83f,
                baselineStaticScore = if (exactInLexicon) 90f else 80f,
                spatialAnchorScore = 95f,
                dtwShapeScore = 95f,
                inflectionMatchScore = 95f,
                contextBigramBoost = exactBigramBoost,
                personalStyleBoost = exactBigramBoost,
                explanationBadge = if (exactInLexicon) "Exact" else "\"$cleanPrefix\""
            )
        )

        val combinedPool = HashMap<String, Int>(defaultWordsWithFreq)
        for ((w, u) in userWords) {
            // Boost already-used words strongly so they always rank above unused dictionary words
            val usedBonus = u.personalUseCount * 18 + u.correctionSelectCount * 30 + (if (u.isCustomWord) 25 else 0)
            combinedPool[w] = (combinedPool[w] ?: 195) + usedBonus
        }

        for ((word, freq) in combinedPool) {
            if (word == cleanPrefix) continue
            val isPrefixMatch = word.startsWith(cleanPrefix) && word.length <= cleanPrefix.length + 6
            val isSameLenTypo = word.length == cleanPrefix.length && cleanPrefix.length >= 3 &&
                isAdjacentKeyTypo(cleanPrefix, word, keyGeometries)

            if (!isPrefixMatch && !isSameLenTypo) continue

            val lenDiffPenalty = (word.length - cleanPrefix.length).coerceAtLeast(0) * 2.0f
            val baseScore = if (isPrefixMatch) {
                (76f + (freq - 180) * 0.25f - lenDiffPenalty).coerceIn(50f, 92f)
            } else {
                (78f + (freq - 180) * 0.22f).coerceIn(55f, 90f)
            }

            val bigramBoost = if (prevNorm.isNotEmpty()) {
                ((userBigrams[prevNorm to word]?.transitionCount ?: 0) * 4.5f +
                    (starterBigrams[prevNorm to word] ?: 0) * 1.8f)
            } else 0f
            val useCount = userWords[word]?.personalUseCount ?: 0
            val personalBoost = useCount * 3.2f +
                (userWords[word]?.correctionSelectCount ?: 0) * 4.5f

            val total = (baseScore + bigramBoost + personalBoost).coerceIn(40f, 99.5f)
            results.add(
                GestureCandidate(
                    word = word,
                    adaptiveScore = total,
                    baselineStaticScore = baseScore,
                    spatialAnchorScore = 90f,
                    dtwShapeScore = 88f,
                    inflectionMatchScore = 90f,
                    contextBigramBoost = bigramBoost,
                    personalStyleBoost = bigramBoost + personalBoost,
                    explanationBadge = if (useCount > 0) "Used" else ""
                )
            )
        }

        return results.sortedByDescending { it.adaptiveScore }.distinctBy { it.word }.take(6)
    }

    private fun isAdjacentKeyTypo(typed: String, candidate: String, keys: Map<Char, KeyGeometry>): Boolean {
        var mismatches = 0
        for (i in typed.indices) {
            val c1 = typed[i]
            val c2 = candidate[i]
            if (c1 != c2) {
                mismatches++
                if (mismatches > 1) return false
                val g1 = keys[c1] ?: return false
                val g2 = keys[c2] ?: return false
                val dist = kotlin.math.hypot(g1.canonicalX - g2.canonicalX, g1.canonicalY - g2.canonicalY)
                if (dist > 0.22f) return false
            }
        }
        return mismatches == 1
    }
}
