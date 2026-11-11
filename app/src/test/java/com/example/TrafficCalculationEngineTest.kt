package com.example

import com.example.logic.TrafficCalculationEngine
import com.example.model.ManualConfig
import com.example.model.MeasurementEvent
import com.example.model.MeasurementType
import com.example.model.TrafficLightId
import com.example.model.WalkingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficCalculationEngineTest {

    @Test
    fun testInsufficientData_singleMeasurementReturnsNoCycle() {
        val now = 1700000000000L
        val events = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = now, type = MeasurementType.GREEN_START)
        )

        val analysis = TrafficCalculationEngine.analyzeLight(
            measurements = events,
            lightId = TrafficLightId.LIGHT_1,
            currentTimeMs = now + 10000L,
            manualConfig = ManualConfig(isEnabled = false)
        )

        assertNull("Cycle duration should be null with single measurement", analysis.cycleDurationSeconds)
        assertEquals(1, analysis.cycleCount)
        assertTrue(analysis.confidencePercent <= 30)
    }

    @Test
    fun testCycleCalculation_withConsistentMeasurements() {
        val base = 1700000000000L
        val cycle = 80000L // 80 seconds

        val events = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + cycle, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + (2 * cycle), type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + (3 * cycle), type = MeasurementType.GREEN_START)
        )

        val analysis = TrafficCalculationEngine.analyzeLight(
            measurements = events,
            lightId = TrafficLightId.LIGHT_1,
            currentTimeMs = base + (3 * cycle) + 5000L,
            manualConfig = ManualConfig(isEnabled = false)
        )

        assertNotNull(analysis.cycleDurationSeconds)
        assertEquals(80.0, analysis.cycleDurationSeconds!!, 0.5)
        assertTrue("Confidence should be high for 4 consistent measurements", analysis.confidencePercent >= 75)
        assertTrue("Should be currently green 5 seconds into cycle", analysis.isCurrentlyGreen)
    }

    @Test
    fun testLearnedGreenDuration_fromRedMeasurements() {
        val base = 1700000000000L
        val cycle = 70000L
        val greenDuration = 22000L // 22s green

        val events = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + greenDuration, type = MeasurementType.RED_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + cycle, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + cycle + greenDuration, type = MeasurementType.RED_START)
        )

        val analysis = TrafficCalculationEngine.analyzeLight(
            measurements = events,
            lightId = TrafficLightId.LIGHT_1,
            currentTimeMs = base + cycle + 5000L,
            manualConfig = ManualConfig(isEnabled = false)
        )

        assertEquals(22.0, analysis.greenDurationSeconds, 0.5)
    }

    @Test
    fun testMidnightRollover() {
        // Test timestamps that span across midnight (e.g. 23:59:00 to 00:01:40)
        val nearMidnight = 1700092740000L // 23:59:00
        val afterMidnight = nearMidnight + 160000L // 00:01:40 (80s * 2)

        val events = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = nearMidnight, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = nearMidnight + 80000L, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = afterMidnight, type = MeasurementType.GREEN_START)
        )

        val analysis = TrafficCalculationEngine.analyzeLight(
            measurements = events,
            lightId = TrafficLightId.LIGHT_1,
            currentTimeMs = afterMidnight + 10000L,
            manualConfig = ManualConfig(isEnabled = false)
        )

        assertEquals(80.0, analysis.cycleDurationSeconds!!, 0.5)
        assertTrue(analysis.confidencePercent > 50)
    }

    @Test
    fun testDifferentCycles_handlesMismatchGracefully() {
        val base = 1700000000000L
        val eventsL1 = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + 60000L, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + 120000L, type = MeasurementType.GREEN_START)
        )
        val eventsL2 = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_2, timestamp = base, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_2, timestamp = base + 110000L, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_2, timestamp = base + 220000L, type = MeasurementType.GREEN_START)
        )

        val l1 = TrafficCalculationEngine.analyzeLight(eventsL1, TrafficLightId.LIGHT_1, base + 250000L, ManualConfig())
        val l2 = TrafficCalculationEngine.analyzeLight(eventsL2, TrafficLightId.LIGHT_2, base + 250000L, ManualConfig())

        val sync = TrafficCalculationEngine.analyzeSync(l1, l2, WalkingSettings(walkBetweenLightsSeconds = 70))
        assertFalse("Cycles of 60s and 110s should not report seamless sync", sync.isSynchronized)
        assertTrue(sync.statusBadge.contains("מחזורים שונים"))
    }

    @Test
    fun testGreenWaveDeparturePlan() {
        val base = 1700000000000L
        val cycle = 80000L
        val greenDur = 25000L
        val walk1 = 60 // 60s
        val walkBetween = 80 // 80s (exactly 1 cycle offset!)

        // Light 1 and Light 2 are synchronized so that Light 2 turns green 80s after Light 1
        val events = listOf(
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_1, timestamp = base + cycle, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_2, timestamp = base + cycle, type = MeasurementType.GREEN_START),
            MeasurementEvent(lightId = TrafficLightId.LIGHT_2, timestamp = base + (2 * cycle), type = MeasurementType.GREEN_START)
        )

        val l1 = TrafficCalculationEngine.analyzeLight(events, TrafficLightId.LIGHT_1, base + 10000L, ManualConfig())
        val l2 = TrafficCalculationEngine.analyzeLight(events, TrafficLightId.LIGHT_2, base + 10000L, ManualConfig())

        val walking = WalkingSettings(walkToLight1Seconds = walk1, walkBetweenLightsSeconds = walkBetween, safetyBufferSeconds = 0)
        val plan = TrafficCalculationEngine.calculateDeparturePlan(l1, l2, walking, base + 10000L)

        assertNotNull(plan)
        assertTrue("Should produce a plan", plan!!.isAvailable)
        assertTrue("Plan should have direct green wave or minimal wait", plan.hasDirectGreenWave || plan.waitAtLight1Seconds < 30)
    }

    @Test
    fun testManualModeOverride() {
        val manual = ManualConfig(
            isEnabled = true,
            light1CycleSeconds = 90,
            light1GreenDurationSeconds = 30,
            light1OffsetSeconds = 0,
            light2CycleSeconds = 90,
            light2GreenDurationSeconds = 30,
            light2OffsetSeconds = 45
        )

        val now = 1700000000000L
        val l1 = TrafficCalculationEngine.analyzeLight(emptyList(), TrafficLightId.LIGHT_1, now, manual)
        val l2 = TrafficCalculationEngine.analyzeLight(emptyList(), TrafficLightId.LIGHT_2, now, manual)

        assertEquals(90.0, l1.cycleDurationSeconds!!, 0.01)
        assertEquals(30.0, l1.greenDurationSeconds, 0.01)
        assertEquals(100, l1.confidencePercent)
        assertEquals(90.0, l2.cycleDurationSeconds!!, 0.01)
    }
}
