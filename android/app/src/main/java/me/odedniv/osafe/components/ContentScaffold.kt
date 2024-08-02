package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun ContentScaffold(
  value: String,
  onUpdate: (String) -> Unit,
  writing: Boolean,
  onChangePassphrase: () -> Unit,
  fingerprintSupported: Boolean,
  hasFingerprint: Boolean,
  onAddFingerprint: suspend () -> Unit,
  onRemoveThisFingerprint: suspend () -> Unit,
  otherFingerprints: Int,
  onRemoveOtherFingerprints: suspend () -> Unit,
  generatePassphraseDialog: IGeneratePassphraseDialog,
) {
  val coroutineScope = rememberCoroutineScope()
  var textFieldValue by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
  var readOnly by rememberSaveable { mutableStateOf(true) }
  var loading by rememberSaveable { mutableStateOf(false) }
  var showGeneratePassphraseDialog by rememberSaveable { mutableStateOf(false) }
  val valueFocus = remember { FocusRequester() }

  // Invoke onUpdate.
  LaunchedEffect(textFieldValue) { if (textFieldValue.text != value) onUpdate(textFieldValue.text) }
  // Set focus on edit.
  LaunchedEffect(readOnly) { if (!readOnly) valueFocus.requestFocus() }

  Scaffold(
    modifier = Modifier.imePadding(),
    topBar = {
      @OptIn(ExperimentalMaterial3Api::class)
      CenterAlignedTopAppBar(
        title = { Text("OSafe") },
        actions = {
          // Loading
          if (writing || loading) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 4.dp))
            return@CenterAlignedTopAppBar
          }

          var expanded by rememberSaveable { mutableStateOf(false) }
          IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Filled.MoreVert, "Options") }
          DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Change passphrase
            DropdownMenuItem(
              text = { Text("Change passphrase") },
              onClick = {
                onChangePassphrase()
                expanded = false
              },
            )
            if (!hasFingerprint) {
              if (fingerprintSupported) {
                // Add fingerprint
                DropdownMenuItem(
                  text = { Text("Add fingerprint") },
                  onClick = {
                    loading = true
                    coroutineScope.launch {
                      onAddFingerprint()
                      loading = false
                    }
                    expanded = false
                  },
                )
              }
            } else {
              // Remove fingerprint
              DropdownMenuItem(
                text = { Text("Remove device's fingerprint") },
                onClick = {
                  loading = true
                  coroutineScope.launch {
                    onRemoveThisFingerprint()
                    loading = false
                  }
                  expanded = false
                },
              )
            }
            // Clear fingerprints
            if (otherFingerprints > 0) {
              DropdownMenuItem(
                text = { Text("Remove other fingerprints ($otherFingerprints)") },
                onClick = {
                  loading = true
                  coroutineScope.launch {
                    onRemoveOtherFingerprints()
                    loading = false
                  }
                  expanded = false
                },
              )
            }
          }
        },
      )
    },
    floatingActionButton = {
      Column {
        // Generate
        if (!readOnly) {
          FloatingActionButton(
            onClick = { showGeneratePassphraseDialog = true },
            modifier = Modifier.padding(bottom = 16.dp),
          ) {
            Icon(Icons.Filled.Add, "Generate")
          }
        }
        // Edit
        FloatingActionButton(onClick = { readOnly = !readOnly }) {
          if (readOnly) {
            Icon(Icons.Filled.Edit, "Edit")
          } else {
            Icon(Icons.Filled.Done, "Done")
          }
        }
      }
    },
  ) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) {
      // Search
      SearchField(
        text = textFieldValue.text,
        onFind = { textFieldValue = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      )
      // Content
      TextField(
        value = textFieldValue,
        onValueChange = { textFieldValue = it },
        placeholder = { Text("Write your secrets here...") },
        readOnly = readOnly,
        colors =
          TextFieldDefaults.colors()
            .copy(
              focusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
            ),
        modifier = Modifier.fillMaxSize().focusRequester(valueFocus),
      )
    }
  }

  if (showGeneratePassphraseDialog) {
    generatePassphraseDialog.Show(
      onDone = {
        textFieldValue =
          textFieldValue.copy(
            text =
              textFieldValue.text.replaceRange(
                textFieldValue.selection.start..<textFieldValue.selection.end,
                it,
              ),
            selection =
              TextRange(
                start = textFieldValue.selection.start,
                end = textFieldValue.selection.start + it.length,
              ),
          )
        showGeneratePassphraseDialog = false
      },
      onDismiss = { showGeneratePassphraseDialog = false },
    )
  }
}

@Preview
@Composable
fun ContentScaffoldPreview() {
  val coroutineScope = rememberCoroutineScope()
  var value by remember { mutableStateOf("") }
  var writing by remember { mutableStateOf(false) }
  var hasFingerprint by remember { mutableStateOf(false) }

  OSafeTheme {
    ContentScaffold(
      value = value,
      onUpdate = {
        value = it
        coroutineScope.launch {
          delay(2.seconds)
          writing = true
          delay(1.seconds)
          writing = false
        }
      },
      writing = writing,
      onChangePassphrase = {},
      fingerprintSupported = true,
      hasFingerprint = hasFingerprint,
      onAddFingerprint = {
        delay(1.seconds)
        hasFingerprint = true
      },
      onRemoveThisFingerprint = {
        delay(1.seconds)
        hasFingerprint = false
      },
      otherFingerprints = 3,
      onRemoveOtherFingerprints = {
        delay(1.seconds)
        hasFingerprint = false
      },
      generatePassphraseDialog = { onDone, onDismiss ->
        IGeneratePassphraseDialogPreview(onDone = onDone, onDismiss = onDismiss)
      },
    )
  }
}
