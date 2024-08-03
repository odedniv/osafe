package me.odedniv.osafe.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.NewPassphraseScaffold
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.DecryptedMessage

@Serializable object NewPassphraseDestination

fun NavGraphBuilder.newPassphraseDestinationComposable(
  navController: NavController,
  appState: MutableState<AppState>,
  storage: suspend () -> Storage,
  generatePassphraseConfig: MutableState<GeneratePassphraseConfig>,
) {
  composable<NewPassphraseDestination> {
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    @Suppress("NAME_SHADOWING") var appState by remember { appState }

    if (appState !is AppState.Unloaded) return@composable

    NewPassphraseScaffold(
      onDone = { passphrase ->
        lifecycleScope.launch {
          appState = AppState.Ready(DecryptedMessage.create(passphrase))
          storage().write(appState.asReady.decrypted.message)
          navController.navigate(
            ContentDestination,
            navOptions {
              launchSingleTop = true
              popUpTo(NewPassphraseDestination) { inclusive = true }
            },
          )
        }
      },
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
