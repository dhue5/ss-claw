package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.engine.DeviceStats
import com.example.engine.ExecutionStep
import com.example.ui.theme.*

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    borderColor: Color = ObsidianCardBorder,
    backgroundColor: Color = ObsidianCard,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun TelemetryHeaderBar(
    stats: DeviceStats?,
    isDaemonRunning: Boolean = true,
    onRefresh: () -> Unit,
    onToggleDaemon: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Live 24/7 Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onToggleDaemon != null) Modifier.clip(RoundedCornerShape(8.dp)).background(if (isDaemonRunning) EmeraldTertiary.copy(alpha = 0.1f) else RedError.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 4.dp) else Modifier
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDaemonRunning) EmeraldTertiary.copy(alpha = pulseAlpha) else RedError)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isDaemonRunning) "24/7 后台哨兵已激活" else "后台哨兵已暂停",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDaemonRunning) EmeraldTertiary else RedError
                )
            }

            // Device Telemetry
            if (stats != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Battery
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (stats.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            contentDescription = "电池电量",
                            tint = if (stats.batteryLevel <= 20) AmberWarning else CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${stats.batteryLevel}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }

                    // Network
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "网络状态",
                            tint = PurpleSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stats.networkType,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }

                    // Storage Free
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "存储空间",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "可用 ${stats.freeStorageMb / 1024}GB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    text = "正在同步设备遥测...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color = CyanPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StepTimelineItem(
    step: ExecutionStep,
    isLast: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Step bullet + connecting line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            val statusColor = when (step.status) {
                "SUCCESS" -> EmeraldTertiary
                "RUNNING" -> CyanPrimary
                "ERROR" -> RedError
                else -> TextSecondary
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(ObsidianCardBorder)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    soundLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 12
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animFactor by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + i * 40, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            val dynamicHeight = if (isListening) {
                val base = (soundLevel * 2f).coerceIn(4f, 22f)
                (base * animFactor).coerceIn(4f, 24f).dp
            } else {
                4.dp
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(3.dp)
                    .height(dynamicHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CyanPrimary, EmeraldTertiary)
                        )
                    )
            )
        }
    }
}

@Composable
fun MiniMetricBar(
    label: String,
    valueText: String,
    percentage: Float,
    color: Color = CyanPrimary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percentage.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = ObsidianCardBorder
        )
    }
}

