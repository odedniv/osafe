package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.odedniv.osafe.theme.OSafeTheme

val TIMEOUTS =
  mapOf<Duration, String>(
    Duration.ofSeconds(3) to "Immediately",
    Duration.ofMinutes(1) to "1 minute",
    Duration.ofMinutes(5) to "5 minutes",
    Duration.ofHours(1) to "1 hour",
    Duration.ofHours(6) to "6 hours",
    Duration.ofDays(1) to "1 day",
    Duration.ofDays(7) to "1 week",
    Duration.ofDays(3650) to "Never",
  )

@Composable
fun ExistingPassphraseScaffold(
  defaultTimeout: Duration,
  onPassphrase: suspend (String, Duration) -> Boolean,
  hasFingerprint: Boolean,
  onFingerprint: suspend (Duration) -> Boolean,
) {
  val coroutineScope = rememberCoroutineScope()
  var passphrase by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
  var timeout by rememberSaveable { mutableStateOf(TIMEOUTS.keys.first { it >= defaultTimeout }) }
  var loading by rememberSaveable { mutableStateOf(false) }
  val passphraseFocus = remember { FocusRequester() }
  val snackbarHostState = remember { SnackbarHostState() }

  fun submit() {
    loading = true
    coroutineScope.launch {
      if (!onPassphrase(passphrase.text, timeout).also { loading = false }) {
        passphraseFocus.requestFocus()
        snackbarHostState.showSnackbar("Wrong passphrase, try again.")
      }
    }
  }

  Scaffold(
    topBar = {
      @OptIn(ExperimentalMaterial3Api::class) CenterAlignedTopAppBar(title = { Text("OSafe") })
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = Modifier.imePadding(),
  ) { innerPadding ->
    Column(
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
    ) {
      // Passphrase
      TextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text("Enter your passphrase...") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(passphraseFocus).padding(bottom = 16.dp),
      )
      // Set focus or check fingerprint on start.
      LaunchedEffect(Unit) {
        if (hasFingerprint) onFingerprint(timeout) else passphraseFocus.requestFocus()
      }
      // Expiration
      Text("Ask me again: ${TIMEOUTS[timeout]}")
      Slider(
        value = TIMEOUTS.keys.indexOf(timeout).toFloat(),
        onValueChange = { timeout = TIMEOUTS.keys.toList()[it.roundToInt()] },
        valueRange = 0f..(TIMEOUTS.size - 1).toFloat(),
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.padding(bottom = 8.dp),
      )
      // Submit
      Button(
        onClick = { submit() },
        enabled = passphrase.text.isNotEmpty() && !loading,
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
      ) {
        Text("SUBMIT")
      }
      // Fingerprint
      if (hasFingerprint) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
          TextButton(
            enabled = !loading,
            onClick = {
              loading = true
              coroutineScope.launch {
                if (!onFingerprint(timeout).also { loading = false }) {
                  snackbarHostState.showSnackbar("Fingerprint authentication failed.")
                }
              }
            },
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Fingerprint, "Fingerprint")
              Text("Use fingerprint", modifier = Modifier.padding(start = 8.dp))
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun ExistingPassphraseScaffoldPreview() {
  OSafeTheme {
    ExistingPassphraseScaffold(
      defaultTimeout = TIMEOUTS.keys.first(),
      onPassphrase = { _, _ ->
        delay(1.seconds)
        false
      },
      hasFingerprint = true,
      onFingerprint = {
        delay(1.seconds)
        false
      },
    )
  }
}
