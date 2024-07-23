package me.odedniv.osafe

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import me.odedniv.osafe.components.ContentScaffold
import me.odedniv.osafe.components.ExistingPassphraseScaffold
import me.odedniv.osafe.components.GeneratePassphraseDialog
import me.odedniv.osafe.components.LoadingScaffold
import me.odedniv.osafe.components.NewPassphraseScaffold
import me.odedniv.osafe.components.TIMEOUTS
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.PREF_BIOMETRIC_CREATED_AT
import me.odedniv.osafe.models.PREF_TIMEOUT
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.Content
import me.odedniv.osafe.models.encryption.DecryptedMessage
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.Message
import me.odedniv.osafe.models.preferences
import me.odedniv.osafe.theme.OSafeTheme

private object Destinations {
  @Serializable object Loading

  @Serializable object NewPassphrase

  @Serializable object ChangePassphrase

  @Serializable object ExistingPassphrase

  @Serializable object Content
}

class AppActivity : FragmentActivity() {
  private val googleSignInActivityResult = Channel<ActivityResult>()
  private val startGoogleSignInActivity =
    registerForActivityResult(StartActivityForResult()) {
      lifecycleScope.launch { googleSignInActivityResult.send(it) }
    }
  @Volatile private var writeJob: Job? = null
  @Volatile private var delayWriteJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    enableEdgeToEdge()

    val biometricSupported =
      BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS
    val defaultTimeout =
      Duration.ofSeconds(preferences.getLong(PREF_TIMEOUT, TIMEOUTS.keys.first().seconds))
    val defaultBiometricKeyLabel: Key.Label.Biometric? =
      preferences
        .getLong(PREF_BIOMETRIC_CREATED_AT, 0L)
        .takeIf { it != 0L }
        ?.let { Key.Label.Biometric(Instant.ofEpochSecond(it)) }
    val defaultGeneratePassphraseConfig = GeneratePassphraseConfig.readPreferences(preferences)

    var rememberDecryptedJob: Job? = null

    setContent {
      val navController = rememberNavController()
      val context = LocalContext.current
      var googleSignInAccount: GoogleSignInAccount? by rememberSaveable { mutableStateOf(null) }
      val storage by lazy { Storage(context, googleSignInAccount!!) }
      var initialMessage: Message? by rememberSaveable { mutableStateOf(null) }
      var decrypted: DecryptedMessage? by rememberSaveable {
        mutableStateOf(DecryptedMessage.instance)
      }
      var timeout: Duration by rememberSaveable { mutableStateOf(defaultTimeout) }
      var biometricKey: Key? by rememberSaveable { mutableStateOf(null) }
      var writing: Boolean by rememberSaveable { mutableStateOf(false) }
      var generatePassphraseConfig: GeneratePassphraseConfig by rememberSaveable {
        mutableStateOf(defaultGeneratePassphraseConfig)
      }

      // Loading from storage.
      LaunchedEffect(Unit) {
        if (initialMessage != null) return@LaunchedEffect
        googleSignInAccount = googleSignIn()
        if (googleSignInAccount == null) {
          finish()
          return@LaunchedEffect
        }
        storage
          .read()
          .onEmpty {
            navController.navigate(
              Destinations.NewPassphrase,
              navOptions {
                restoreState = true
                popUpTo(Destinations.Loading) { inclusive = true }
              },
            )
          }
          .collect { message ->
            initialMessage = message
            biometricKey = message.keys.firstOrNull { it.label == defaultBiometricKeyLabel }
          }
      }

      // Navigating to decrypt.
      LaunchedEffect(initialMessage) {
        if (initialMessage == null || decrypted?.message == initialMessage) return@LaunchedEffect
        navController.navigate(
          Destinations.ExistingPassphrase,
          navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(Destinations.Loading) { inclusive = true }
          },
        )
      }

      // Remembering decrypted message.
      LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        rememberDecryptedJob?.cancel()
        val currentDecrypted = DecryptedMessage.instance ?: decrypted ?: return@LifecycleEventEffect
        rememberDecryptedJob =
          lifecycleScope.launch {
            currentDecrypted.remember(timeout)
            writeJob?.join() // Wait for writing to complete.
            decrypted = null // Sanity
            finish()
          }
      }
      // Speeding up write.
      LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        delayWriteJob?.cancel(SpeedWriteCancellationException())
      }
      // Stop remember timeout.
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { rememberDecryptedJob?.cancel() }

      suspend fun decrypt(key: Key, cipher: Cipher, currentTimeout: Duration): Boolean {
        timeout = currentTimeout
        preferences.edit { putLong(PREF_TIMEOUT, timeout.seconds) }
        decrypted = initialMessage!!.decrypt(key, cipher) ?: return false
        navController.navigate(
          Destinations.Content,
          navOptions {
            launchSingleTop = true
            popUpTo(Destinations.ExistingPassphrase) { inclusive = true }
          },
        )
        return true
      }

      @Composable
      fun generatePassphraseDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
        GeneratePassphraseDialog(
          config = generatePassphraseConfig,
          onUpdateConfig = {
            generatePassphraseConfig = it.also { it.writePreferences(preferences) }
          },
          onDone = onDone,
          onDismiss = onDismiss,
        )
      }

      OSafeTheme {
        NavHost(navController = navController, startDestination = Destinations.Loading) {
          composable<Destinations.Loading> { LoadingScaffold() }
          composable<Destinations.NewPassphrase> {
            NewPassphraseScaffold(
              onDone = { passphrase ->
                lifecycleScope.launch {
                  decrypted = DecryptedMessage.create(passphrase)
                  navController.navigate(
                    Destinations.Content,
                    navOptions {
                      launchSingleTop = true
                      popUpTo(Destinations.NewPassphrase) { inclusive = true }
                    },
                  )
                }
              },
              generatePassphraseDialog = { onDone, onDismiss ->
                generatePassphraseDialog(onDone, onDismiss)
              },
            )
          }
          composable<Destinations.ChangePassphrase> {
            if (decrypted == null) return@composable // finished() was called.
            NewPassphraseScaffold(
              onDone = { passphrase ->
                lifecycleScope.launch {
                  decrypted =
                    decrypted!!.changePassphrase(passphrase).also { storage.write(it.message) }
                  navController.navigateUp()
                }
              },
              onCancel = { navController.navigateUp() },
              generatePassphraseDialog = { onDone, onDismiss ->
                generatePassphraseDialog(onDone, onDismiss)
              },
            )
          }
          composable<Destinations.ExistingPassphrase> {
            ExistingPassphraseScaffold(
              defaultTimeout = timeout,
              onPassphrase = { passphrase, currentTimeout ->
                val key: Key =
                  requireNotNull(initialMessage!!.keys.find { it.label is Key.Label.Passphrase }) {
                    "Missing passphrase key."
                  }
                decrypt(
                  key,
                  key.content.decryptCipher((key.label as Key.Label.Passphrase).digest(passphrase)),
                  currentTimeout,
                )
              },
              hasFingerprint = biometricKey != null,
              onFingerprint = { currentTimeout ->
                /** Failed biometric when we shouldn't have, clearing values to avoid using it. */
                suspend fun clearBiometric(): Boolean {
                  decrypted =
                    decrypted!!.removeKeys(setOf(biometricKey!!)).also { storage.write(it.message) }
                  biometricKey = null
                  preferences.edit { remove(PREF_BIOMETRIC_CREATED_AT) }
                  return false // For easier return to onFingerprint.
                }

                decrypt(
                    biometricKey!!,
                    (biometricKey!!.content.biometricDecryptCipher()
                        // Missing from Android Storage.
                        ?: return@ExistingPassphraseScaffold clearBiometric())
                      .authenticateBiometric()
                      // User didn't authenticate.
                      ?: return@ExistingPassphraseScaffold false,
                    currentTimeout,
                  )
                  .also { if (!it) clearBiometric() } // Didn't decrypt message.
              },
            )
          }
          composable<Destinations.Content> {
            if (decrypted == null) return@composable // finished() was called.
            ContentScaffold(
              value = decrypted!!.content,
              onUpdate = { content ->
                lifecycleScope.launch {
                  decrypted = decrypted!!.updateContent(content)
                  writeJob?.cancelAndJoin()
                  writeJob = launch {
                    try {
                      coroutineScope { delayWriteJob = launch { delay(2.seconds) } }
                    } catch (_: SpeedWriteCancellationException) {} // Ignoring speed-up.
                    writing = true
                    storage.write(decrypted!!.message)
                    writing = false
                  }
                }
              },
              writing = writing,
              onChangePassphrase = { navController.navigate(Destinations.ChangePassphrase) },
              fingerprintSupported = biometricSupported,
              hasFingerprint = biometricKey != null,
              onAddFingerprint = {
                val keyLabel = Key.Label.Biometric()
                val (newDecrypted, newBiometricKey) =
                  decrypted!!.addKey(
                    keyLabel,
                    Content.biometricEncryptCipher().authenticateBiometric()
                      // User didn't authenticate.
                      ?: return@ContentScaffold,
                  )
                decrypted = newDecrypted.also { storage.write(it.message) }
                biometricKey = newBiometricKey
                preferences.edit {
                  putLong(PREF_BIOMETRIC_CREATED_AT, keyLabel.createdAt.epochSecond)
                }
              },
              onRemoveThisFingerprint = {
                decrypted =
                  decrypted!!.removeKeys(setOf(biometricKey!!)).also { storage.write(it.message) }
                biometricKey = null
              },
              otherFingerprints =
                decrypted!!.message.keys.count {
                  it != biometricKey && it.label is Key.Label.Biometric
                },
              onRemoveOtherFingerprints = {
                decrypted =
                  decrypted!!
                    .removeKeys(
                      decrypted!!
                        .message
                        .keys
                        .filter { it != biometricKey && it.label is Key.Label.Biometric }
                        .toSet()
                    )
                    .also { storage.write(it.message) }
                biometricKey = null
              },
              generatePassphraseDialog = { onDone, onDismiss ->
                generatePassphraseDialog(onDone, onDismiss)
              },
            )
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    runBlocking { writeJob?.join() } // Make sure we finish writing.
  }

  private class SpeedWriteCancellationException : CancellationException()

  @Suppress("deprecation") // TODO: Replace GoogleSignIn.
  private suspend fun googleSignIn(): GoogleSignInAccount? {
    if (
      GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) !=
        ConnectionResult.SUCCESS
    ) {
      return null
    }
    val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this)
    if (
      googleSignInAccount != null &&
        googleSignInAccount.grantedScopes.contains(Scope(Scopes.EMAIL)) &&
        googleSignInAccount.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))
    ) {
      return googleSignInAccount
    }
    startGoogleSignInActivity.launch(
      GoogleSignIn.getClient(
          this,
          GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build(),
        )
        .signInIntent
    )
    return getGoogleSignInAccountFromIntent(googleSignInActivityResult.receive().data)
  }

  @Suppress("deprecation") // TODO: Replace GoogleSignIn.
  private suspend fun getGoogleSignInAccountFromIntent(data: Intent?) =
    suspendCoroutine<GoogleSignInAccount> { continuation ->
      GoogleSignIn.getSignedInAccountFromIntent(data)
        .addOnSuccessListener(continuation::resume)
        .addOnFailureListener(continuation::resumeWithException)
    }

  private suspend fun Cipher.authenticateBiometric(): Cipher? =
    suspendCancellableCoroutine { continuation ->
      val prompt =
        BiometricPrompt(
          this@AppActivity,
          ContextCompat.getMainExecutor(this@AppActivity),
          object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
              super.onAuthenticationSucceeded(result)
              continuation.resume(result.cryptoObject!!.cipher!!)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
              super.onAuthenticationError(errorCode, errString)
              continuation.resume(null)
            }
          },
        )
      prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
          .setAllowedAuthenticators(BIOMETRIC_STRONG)
          .setTitle("Use fingerprint")
          .setNegativeButtonText("Cancel")
          .build(),
        BiometricPrompt.CryptoObject(this),
      )
      continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
