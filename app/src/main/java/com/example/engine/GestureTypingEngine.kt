package com.example.engine

import com.example.data.GesturePrototypeEntity
import com.example.data.KeySpatialProfileEntity
import com.example.data.UserBigramEntity
import com.example.data.UserWordFrequencyEntity
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * High-accuracy Gesture Typing Engine modeled on FUTO Keyboard / LatinIME:
 * 1. Exact LatinIME QWERTY key coordinates (Row 1: 10 keys, Row 2: 9 keys, Row 3: 7 keys between Shift & Backspace).
 * 2. Savitzky-Golay smoothing + 32-point equidistant resampling.
 * 3. Monotonic Sequence Alignment: enforces that all characters of a candidate word appear in
 *    chronological arc-length order along the swipe path.
 * 4. Corner Inflection Verification: ensures sharp turns in the user's swipe align with interior word keys,
 *    and penalizes candidate words that require a sharp turn not present in the user's swipe.
 * 5. Strong Common-Word Prior + Previously-Used Word & Bigram Priority: words the user has already
 *    used (and common everyday words) are strongly prioritized over obscure candidates.
 */
class GestureTypingEngine {

    val qwertyKeys: Map<Char, KeyGeometry> = buildLatinImeQwertyGeometry()

    /**
     * Builds exact normalized coordinates [0f..1f] for the 3-row letter grid:
     * - Row 0: q w e r t y u i o p (10 keys across [0.0 .. 1.0])
     * - Row 1: a s d f g h j k l   (9 keys centered across [0.05 .. 0.95])
     * - Row 2: z x c v b n m       (7 keys centered between Shift [0.0..0.15] and Backspace [0.85..1.0])
     */
    private fun buildLatinImeQwertyGeometry(): Map<Char, KeyGeometry> {
        val rows = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
        )
        val rowY = listOf(0.1667f, 0.50f, 0.8333f)
        val rowStartX = listOf(0.0f, 0.05f, 0.15f)
        val keyWidth = 0.10f
        val keyHeight = 0.3333f

        val map = mutableMapOf<Char, KeyGeometry>()
        for (r in rows.indices) {
            val rowStr = rows[r]
            val startX = rowStartX[r]
            for (c in rowStr.indices) {
                val ch = rowStr[c]
                val cx = startX + (c + 0.5f) * keyWidth
                val cy = rowY[r]
                map[ch] = KeyGeometry(
                    char = ch,
                    row = r,
                    col = c,
                    canonicalX = cx,
                    canonicalY = cy,
                    width = keyWidth,
                    height = keyHeight
                )
            }
        }
        return map
    }

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
     * Decodes a continuous swipe trajectory into ranked word candidates.
     * Heavily prioritizes:
     * 1. Accurate start/end + monotonic interior key alignment + corner turning points
     * 2. Words the user has already used (`userWords` + `userBigrams` + `userPrototypes`)
     * 3. High-frequency common words over rare dictionary words
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
        if (rawPoints.isEmpty()) return emptyResult()

        val durationMs = max(1L, rawPoints.last().timestampMs - rawPoints.first().timestampMs)
        val totalArcLength = computeArcLength(rawPoints)

        // Short tap fallback
        if (rawPoints.size < 3 || totalArcLength < 0.045f) {
            val pt = rawPoints.first()
            val ch = findNearestKey(pt.x, pt.y, spatialProfiles, hyperparams.enablePersonalDrift)
            val tapCandidates = FutoLexicon.getPrefixAndTypoCandidates(
                composingPrefix = ch.toString(),
                prevContextWord = prevContextWord,
                userWords = userWords,
                userBigrams = userBigrams,
                keyGeometries = qwertyKeys
            )
            return GestureDecodeResult(
                candidates = tapCandidates,
                smoothedPath = listOf(NormalizedPoint(pt.x, pt.y, 0f, 0f, 0f, true, false)),
                prototype16Path = List(16) { pt.x to pt.y },
                inflectionNodes = listOf(InflectionNode(0, pt.x, pt.y, 0f, ch, InflectionNode.NodeType.START)),
                detectedDoubleLetterZone = false,
                durationMs = durationMs,
                pathLengthNormalized = totalArcLength,
                staticTopWord = tapCandidates.firstOrNull()?.word.orEmpty(),
                adaptiveTopWord = tapCandidates.firstOrNull()?.word.orEmpty(),
                wasPromotedByAdaptation = false
            )
        }

        // 1. Smooth jitter and resample into 32 equidistant points + 16 prototype points
        val smoothedRaw = smoothTrajectory(rawPoints)
        val resampled32 = resampleWithKinematics(smoothedRaw, 32)
        val prototype16 = resampleSimplePoints(smoothedRaw, 16)

        // 2. Extract Corner Inflections & Dwell/Loop Zones
        val inflectionNodes = extractInflectionNodes(
            points = resampled32,
            spatialProfiles = spatialProfiles,
            useAdapted = hyperparams.enablePersonalDrift
        )
        val hasDoubleLetterSignal = resampled32.any { it.isLoopOrDwell }

        // 3. Build lexicon combining built-in common words and user's personal words
        val combinedLexicon = HashMap<String, Int>(FutoLexicon.defaultWordsWithFreq.size + userWords.size)
        combinedLexicon.putAll(FutoLexicon.defaultWordsWithFreq)
        for ((w, entity) in userWords) {
            val clean = w.lowercase().filter { it in 'a'..'z' }
            if (clean.length >= 2) {
                combinedLexicon[clean] = max(combinedLexicon[clean] ?: 195, entity.baseFrequency)
            }
        }

        val startPt = resampled32.first()
        val endPt = resampled32.last()
        val prevNorm = prevContextWord.trim().lowercase().filter { it in 'a'..'z' }

        val candidateList = ArrayList<GestureCandidate>(64)

        for ((word, baseFreq) in combinedLexicon) {
            if (word.length < 2) continue
            val firstChar = word.first()
            val lastChar = word.last()
            val firstGeom = qwertyKeys[firstChar] ?: continue
            val lastGeom = qwertyKeys[lastChar] ?: continue

            val firstDrift = if (hyperparams.enablePersonalDrift) spatialProfiles[firstChar] else null
            val lastDrift = if (hyperparams.enablePersonalDrift) spatialProfiles[lastChar] else null

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

            // Fast endpoint pruning: start and end of swipe must be within ~2.2 key widths of word's first/last letter
            if (min(staticStartDist, adaptedStartDist) > 0.24f ||
                min(staticEndDist, adaptedEndDist) > 0.25f
            ) {
                continue
            }

            // Dedup consecutive duplicate letters for spatial path geometry (e.g. "hello" -> "helo")
            val compressedChars = compressConsecutiveDuplicates(word)
            val wordHasDoubleLetters = compressedChars.length < word.length

            // 4. Monotonic Sequence Alignment Score (verifies letters appear in order along the swipe!)
            val staticMonotonicDist = computeMonotonicKeyAlignment(compressedChars, resampled32, emptyMap())
            val adaptedMonotonicDist = if (hyperparams.enablePersonalDrift) {
                computeMonotonicKeyAlignment(compressedChars, resampled32, spatialProfiles)
            } else {
                staticMonotonicDist
            }

            // Reject candidates whose interior letters are far from the trajectory
            if (min(staticMonotonicDist, adaptedMonotonicDist) > 0.19f) continue

            // 5. Ideal 32-point Polygonal Template & Band-Constrained DTW
            val staticTemplate = buildWordTemplate32(compressedChars, emptyMap())
            val adaptedTemplate = if (hyperparams.enablePersonalDrift) {
                buildWordTemplate32(compressedChars, spatialProfiles)
            } else {
                staticTemplate
            }

            val idealArcLen = computePairArcLength(adaptedTemplate)
            // Reject words whose ideal path length is wildly different from the user's swipe length
            val lengthRatio = if (idealArcLen > 0.05f) totalArcLength / idealArcLen else 1f
            if (lengthRatio < 0.38f || lengthRatio > 2.35f) continue

            val staticDtwDist = computeBandDtw(resampled32, staticTemplate)
            val adaptedDtwDist = computeBandDtw(resampled32, adaptedTemplate)

            // Start & End Gaussian Anchor Score (start key weighted slightly higher as users aim first key carefully)
            val sigmaStart = 0.082f
            val sigmaEnd = 0.092f
            val staticSpatial = 92f * (
                0.56f * gaussianScore(staticStartDist, sigmaStart) +
                    0.44f * gaussianScore(staticEndDist, sigmaEnd)
                )
            val adaptedSpatial = 92f * (
                0.56f * gaussianScore(adaptedStartDist, sigmaStart * (firstDrift?.radiusScale ?: 1f)) +
                    0.44f * gaussianScore(adaptedEndDist, sigmaEnd * (lastDrift?.radiusScale ?: 1f))
                )

            // Combine DTW curve distance + Monotonic key pass-through distance + Arc-length consistency
            val lenDiff = abs(totalArcLength - idealArcLen) / max(0.16f, idealArcLen)
            val lengthPenalty = (1f - 0.30f * lenDiff.coerceIn(0f, 1.4f)).coerceIn(0.55f, 1f)

            val staticCombinedCurveDist = 0.55f * staticDtwDist + 0.45f * staticMonotonicDist
            val adaptedCombinedCurveDist = 0.55f * adaptedDtwDist + 0.45f * adaptedMonotonicDist

            val staticDtwScore = (92f * exp(-staticCombinedCurveDist * 14.8f) * lengthPenalty).coerceIn(0f, 92f)
            val adaptedDtwScore = (92f * exp(-adaptedCombinedCurveDist * 14.8f) * lengthPenalty).coerceIn(0f, 92f)

            // 6. Bidirectional Corner Inflection & Double-Letter Verification
            val inflectionScore = computeBidirectionalInflectionScore(
                compressedWord = compressedChars,
                inflectionNodes = inflectionNodes,
                spatialProfiles = if (hyperparams.enablePersonalDrift) spatialProfiles else emptyMap(),
                hasDoubleLetterSignal = hasDoubleLetterSignal && hyperparams.enableDoubleLetterDetection,
                wordHasDoubleLetters = wordHasDoubleLetters
            )

            // 7. Common-Word Frequency Prior (strong differentiation between everyday words and rare words)
            // Tier 1 (230..255) -> +5.5..+8.0 pts; Tier 2 (200..229) -> +2.5..+5.4 pts; Rare (<190) -> 0 pts
            val commonWordPrior = ((baseFreq - 175).coerceIn(0, 80) * 0.10f)

            val baselineTotal = (
                staticSpatial * 0.38f +
                    staticDtwScore * 0.44f +
                    inflectionScore * 0.18f +
                    commonWordPrior
                ).coerceIn(1f, 95.0f)

            // 8. Previously-Used Word Priority & Contextual Bigram Boost
            val userWordEntry = userWords[word]
            val usedWordBoost = if (userWordEntry != null && (userWordEntry.personalUseCount > 0 || userWordEntry.isCustomWord)) {
                // Logarithmic + linear boost so words the user has already used get strong priority!
                val countBoost = 4.2f * ln(1f + userWordEntry.personalUseCount.toFloat()) +
                    min(6.0f, userWordEntry.personalUseCount * 0.9f)
                val correctionBoost = min(7.0f, userWordEntry.correctionSelectCount * 2.5f)
                val customBoost = if (userWordEntry.isCustomWord) 4.0f else 0f
                min(15.0f, countBoost + correctionBoost + customBoost)
            } else {
                0f
            }

            var bigramBoost = 0f
            if (hyperparams.enableContextBigrams && prevNorm.isNotEmpty()) {
                val learnedTrans = userBigrams[prevNorm to word]?.transitionCount ?: 0
                val starterTrans = FutoLexicon.starterBigrams[prevNorm to word] ?: 0
                if (learnedTrans > 0 || starterTrans > 0) {
                    val rawBoost = learnedTrans * 3.8f + starterTrans * 1.6f
                    bigramBoost = min(11.0f, rawBoost) * (hyperparams.contextBigramWeight / 0.12f)
                }
            }

            // 9. Personal 16-Point Gesture Prototype Similarity Boost
            var prototypeBoost = 0f
            if (hyperparams.enablePersonalPrototypes) {
                val protoEntity = userPrototypes[word]
                if (protoEntity != null) {
                    val protoPoints = decodePrototypePath(protoEntity.encodedNormalizedPath)
                    if (protoPoints.size == prototype16.size) {
                        val protoDist = computePairDtw(prototype16, protoPoints)
                        val protoSim = exp(-protoDist * 14.5f)
                        if (protoSim > 0.50f) {
                            val maturity = min(1f, protoEntity.sampleCount / 2f)
                            prototypeBoost = (protoSim - 0.45f) * 11f * maturity
                        }
                    }
                }
            }

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
                commonWordPrior

            val spatialDriftGain = max(
                0f,
                (adaptedSpatial - staticSpatial) * wSpatial + (adaptedDtwScore - staticDtwScore) * wDtw
            )
            val totalPersonalBoost = spatialDriftGain + usedWordBoost + bigramBoost + prototypeBoost

            val adaptiveTotal = (coreAdaptive + usedWordBoost + bigramBoost + prototypeBoost)
                .coerceIn(1f, 99.8f)

            val badge = when {
                usedWordBoost >= 3.5f && bigramBoost >= 2.0f -> "Used + Context"
                usedWordBoost >= 3.0f -> "Used (${userWordEntry?.personalUseCount ?: 1}x)"
                bigramBoost >= 2.5f -> "Context"
                prototypeBoost >= 2.0f -> "Learned Path"
                else -> ""
            }

            candidateList.add(
                GestureCandidate(
                    word = word,
                    adaptiveScore = adaptiveTotal,
                    baselineStaticScore = baselineTotal,
                    spatialAnchorScore = adaptedSpatial,
                    dtwShapeScore = adaptedDtwScore,
                    inflectionMatchScore = inflectionScore,
                    contextBigramBoost = bigramBoost,
                    personalStyleBoost = totalPersonalBoost,
                    explanationBadge = badge
                )
            )
        }

        val sortedByAdaptive = candidateList.sortedByDescending { it.adaptiveScore }.take(8)
        val sortedByStatic = candidateList.sortedByDescending { it.baselineStaticScore }

        val staticTop = sortedByStatic.firstOrNull()?.word.orEmpty()
        val adaptiveTop = sortedByAdaptive.firstOrNull()?.word.orEmpty()

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

    /**
     * Dynamic Programming Monotonic Key Alignment:
     * Finds the minimum average Euclidean distance from each character in `compressedWord`
     * to a non-decreasing sequence of trajectory points `0 = idx_0 <= idx_1 <= ... <= idx_{k-1} = N-1`.
     * This guarantees the user's finger visited the word's letters in the right chronological order!
     */
    private fun computeMonotonicKeyAlignment(
        compressedWord: String,
        trajectory: List<NormalizedPoint>,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>
    ): Float {
        val k = compressedWord.length
        val n = trajectory.size
        if (k == 0 || n == 0) return 0f
        if (k == 1) {
            val g = qwertyKeys[compressedWord[0]] ?: return 0.1f
            val drift = spatialProfiles[compressedWord[0]]
            val kx = g.canonicalX + (drift?.driftX ?: 0f)
            val ky = g.canonicalY + (drift?.driftY ?: 0f)
            return trajectory.minOf { hypot(it.x - kx, it.y - ky) }
        }

        val dp = FloatArray(k * n) { Float.POSITIVE_INFINITY }

        // First character anchored at start of swipe (or first 3 samples)
        val g0 = qwertyKeys[compressedWord[0]] ?: return 0.2f
        val d0 = spatialProfiles[compressedWord[0]]
        val k0x = g0.canonicalX + (d0?.driftX ?: 0f)
        val k0y = g0.canonicalY + (d0?.driftY ?: 0f)
        for (j in 0 until min(4, n)) {
            dp[j] = hypot(trajectory[j].x - k0x, trajectory[j].y - k0y)
        }

        for (i in 1 until k) {
            val ch = compressedWord[i]
            val geom = qwertyKeys[ch] ?: continue
            val drift = spatialProfiles[ch]
            val kx = geom.canonicalX + (drift?.driftX ?: 0f)
            val ky = geom.canonicalY + (drift?.driftY ?: 0f)

            var runningMinPrev = dp[(i - 1) * n]
            for (j in 0 until n) {
                if (dp[(i - 1) * n + j] < runningMinPrev) {
                    runningMinPrev = dp[(i - 1) * n + j]
                }
                val cost = hypot(trajectory[j].x - kx, trajectory[j].y - ky)
                dp[i * n + j] = runningMinPrev + cost
            }
        }

        // Last character must align near the end of the trajectory
        var bestTotal = Float.POSITIVE_INFINITY
        for (j in max(0, n - 4) until n) {
            bestTotal = min(bestTotal, dp[(k - 1) * n + j])
        }
        return bestTotal / k.toFloat()
    }

    /**
     * Bidirectional Corner Inflection Scoring:
     * 1. Every sharp corner turn made by the user's finger should be close to one of the word's keys.
     * 2. Every sharp turn required by the word's key sequence should be close to the user's trajectory.
     */
    private fun computeBidirectionalInflectionScore(
        compressedWord: String,
        inflectionNodes: List<InflectionNode>,
        spatialProfiles: Map<Char, KeySpatialProfileEntity>,
        hasDoubleLetterSignal: Boolean,
        wordHasDoubleLetters: Boolean
    ): Float {
        val userCorners = inflectionNodes.filter { it.nodeType == InflectionNode.NodeType.CORNER }

        // Part A: Do user corners land near characters of the word?
        var userCornerMatch = 1f
        if (userCorners.isNotEmpty() && compressedWord.length >= 2) {
            var sum = 0f
            for (node in userCorners) {
                var minCharDist = Float.MAX_VALUE
                for (ch in compressedWord) {
                    val geom = qwertyKeys[ch] ?: continue
                    val drift = spatialProfiles[ch]
                    val kx = geom.canonicalX + (drift?.driftX ?: 0f)
                    val ky = geom.canonicalY + (drift?.driftY ?: 0f)
                    val d = hypot(node.x - kx, node.y - ky)
                    if (d < minCharDist) minCharDist = d
                }
                sum += gaussianScore(minCharDist, 0.125f)
            }
            userCornerMatch = sum / userCorners.size
        }

        // Part B: Count how many sharp turns the word template itself has
        var wordCornerCount = 0
        if (compressedWord.length >= 3) {
            for (i in 1 until compressedWord.length - 1) {
                val p = qwertyKeys[compressedWord[i - 1]] ?: continue
                val c = qwertyKeys[compressedWord[i]] ?: continue
                val n = qwertyKeys[compressedWord[i + 1]] ?: continue
                val v1x = c.canonicalX - p.canonicalX
                val v1y = c.canonicalY - p.canonicalY
                val v2x = n.canonicalX - c.canonicalX
                val v2y = n.canonicalY - c.canonicalY
                val m1 = hypot(v1x, v1y)
                val m2 = hypot(v2x, v2y)
                if (m1 > 1e-3f && m2 > 1e-3f) {
                    val cosA = ((v1x * v2x + v1y * v2y) / (m1 * m2)).coerceIn(-1f, 1f)
                    val deg = Math.toDegrees(acos(cosA.toDouble())).toFloat()
                    if (deg >= 42f) wordCornerCount++
                }
            }
        }

        val cornerCountDiff = abs(userCorners.size - wordCornerCount)
        val countAgreement = exp(-cornerCountDiff * 0.22f)

        var score = 92f * (0.68f * userCornerMatch + 0.32f * countAgreement)
        if (hasDoubleLetterSignal && wordHasDoubleLetters) {
            score = min(98f, score + 8f)
        } else if (!hasDoubleLetterSignal && wordHasDoubleLetters) {
            score = max(45f, score - 2.5f)
        }
        return score.coerceIn(0f, 98f)
    }

    private fun compressConsecutiveDuplicates(word: String): String {
        if (word.length <= 1) return word
        val sb = StringBuilder(word.length)
        sb.append(word[0])
        for (i in 1 until word.length) {
            if (word[i] != word[i - 1]) sb.append(word[i])
        }
        return sb.toString()
    }

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

    private fun smoothTrajectory(raw: List<RawTouchPoint>): List<RawTouchPoint> {
        if (raw.size <= 3) return raw
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
                angles[i] >= 32f &&
                angles[i] >= angles[i - 1] &&
                angles[i] >= angles[i + 1]

            val isDwellOrLoop = i in 2..(targetCount - 3) &&
                (velocities[i] < avgVelocity * 0.35f || angles[i] >= 115f)

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
