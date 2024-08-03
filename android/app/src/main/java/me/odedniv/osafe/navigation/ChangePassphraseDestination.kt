package me.odedniv.osafe.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.NewPassphraseScaffold
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.Storage

@Serializable object ChangePassphraseDestination

fun NavGraphBuilder.changePassphraseDestinationComposable(
  navController: NavController,
  appState: State<AppState>,
  storage: suspend () -> Storage,
  generatePassphraseConfig: MutableState<GeneratePassphraseConfig>,
) {
  composable<ChangePassphraseDestination> {
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    @Suppress("NAME_SHADOWING") val appState by remember { appState }

    if (appState !is AppState.Ready) return@composable

    NewPassphraseScaffold(
      onDone = { passphrase ->
        lifecycleScope.launch {
          appState.let { appState ->
            appState.asReady.decrypted =
              appState.asReady.decrypted.changePassphrase(passphrase).also {
                storage().write(it.message)
              }
          }
          navController.navigateUp()
        }
      },
      onCancel = { navController.navigateUp() },
      generatePassphraseDialog = { onDone, onDismiss ->
        GeneratePassphraseDialogDestination(
          config = generatePassphraseConfig,
          onDone = onDone,
          onDismiss = onDismiss,
        )
      },
    )
  }
}
