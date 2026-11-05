package com.example.engine

import com.example.data.GesturePrototypeEntity
import com.example.data.KeySpatialProfileEntity
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Online Supervised & Self-Supervised Learning Module that adapts the gesture keyboard
 * to the user's personal thumb arc, spatial key offsets, corner rounding, and swipe prototypes.
 */
class WritingStyleLearner(
    private val gestureEngine: GestureTypingEngine
) {

    /**
     * Given a confirmed target word and the user's actual gesture decode result, computes
     * updated KeySpatialProfileEntity entries by aligning the user's swipe trajectory
     * (start point, end point, and matched interior inflection anchors) against the
     * canonical QWERTY key centers of `committedWord`.
     */
    fun computeUpdatedSpatialProfiles(
        committedWord: String,
        decodeResult: GestureDecodeResult,
        currentProfiles: Map<Char, KeySpatialProfileEntity>,
        learningRateEta: Float,
        isExplicitUserCorrection: Boolean
    ): List<KeySpatialProfileEntity> {
        val cleanWord = committedWord.lowercase().filter { it in 'a'..'z' }
        val path = decodeResult.smoothedPath
        if (cleanWord.isEmpty() || path.isEmpty()) return emptyList()

        // Boost learning rate when the user explicitly corrected a candidate chip
        val effectiveEta = if (isExplicitUserCorrection) {
            (learningRateEta * 1.45f).coerceIn(0.05f, 0.40f)
        } else {
            learningRateEta.coerceIn(0.04f, 0.30f)
        }

        val updates = mutableMapOf<Char, KeySpatialProfileEntity>()

        fun applyKeyObservation(ch: Char, observedX: Float, observedY: Float, cornerAngle: Float) {
            val geom = gestureEngine.qwertyKeys[ch] ?: return
            val rawDx = (observedX - geom.canonicalX).coerceIn(-0.085f, 0.085f)
            val rawDy = (observedY - geom.canonicalY).coerceIn(-0.085f, 0.085f)

            // Ignore wild outliers where the user clearly didn't pass near this key
            if (hypot(rawDx, rawDy) > 0.22f) return

            val existing = updates[ch] ?: currentProfiles[ch] ?: KeySpatialProfileEntity(keyChar = ch.toString())
            val newDriftX = ((1f - effectiveEta) * existing.driftX + effectiveEta * rawDx)
                .coerceIn(-0.065f, 0.065f)
            val newDriftY = ((1f - effectiveEta) * existing.driftY + effectiveEta * rawDy)
                .coerceIn(-0.065f, 0.065f)

            val residual = hypot(rawDx - newDriftX, rawDy - newDriftY)
            val targetRadiusScale = (0.92f + residual * 2.2f).coerceIn(0.85f, 1.32f)
            val newRadiusScale = 0.85f * existing.radiusScale + 0.15f * targetRadiusScale
            val newSharpness = if (cornerAngle > 10f) {
                0.8f * existing.avgCornerSharpness + 0.2f * (cornerAngle / 120f).coerceIn(0.1f, 1f)
            } else {
                existing.avgCornerSharpness
            }

            updates[ch] = existing.copy(
                driftX = newDriftX,
                driftY = newDriftY,
                radiusScale = newRadiusScale,
                hitCount = existing.hitCount + 1,
                avgCornerSharpness = newSharpness
            )
        }

        // 1. Align Start Key
        val firstChar = cleanWord.first()
        val firstPt = path.first()
        applyKeyObservation(firstChar, firstPt.x, firstPt.y, 0f)

        // 2. Align End Key
        if (cleanWord.length > 1) {
            val lastChar = cleanWord.last()
            val lastPt = path.last()
            applyKeyObservation(lastChar, lastPt.x, lastPt.y, 0f)
        }

        // 3. Align interior characters that correspond to trajectory corners or local minima
        if (cleanWord.length >= 3 && path.size >= 8) {
            for (i in 1 until cleanWord.length - 1) {
                val ch = cleanWord[i]
                val geom = gestureEngine.qwertyKeys[ch] ?: continue
                // Expected progress window along the swipe path
                val expectedProgress = i.toFloat() / (cleanWord.length - 1).toFloat()
                val minIdx = max(1, ((expectedProgress - 0.28f) * path.lastIndex).toInt())
                val maxIdx = min(path.lastIndex - 1, ((expectedProgress + 0.28f) * path.lastIndex).toInt())

                var bestIdx = -1
                var bestDist = Float.MAX_VALUE
                for (idx in minIdx..maxIdx) {
                    val pt = path[idx]
                    val d = hypot(pt.x - geom.canonicalX, pt.y - geom.canonicalY)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = idx
                    }
                }
                if (bestIdx != -1 && bestDist < 0.17f) {
                    val matchedPt = path[bestIdx]
                    applyKeyObservation(ch, matchedPt.x, matchedPt.y, matchedPt.curvatureDeg)
                }
            }
        }

        return updates.values.toList()
    }

    /**
     * Blends the current 16-point swipe trajectory into the user's personal prototype
     * for `committedWord` using Exponential Moving Average (EMA).
     */
    fun computeUpdatedPrototype(
        committedWord: String,
        current16Path: List<Pair<Float, Float>>,
        durationMs: Long,
        existingPrototype: GesturePrototypeEntity?,
        learningRateEta: Float
    ): GesturePrototypeEntity? {
        val cleanWord = committedWord.lowercase().filter { it in 'a'..'z' }
        if (cleanWord.length < 2 || current16Path.size != 16) return null

        if (existingPrototype == null) {
            return GesturePrototypeEntity(
                word = cleanWord,
                encodedNormalizedPath = gestureEngine.encodePrototypePath(current16Path),
                sampleCount = 1,
                avgDurationMs = durationMs,
                lastUpdated = System.currentTimeMillis()
            )
        }

        val oldPoints = gestureEngine.decodePrototypePath(existingPrototype.encodedNormalizedPath)
        if (oldPoints.size != 16) {
            return existingPrototype.copy(
                encodedNormalizedPath = gestureEngine.encodePrototypePath(current16Path),
                sampleCount = existingPrototype.sampleCount + 1,
                lastUpdated = System.currentTimeMillis()
            )
        }

        val alpha = learningRateEta.coerceIn(0.10f, 0.35f)
        val blendedPoints = oldPoints.indices.map { idx ->
            val (ox, oy) = oldPoints[idx]
            val (nx, ny) = current16Path[idx]
            ((1f - alpha) * ox + alpha * nx) to ((1f - alpha) * oy + alpha * ny)
        }

        return existingPrototype.copy(
            encodedNormalizedPath = gestureEngine.encodePrototypePath(blendedPoints),
            sampleCount = existingPrototype.sampleCount + 1,
            avgDurationMs = ((existingPrototype.avgDurationMs * 3 + durationMs) / 4),
            lastUpdated = System.currentTimeMillis()
        )
    }
}
