package me.odedniv.osafe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.ContentScaffold
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.PREF_BIOMETRIC_CREATED_AT
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.Content
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.biometricKey
import me.odedniv.osafe.models.preferences
import me.odedniv.osafe.models.updateLaunch

@Serializable object ContentDestination

@Composable
fun ContentDestinationEvents(delayWriteJob: MutableStateFlow<Job?>) {
  // Speeding up write.
  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
    delayWriteJob.value?.cancel(SpeedWriteCancellationException())
  }
}

fun NavGraphBuilder.contentDestinationComposable(
  navController: NavController,
  appState: MutableState<AppState>,
  writeJob: MutableStateFlow<Job?>,
  delayWriteJob: MutableStateFlow<Job?>,
  storage: suspend () -> Storage,
  biometricSupported: Boolean,
  authenticateBiometric: suspend Cipher.() -> Cipher?,
  generatePassphraseConfig: MutableState<GeneratePassphraseConfig>,
) {
  composable<ContentDestination> {
    @Suppress("NAME_SHADOWING") val appState by remember { appState }

    if (appState !is AppState.Ready) return@composable

    val preferences = LocalContext.current.preferences
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    var content: String by rememberSaveable { mutableStateOf(appState.asReady.decrypted.content) }
    var writing: Boolean by rememberSaveable { mutableStateOf(false) }

    fun biometricKey(): Key? = appState.asReady.decrypted.message.keys.biometricKey(preferences)

    ContentScaffold(
      value = content,
      onUpdate = {
        content = it
        lifecycleScope.launch {
          appState.asReady.decrypted = appState.asReady.decrypted.updateContent(content)
          writeJob.updateLaunch {
            try {
              delayWriteJob.updateLaunch { delay(2.seconds) }
            } catch (_: SpeedWriteCancellationException) {} // Ignoring speed-up.
            writing = true
            storage().write(appState.asReady.decrypted.message)
            writing = false
          }
        }
      },
      writing = writing,
      onChangePassphrase = {
        navController.navigate(ChangePassphraseDestination, navOptions { launchSingleTop = true })
      },
      fingerprintSupported = biometricSupported,
      hasFingerprint = biometricKey() != null,
      onAddFingerprint = {
        val keyLabel = Key.Label.Biometric()
        appState.asReady.decrypted =
          appState.asReady.decrypted
            .addKey(
              keyLabel,
              Content.biometricEncryptCipher().authenticateBiometric()
                // User didn't authenticate.
                ?: return@ContentScaffold,
            )
            .also { storage().write(it.message) }
        preferences.edit { putLong(PREF_BIOMETRIC_CREATED_AT, keyLabel.createdAt.epochSecond) }
      },
      onRemoveThisFingerprint = {
        appState.asReady.decrypted =
          appState.asReady.decrypted.removeKeys(setOf(biometricKey()!!)).also {
            storage().write(it.message)
          }
      },
      otherFingerprints =
        appState.asReady.decrypted.message.keys.count {
          it != biometricKey() && it.label is Key.Label.Biometric
        },
      onRemoveOtherFingerprints = {
        appState.asReady.decrypted =
          appState.asReady.decrypted
            .removeKeys(
              appState.asReady.decrypted.message.keys
                .filter { it != biometricKey() && it.label is Key.Label.Biometric }
                .toSet()
            )
            .also { storage().write(it.message) }
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

private class SpeedWriteCancellationException : CancellationException()
