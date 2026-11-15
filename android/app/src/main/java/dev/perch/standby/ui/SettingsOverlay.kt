package dev.perch.standby.ui

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.perch.standby.data.Settings

@Composable
fun SettingsOverlay(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onRequestPermissions: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var city by rememberSaveable { mutableStateOf(settings.city) }
    val applyCity = {
        onChange { it.copy(city = city.trim()) }
        focus.clearFocus()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                SwitchRow("24-hour clock", settings.use24h) { v -> onChange { it.copy(use24h = v) } }
                SwitchRow("Fahrenheit", settings.fahrenheit) { v -> onChange { it.copy(fahrenheit = v) } }
                SwitchRow("Night mode in the dark", settings.autoNight) { v -> onChange { it.copy(autoNight = v) } }
                if (settings.autoNight) {
                    TintPicker(NightTint.of(settings.nightTint)) { t -> onChange { it.copy(nightTint = t.ordinal) } }
                }
                SwitchRow("Demo Mac data (no Bluetooth)", settings.demoMac) { v -> onChange { it.copy(demoMac = v) } }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Weather city") },
                    placeholder = { Text("Blank uses this device's location") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyCity() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = applyCity) { Text("Apply city") }
                    OutlinedButton(onClick = onRequestPermissions) { Text("Permissions") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        // Lets the user pick Perch as the screensaver shown while charging.
                        context.startActivity(Intent(SystemSettings.ACTION_DREAM_SETTINGS))
                    }) { Text("Screen saver settings") }
                    FilledTonalButton(onClick = { applyCity(); onClose() }) { Text("Done") }
                }
            }
        }
    }
}

/** A row of colour swatches; each is a 48dp target with its name read out by TalkBack. */
@Composable
private fun TintPicker(selected: NightTint, onSelect: (NightTint) -> Unit) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            "Night colour: ${selected.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.selectableGroup()) {
            NightTint.entries.forEach { tint ->
                val isSelected = tint == selected
                Box(
                    Modifier
                        .size(48.dp)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(tint) })
                        .semantics { contentDescription = tint.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .padding(if (isSelected) 4.dp else 0.dp)
                            .background(tint.color, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
