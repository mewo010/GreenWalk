package com.example.logic

import com.example.model.DeparturePlan
import com.example.model.ManualConfig
import com.example.model.MeasurementEvent
import com.example.model.MeasurementType
import com.example.model.SyncAnalysis
import com.example.model.TrafficLightAnalysis
import com.example.model.TrafficLightId
import com.example.model.WalkingSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TrafficCalculationEngine {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun formatEpochTime(epochMs: Long): String {
        return timeFormat.format(Date(epochMs))
    }

    fun formatShortTime(epochMs: Long): String {
        return shortTimeFormat.format(Date(epochMs))
    }

    fun formatSecondsToMinutes(totalSeconds: Long): String {
        val safeSeconds = max(0L, totalSeconds)
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /**
     * Analyzes measurements for a specific traffic light.
     */
    fun analyzeLight(
        measurements: List<MeasurementEvent>,
        lightId: TrafficLightId,
        currentTimeMs: Long,
        manualConfig: ManualConfig
    ): TrafficLightAnalysis {
        // If manual config is enabled, use fixed parameters
        if (manualConfig.isEnabled) {
            val cycleSec = if (lightId == TrafficLightId.LIGHT_1) {
                manualConfig.light1CycleSeconds.toDouble()
            } else {
                manualConfig.light2CycleSeconds.toDouble()
            }
            val greenSec = if (lightId == TrafficLightId.LIGHT_1) {
                manualConfig.light1GreenDurationSeconds.toDouble()
            } else {
                manualConfig.light2GreenDurationSeconds.toDouble()
            }
            val offsetSec = if (lightId == TrafficLightId.LIGHT_1) {
                manualConfig.light1OffsetSeconds.toLong()
            } else {
                manualConfig.light2OffsetSeconds.toLong()
            }

            val cycleMs = max(5000L, (cycleSec * 1000).toLong())
            val greenMs = max(2000L, min(cycleMs - 2000L, (greenSec * 1000).toLong()))

            // Base reference anchored at start of today + offset
            val baseTodayMs = getStartOfDayMs(currentTimeMs) + (offsetSec * 1000L)
            val elapsed = currentTimeMs - baseTodayMs
            val posInCycle = ((elapsed % cycleMs) + cycleMs) % cycleMs

            val isGreen = posInCycle < greenMs
            val remainingGreenSec = if (isGreen) (greenMs - posInCycle) / 1000.0 else null
            val timeToNextGreenSec = if (isGreen) 0.0 else (cycleMs - posInCycle) / 1000.0

            val nextGreenMs = if (isGreen) {
                currentTimeMs + (cycleMs - posInCycle)
            } else {
                currentTimeMs + (cycleMs - posInCycle)
            }

            return TrafficLightAnalysis(
                lightId = lightId,
                cycleDurationSeconds = cycleSec,
                greenDurationSeconds = greenSec,
                cycleCount = 10,
                confidencePercent = 100,
                lastGreenEpochMs = currentTimeMs - posInCycle,
                nextGreenEpochMs = nextGreenMs,
                isCurrentlyGreen = isGreen,
                remainingGreenSeconds = remainingGreenSec,
                timeUntilNextGreenSeconds = timeToNextGreenSec,
                statusText = if (isGreen) "ירוק כעת (הזנה ידנית)" else "אדום כעת (הזנה ידנית)",
                cycleStdDev = 0.0
            )
        }

        // Real measurement analysis
        val lightEvents = measurements
            .filter { it.lightId == lightId }
            .sortedBy { it.timestamp }

        val greenEvents = lightEvents.filter { it.type == MeasurementType.GREEN_START }
        val redEvents = lightEvents.filter { it.type == MeasurementType.RED_START }

        if (greenEvents.isEmpty()) {
            return TrafficLightAnalysis(
                lightId = lightId,
                statusText = "אין מדידות לרמזור זה"
            )
        }

        val lastGreenEvent = greenEvents.last()

        if (greenEvents.size == 1) {
            val elapsedSec = (currentTimeMs - lastGreenEvent.timestamp) / 1000.0
            return TrafficLightAnalysis(
                lightId = lightId,
                cycleDurationSeconds = null,
                greenDurationSeconds = 20.0,
                cycleCount = 1,
                confidencePercent = 25,
                lastGreenEpochMs = lastGreenEvent.timestamp,
                nextGreenEpochMs = null,
                isCurrentlyGreen = elapsedSec < 20.0,
                remainingGreenSeconds = if (elapsedSec < 20.0) max(0.0, 20.0 - elapsedSec) else null,
                timeUntilNextGreenSeconds = null,
                statusText = "נמדדה פעם אחת - דרושה מדידה נוספת לחישוב מחזור",
                cycleStdDev = 0.0
            )
        }

        // Calculate intervals between consecutive green onsets
        val rawIntervalsSec = mutableListOf<Double>()
        for (i in 0 until greenEvents.size - 1) {
            val deltaMs = greenEvents[i + 1].timestamp - greenEvents[i].timestamp
            if (deltaMs > 5000) { // filter out accidental micro-taps
                rawIntervalsSec.add(deltaMs / 1000.0)
            }
        }

        if (rawIntervalsSec.isEmpty()) {
            return TrafficLightAnalysis(
                lightId = lightId,
                cycleCount = greenEvents.size,
                statusText = "מדידות קרובות מדי - נסה שוב"
            )
        }

        // Normalize intervals if a cycle was missed (e.g. interval is ~2x or ~3x minimum)
        val minInterval = rawIntervalsSec.minOrNull() ?: 60.0
        val normalizedIntervals = rawIntervalsSec.map { interval ->
            val ratio = (interval / minInterval).roundToInt()
            if (ratio in 2..4 && abs(interval - (ratio * minInterval)) < (minInterval * 0.35)) {
                interval / ratio
            } else {
                interval
            }
        }

        val meanCycleSec = normalizedIntervals.average()
        val variance = normalizedIntervals.map { (it - meanCycleSec) * (it - meanCycleSec) }.average()
        val stdDev = sqrt(variance)

        // Consistency score
        val relDeviation = if (meanCycleSec > 0) stdDev / meanCycleSec else 1.0
        val consistencyFactor = max(0.0, 1.0 - relDeviation)
        val countFactor = min(1.0, normalizedIntervals.size / 3.0)
        val confidencePercent = ((consistencyFactor * 0.7 + countFactor * 0.3) * 100.0).roundToInt().coerceIn(10, 99)

        // Calculate learned green duration from red events if available
        var learnedGreenDurationSec = 20.0
        val pairedGreenDurations = mutableListOf<Double>()
        for (green in greenEvents) {
            val nextRed = redEvents.firstOrNull { it.timestamp > green.timestamp }
            if (nextRed != null) {
                val nextGreen = greenEvents.firstOrNull { it.timestamp > green.timestamp }
                if (nextGreen == null || nextRed.timestamp < nextGreen.timestamp) {
                    val durationMs = nextRed.timestamp - green.timestamp
                    if (durationMs in 3000..90000) {
                        pairedGreenDurations.add(durationMs / 1000.0)
                    }
                }
            }
        }
        if (pairedGreenDurations.isNotEmpty()) {
            learnedGreenDurationSec = pairedGreenDurations.average()
        }

        val cycleMs = max(10000L, (meanCycleSec * 1000).toLong())
        val greenMs = max(3000L, min(cycleMs - 3000L, (learnedGreenDurationSec * 1000).toLong()))

        val elapsedSinceLastGreen = currentTimeMs - lastGreenEvent.timestamp
        val posInCycle = ((elapsedSinceLastGreen % cycleMs) + cycleMs) % cycleMs

        val isGreen = posInCycle < greenMs
        val remainingGreenSec = if (isGreen) (greenMs - posInCycle) / 1000.0 else null
        val timeToNextGreenSec = if (isGreen) (cycleMs - posInCycle) / 1000.0 else (cycleMs - posInCycle) / 1000.0
        val nextGreenEpochMs = currentTimeMs + (cycleMs - posInCycle)

        val statusText = when {
            isGreen -> "ירוק כעת! נותרו כ-${remainingGreenSec?.roundToInt() ?: 0} שניות"
            timeToNextGreenSec < 10 -> "עומד להתחלף לירוק בקרוב (${timeToNextGreenSec.roundToInt()} שניות)"
            else -> "אדום כעת (${timeToNextGreenSec.roundToInt()} שניות לירוק הבא)"
        }

        return TrafficLightAnalysis(
            lightId = lightId,
            cycleDurationSeconds = meanCycleSec,
            greenDurationSeconds = learnedGreenDurationSec,
            cycleCount = greenEvents.size,
            confidencePercent = confidencePercent,
            lastGreenEpochMs = lastGreenEvent.timestamp,
            nextGreenEpochMs = nextGreenEpochMs,
            isCurrentlyGreen = isGreen,
            remainingGreenSeconds = remainingGreenSec,
            timeUntilNextGreenSeconds = timeToNextGreenSec,
            statusText = statusText,
            cycleStdDev = stdDev
        )
    }

    /**
     * Calculates the offset between Light 1 and Light 2.
     */
    fun analyzeSync(
        light1: TrafficLightAnalysis,
        light2: TrafficLightAnalysis,
        walkingSettings: WalkingSettings
    ): SyncAnalysis {
        val l1Last = light1.lastGreenEpochMs
        val l2Last = light2.lastGreenEpochMs
        val cycle1 = light1.cycleDurationSeconds
        val cycle2 = light2.cycleDurationSeconds

        if (l1Last == null || l2Last == null || cycle1 == null || cycle2 == null) {
            return SyncAnalysis(
                offsetSeconds = null,
                walkTimeSeconds = walkingSettings.walkBetweenLightsSeconds,
                isSynchronized = false,
                statusDescription = "נדרשות מדידות של שני הרמזורים לחישוב סנכרון",
                statusBadge = "נתונים חסרים"
            )
        }

        val avgCycle = (cycle1 + cycle2) / 2.0
        val cycleDiff = abs(cycle1 - cycle2)
        if (cycleDiff > avgCycle * 0.35) {
            return SyncAnalysis(
                offsetSeconds = null,
                walkTimeSeconds = walkingSettings.walkBetweenLightsSeconds,
                isSynchronized = false,
                statusDescription = "מחזורי הרמזורים שונים משמעותית (${cycle1.roundToInt()} ש׳ לעומת ${cycle2.roundToInt()} ש׳). הסנכרון ישתנה בין מחזורים.",
                statusBadge = "מחזורים שונים"
            )
        }

        // Calculate phase offset modulo average cycle
        val diffMs = l2Last - l1Last
        val cycleMs = (avgCycle * 1000).toLong()
        val offsetMs = ((diffMs % cycleMs) + cycleMs) % cycleMs
        val offsetSec = offsetMs / 1000.0

        val walkSec = walkingSettings.walkBetweenLightsSeconds.toDouble()
        val expectedArrivalOffset = (offsetSec - walkSec)
        val greenWindow = (light2.greenDurationSeconds)

        // Perfect synchronization: arrival at Light 2 is inside Light 2's green window
        val normalizedArrivalInCycle = ((expectedArrivalOffset % avgCycle) + avgCycle) % avgCycle
        val isSync = normalizedArrivalInCycle <= greenWindow || (avgCycle - normalizedArrivalInCycle <= 3.0)

        val badge = if (isSync) "גל ירוק רציף ⚡" else "המתנה חלקית"
        val desc = if (isSync) {
            "הפרש ירוק: ${offsetSec.roundToInt()} ש׳. זמן הליכה: ${walkSec.roundToInt()} ש׳. מגיעים בדיוק בירוק!"
        } else {
            "הפרש ירוק: ${offsetSec.roundToInt()} ש׳. זמן הליכה: ${walkSec.roundToInt()} ש׳. צפויה המתנה קלה ברמזור הראשון או השני."
        }

        return SyncAnalysis(
            offsetSeconds = offsetSec,
            walkTimeSeconds = walkingSettings.walkBetweenLightsSeconds,
            isSynchronized = isSync,
            statusDescription = desc,
            statusBadge = badge
        )
    }

    /**
     * Calculates the optimal upcoming departure plan.
     */
    fun calculateDeparturePlan(
        light1: TrafficLightAnalysis,
        light2: TrafficLightAnalysis,
        walking: WalkingSettings,
        currentTimeMs: Long
    ): DeparturePlan? {
        val cycle1 = light1.cycleDurationSeconds ?: return null
        val l1Last = light1.lastGreenEpochMs ?: return null
        val greenDur1 = light1.greenDurationSeconds

        val cycle2 = light2.cycleDurationSeconds
        val l2Last = light2.lastGreenEpochMs
        val greenDur2 = light2.greenDurationSeconds

        val walk1Sec = walking.walkToLight1Seconds
        val bufferSec = walking.safetyBufferSeconds
        val walkBetweenSec = walking.walkBetweenLightsSeconds
        val minDeparturePrepMs = 4000L // 4 seconds preparation before leaving

        val c1Ms = (cycle1 * 1000).toLong()
        val g1Ms = (greenDur1 * 1000).toLong()

        // If Light 2 is not yet calibrated, plan departure for Light 1 only
        if (cycle2 == null || l2Last == null) {
            // Find next green window of Light 1
            var bestDepMs = currentTimeMs + minDeparturePrepMs
            for (k in 0..15) {
                val greenStartMs = l1Last + (k * c1Ms)
                // We want to arrive at light1 during its green window:
                // arrival = departure + walk1 + buffer
                // We aim for greenStart + (greenDur / 3) for comfortable crossing
                val targetArrival = greenStartMs + (g1Ms / 3)
                val requiredDeparture = targetArrival - ((walk1Sec + bufferSec) * 1000L)
                if (requiredDeparture >= currentTimeMs + minDeparturePrepMs) {
                    bestDepMs = requiredDeparture
                    val arr1 = bestDepMs + ((walk1Sec + bufferSec) * 1000L)
                    val arr2 = arr1 + (walkBetweenSec * 1000L)
                    val remainingSec = (bestDepMs - currentTimeMs) / 1000L

                    return DeparturePlan(
                        recommendedDepartureEpochMs = bestDepMs,
                        secondsUntilDeparture = max(0L, remainingSec),
                        arrivalLight1EpochMs = arr1,
                        arrivalLight2EpochMs = arr2,
                        light1IsInGreen = true,
                        light2IsInGreen = false,
                        hasDirectGreenWave = false,
                        waitAtLight1Seconds = 0,
                        summary = "יציאה מומלצת: ${formatEpochTime(bestDepMs)} (בעוד ${formatSecondsToMinutes(remainingSec)})",
                        details = "הגעה לרמזור 1 בירוק (${formatEpochTime(arr1)}). מדוד את רמזור 2 לחישוב גל ירוק רציף לשניהם.",
                        isAvailable = true
                    )
                }
            }
            return null
        }

        val c2Ms = (cycle2 * 1000).toLong()
        val g2Ms = (greenDur2 * 1000).toLong()

        // Candidate search: search departures in the next 35 minutes
        var bestWavePlan: DeparturePlan? = null
        var bestAlternativePlan: DeparturePlan? = null
        var lowestWaitAtLight1 = Long.MAX_VALUE

        val horizonMs = currentTimeMs + (35 * 60 * 1000L)
        val kMax = ((horizonMs - l1Last) / c1Ms).toInt() + 2

        for (k in 0..kMax) {
            val g1Start = l1Last + (k * c1Ms)
            val g1End = g1Start + g1Ms

            // Try arrival points within Light 1's green window (early, mid, late)
            for (stepFrac in listOf(0.2, 0.5, 0.8)) {
                val targetArr1 = g1Start + (g1Ms * stepFrac).toLong()
                val candidateDep = targetArr1 - ((walk1Sec + bufferSec) * 1000L)

                if (candidateDep < currentTimeMs + minDeparturePrepMs) continue

                // Check Light 2 arrival if walking without stopping
                val targetArr2 = targetArr1 + (walkBetweenSec * 1000L)
                val l2CyclePos = ((targetArr2 - l2Last) % c2Ms + c2Ms) % c2Ms

                val isL2InGreen = l2CyclePos in 0..g2Ms
                val remainingSec = (candidateDep - currentTimeMs) / 1000L

                if (isL2InGreen) {
                    // Direct Green Wave found!
                    val plan = DeparturePlan(
                        recommendedDepartureEpochMs = candidateDep,
                        secondsUntilDeparture = remainingSec,
                        arrivalLight1EpochMs = targetArr1,
                        arrivalLight2EpochMs = targetArr2,
                        light1IsInGreen = true,
                        light2IsInGreen = true,
                        hasDirectGreenWave = true,
                        waitAtLight1Seconds = 0,
                        summary = "גל ירוק כפול! צא ב-${formatEpochTime(candidateDep)}",
                        details = "הגעה לרמזור 1 ב-${formatEpochTime(targetArr1)} (ירוק), המשך רציף לרמזור 2 ב-${formatEpochTime(targetArr2)} (ירוק). ללא המתנה!",
                        isAvailable = true
                    )
                    if (bestWavePlan == null || plan.secondsUntilDeparture < bestWavePlan.secondsUntilDeparture) {
                        bestWavePlan = plan
                    }
                } else {
                    // Compute wait needed at Light 1 to catch Light 2 green
                    val waitMs = if (l2CyclePos < g2Ms) 0L else c2Ms - l2CyclePos
                    val waitSec = waitMs / 1000L

                    if (waitSec < lowestWaitAtLight1 && bestAlternativePlan == null) {
                        lowestWaitAtLight1 = waitSec
                        bestAlternativePlan = DeparturePlan(
                            recommendedDepartureEpochMs = candidateDep,
                            secondsUntilDeparture = remainingSec,
                            arrivalLight1EpochMs = targetArr1,
                            arrivalLight2EpochMs = targetArr2 + waitMs,
                            light1IsInGreen = true,
                            light2IsInGreen = true,
                            hasDirectGreenWave = false,
                            waitAtLight1Seconds = waitSec,
                            summary = "יציאה מומלצת: ${formatEpochTime(candidateDep)} (המתנה קצרה)",
                            details = "הגעה לרמזור 1 ב-${formatEpochTime(targetArr1)} בירוק. המתנה קלה של כ-${waitSec} ש׳, והגעה לרמזור 2 בירוק.",
                            isAvailable = true
                        )
                    }
                }
            }

            if (bestWavePlan != null) {
                break
            }
        }

        return bestWavePlan ?: bestAlternativePlan
    }

    private fun getStartOfDayMs(epochMs: Long): Long {
        val msInDay = 86400000L
        return (epochMs / msInDay) * msInDay
    }
}
