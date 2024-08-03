package me.odedniv.osafe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.LoadingScaffold
import me.odedniv.osafe.models.Storage

@Serializable object LoadingDestination

@Composable
fun LoadingDestinationEvents(
  navController: NavController,
  appState: MutableState<AppState>,
  storage: suspend () -> Storage,
) {
  @Suppress("NAME_SHADOWING") var appState by remember { appState }

  // Load from storage.
  LaunchedEffect(Unit) {
    if (appState != AppState.Unloaded) return@LaunchedEffect

    storage()
      .read()
      .onEmpty {
        navController.navigate(
          NewPassphraseDestination,
          navOptions { popUpTo(LoadingDestination) { inclusive = true } },
        )
      }
      .collect { message ->
        appState = AppState.Encrypted(message)
        navController.navigate(
          DecryptDestination,
          navOptions {
            launchSingleTop = true
            popUpTo(LoadingDestination) { inclusive = true }
          },
        )
      }
  }
}

fun NavGraphBuilder.loadingDestinationComposable() {
  composable<LoadingDestination> { LoadingScaffold() }
}
