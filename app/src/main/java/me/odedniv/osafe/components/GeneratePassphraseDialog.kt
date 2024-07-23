package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.PassphraseRule
import me.odedniv.osafe.models.PassphraseType
import me.odedniv.osafe.models.generatePassphrase
import me.odedniv.osafe.models.toggle
import me.odedniv.osafe.theme.OSafeTheme

fun interface IGeneratePassphraseDialog {
  @Composable fun Show(onDone: (String) -> Unit, onDismiss: () -> Unit)
}

@Composable
fun GeneratePassphraseDialog(
  config: GeneratePassphraseConfig,
  onUpdateConfig: (GeneratePassphraseConfig) -> Unit,
  onDone: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  CardDialog(onDismissRequest = onDismiss) {
    Content(config = config, onUpdateConfig = onUpdateConfig, onDone = onDone, onCancel = onDismiss)
  }
}

@Composable
private fun Content(
  config: GeneratePassphraseConfig,
  onUpdateConfig: (GeneratePassphraseConfig) -> Unit,
  onDone: (String) -> Unit,
  onCancel: () -> Unit,
) {
  var result by rememberSaveable { mutableStateOf(generatePassphrase(config)) }

  // Update result on config change.
  LaunchedEffect(config) { result = generatePassphrase(config) }

  // Type group
  Row(
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
  ) {
    Dropdown(
      selected = config.type,
      options = PassphraseType.entries,
      onSelected = { onUpdateConfig(config.copy(type = it, length = it.defaultLength)) },
    )
  }
  // Length
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
    Text(config.length.toString(), modifier = Modifier.padding(start = 4.dp, end = 8.dp))
    Slider(
      value = config.length.toFloat(),
      onValueChange = { onUpdateConfig(config.copy(length = it.roundToInt())) },
      valueRange = 1f..config.type.maxLength.toFloat(),
      colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.primary),
    )
  }
  // Rules
  for (rule in PassphraseRule.entries) {
    LabeledCheckbox(
      checked = rule in config.rules,
      onCheckedChange = { onUpdateConfig(config.copy(rules = config.rules.toggle(rule, it))) },
      label = rule.label,
      modifier = Modifier.padding(bottom = 8.dp),
    )
  }
  // Result
  Row(
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
  ) {
    TextField(
      value = result,
      textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
      readOnly = true,
      onValueChange = {},
      colors =
        TextFieldDefaults.colors()
          .copy(
            focusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
          ),
    )
  }
  // Regenerate
  Row(
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
  ) {
    Button(onClick = { result = generatePassphrase(config) }) { Text("REGENERATE") }
  }
  // Done/cancel
  Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
    TextButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("CANCEL") }
    TextButton(onClick = { onDone(result) }) { Text("INSERT") }
  }
}

@Composable
fun IGeneratePassphraseDialogPreview(onDone: (String) -> Unit, onDismiss: () -> Unit) {
  var config by remember {
    mutableStateOf(
      GeneratePassphraseConfig(
        type = PassphraseType.entries.first(),
        length = PassphraseType.entries.first().defaultLength,
        rules = setOf(),
      )
    )
  }
  CardDialog(onDismissRequest = onDismiss) {
    Content(
      config = config,
      // Note that this is not saved in previews for future invocations.
      onUpdateConfig = { config = it },
      onDone = onDone,
      onCancel = onDismiss,
    )
  }
}

@Preview
@Composable
fun GeneratePassphraseDialogPreview() {
  OSafeTheme { IGeneratePassphraseDialogPreview(onDone = {}, onDismiss = {}) }
}
