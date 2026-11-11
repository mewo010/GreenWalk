package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MeasurementType
import com.example.model.TrafficLightId
import com.example.ui.components.DigitalClockCard
import com.example.ui.components.ManualInputCard
import com.example.ui.components.MeasurementHistoryDialog
import com.example.ui.components.SafetyDisclaimerCard
import com.example.ui.components.SyncAnalysisCard
import com.example.ui.components.TrafficLightCard
import com.example.ui.components.WalkingSettingsCard
import com.example.ui.components.WhenToLeaveHeroCard
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.DarkBackground
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
import com.example.viewmodel.GreenWalkViewModel

@Composable
fun GreenWalkHomeScreen(
    viewModel: GreenWalkViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showHistoryDialog by remember { mutableStateOf(false) }

    // Handle toast messages
    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissToast()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Bar
                    HeaderBar(
                        onOpenHistory = { showHistoryDialog = true },
                        onResetAll = { viewModel.clearAllMeasurements() },
                        measurementsCount = uiState.measurements.size
                    )

                    // 1. Live Digital Clock
                    DigitalClockCard(currentTimeMs = uiState.currentTimeMs)

                    // 2. Central Hero Card: When to Leave
                    WhenToLeaveHeroCard(departurePlan = uiState.departurePlan)

                    // 3. Primary Fast Measurement Hub (Big buttons)
                    MeasurementQuickHub(
                        onMeasureLight1Green = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_1, MeasurementType.GREEN_START)
                        },
                        onMeasureLight2Green = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_2, MeasurementType.GREEN_START)
                        },
                        onMeasureLight1Red = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_1, MeasurementType.RED_START)
                        },
                        onMeasureLight2Red = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_2, MeasurementType.RED_START)
                        },
                        measurementsCount = uiState.measurements.size
                    )

                    // 4. Traffic Light 1 Card
                    TrafficLightCard(
                        analysis = uiState.light1Analysis,
                        onMeasureGreen = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_1, MeasurementType.GREEN_START)
                        },
                        onMeasureRed = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_1, MeasurementType.RED_START)
                        }
                    )

                    // 5. Traffic Light 2 Card
                    TrafficLightCard(
                        analysis = uiState.light2Analysis,
                        onMeasureGreen = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_2, MeasurementType.GREEN_START)
                        },
                        onMeasureRed = {
                            viewModel.recordMeasurement(TrafficLightId.LIGHT_2, MeasurementType.RED_START)
                        }
                    )

                    // 6. Traffic Lights Synchronization Analysis
                    SyncAnalysisCard(sync = uiState.syncAnalysis)

                    // 7. Walking Times & Speed Settings
                    WalkingSettingsCard(
                        settings = uiState.walkingSettings,
                        onSettingsChanged = { viewModel.updateWalkingSettings(it) }
                    )

                    // 8. Manual Override Configuration
                    ManualInputCard(
                        config = uiState.manualConfig,
                        onConfigChanged = { viewModel.updateManualConfig(it) }
                    )

                    // 9. Safety Note
                    SafetyDisclaimerCard()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // History Modal Dialog
            if (showHistoryDialog) {
                MeasurementHistoryDialog(
                    measurements = uiState.measurements,
                    onDelete = { viewModel.deleteMeasurement(it) },
                    onClearAll = { viewModel.clearAllMeasurements() },
                    onDismiss = { showHistoryDialog = false }
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    onOpenHistory: () -> Unit,
    onResetAll: () -> Unit,
    measurementsCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(TrafficGreen.copy(alpha = 0.3f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = DarkSurfaceElevated,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "GW",
                            color = TrafficGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "GreenWalk",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "תזמון רמזורים להולכי רגל",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenHistory() }
                    .testTag("btn_open_history"),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "היסטוריה ($measurementsCount)",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onResetAll,
                modifier = Modifier
                    .size(34.dp)
                    .background(DarkSurfaceElevated, CircleShape)
                    .testTag("btn_reset_all")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MeasurementQuickHub(
    onMeasureLight1Green: () -> Unit,
    onMeasureLight2Green: () -> Unit,
    onMeasureLight1Red: () -> Unit,
    onMeasureLight2Red: () -> Unit,
    measurementsCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("measurement_quick_hub"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(
                    TrafficGreen.copy(alpha = 0.4f),
                    DarkBorder
                )
            )
        )
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
                Column {
                    Text(
                        text = "מדידה בזמן אמת ברחוב",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "לחץ ברגע שהרמזור מתחלף לירוק ללמידת המחזור",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = TrafficGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$measurementsCount מדידות",
                        color = TrafficGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two Large Primary Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onMeasureLight1Green() }
                        .testTag("big_btn_light_1_green"),
                    color = TrafficGreen,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "רמזור ראשון",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "הפך לירוק!",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Button 2
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onMeasureLight2Green() }
                        .testTag("big_btn_light_2_green"),
                    color = TrafficGreen,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "רמזור שני",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "הפך לירוק!",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtle Red measurement buttons to calculate green phase length
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMeasureLight1Red() },
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)))
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "רמזור 1 הפך לאדום",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onMeasureLight2Red() },
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)))
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "רמזור 2 הפך לאדום",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
