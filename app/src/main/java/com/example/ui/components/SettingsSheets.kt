package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logic.TrafficCalculationEngine
import com.example.model.ManualConfig
import com.example.model.MeasurementEvent
import com.example.model.MeasurementType
import com.example.model.TrafficLightId
import com.example.model.WalkingSettings
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrafficAmber
import com.example.ui.theme.TrafficGreen
import com.example.ui.theme.TrafficRed

@Composable
fun WalkingSettingsCard(
    settings: WalkingSettings,
    onSettingsChanged: (WalkingSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("walking_settings_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder.copy(alpha = 0.5f))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsWalk,
                            contentDescription = "Walking",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "מחשבון זמני הליכה",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "התאמת מהירות ומשכי הליכה",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "סה״כ: ${settings.totalWalkSeconds} ש׳",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Setting 1: Walk to Light 1
            WalkingStepper(
                label = "זמן הליכה מהמיקום הנוכחי לרמזור 1",
                seconds = settings.walkToLight1Seconds,
                step = 10,
                minSec = 10,
                maxSec = 600,
                onChanged = { onSettingsChanged(settings.copy(walkToLight1Seconds = it)) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Setting 2: Walk between lights
            WalkingStepper(
                label = "זמן הליכה מרמזור 1 לרמזור 2",
                seconds = settings.walkBetweenLightsSeconds,
                step = 10,
                minSec = 10,
                maxSec = 600,
                onChanged = { onSettingsChanged(settings.copy(walkBetweenLightsSeconds = it)) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Setting 3: Safety buffer
            WalkingStepper(
                label = "מרווח ביטחון נוסף להגעה בטוחה",
                seconds = settings.safetyBufferSeconds,
                step = 5,
                minSec = 0,
                maxSec = 60,
                onChanged = { onSettingsChanged(settings.copy(safetyBufferSeconds = it)) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Presets
            Text(
                text = "מהירות הליכה מהירה:",
                color = TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickSpeedChip(
                    title = "איטית (רגוע)",
                    isSelected = settings.walkBetweenLightsSeconds == 120,
                    onClick = {
                        onSettingsChanged(settings.copy(walkToLight1Seconds = 80, walkBetweenLightsSeconds = 120, safetyBufferSeconds = 15))
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickSpeedChip(
                    title = "רגילה (4.8 קמ״ש)",
                    isSelected = settings.walkBetweenLightsSeconds == 90,
                    onClick = {
                        onSettingsChanged(settings.copy(walkToLight1Seconds = 60, walkBetweenLightsSeconds = 90, safetyBufferSeconds = 10))
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickSpeedChip(
                    title = "מהירה (נמרץ)",
                    isSelected = settings.walkBetweenLightsSeconds == 65,
                    onClick = {
                        onSettingsChanged(settings.copy(walkToLight1Seconds = 45, walkBetweenLightsSeconds = 65, safetyBufferSeconds = 5))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WalkingStepper(
    label: String,
    seconds: Int,
    step: Int,
    minSec: Int,
    maxSec: Int,
    onChanged: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 13.sp
            )
            Text(
                text = if (seconds >= 60) "${seconds / 60} דק׳ ו-${seconds % 60} ש׳ (${seconds} ש׳)" else "$seconds שניות",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (seconds - step >= minSec) onChanged(seconds - step) },
                modifier = Modifier
                    .size(36.dp)
                    .background(DarkSurfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Minus",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$seconds שניות",
                    color = AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { if (seconds + step <= maxSec) onChanged(seconds + step) },
                modifier = Modifier
                    .size(36.dp)
                    .background(DarkSurfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Plus",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickSpeedChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    if (isSelected) AccentCyan else DarkBorder,
                    if (isSelected) AccentCyan else DarkBorder
                )
            )
        )
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) AccentCyan else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun ManualInputCard(
    config: ManualConfig,
    onConfigChanged: (ManualConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var l1Cycle by remember(config) { mutableStateOf(config.light1CycleSeconds.toString()) }
    var l1Green by remember(config) { mutableStateOf(config.light1GreenDurationSeconds.toString()) }
    var l1Offset by remember(config) { mutableStateOf(config.light1OffsetSeconds.toString()) }

    var l2Cycle by remember(config) { mutableStateOf(config.light2CycleSeconds.toString()) }
    var l2Green by remember(config) { mutableStateOf(config.light2GreenDurationSeconds.toString()) }
    var l2Offset by remember(config) { mutableStateOf(config.light2OffsetSeconds.toString()) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("manual_input_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder.copy(alpha = 0.5f))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(TrafficAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manual",
                            tint = TrafficAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "הזנת זמנים ידנית",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "הגדרה ישירה ללא צורך במדידה ברחוב",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = config.isEnabled,
                    onCheckedChange = { onConfigChanged(config.copy(isEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TrafficGreen,
                        checkedTrackColor = TrafficGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.testTag("manual_mode_switch")
                )
            }

            if (config.isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "רמזור 1:",
                    color = TrafficGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = l1Cycle,
                        onValueChange = { l1Cycle = it },
                        label = { Text("מחזור (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = l1Green,
                        onValueChange = { l1Green = it },
                        label = { Text("ירוק (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = l1Offset,
                        onValueChange = { l1Offset = it },
                        label = { Text("היסט (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "רמזור 2:",
                    color = AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = l2Cycle,
                        onValueChange = { l2Cycle = it },
                        label = { Text("מחזור (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = l2Green,
                        onValueChange = { l2Green = it },
                        label = { Text("ירוק (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = l2Offset,
                        onValueChange = { l2Offset = it },
                        label = { Text("היסט (ש׳)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = TrafficRed,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val c1 = l1Cycle.toIntOrNull()
                        val g1 = l1Green.toIntOrNull()
                        val o1 = l1Offset.toIntOrNull() ?: 0

                        val c2 = l2Cycle.toIntOrNull()
                        val g2 = l2Green.toIntOrNull()
                        val o2 = l2Offset.toIntOrNull() ?: 0

                        if (c1 == null || c1 < 10 || c2 == null || c2 < 10) {
                            errorMessage = "אורך מחזור חייב להיות מספר חיובי לפחות 10 שניות"
                            return@Button
                        }
                        if (g1 == null || g1 <= 0 || g1 >= c1) {
                            errorMessage = "משך ירוק ברמזור 1 חייב להיות קטן מאורך המחזור"
                            return@Button
                        }
                        if (g2 == null || g2 <= 0 || g2 >= c2) {
                            errorMessage = "משך ירוק ברמזור 2 חייב להיות קטן מאורך המחזור"
                            return@Button
                        }

                        errorMessage = null
                        onConfigChanged(
                            config.copy(
                                isEnabled = true,
                                light1CycleSeconds = c1,
                                light1GreenDurationSeconds = g1,
                                light1OffsetSeconds = o1,
                                light2CycleSeconds = c2,
                                light2GreenDurationSeconds = g2,
                                light2OffsetSeconds = o2
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TrafficGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "החל נתונים ידניים",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = TrafficGreen,
    unfocusedBorderColor = DarkBorder,
    focusedLabelColor = TrafficGreen,
    unfocusedLabelColor = TextSecondary,
    cursorColor = TrafficGreen
)

@Composable
fun MeasurementHistoryDialog(
    measurements: List<MeasurementEvent>,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = AccentCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("היסטוריית מדידות (${measurements.size})", color = TextPrimary)
                }
                if (measurements.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear all",
                            tint = TrafficRed
                        )
                    }
                }
            }
        },
        text = {
            if (measurements.isEmpty()) {
                Text("אין עדיין מדידות שמורות. לחץ על 'הפך לירוק' ברמזור 1 או 2.", color = TextSecondary)
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(measurements.reversed()) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (item.type == MeasurementType.GREEN_START) TrafficGreen else TrafficRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${item.lightId.shortNameHe} – ${item.type.titleHe}",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = TrafficCalculationEngine.formatEpochTime(item.timestamp),
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDelete(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete item",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור", color = TrafficGreen)
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(20.dp)
    )
}
