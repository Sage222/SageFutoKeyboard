package com.example.engine

import com.example.data.GesturePrototypeEntity
import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Improved Gesture Typing Recognition Engine built on FUTO Keyboard's LatinIME principles,
 * augmented with Curvature-Velocity Inflection Detection, Band-Constrained Dynamic Time Warping (DTW),
 * Double-Letter Loop/Dwell Detection, and Continuous User Style Adaptation.
 */
class GestureTypingEngine {

    val qwertyKeys: Map<Char, KeyGeometry> = buildStandardQwertyGeometry()

    private fun buildStandardQwertyGeometry(): Map<Char, KeyGeometry> {
        val rows = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
        )
        // Row vertical centers in normalized letter-area space [0f..1f]
        val rowY = listOf(0.18f, 0.50f, 0.82f)
        val rowHorizontalOffsets = listOf(0.0f, 0.05f, 0.15f)
        val keyWidth = 0.10f
        val keyHeight = 0.30f

        val map = mutableMapOf<Char, KeyGeometry>()
        for (r in rows.indices) {
            val rowStr = rows[r]
            val startX = rowHorizontalOffsets[r]
            for (c in rowStr.indices) {
                val ch = rowStr[c]
                val cx = startX + (c + 0.5f) * keyWidth
                val cy = rowY[r]
                map[ch] = KeyGeometry(
                    char = ch,
                    row = r,
                    col = c,
                    canonicalX = cx.coerceIn(0.04f, 0.96f),
                    canonicalY = cy,
                    width = keyWidth,
                    height = keyHeight
                )
            }
        }
        return map
    }

    /**
     * Finds the nearest key to (x, y), optionally using learned spatial offsets.
     */
    fun findNearestKey(
        x: Float,
        y: Float,
        spatialProfiles: Map<Char, KeySpatialProfileEntity> = emptyMap(),
        useAdapted: Boolean = true
    ): Char {
        var bestChar = 'e'
        var bestDistSq = Float.MAX_VALUE
        for ((ch, geom) in qwertyKeys) {
            val profile = if (useAdapted) spatialProfiles[ch] else null
            val kx = geom.canonicalX + (profile?.driftX ?: 0f)
            val ky = geom.canonicalY + (profile?.driftY ?: 0f)
            val dx = x - kx
            val dy = y - ky
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestChar = ch
            }
        }
        return bestChar
    }

    /**
     * Decodes a continuous swipe trajectory into ranked word candidates, computing both
     * the Static Baseline score and the User-Adapted score for real-time telemetry comparison.
     */
    fun decodeGesture(
        rawPoints: List<RawTouchPoint>,
        prevContextWord: String,
        hyperparams: DecoderHyperparameters,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        userWords: Map<String, UserWordFrequencyEntity>,
        userBigrams: Map<Pair<String, String>, UserBigramEntity>,
        userPrototypes: Map<String, GesturePrototypeEntity>
    ): GestureDecodeResult {
        if (rawPoints.isEmpty()) {
            return emptyResult()
        }

        val durationMs = max(1L, rawPoints.last().timestampMs - rawPoints.first().timestampMs)
        val totalArcLength = computeArcLength(rawPoints)

        // If the gesture is very short (tap or micro-slip), treat as single-key tap + prefix prediction
        if (rawPoints.size < 3 || totalArcLength < 0.045f) {
            val pt = rawPoints.first()
            val tappedChar = findNearestKey(pt.x, pt.y, spatialProfiles, hyperparams.enablePersonalDrift)
            return decodeTapSingleKey(
                tappedChar = tappedChar,
                pt = pt,
                prevContextWord = prevContextWord,
                hyperparams = hyperparams,
                spatialProfiles = spatialProfiles,
                userWords = userWords,
                userBigrams = userBigrams
            )
        }

        // Step 1: Smooth micro-jitter and resample into 32 equidistant points + 16 prototype points
        val smoothedRaw = smoothTrajectory(rawPoints)
        val resampled32 = resampleWithKinematics(smoothedRaw, 32)
        val prototype16 = resampleSimplePoints(smoothedRaw, 16)

        // Step 2: Extract Inflection Nodes (Start, Corners, Dwell/Loops, End)
        val inflectionNodes = extractInflectionNodes(
            points = resampled32,
            spatialProfiles = spatialProfiles,
            useAdapted = hyperparams.enablePersonalDrift
        )
        val hasDoubleLetterSignal = resampled32.any { it.isLoopOrDwell }

        // Step 3: Build combined lexicon (FUTO built-in + custom user words)
        val combinedLexicon = LinkedHashMap<String, Int>(FutoLexicon.defaultWordsWithFreq.size + userWords.size)
        combinedLexicon.putAll(FutoLexicon.defaultWordsWithFreq)
        for ((w, entity) in userWords) {
            val clean = w.lowercase().filter { it in 'a'..'z' }
            if (clean.isNotEmpty()) {
                combinedLexicon[clean] = max(combinedLexicon[clean] ?: 120, entity.baseFrequency)
            }
        }

        val startPt = resampled32.first()
        val endPt = resampled32.last()
        val prevNorm = prevContextWord.trim().lowercase()

        // Step 4: Fast spatial pre-filter on start & end keys (generous radius so drift-shifted words pass)
        val candidateList = mutableListOf<GestureCandidate>()

        for ((word, baseFreq) in combinedLexicon) {
            if (word.isEmpty()) continue
            val firstChar = word.first()
            val lastChar = word.last()
            val firstGeom = qwertyKeys[firstChar] ?: continue
            val lastGeom = qwertyKeys[lastChar] ?: continue

            val firstDrift = spatialProfiles[firstChar]
            val lastDrift = spatialProfiles[lastChar]

            val staticStartDist = hypot(startPt.x - firstGeom.canonicalX, startPt.y - firstGeom.canonicalY)
            val adaptedStartDist = hypot(
                startPt.x - (firstGeom.canonicalX + (firstDrift?.driftX ?: 0f)),
                startPt.y - (firstGeom.canonicalY + (firstDrift?.driftY ?: 0f))
            )

            val staticEndDist = hypot(endPt.x - lastGeom.canonicalX, endPt.y - lastGeom.canonicalY)
            val adaptedEndDist = hypot(
                endPt.x - (lastGeom.canonicalX + (lastDrift?.driftX ?: 0f)),
                endPt.y - (lastGeom.canonicalY + (lastDrift?.driftY ?: 0f))
            )

            // Pre-filter: either static or adapted endpoints must be within reasonable reach (0.28 normalized)
            if (min(staticStartDist, adaptedStartDist) > 0.29f ||
                min(staticEndDist, adaptedEndDist) > 0.31f
            ) {
                continue
            }

            // Generate 32-point ideal template path for both Static and User-Adapted key centroids
            val staticWordTemplate = buildWordTemplate32(word, emptyMap())
            val adaptedWordTemplate = if (hyperparams.enablePersonalDrift) {
                buildWordTemplate32(word, spatialProfiles)
            } else {
                staticWordTemplate
            }

            // 1. Spatial Anchor Score (Start + End Gaussian receptor likelihood)
            val sigmaStart = 0.085f
            val sigmaEnd = 0.095f
            val staticSpatial = 92f * (
                0.55f * gaussianScore(staticStartDist, sigmaStart) +
                    0.45f * gaussianScore(staticEndDist, sigmaEnd)
                )

            val adaptedStartSigma = sigmaStart * (firstDrift?.radiusScale ?: 1f)
            val adaptedEndSigma = sigmaEnd * (lastDrift?.radiusScale ?: 1f)
            val adaptedSpatial = if (hyperparams.enablePersonalDrift) {
                92f * (
                    0.55f * gaussianScore(adaptedStartDist, adaptedStartSigma) +
                        0.45f * gaussianScore(adaptedEndDist, adaptedEndSigma)
                    )
            } else {
                staticSpatial
            }

            // 2. Band-Constrained Dynamic Time Warping (DTW) Shape Score + Length Ratio Consistency
            val staticDtwDist = computeBandDtw(resampled32, staticWordTemplate)
            val adaptedDtwDist = computeBandDtw(resampled32, adaptedWordTemplate)

            val idealArcLen = computePairArcLength(adaptedWordTemplate)
            val lenDiff = abs(totalArcLength - idealArcLen) / max(0.18f, idealArcLen)
            val lengthPenalty = (1f - 0.28f * lenDiff.coerceIn(0f, 1.5f)).coerceIn(0.55f, 1f)

            val staticDtwScore = (90f * exp(-staticDtwDist * 14.5f) * lengthPenalty).coerceIn(0f, 92f)
            val adaptedDtwScore = (90f * exp(-adaptedDtwDist * 14.5f) * lengthPenalty).coerceIn(0f, 92f)

            // 3. Corner Inflection & Double-Letter Alignment Score
            val wordHasDoubleLetters = hasConsecutiveDuplicateLetters(word)
            val inflectionScore = computeInflectionAlignmentScore(
                word = word,
                inflectionNodes = inflectionNodes,
                spatialProfiles = if (hyperparams.enablePersonalDrift) spatialProfiles else emptyMap(),
                hasDoubleLetterSignal = hasDoubleLetterSignal && hyperparams.enableDoubleLetterDetection,
                wordHasDoubleLetters = wordHasDoubleLetters
            )

            // Base lexical prior [0f..3.5f]
            val lexicalPrior = (baseFreq - 150).coerceIn(0, 100) * 0.03f

            // Compute Baseline Static Score
            val baselineTotal = (
                staticSpatial * 0.42f +
                    staticDtwScore * 0.46f +
                    inflectionScore * 0.12f +
                    lexicalPrior
                ).coerceIn(1f, 94.0f)

            // 4. Contextual Bigram Boost P(word | prevContextWord)
            var bigramBoost = 0f
            if (hyperparams.enableContextBigrams && prevNorm.isNotEmpty()) {
                val learnedCount = userBigrams[prevNorm to word]?.transitionCount ?: 0
                val starterCount = FutoLexicon.starterBigrams[prevNorm to word] ?: 0
                val totalTransitionWeight = learnedCount * 2.5f + starterCount * 1.2f
                if (totalTransitionWeight > 0f) {
                    bigramBoost = min(7.5f, totalTransitionWeight * 1.6f) * (hyperparams.contextBigramWeight / 0.12f)
                }
            }

            // 5. Personal Unigram & Correction Priority Boost
            val userWordEntry = userWords[word]
            val personalFreqBoost = if (userWordEntry != null) {
                min(
                    6.5f,
                    userWordEntry.personalUseCount * 0.85f +
                        userWordEntry.correctionSelectCount * 1.8f +
                        (if (userWordEntry.isCustomWord) 3.0f else 0f)
                )
            } else {
                0f
            }

            // 6. Personal Gesture Prototype Similarity Boost (16-point DTW against user's learned swipe signature)
            var prototypeBoost = 0f
            if (hyperparams.enablePersonalPrototypes) {
                val protoEntity = userPrototypes[word]
                if (protoEntity != null) {
                    val protoPoints = decodePrototypePath(protoEntity.encodedNormalizedPath)
                    if (protoPoints.size == prototype16.size) {
                        val protoDist = computePairDtw(prototype16, protoPoints)
                        // Strong similarity when protoDist < 0.065
                        val protoSim = exp(-protoDist * 14.0f)
                        if (protoSim > 0.52f) {
                            val maturity = min(1f, protoEntity.sampleCount / 3f)
                            prototypeBoost = (protoSim - 0.45f) * 10f * maturity
                        }
                    }
                }
            }

            // Combine with user-tunable weights
            val weightSum = max(
                0.01f,
                hyperparams.spatialAnchorWeight +
                    hyperparams.dtwShapeWeight +
                    hyperparams.cornerInflectionWeight
            )
            val wSpatial = hyperparams.spatialAnchorWeight / weightSum
            val wDtw = hyperparams.dtwShapeWeight / weightSum
            val wInflect = hyperparams.cornerInflectionWeight / weightSum

            val coreAdaptive = adaptedSpatial * wSpatial +
                adaptedDtwScore * wDtw +
                inflectionScore * wInflect +
                lexicalPrior

            val spatialDriftGain = max(0f, (adaptedSpatial - staticSpatial) * wSpatial + (adaptedDtwScore - staticDtwScore) * wDtw)
            val totalPersonalStyleBoost = spatialDriftGain + personalFreqBoost + prototypeBoost + bigramBoost

            val adaptiveTotal = (coreAdaptive + bigramBoost + personalFreqBoost + prototypeBoost)
                .coerceIn(1f, 99.8f)

            val badge = buildExplanationBadge(
                spatialDriftGain = spatialDriftGain,
                bigramBoost = bigramBoost,
                prototypeBoost = prototypeBoost,
                personalFreqBoost = personalFreqBoost,
                doubleLetterBonus = hasDoubleLetterSignal && wordHasDoubleLetters
            )

            candidateList.add(
                GestureCandidate(
                    word = word,
                    adaptiveScore = adaptiveTotal,
                    baselineStaticScore = baselineTotal,
                    spatialAnchorScore = adaptedSpatial,
                    dtwShapeScore = adaptedDtwScore,
                    inflectionMatchScore = inflectionScore,
                    contextBigramBoost = bigramBoost,
                    personalStyleBoost = totalPersonalStyleBoost,
                    explanationBadge = badge
                )
            )
        }

        val sortedByAdaptive = candidateList.sortedByDescending { it.adaptiveScore }.take(8)
        val sortedByStatic = candidateList.sortedByDescending { it.baselineStaticScore }

        val staticTop = sortedByStatic.firstOrNull()?.word ?: ""
        val adaptiveTop = sortedByAdaptive.firstOrNull()?.word ?: ""

        return GestureDecodeResult(
            candidates = sortedByAdaptive,
            smoothedPath = resampled32,
            prototype16Path = prototype16,
            inflectionNodes = inflectionNodes,
            detectedDoubleLetterZone = hasDoubleLetterSignal,
            durationMs = durationMs,
            pathLengthNormalized = totalArcLength,
            staticTopWord = staticTop,
            adaptiveTopWord = adaptiveTop,
            wasPromotedByAdaptation = staticTop.isNotEmpty() && adaptiveTop.isNotEmpty() && staticTop != adaptiveTop
        )
    }

    private fun decodeTapSingleKey(
        tappedChar: Char,
        pt: RawTouchPoint,
        prevContextWord: String,
        hyperparams: DecoderHyperparameters,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        userWords: Map<String, UserWordFrequencyEntity>,
        userBigrams: Map<Pair<String, String>, UserBigramEntity>
    ): GestureDecodeResult {
        val prefix = tappedChar.toString()
        val prevNorm = prevContextWord.trim().lowercase()
        val candidates = mutableListOf<GestureCandidate>()

        // Include exact single-letter word if 'a' or 'i', plus top words starting with tappedChar
        val pool = LinkedHashMap<String, Int>()
        pool[prefix] = if (tappedChar == 'a' || tappedChar == 'i') 245 else 185
        for ((w, freq) in FutoLexicon.defaultWordsWithFreq) {
            if (w.startsWith(prefix)) pool[w] = freq
        }
        for ((w, entity) in userWords) {
            if (w.startsWith(prefix)) pool[w] = max(pool[w] ?: 150, entity.baseFrequency + entity.personalUseCount * 10)
        }

        for ((word, freq) in pool) {
            val baseScore = if (word == prefix) 88f else (60f + (freq - 180) * 0.35f).coerceIn(45f, 86f)
            val bigramCount = if (prevNorm.isNotEmpty() && hyperparams.enableContextBigrams) {
                (userBigrams[prevNorm to word]?.transitionCount ?: 0) * 2 +
                    (FutoLexicon.starterBigrams[prevNorm to word] ?: 0)
            } else 0
            val bigramBoost = min(12f, bigramCount * 2.5f)
            val personalBoost = (userWords[word]?.personalUseCount ?: 0) * 1.5f
            val adaptiveScore = (baseScore + bigramBoost + personalBoost).coerceIn(10f, 99f)

            candidates.add(
                GestureCandidate(
                    word = word,
                    adaptiveScore = adaptiveScore,
                    baselineStaticScore = baseScore,
                    spatialAnchorScore = 92f,
                    dtwShapeScore = 85f,
                    inflectionMatchScore = 90f,
                    contextBigramBoost = bigramBoost,
                    personalStyleBoost = bigramBoost + personalBoost,
                    explanationBadge = if (bigramBoost > 0f) "+Context Next" else "Tap Prefix"
                )
            )
        }

        val sorted = candidates.sortedByDescending { it.adaptiveScore }.take(6)
        val normPt = NormalizedPoint(pt.x, pt.y, 0f, 0f, 0f, isInflection = true, isLoopOrDwell = false)
        return GestureDecodeResult(
            candidates = sorted,
            smoothedPath = listOf(normPt),
            prototype16Path = List(16) { pt.x to pt.y },
            inflectionNodes = listOf(
                InflectionNode(0, pt.x, pt.y, 0f, tappedChar, InflectionNode.NodeType.START)
            ),
            detectedDoubleLetterZone = false,
            durationMs = 45L,
            pathLengthNormalized = 0f,
            staticTopWord = sorted.maxByOrNull { it.baselineStaticScore }?.word ?: prefix,
            adaptiveTopWord = sorted.firstOrNull()?.word ?: prefix,
            wasPromotedByAdaptation = false
        )
    }

    private fun buildExplanationBadge(
        spatialDriftGain: Float,
        bigramBoost: Float,
        prototypeBoost: Float,
        personalFreqBoost: Float,
        doubleLetterBonus: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (prototypeBoost >= 2.2f) parts.add("Prototype")
        if (spatialDriftGain >= 1.5f) parts.add("KeyDrift")
        if (bigramBoost >= 2.0f) parts.add("N-Gram")
        if (personalFreqBoost >= 2.0f) parts.add("UserLex")
        if (doubleLetterBonus) parts.add("2xLetter")
        return if (parts.isEmpty()) "DTW+Anchor" else "+" + parts.take(2).joinToString("+")
    }

    private fun computeInflectionAlignmentScore(
        word: String,
        inflectionNodes: List<InflectionNode>,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        hasDoubleLetterSignal: Boolean,
        wordHasDoubleLetters: Boolean
    ): Float {
        if (word.length <= 2) {
            val doubleBonus = if (hasDoubleLetterSignal && wordHasDoubleLetters) 10f else 0f
            return (84f + doubleBonus).coerceAtMost(100f)
        }

        val cornerNodes = inflectionNodes.filter { it.nodeType == InflectionNode.NodeType.CORNER }
        if (cornerNodes.isEmpty()) {
            // Smooth straight swipe: reward words whose keys are roughly collinear
            val wordCollinearity = measureWordCollinearity(word)
            val base = 72f + wordCollinearity * 22f
            return if (hasDoubleLetterSignal && wordHasDoubleLetters) min(100f, base + 9f) else base
        }

        // For each detected user corner inflection, find how close it lands to any interior character of `word`
        var totalCornerScore = 0f
        for (node in cornerNodes) {
            var minCharDist = Float.MAX_VALUE
            for (i in 1 until word.length - 1) {
                val ch = word[i]
                val geom = qwertyKeys[ch] ?: continue
                val drift = spatialProfiles[ch]
                val kx = geom.canonicalX + (drift?.driftX ?: 0f)
                val ky = geom.canonicalY + (drift?.driftY ?: 0f)
                val d = hypot(node.x - kx, node.y - ky)
                if (d < minCharDist) minCharDist = d
            }
            totalCornerScore += gaussianScore(minCharDist, 0.135f)
        }

        var score = 100f * (totalCornerScore / cornerNodes.size)
        if (hasDoubleLetterSignal && wordHasDoubleLetters) {
            score = min(100f, score + 11.5f)
        } else if (!hasDoubleLetterSignal && wordHasDoubleLetters) {
            // Slight penalty Only if a non-double-letter competitor is identical path (e.g. to vs too)
            score = max(40f, score - 3.5f)
        }
        return score.coerceIn(0f, 100f)
    }

    private fun measureWordCollinearity(word: String): Float {
        if (word.length <= 2) return 1f
        val first = qwertyKeys[word.first()] ?: return 0.5f
        val last = qwertyKeys[word.last()] ?: return 0.5f
        val dx = last.canonicalX - first.canonicalX
        val dy = last.canonicalY - first.canonicalY
        val segLen = hypot(dx, dy)
        if (segLen < 0.05f) return 0.3f

        var maxDeviation = 0f
        for (i in 1 until word.length - 1) {
            val g = qwertyKeys[word[i]] ?: continue
            // Perpendicular distance from point to line (first -> last)
            val dev = abs(dy * g.canonicalX - dx * g.canonicalY + last.canonicalX * first.canonicalY - last.canonicalY * first.canonicalX) / segLen
            if (dev > maxDeviation) maxDeviation = dev
        }
        return exp(-maxDeviation * 5.0f).coerceIn(0f, 1f)
    }

    /**
     * Generates a 32-point equidistant polygonal template along the key centers of `word`.
     */
    fun buildWordTemplate32(
        word: String,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        targetCount: Int = 32
    ): List<Pair<Float, Float>> {
        val vertices = mutableListOf<Pair<Float, Float>>()
        for (ch in word) {
            val geom = qwertyKeys[ch] ?: continue
            val drift = spatialProfiles[ch]
            val kx = (geom.canonicalX + (drift?.driftX ?: 0f)).coerceIn(0.02f, 0.98f)
            val ky = (geom.canonicalY + (drift?.driftY ?: 0f)).coerceIn(0.04f, 0.96f)
            if (vertices.isEmpty() || vertices.last() != (kx to ky)) {
                vertices.add(kx to ky)
            }
        }
        if (vertices.isEmpty()) return List(targetCount) { 0.5f to 0.5f }
        if (vertices.size == 1) return List(targetCount) { vertices.first() }
        return resamplePairsEquidistant(vertices, targetCount)
    }

    /**
     * Sakoe-Chiba band-constrained Dynamic Time Warping distance between 32-point user swipe
     * and 32-point word template.
     */
    private fun computeBandDtw(
        userPoints: List<NormalizedPoint>,
        templatePoints: List<Pair<Float, Float>>
    ): Float {
        val n = userPoints.size
        val m = templatePoints.size
        if (n == 0 || m == 0) return 1f
        val band = max(6, max(n, m) / 3)
        val dp = FloatArray((n + 1) * (m + 1)) { Float.POSITIVE_INFINITY }
        dp[0] = 0f

        for (i in 1..n) {
            val ux = userPoints[i - 1].x
            val uy = userPoints[i - 1].y
            val jStart = max(1, i - band)
            val jEnd = min(m, i + band)
            for (j in jStart..jEnd) {
                val (tx, ty) = templatePoints[j - 1]
                val cost = hypot(ux - tx, uy - ty)
                val prevMin = min(
                    dp[(i - 1) * (m + 1) + (j - 1)],
                    min(dp[(i - 1) * (m + 1) + j], dp[i * (m + 1) + (j - 1)])
                )
                dp[i * (m + 1) + j] = cost + prevMin
            }
        }
        return dp[n * (m + 1) + m] / (n + m).toFloat()
    }

    fun computePairDtw(
        pathA: List<Pair<Float, Float>>,
        pathB: List<Pair<Float, Float>>
    ): Float {
        val n = pathA.size
        val m = pathB.size
        if (n == 0 || m == 0) return 1f
        val dp = FloatArray((n + 1) * (m + 1)) { Float.POSITIVE_INFINITY }
        dp[0] = 0f
        for (i in 1..n) {
            val (ax, ay) = pathA[i - 1]
            for (j in 1..m) {
                val (bx, by) = pathB[j - 1]
                val cost = hypot(ax - bx, ay - by)
                val prev = min(
                    dp[(i - 1) * (m + 1) + (j - 1)],
                    min(dp[(i - 1) * (m + 1) + j], dp[i * (m + 1) + (j - 1)])
                )
                dp[i * (m + 1) + j] = cost + prev
            }
        }
        return dp[n * (m + 1) + m] / (n + m).toFloat()
    }

    /**
     * Savitzky-Golay / 3-tap weighted trajectory smoothing that preserves endpoints and sharp turns.
     */
    private fun smoothTrajectory(raw: List<RawTouchPoint>): List<RawTouchPoint> {
        if (raw.size <= 3) return raw
        // Filter duplicate points closer than 0.004 normalized distance
        val dedup = mutableListOf(raw.first())
        for (i in 1 until raw.size) {
            val p = raw[i]
            val prev = dedup.last()
            if (hypot(p.x - prev.x, p.y - prev.y) >= 0.004f || i == raw.lastIndex) {
                dedup.add(p)
            }
        }
        if (dedup.size <= 3) return dedup
        val out = mutableListOf<RawTouchPoint>()
        out.add(dedup.first())
        for (i in 1 until dedup.lastIndex) {
            val prev = dedup[i - 1]
            val curr = dedup[i]
            val next = dedup[i + 1]
            out.add(
                RawTouchPoint(
                    x = 0.2f * prev.x + 0.6f * curr.x + 0.2f * next.x,
                    y = 0.2f * prev.y + 0.6f * curr.y + 0.2f * next.y,
                    timestampMs = curr.timestampMs
                )
            )
        }
        out.add(dedup.last())
        return out
    }

    private fun resampleWithKinematics(
        points: List<RawTouchPoint>,
        targetCount: Int
    ): List<NormalizedPoint> {
        val pairs = points.map { it.x to it.y }
        val resampledCoords = resamplePairsEquidistant(pairs, targetCount)

        // Compute turning angle and local velocity along resampled nodes
        val velocities = FloatArray(targetCount)
        val angles = FloatArray(targetCount)

        for (i in 1 until targetCount - 1) {
            val (px, py) = resampledCoords[i - 1]
            val (cx, cy) = resampledCoords[i]
            val (nx, ny) = resampledCoords[i + 1]

            val v1x = cx - px
            val v1y = cy - py
            val v2x = nx - cx
            val v2y = ny - cy

            val mag1 = hypot(v1x, v1y)
            val mag2 = hypot(v2x, v2y)
            if (mag1 > 1e-4f && mag2 > 1e-4f) {
                val cosTheta = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1f, 1f)
                angles[i] = Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
            }

            // Map index back to raw timestamps to estimate local velocity
            val rawIdx = ((i.toFloat() / (targetCount - 1)) * (points.size - 1)).toInt().coerceIn(1, points.lastIndex)
            val dt = max(1L, points[rawIdx].timestampMs - points[rawIdx - 1].timestampMs)
            val ds = hypot(points[rawIdx].x - points[rawIdx - 1].x, points[rawIdx].y - points[rawIdx - 1].y)
            velocities[i] = (ds * 1000f) / dt.toFloat()
        }
        if (targetCount >= 2) {
            velocities[0] = velocities[1]
            velocities[targetCount - 1] = velocities[targetCount - 2]
        }

        val avgVelocity = velocities.average().toFloat().coerceAtLeast(0.1f)
        val result = mutableListOf<NormalizedPoint>()
        for (i in 0 until targetCount) {
            val isCorner = i in 2..(targetCount - 3) &&
                angles[i] >= 31f &&
                angles[i] >= angles[i - 1] &&
                angles[i] >= angles[i + 1]

            val isDwellOrLoop = i in 2..(targetCount - 3) &&
                (velocities[i] < avgVelocity * 0.36f || angles[i] >= 115f)

            result.add(
                NormalizedPoint(
                    x = resampledCoords[i].first,
                    y = resampledCoords[i].second,
                    progress = i.toFloat() / (targetCount - 1).coerceAtLeast(1),
                    velocity = velocities[i],
                    curvatureDeg = angles[i],
                    isInflection = (i == 0 || i == targetCount - 1 || isCorner),
                    isLoopOrDwell = isDwellOrLoop
                )
            )
        }
        return result
    }

    fun resampleSimplePoints(
        points: List<RawTouchPoint>,
        targetCount: Int
    ): List<Pair<Float, Float>> {
        return resamplePairsEquidistant(points.map { it.x to it.y }, targetCount)
    }

    private fun resamplePairsEquidistant(
        pts: List<Pair<Float, Float>>,
        targetCount: Int
    ): List<Pair<Float, Float>> {
        if (pts.isEmpty()) return List(targetCount) { 0.5f to 0.5f }
        if (pts.size == 1) return List(targetCount) { pts.first() }

        val cumulative = FloatArray(pts.size)
        var total = 0f
        for (i in 1 until pts.size) {
            total += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
            cumulative[i] = total
        }
        if (total < 1e-5f) return List(targetCount) { pts.first() }

        val out = ArrayList<Pair<Float, Float>>(targetCount)
        var segIdx = 0
        for (k in 0 until targetCount) {
            val targetDist = (k.toFloat() / (targetCount - 1)) * total
            while (segIdx < pts.size - 2 && cumulative[segIdx + 1] < targetDist) {
                segIdx++
            }
            val d0 = cumulative[segIdx]
            val d1 = cumulative[segIdx + 1]
            val t = if (d1 - d0 > 1e-6f) ((targetDist - d0) / (d1 - d0)).coerceIn(0f, 1f) else 0f
            val x = pts[segIdx].first + t * (pts[segIdx + 1].first - pts[segIdx].first)
            val y = pts[segIdx].second + t * (pts[segIdx + 1].second - pts[segIdx].second)
            out.add(x to y)
        }
        return out
    }

    private fun extractInflectionNodes(
        points: List<NormalizedPoint>,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        useAdapted: Boolean
    ): List<InflectionNode> {
        if (points.isEmpty()) return emptyList()
        val nodes = mutableListOf<InflectionNode>()
        val first = points.first()
        nodes.add(
            InflectionNode(
                index = 0,
                x = first.x,
                y = first.y,
                turningAngleDeg = 0f,
                nearestKey = findNearestKey(first.x, first.y, spatialProfiles, useAdapted),
                nodeType = InflectionNode.NodeType.START
            )
        )
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            if (p.isInflection) {
                nodes.add(
                    InflectionNode(
                        index = i,
                        x = p.x,
                        y = p.y,
                        turningAngleDeg = p.curvatureDeg,
                        nearestKey = findNearestKey(p.x, p.y, spatialProfiles, useAdapted),
                        nodeType = InflectionNode.NodeType.CORNER
                    )
                )
            } else if (p.isLoopOrDwell) {
                nodes.add(
                    InflectionNode(
                        index = i,
                        x = p.x,
                        y = p.y,
                        turningAngleDeg = p.curvatureDeg,
                        nearestKey = findNearestKey(p.x, p.y, spatialProfiles, useAdapted),
                        nodeType = InflectionNode.NodeType.DWELL_LOOP
                    )
                )
            }
        }
        val last = points.last()
        nodes.add(
            InflectionNode(
                index = points.lastIndex,
                x = last.x,
                y = last.y,
                turningAngleDeg = 0f,
                nearestKey = findNearestKey(last.x, last.y, spatialProfiles, useAdapted),
                nodeType = InflectionNode.NodeType.END
            )
        )
        return nodes
    }

    private fun gaussianScore(dist: Float, sigma: Float): Float {
        val ratio = dist / max(0.02f, sigma)
        return exp(-0.5f * ratio * ratio)
    }

    private fun computeArcLength(pts: List<RawTouchPoint>): Float {
        var sum = 0f
        for (i in 1 until pts.size) {
            sum += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        }
        return sum
    }

    private fun computePairArcLength(pts: List<Pair<Float, Float>>): Float {
        var sum = 0f
        for (i in 1 until pts.size) {
            sum += hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
        }
        return sum
    }

    private fun hasConsecutiveDuplicateLetters(word: String): Boolean {
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) return true
        }
        return false
    }

    fun encodePrototypePath(points: List<Pair<Float, Float>>): String {
        return points.joinToString(";") { (x, y) ->
            "${(x * 1000f).toInt() / 1000f},${(y * 1000f).toInt() / 1000f}"
        }
    }

    fun decodePrototypePath(encoded: String): List<Pair<Float, Float>> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) x to y else null
            } else null
        }
    }

    private fun emptyResult() = GestureDecodeResult(
        candidates = emptyList(),
        smoothedPath = emptyList(),
        prototype16Path = emptyList(),
        inflectionNodes = emptyList(),
        detectedDoubleLetterZone = false,
        durationMs = 0L,
        pathLengthNormalized = 0f,
        staticTopWord = "",
        adaptiveTopWord = "",
        wasPromotedByAdaptation = false
    )
}
