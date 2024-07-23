package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun CardDialog(
  onDismissRequest: () -> Unit,
  properties: DialogProperties = DialogProperties(),
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(onDismissRequest = onDismissRequest, properties = properties) {
    Card { Column(modifier = Modifier.padding(16.dp)) { content() } }
  }
}

@Preview
@Composable
fun CardDialogPreview() {
  OSafeTheme { CardDialog(onDismissRequest = {}) { Text("Dialog") } }
}
