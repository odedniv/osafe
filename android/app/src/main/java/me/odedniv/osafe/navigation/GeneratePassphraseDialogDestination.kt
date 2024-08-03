package me.odedniv.osafe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.odedniv.osafe.components.GeneratePassphraseDialog
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.preferences

@Composable
fun GeneratePassphraseDialogDestination(
  config: MutableState<GeneratePassphraseConfig>,
  onDone: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  @Suppress("NAME_SHADOWING") var config by remember { config }
  val preferences = LocalContext.current.preferences

  GeneratePassphraseDialog(
    config = config,
    onUpdateConfig = { config = it.also { it.writePreferences(preferences) } },
    onDone = onDone,
    onDismiss = onDismiss,
  )
}
