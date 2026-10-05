package com.zlight106.nvvocab.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zlight106.nvvocab.BuildConfig
import com.zlight106.nvvocab.data.update.EasyUpdatePhase
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.icons.NvvIcons
import java.util.Locale

/** Keep installation handling composed even when the settings card is collapsed. */
@Composable
internal fun rememberEasyUpdateInstallation(viewModel: MainViewModel): () -> Unit {
    val updateState by viewModel.easyUpdateState.collectAsStateWithLifecycle()
    var pendingPermissionApk by rememberSaveable { mutableStateOf<String?>(null) }
    val installerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* The Android installer owns confirmation and the installation result. */ }

    fun reportLaunchFailure(error: Throwable) {
        viewModel.notifyUser(
            when (error) {
                is ActivityNotFoundException -> "未找到可处理更新的系统安装器或设置页面"
                is SecurityException -> "系统未允许打开安装器，请检查安装权限"
                else -> error.message ?: "无法打开更新安装器"
            },
        )
    }

    fun launchInstaller() {
        runCatching { installerLauncher.launch(viewModel.easyUpdateInstallerIntent()) }
            .onFailure(::reportLaunchFailure)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val expectedApk = pendingPermissionApk
        pendingPermissionApk = null
        val current = viewModel.easyUpdateState.value
        if (expectedApk != null && current.phase == EasyUpdatePhase.READY && current.apkPath == expectedApk) {
            if (viewModel.canInstallEasyUpdate()) {
                launchInstaller()
            } else {
                viewModel.notifyUser("更新已下载；允许本应用安装未知应用后，可点击‘安装更新’重试")
            }
        }
    }

    val requestInstallation: () -> Unit = {
        val current = viewModel.easyUpdateState.value
        if (current.phase == EasyUpdatePhase.READY && current.apkPath != null) {
            if (viewModel.canInstallEasyUpdate()) {
                launchInstaller()
            } else {
                pendingPermissionApk = current.apkPath
                runCatching { permissionLauncher.launch(viewModel.easyUpdateUnknownSourcesIntent()) }
                    .onFailure {
                        pendingPermissionApk = null
                        reportLaunchFailure(it)
                    }
            }
        }
    }

    LaunchedEffect(updateState.phase, updateState.apkPath) {
        val apkPath = updateState.apkPath
        if (updateState.phase == EasyUpdatePhase.READY && apkPath != null &&
            viewModel.consumeEasyUpdateAutomaticInstall(apkPath)
        ) {
            requestInstallation()
        }
    }
    return requestInstallation
}

@Composable
internal fun EasyUpdateSettingsPanel(
    viewModel: MainViewModel,
    requestInstallation: () -> Unit,
) {
    val savedServerUrl by viewModel.easyUpdateServerUrl.collectAsStateWithLifecycle()
    val updateState by viewModel.easyUpdateState.collectAsStateWithLifecycle()
    var serverUrl by remember(savedServerUrl) { mutableStateOf(savedServerUrl) }
    val busy = updateState.phase == EasyUpdatePhase.CHECKING ||
        updateState.phase == EasyUpdatePhase.DOWNLOADING

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("当前版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(
            "检查更新后，有新版本会自动下载并打开系统安装器；首次安装可能需要允许本应用安装未知应用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = serverUrl,
            onValueChange = { serverUrl = it },
            enabled = !busy,
            label = { Text("更新源服务器") },
            supportingText = { Text("填写 EasyUpdate 服务器地址，例如 http://192.168.95.55:19910") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        OutlinedButton(
            onClick = { viewModel.saveEasyUpdateServerUrl(serverUrl) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) { Text("保存更新源") }
        Button(
            onClick = {
                if (viewModel.saveEasyUpdateServerUrl(serverUrl)) viewModel.checkEasyUpdate()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Icon(NvvIcons.RefreshCw, contentDescription = null)
            Text(
                when (updateState.phase) {
                    EasyUpdatePhase.CHECKING -> "正在检查更新"
                    EasyUpdatePhase.DOWNLOADING -> "正在下载更新"
                    else -> "检查更新"
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (busy) {
            val progress = updateState.progress
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("下载进度 ${(progress.coerceIn(0f, 1f) * 100).toInt()}%")
            }
        }
        if (updateState.message.isNotBlank()) {
            Text(
                updateState.message,
                color = if (updateState.phase == EasyUpdatePhase.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        updateState.release?.let { release ->
            Text("可用版本：${release.versionName} (${release.versionCode})")
            if (release.size > 0) {
                Text("安装包：${String.format(Locale.ROOT, "%.1f", release.size / 1_048_576.0)} MB")
            }
            if (release.releaseNotes.isNotBlank()) {
                Text("更新内容", style = MaterialTheme.typography.titleSmall)
                Text(release.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (updateState.phase == EasyUpdatePhase.READY) {
            Button(
                onClick = requestInstallation,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(NvvIcons.Download, contentDescription = null)
                Text("安装更新", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
