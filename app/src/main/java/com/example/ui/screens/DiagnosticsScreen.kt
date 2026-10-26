package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.DiagnosticItem
import com.example.diagnostics.DiagnosticStatus
import com.example.diagnostics.DiagnosticsReport
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    report: DiagnosticsReport,
    onRunDiagnostics: () -> Unit,
    onAutoFixModels: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expandedItemIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler {
        onClose()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Core Engine Diagnostics",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "8-Point Subsystem Checklist & Self-Test",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to App"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRunDiagnostics,
                        enabled = !report.isRunning,
                        modifier = Modifier.testTag("run_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-run all tests",
                            tint = if (report.isRunning) MaterialTheme.colorScheme.onSurfaceVariant else NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("diagnostics_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Overall Status Hero Banner
            item {
                StatusBanner(
                    report = report,
                    onRunDiagnostics = onRunDiagnostics,
                    onAutoFixModels = onAutoFixModels,
                    onCopyReport = {
                        val reportText = buildReportString(report)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("KokoroVoice Diagnostics", reportText))
                        Toast.makeText(context, "Diagnostics report copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. Checklist Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subsystem Checklist",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${report.items.count { it.status == DiagnosticStatus.PASS }} of ${report.items.size} passing",
                        style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan)
                    )
                }
            }

            // 3. Checklist Items
            items(report.items, key = { it.id }) { item ->
                val isExpanded = expandedItemIds.contains(item.id)
                DiagnosticItemCard(
                    item = item,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedItemIds = if (isExpanded) {
                            expandedItemIds - item.id
                        } else {
                            expandedItemIds + item.id
                        }
                    },
                    onAutoFix = onAutoFixModels
                )
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun StatusBanner(
    report: DiagnosticsReport,
    onRunDiagnostics: () -> Unit,
    onAutoFixModels: () -> Unit,
    onCopyReport: () -> Unit
) {
    val (statusColor, statusTitle, statusIcon) = when (report.overallStatus) {
        DiagnosticStatus.PASS -> Triple(StatusGreen, "All Subsystems Verified Operational", Icons.Default.CheckCircle)
        DiagnosticStatus.WARNING -> Triple(StatusAmber, "Warnings Detected • Review Details", Icons.Default.Warning)
        DiagnosticStatus.FAIL -> Triple(StatusRed, "Action Needed • Errors Detected", Icons.Default.Error)
        DiagnosticStatus.RUNNING -> Triple(NeonCyan, "Executing 8-Point Diagnostics...", Icons.Default.Refresh)
        DiagnosticStatus.PENDING -> Triple(ElectricViolet, "Ready to Test Core Functionality", Icons.Default.Build)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (report.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonCyan,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Text(
                text = report.summaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (report.isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = NeonCyan,
                    trackColor = NeonCyan.copy(alpha = 0.2f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunDiagnostics,
                    enabled = !report.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (report.isRunning) "Testing..." else "Run All Tests")
                }

                OutlinedButton(
                    onClick = onCopyReport,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Log")
                }
            }

            if (report.items.any { it.canAutoFix && it.status != DiagnosticStatus.PASS }) {
                Button(
                    onClick = onAutoFixModels,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fix Corrupted Models & Reset Stubs", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItemCard(
    item: DiagnosticItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAutoFix: () -> Unit
) {
    val (statusColor, statusIcon) = when (item.status) {
        DiagnosticStatus.PASS -> Pair(StatusGreen, Icons.Default.CheckCircle)
        DiagnosticStatus.WARNING -> Pair(StatusAmber, Icons.Default.Warning)
        DiagnosticStatus.FAIL -> Pair(StatusRed, Icons.Default.Cancel)
        DiagnosticStatus.RUNNING -> Pair(NeonCyan, Icons.Default.Refresh)
        DiagnosticStatus.PENDING -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Build)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() }
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status == DiagnosticStatus.RUNNING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonCyan)
                    } else {
                        Icon(imageVector = statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                    }

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.latencyMs > 0) {
                        Text(
                            text = "${item.latencyMs}ms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Technical Details & Analysis:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = NeonCyan)
                    )
                    Text(
                        text = item.details.ifBlank { "No additional debug information recorded." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (item.canAutoFix && item.status != DiagnosticStatus.PASS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onAutoFix,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Resolve Issue", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun buildReportString(report: DiagnosticsReport): String {
    val sb = StringBuilder()
    sb.append("=== KOKORO VOICE DIAGNOSTICS REPORT ===\n")
    sb.append("Generated: ${java.util.Date(report.timestamp)}\n")
    sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})\n")
    sb.append("Overall Status: ${report.overallStatus}\n\n")

    for (item in report.items) {
        sb.append("[${item.status}] ${item.title} (${item.category})\n")
        sb.append("  Summary: ${item.summary}\n")
        if (item.latencyMs > 0) sb.append("  Latency: ${item.latencyMs}ms\n")
        if (item.details.isNotBlank()) sb.append("  Details:\n    ${item.details.replace("\n", "\n    ")}\n")
        sb.append("\n")
    }
    return sb.toString()
}
