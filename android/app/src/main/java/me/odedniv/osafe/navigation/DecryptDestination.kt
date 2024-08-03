package me.odedniv.osafe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import java.time.Duration
import javax.crypto.Cipher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.DecryptScaffold
import me.odedniv.osafe.models.PREF_BIOMETRIC_CREATED_AT
import me.odedniv.osafe.models.PREF_TIMEOUT
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.DecryptedMessage
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.biometricKey
import me.odedniv.osafe.models.encryption.passphraseKey
import me.odedniv.osafe.models.preferences

@Serializable object DecryptDestination

@Composable
fun DecryptDestinationEvents(
  navController: NavController,
  appState: MutableState<AppState>,
  rememberDecryptedJob: MutableStateFlow<Job?>,
  writeJob: MutableStateFlow<Job?>,
  timeout: State<Duration>,
) {
  val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
  @Suppress("NAME_SHADOWING") var appState by remember { appState }
  @Suppress("NAME_SHADOWING") val timeout by remember { timeout }

  // Remembering decrypted message.
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
    rememberDecryptedJob.update { job ->
      job?.cancel()
      val currentDecrypted =
        DecryptedMessage.instance ?: (appState as? AppState.Ready)?.decrypted ?: return@update null

      lifecycleScope.launch {
        currentDecrypted.remember(timeout)
        writeJob.value?.join() // Wait for writing to complete.
        (appState as? AppState.Ready)?.let {
          appState = AppState.Encrypted(it.decrypted.message)
          navController.navigate(
            DecryptDestination,
            navOptions {
              launchSingleTop = true
              popUpTo(ContentDestination) { inclusive = true }
            },
          )
        }
      }
    }
  }

  // Stop remember timeout.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { rememberDecryptedJob.value?.cancel() }
}

fun NavGraphBuilder.decryptDestinationComposable(
  navController: NavController,
  appState: MutableState<AppState>,
  timeout: MutableState<Duration>,
  storage: suspend () -> Storage,
  authenticateBiometric: suspend Cipher.() -> Cipher?,
) {
  composable<DecryptDestination> {
    @Suppress("NAME_SHADOWING") var appState by remember { appState }
    @Suppress("NAME_SHADOWING") var timeout by remember { timeout }
    val preferences = LocalContext.current.preferences

    if (appState !is AppState.Encrypted) return@composable

    fun biometricKey(): Key? = appState.asEncrypted.message.keys.biometricKey(preferences)

    suspend fun decrypt(key: Key, cipher: Cipher, currentTimeout: Duration): Boolean {
      timeout = currentTimeout.also { preferences.edit { putLong(PREF_TIMEOUT, it.seconds) } }
      val decrypted = appState.asEncrypted.message.decrypt(key, cipher) ?: return false
      appState = AppState.Ready(decrypted)
      navController.navigate(
        ContentDestination,
        navOptions {
          launchSingleTop = true
          popUpTo(DecryptDestination) { inclusive = true }
        },
      )
      return true
    }

    DecryptScaffold(
      defaultTimeout = timeout,
      onPassphrase = { passphrase, currentTimeout ->
        val key: Key =
          requireNotNull(appState.asEncrypted.message.keys.passphraseKey) {
            "Missing passphrase key."
          }
        decrypt(
          key,
          key.content.decryptCipher((key.label as Key.Label.Passphrase).digest(passphrase)),
          currentTimeout,
        )
      },
      hasFingerprint = biometricKey() != null,
      onFingerprint = { currentTimeout ->
        /** Failed biometric when we shouldn't have, clearing values to avoid using it. */
        suspend fun clearBiometric(): Boolean {
          appState.asEncrypted.message =
            appState.asEncrypted.message.removeKeys(setOf(biometricKey()!!)).also {
              storage().write(it)
            }
          preferences.edit { remove(PREF_BIOMETRIC_CREATED_AT) }
          return false // For easier return to onFingerprint.
        }

        decrypt(
            biometricKey()!!,
            (biometricKey()!!.content.biometricDecryptCipher()
                // Missing from Android Storage.
                ?: return@DecryptScaffold clearBiometric())
              .authenticateBiometric()
              // User didn't authenticate.
              ?: return@DecryptScaffold false,
            currentTimeout,
          )
          .also { if (!it) clearBiometric() } // Didn't decrypt message.
      },
    )
  }
}
