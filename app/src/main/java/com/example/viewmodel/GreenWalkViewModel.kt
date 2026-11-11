package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GreenWalkRepository
import com.example.logic.TrafficCalculationEngine
import com.example.model.DeparturePlan
import com.example.model.ManualConfig
import com.example.model.MeasurementEvent
import com.example.model.MeasurementType
import com.example.model.SyncAnalysis
import com.example.model.TrafficLightAnalysis
import com.example.model.TrafficLightId
import com.example.model.WalkingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GreenWalkUiState(
    val currentTimeMs: Long = System.currentTimeMillis(),
    val measurements: List<MeasurementEvent> = emptyList(),
    val walkingSettings: WalkingSettings = WalkingSettings(),
    val manualConfig: ManualConfig = ManualConfig(),
    val light1Analysis: TrafficLightAnalysis = TrafficLightAnalysis(TrafficLightId.LIGHT_1),
    val light2Analysis: TrafficLightAnalysis = TrafficLightAnalysis(TrafficLightId.LIGHT_2),
    val syncAnalysis: SyncAnalysis = SyncAnalysis(null, 90, false, "אין מספיק נתונים", "ממתין למדידה"),
    val departurePlan: DeparturePlan? = null,
    val selectedTab: Int = 0,
    val toastMessage: String? = null,
    val measureRedPhaseEnabled: Boolean = true
)

class GreenWalkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GreenWalkRepository(application)
    private val _uiState = MutableStateFlow(GreenWalkUiState())
    val uiState: StateFlow<GreenWalkUiState> = _uiState.asStateFlow()

    private val lastTapTimeMap = mutableMapOf<Pair<TrafficLightId, MeasurementType>, Long>()

    init {
        loadInitialData()
        startClockTicker()
    }

    private fun loadInitialData() {
        val loadedMeasurements = repository.loadMeasurements()
        val loadedWalking = repository.loadWalkingSettings()
        val loadedManual = repository.loadManualConfig()

        _uiState.update { current ->
            current.copy(
                measurements = loadedMeasurements,
                walkingSettings = loadedWalking,
                manualConfig = loadedManual
            )
        }
        recalculate(System.currentTimeMillis())
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                recalculate(now)
            }
        }
    }

    private fun recalculate(nowMs: Long) {
        val state = _uiState.value
        val l1 = TrafficCalculationEngine.analyzeLight(
            measurements = state.measurements,
            lightId = TrafficLightId.LIGHT_1,
            currentTimeMs = nowMs,
            manualConfig = state.manualConfig
        )
        val l2 = TrafficCalculationEngine.analyzeLight(
            measurements = state.measurements,
            lightId = TrafficLightId.LIGHT_2,
            currentTimeMs = nowMs,
            manualConfig = state.manualConfig
        )
        val sync = TrafficCalculationEngine.analyzeSync(
            light1 = l1,
            light2 = l2,
            walkingSettings = state.walkingSettings
        )
        val depPlan = TrafficCalculationEngine.calculateDeparturePlan(
            light1 = l1,
            light2 = l2,
            walking = state.walkingSettings,
            currentTimeMs = nowMs
        )

        _uiState.update { current ->
            current.copy(
                currentTimeMs = nowMs,
                light1Analysis = l1,
                light2Analysis = l2,
                syncAnalysis = sync,
                departurePlan = depPlan
            )
        }
    }

    fun recordMeasurement(lightId: TrafficLightId, type: MeasurementType = MeasurementType.GREEN_START) {
        val now = System.currentTimeMillis()
        val key = Pair(lightId, type)
        val lastTap = lastTapTimeMap[key] ?: 0L

        // Debounce: prevent duplicate taps within 2.5 seconds
        if (now - lastTap < 2500L) {
            _uiState.update { it.copy(toastMessage = "לחיצה נקלטה כבר, המתן בין מדידות") }
            return
        }
        lastTapTimeMap[key] = now

        triggerVibrate(80)

        val newEvent = MeasurementEvent(
            lightId = lightId,
            timestamp = now,
            type = type
        )

        val updated = _uiState.value.measurements + newEvent
        repository.saveMeasurements(updated)

        val lightName = if (lightId == TrafficLightId.LIGHT_1) "רמזור 1" else "רמזור 2"
        val actionName = if (type == MeasurementType.GREEN_START) "הפך לירוק" else "הפך לאדום"
        val timeStr = TrafficCalculationEngine.formatEpochTime(now)

        _uiState.update {
            it.copy(
                measurements = updated,
                toastMessage = "נרשם: $lightName $actionName ($timeStr)"
            )
        }
        recalculate(now)
    }

    fun deleteMeasurement(id: String) {
        val updated = _uiState.value.measurements.filterNot { it.id == id }
        repository.saveMeasurements(updated)
        _uiState.update {
            it.copy(
                measurements = updated,
                toastMessage = "מדידה נמחקה"
            )
        }
        recalculate(System.currentTimeMillis())
    }

    fun clearAllMeasurements() {
        repository.saveMeasurements(emptyList())
        _uiState.update {
            it.copy(
                measurements = emptyList(),
                toastMessage = "כל המדידות אופסו בהצלחה"
            )
        }
        recalculate(System.currentTimeMillis())
    }

    fun updateWalkingSettings(settings: WalkingSettings) {
        repository.saveWalkingSettings(settings)
        _uiState.update {
            it.copy(
                walkingSettings = settings,
                toastMessage = "הגדרות הליכה עודכנו"
            )
        }
        recalculate(System.currentTimeMillis())
    }

    fun updateManualConfig(config: ManualConfig) {
        repository.saveManualConfig(config)
        _uiState.update {
            it.copy(
                manualConfig = config,
                toastMessage = if (config.isEnabled) "מצב הזנה ידנית פעיל" else "הגדרות ידניות עודכנו"
            )
        }
        recalculate(System.currentTimeMillis())
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleMeasureRedPhase(enabled: Boolean) {
        _uiState.update { it.copy(measureRedPhaseEnabled = enabled) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun triggerVibrate(millis: Long) {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(millis)
            }
        } catch (e: Exception) {
            // Safe fallback if vibration hardware unavailable
        }
    }
}
