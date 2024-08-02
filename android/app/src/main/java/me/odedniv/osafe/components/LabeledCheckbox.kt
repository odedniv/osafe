package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun LabeledCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.toggleable(
      value = checked,
      onValueChange = { onCheckedChange(!checked) },
      role = Role.Checkbox,
    ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null, // null recommended for accessibility with screen readers.
    )
    Text(text = label, modifier = Modifier.padding(start = 8.dp))
  }
}

@Preview
@Composable
fun LabeledCheckboxPreview() {
  var value by remember { mutableStateOf(false) }
  OSafeTheme {
    LabeledCheckbox(checked = value, onCheckedChange = { value = it }, label = "Check this")
  }
}
