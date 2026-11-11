package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ManualConfig
import com.example.model.MeasurementEvent
import com.example.model.MeasurementType
import com.example.model.TrafficLightId
import com.example.model.WalkingSettings
import org.json.JSONArray
import org.json.JSONObject

class GreenWalkRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("greenwalk_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MEASUREMENTS = "key_measurements"
        private const val KEY_WALK_TO_L1 = "key_walk_to_l1"
        private const val KEY_WALK_L1_TO_L2 = "key_walk_l1_to_l2"
        private const val KEY_SAFETY_BUFFER = "key_safety_buffer"

        private const val KEY_MANUAL_ENABLED = "key_manual_enabled"
        private const val KEY_MANUAL_L1_CYCLE = "key_manual_l1_cycle"
        private const val KEY_MANUAL_L1_GREEN = "key_manual_l1_green"
        private const val KEY_MANUAL_L1_OFFSET = "key_manual_l1_offset"
        private const val KEY_MANUAL_L2_CYCLE = "key_manual_l2_cycle"
        private const val KEY_MANUAL_L2_GREEN = "key_manual_l2_green"
        private const val KEY_MANUAL_L2_OFFSET = "key_manual_l2_offset"
    }

    fun saveMeasurements(events: List<MeasurementEvent>) {
        val array = JSONArray()
        for (event in events) {
            val obj = JSONObject()
            obj.put("id", event.id)
            obj.put("lightId", event.lightId.name)
            obj.put("timestamp", event.timestamp)
            obj.put("type", event.type.name)
            array.put(obj)
        }
        prefs.edit().putString(KEY_MEASUREMENTS, array.toString()).apply()
    }

    fun loadMeasurements(): List<MeasurementEvent> {
        val jsonStr = prefs.getString(KEY_MEASUREMENTS, null) ?: return emptyList()
        val list = mutableListOf<MeasurementEvent>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val lightIdStr = obj.optString("lightId", TrafficLightId.LIGHT_1.name)
                val timestamp = obj.optLong("timestamp", 0L)
                val typeStr = obj.optString("type", MeasurementType.GREEN_START.name)

                val lightId = try {
                    TrafficLightId.valueOf(lightIdStr)
                } catch (e: Exception) {
                    TrafficLightId.LIGHT_1
                }
                val type = try {
                    MeasurementType.valueOf(typeStr)
                } catch (e: Exception) {
                    MeasurementType.GREEN_START
                }

                if (timestamp > 0L) {
                    list.add(MeasurementEvent(id = id, lightId = lightId, timestamp = timestamp, type = type))
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted json and return empty
        }
        return list
    }

    fun saveWalkingSettings(settings: WalkingSettings) {
        prefs.edit()
            .putInt(KEY_WALK_TO_L1, settings.walkToLight1Seconds)
            .putInt(KEY_WALK_L1_TO_L2, settings.walkBetweenLightsSeconds)
            .putInt(KEY_SAFETY_BUFFER, settings.safetyBufferSeconds)
            .apply()
    }

    fun loadWalkingSettings(): WalkingSettings {
        val toL1 = prefs.getInt(KEY_WALK_TO_L1, 60)
        val l1ToL2 = prefs.getInt(KEY_WALK_L1_TO_L2, 90)
        val buffer = prefs.getInt(KEY_SAFETY_BUFFER, 10)
        return WalkingSettings(
            walkToLight1Seconds = toL1,
            walkBetweenLightsSeconds = l1ToL2,
            safetyBufferSeconds = buffer
        )
    }

    fun saveManualConfig(config: ManualConfig) {
        prefs.edit()
            .putBoolean(KEY_MANUAL_ENABLED, config.isEnabled)
            .putInt(KEY_MANUAL_L1_CYCLE, config.light1CycleSeconds)
            .putInt(KEY_MANUAL_L1_GREEN, config.light1GreenDurationSeconds)
            .putInt(KEY_MANUAL_L1_OFFSET, config.light1OffsetSeconds)
            .putInt(KEY_MANUAL_L2_CYCLE, config.light2CycleSeconds)
            .putInt(KEY_MANUAL_L2_GREEN, config.light2GreenDurationSeconds)
            .putInt(KEY_MANUAL_L2_OFFSET, config.light2OffsetSeconds)
            .apply()
    }

    fun loadManualConfig(): ManualConfig {
        return ManualConfig(
            isEnabled = prefs.getBoolean(KEY_MANUAL_ENABLED, false),
            light1CycleSeconds = prefs.getInt(KEY_MANUAL_L1_CYCLE, 75),
            light1GreenDurationSeconds = prefs.getInt(KEY_MANUAL_L1_GREEN, 22),
            light1OffsetSeconds = prefs.getInt(KEY_MANUAL_L1_OFFSET, 0),
            light2CycleSeconds = prefs.getInt(KEY_MANUAL_L2_CYCLE, 75),
            light2GreenDurationSeconds = prefs.getInt(KEY_MANUAL_L2_GREEN, 22),
            light2OffsetSeconds = prefs.getInt(KEY_MANUAL_L2_OFFSET, 30)
        )
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
