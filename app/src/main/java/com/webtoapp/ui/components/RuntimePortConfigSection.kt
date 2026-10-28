package com.webtoapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.webtoapp.core.i18n.Strings
import com.webtoapp.data.model.PortConflictMode
import com.webtoapp.ui.design.WtaSwitch
import com.webtoapp.ui.theme.ifDescriptionsShown















@Composable
fun RuntimePortConfigSection(
    port: Int,
    portConflictMode: PortConflictMode,
    onPortChange: (Int) -> Unit,
    onPortConflictModeChange: (PortConflictMode) -> Unit,
    modifier: Modifier = Modifier
) {
    EnhancedElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            RuntimeSectionHeader(
                icon = Icons.Outlined.Lan,
                title = Strings.portConfigTitle
            )
            Spacer(Modifier.height(16.dp))

            
            
            
            var portInput by remember {
                mutableStateOf(if (port > 0) port.toString() else "")
            }
            LaunchedEffect(port == 0) {
                if (port > 0 && portInput.toIntOrNull() != port) {
                    portInput = port.toString()
                }
            }

            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Strings.portAutoAssign,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Strings.portAutoAssignHint.ifDescriptionsShown()?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                WtaSwitch(
                    checked = port == 0,
                    onCheckedChange = { auto ->
                        if (auto) {
                            onPortChange(0)
                        } else {
                            val restored = portInput.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 8080
                            portInput = restored.toString()
                            onPortChange(restored)
                        }
                    }
                )
            }

            
            val portNumber = portInput.toIntOrNull()
            val portInvalid = portInput.isNotEmpty() && (portNumber == null || portNumber !in 1024..65535)
            AnimatedVisibility(visible = port > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(5)
                            portInput = digits
                            val num = digits.toIntOrNull()
                            if (num != null && num in 1024..65535) onPortChange(num)
                        },
                        label = { Text(Strings.portDefaultLabel) },
                        isError = portInvalid,
                        supportingText = if (portInvalid) {
                            { Text(Strings.portRangeHint) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            
            Text(
                text = Strings.portConflictTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            PortConflictSegmentedRow(
                mode = portConflictMode,
                onModeChange = onPortConflictModeChange,
                enabled = port > 0
            )
        }
    }
}






@Composable
fun RuntimePortConflictSelector(
    portConflictMode: PortConflictMode,
    onPortConflictModeChange: (PortConflictMode) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    EnhancedElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            RuntimeSectionHeader(
                icon = Icons.Outlined.Shield,
                title = Strings.portConflictTitle
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = Strings.portConflictAutoKillHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            PortConflictSegmentedRow(
                mode = portConflictMode,
                onModeChange = onPortConflictModeChange,
                enabled = true
            )
        }
    }
}

@Composable
private fun PortConflictSegmentedRow(
    mode: PortConflictMode,
    onModeChange: (PortConflictMode) -> Unit,
    enabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PortConflictOption(
            title = Strings.portConflictAutoKill,
            hint = Strings.portConflictAutoKillHint,
            selected = mode == PortConflictMode.AUTO_KILL,
            enabled = enabled,
            onClick = { onModeChange(PortConflictMode.AUTO_KILL) }
        )
        PortConflictOption(
            title = Strings.portConflictAlert,
            hint = Strings.portConflictAlertHint,
            selected = mode == PortConflictMode.ALERT,
            enabled = enabled,
            onClick = { onModeChange(PortConflictMode.ALERT) }
        )
    }
}

@Composable
private fun PortConflictOption(
    title: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            hint.ifDescriptionsShown()?.let { shown ->
                Text(
                    text = shown,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
