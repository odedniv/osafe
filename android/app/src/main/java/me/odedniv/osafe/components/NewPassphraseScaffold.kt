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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun NewPassphraseScaffold(
  onDone: (String) -> Unit,
  onCancel: (() -> Unit)? = null,
  generatePassphraseDialog: IGeneratePassphraseDialog,
) {
  var passphrase by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
  var repeat by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
  var showGeneratePassphraseDialog by rememberSaveable { mutableStateOf(false) }
  val passphraseFocus = remember { FocusRequester() }
  val repeatFocus = remember { FocusRequester() }
  var submitted by rememberSaveable { mutableStateOf(false) }

  fun submit() {
    submitted = true
    onDone(passphrase.text)
  }

  Scaffold(
    topBar = {
      @OptIn(ExperimentalMaterial3Api::class) (CenterAlignedTopAppBar(title = { Text("OSafe") }))
    },
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
        label = { Text("Enter your new passphrase...") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { repeatFocus.requestFocus() }),
        modifier = Modifier.fillMaxWidth().focusRequester(passphraseFocus),
      )
      LaunchedEffect(Unit) { passphraseFocus.requestFocus() } // Set focus on start.
      // Repeat
      TextField(
        value = repeat,
        onValueChange = { repeat = it },
        label = { Text("Repeat your new passphrase...") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        isError = passphrase.text != "" && repeat.text != "" && passphrase.text != repeat.text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).focusRequester(repeatFocus),
      )
      // Generate
      Button(
        onClick = { showGeneratePassphraseDialog = true },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("GENERATE")
      }
      // Submit
      Button(
        onClick = { submit() },
        enabled = passphrase.text != "" && passphrase.text == repeat.text && !submitted,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("SUBMIT")
      }
      // Cancel
      if (onCancel != null) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier.padding(top = 16.dp),
          ) {
            Text("CANCEL")
          }
        }
      }

      if (showGeneratePassphraseDialog) {
        generatePassphraseDialog.Show(
          onDone = {
            passphrase = passphrase.copy(it)
            repeat = repeat.copy("")
            repeatFocus.requestFocus()
            showGeneratePassphraseDialog = false
          },
          onDismiss = { showGeneratePassphraseDialog = false },
        )
      }
    }
  }
}

@Preview
@Composable
fun NewPassphraseScaffoldPreview() {
  OSafeTheme {
    NewPassphraseScaffold(
      onDone = {},
      onCancel = {},
      generatePassphraseDialog = { onDone, onDismiss ->
        IGeneratePassphraseDialogPreview(onDone = onDone, onDismiss = onDismiss)
      },
    )
  }
}
