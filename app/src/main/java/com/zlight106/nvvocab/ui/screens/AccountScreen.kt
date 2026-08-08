package com.zlight106.nvvocab.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zlight106.nvvocab.ui.AppUiState
import com.zlight106.nvvocab.ui.MainViewModel
import com.zlight106.nvvocab.ui.icons.NvvIcons

@Composable
fun AccountSettingsPanel(viewModel: MainViewModel, state: AppUiState) {
    var registering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    if (state.session != null) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(state.session.email, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::synchronize, enabled = !state.syncing, shape = CircleShape) {
                    Icon(NvvIcons.RefreshCw, null)
                    Text("同步", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = viewModel::signOut, shape = CircleShape) {
                    Icon(NvvIcons.LogOut, null)
                    Text("退出登录", Modifier.padding(start = 8.dp))
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AccountModeSelector(
                registering = registering,
                onRegisteringChange = { registering = it },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Button(
                modifier = Modifier.align(Alignment.End),
                enabled = email.isNotBlank() && password.length >= 6 && !state.syncing,
                shape = CircleShape,
                onClick = {
                    if (registering) viewModel.signUp(email, password) else viewModel.signIn(email, password)
                },
            ) {
                Icon(if (registering) NvvIcons.UserRound else NvvIcons.LogIn, null)
                Text(if (registering) "创建账户" else "登录", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun AccountModeSelector(
    registering: Boolean,
    onRegisteringChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AccountModeButton(
                modifier = Modifier.weight(1f),
                selected = !registering,
                label = "登录",
                onClick = { onRegisteringChange(false) },
            )
            AccountModeButton(
                modifier = Modifier.weight(1f),
                selected = registering,
                label = "注册",
                onClick = { onRegisteringChange(true) },
            )
        }
    }
}

@Composable
private fun AccountModeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
        }
    }
}
