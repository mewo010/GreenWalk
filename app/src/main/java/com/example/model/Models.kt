package com.example.model

import java.util.UUID

enum class TrafficLightId(val displayNameHe: String, val shortNameHe: String) {
    LIGHT_1("רמזור ראשון", "רמזור 1"),
    LIGHT_2("רמזור שני", "רמזור 2")
}

enum class MeasurementType(val titleHe: String) {
    GREEN_START("הפך לירוק"),
    RED_START("הפך לאדום")
}

data class MeasurementEvent(
    val id: String = UUID.randomUUID().toString(),
    val lightId: TrafficLightId,
    val timestamp: Long,
    val type: MeasurementType = MeasurementType.GREEN_START
)

data class TrafficLightAnalysis(
    val lightId: TrafficLightId,
    val cycleDurationSeconds: Double? = null,
    val greenDurationSeconds: Double = 20.0,
    val cycleCount: Int = 0,
    val confidencePercent: Int = 0,
    val lastGreenEpochMs: Long? = null,
    val nextGreenEpochMs: Long? = null,
    val isCurrentlyGreen: Boolean = false,
    val remainingGreenSeconds: Double? = null,
    val timeUntilNextGreenSeconds: Double? = null,
    val statusText: String = "טרם נמדד",
    val cycleStdDev: Double = 0.0
)

data class WalkingSettings(
    val walkToLight1Seconds: Int = 60,
    val walkBetweenLightsSeconds: Int = 90,
    val safetyBufferSeconds: Int = 10
) {
    val totalWalkSeconds: Int
        get() = walkToLight1Seconds + walkBetweenLightsSeconds + safetyBufferSeconds
}

data class ManualConfig(
    val isEnabled: Boolean = false,
    val light1CycleSeconds: Int = 75,
    val light1GreenDurationSeconds: Int = 22,
    val light1OffsetSeconds: Int = 0,
    val light2CycleSeconds: Int = 75,
    val light2GreenDurationSeconds: Int = 22,
    val light2OffsetSeconds: Int = 30
)

data class DeparturePlan(
    val recommendedDepartureEpochMs: Long,
    val secondsUntilDeparture: Long,
    val arrivalLight1EpochMs: Long,
    val arrivalLight2EpochMs: Long,
    val light1IsInGreen: Boolean,
    val light2IsInGreen: Boolean,
    val hasDirectGreenWave: Boolean,
    val waitAtLight1Seconds: Long = 0,
    val summary: String,
    val details: String,
    val isAvailable: Boolean = true
)

data class SyncAnalysis(
    val offsetSeconds: Double?,
    val walkTimeSeconds: Int,
    val isSynchronized: Boolean,
    val statusDescription: String,
    val statusBadge: String
)
