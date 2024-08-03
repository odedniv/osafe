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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import java.time.Duration
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import me.odedniv.osafe.components.TIMEOUTS
import me.odedniv.osafe.models.GeneratePassphraseConfig
import me.odedniv.osafe.models.PREF_TIMEOUT
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.DecryptedMessage
import me.odedniv.osafe.models.preferences
import me.odedniv.osafe.navigation.ContentDestinationEvents
import me.odedniv.osafe.navigation.DecryptDestinationEvents
import me.odedniv.osafe.navigation.LoadingDestination
import me.odedniv.osafe.navigation.LoadingDestinationEvents
import me.odedniv.osafe.navigation.AppState
import me.odedniv.osafe.navigation.changePassphraseDestinationComposable
import me.odedniv.osafe.navigation.contentDestinationComposable
import me.odedniv.osafe.navigation.decryptDestinationComposable
import me.odedniv.osafe.navigation.loadingDestinationComposable
import me.odedniv.osafe.navigation.newPassphraseDestinationComposable
import me.odedniv.osafe.theme.OSafeTheme

class AppActivity : FragmentActivity() {
  private val googleSignInActivityResult = Channel<ActivityResult>()
  private val startGoogleSignInActivity =
    registerForActivityResult(StartActivityForResult()) {
      lifecycleScope.launch { googleSignInActivityResult.send(it) }
    }
  private val rememberDecryptedJob = MutableStateFlow<Job?>(null)
  private val writeJob = MutableStateFlow<Job?>(null)
  private val delayWriteJob = MutableStateFlow<Job?>(null)
  private var _storage: Deferred<Storage>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    enableEdgeToEdge()

    // Logic for gathering information prior to composition.
    val initialAppState: AppState = DecryptedMessage.instance?.let { AppState.Ready(it) } ?: AppState.Unloaded
    val biometricSupported =
      BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS
    val defaultTimeout =
      Duration.ofSeconds(preferences.getLong(PREF_TIMEOUT, TIMEOUTS.keys.first().seconds))
    val defaultGeneratePassphraseConfig = GeneratePassphraseConfig.readPreferences(preferences)

    setContent {
      // State shared between destinations.
      val navController = rememberNavController()
      val appState: MutableState<AppState> = rememberSaveable { mutableStateOf(initialAppState) }
      val timeout: MutableState<Duration> = rememberSaveable { mutableStateOf(defaultTimeout) }
      val generatePassphraseConfig: MutableState<GeneratePassphraseConfig> = rememberSaveable {
        mutableStateOf(defaultGeneratePassphraseConfig)
      }

      // Lifecycle event handling.
      LoadingDestinationEvents(
        navController = navController,
        appState = appState,
        storage = { storage() },
      )
      DecryptDestinationEvents(
        navController = navController,
        appState = appState,
        rememberDecryptedJob = rememberDecryptedJob,
        writeJob = writeJob,
        timeout = timeout,
      )
      ContentDestinationEvents(delayWriteJob = delayWriteJob)

      OSafeTheme {
        NavHost(navController = navController, startDestination = LoadingDestination) {
          // Destinations.
          loadingDestinationComposable()
          newPassphraseDestinationComposable(
            navController = navController,
            appState = appState,
            storage = { storage() },
            generatePassphraseConfig = generatePassphraseConfig,
          )
          changePassphraseDestinationComposable(
            navController = navController,
            appState = appState,
            storage = { storage() },
            generatePassphraseConfig = generatePassphraseConfig,
          )
          decryptDestinationComposable(
            navController = navController,
            appState = appState,
            timeout = timeout,
            storage = { storage() },
            authenticateBiometric = { authenticateBiometric() },
          )
          contentDestinationComposable(
            navController = navController,
            appState = appState,
            writeJob = writeJob,
            delayWriteJob = delayWriteJob,
            storage = { storage() },
            biometricSupported = biometricSupported,
            authenticateBiometric = { authenticateBiometric() },
            generatePassphraseConfig = generatePassphraseConfig,
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    runBlocking { writeJob.value?.join() } // Make sure we finish writing.
  }

  private suspend fun storage(): Storage = coroutineScope {
    synchronized(this) {
      if (_storage != null) return@synchronized
      _storage = async {
        val googleSignInAccount = if (Storage.DEBUG) null else googleSignIn()
        if (!Storage.DEBUG && googleSignInAccount == null) {
          finish()
          throw CancellationException()
        }
        Storage(this@AppActivity, googleSignInAccount)
      }
    }
    _storage!!.await()
  }

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
