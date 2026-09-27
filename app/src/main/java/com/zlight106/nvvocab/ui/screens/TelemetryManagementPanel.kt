package com.zlight106.nvvocab.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.components.NvvDropdown
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryManagementPanel(viewModel: MainViewModel) {
    val entries by viewModel.telemetryEntries.collectAsStateWithLifecycle()
    var periodDays by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf("全部类型") }
    var source by remember { mutableStateOf("全部题库") }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var singleId by remember { mutableStateOf<String?>(null) }
    var zipIds by remember { mutableStateOf(emptySet<String>()) }
    val singleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/xml"),
    ) { uri ->
        val id = singleId
        singleId = null
        if (uri != null && id != null) viewModel.exportArchivedTelemetry(setOf(id), false, uri)
    }
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.exportArchivedTelemetry(zipIds, true, uri)
        zipIds = emptySet()
    }
    val cutoff = if (periodDays == 0) 0L else System.currentTimeMillis() - periodDays * 86_400_000L
    val visible = entries.filter { entry ->
        entry.createdAt >= cutoff &&
            (category == "全部类型" || entry.category == category) &&
            (source == "全部题库" || entry.sourceName == source)
    }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Text("已结算的遥测会自动存入本机，可按时间和来源筛选后导出。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "全部", 1 to "近 24 小时", 7 to "近 7 天", 30 to "近 30 天").forEach { (days, label) ->
            FilterChip(selected = periodDays == days, onClick = { periodDays = days }, label = { Text(label) })
        }
    }
    NvvDropdown(
        label = "练习类型",
        value = category,
        options = (listOf("全部类型") + entries.map { it.category }.distinct().sorted()).map { it to it },
        icon = NvvIcons.FileQuestion,
        onChange = { category = it },
    )
    NvvDropdown(
        label = "题库或来源",
        value = source,
        options = (listOf("全部题库") + entries.map { it.sourceName }.distinct().sorted()).map { it to it },
        icon = NvvIcons.FileQuestion,
        onChange = { source = it },
    )
    if (visible.isEmpty()) {
        Text("当前筛选下没有遥测记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        OutlinedButton(
            onClick = {
                val ids = visible.map { it.sessionId }.toSet()
                selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids else selectedIds + ids
            },
            shape = CircleShape,
        ) { Text("全选/取消当前筛选") }
        visible.forEach { entry ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = entry.sessionId in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + entry.sessionId else selectedIds - entry.sessionId
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(entry.sourceName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${dateFormat.format(Date(entry.createdAt))} · ${entry.category} · ${entry.attemptCount} 题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            singleId = entry.sessionId
                            singleLauncher.launch("nvvocab-telemetry-${entry.sessionId}.xml")
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = CircleShape,
                    ) { Text("单独导出") }
                }
            }
        }
        Button(
            onClick = {
                zipIds = selectedIds.intersect(visible.map { it.sessionId }.toSet())
                zipLauncher.launch("nvvocab-telemetry.zip")
            },
            enabled = selectedIds.any { id -> visible.any { it.sessionId == id } },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) { Text("打包导出所选 ZIP") }
    }
}
