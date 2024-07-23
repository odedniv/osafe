package me.odedniv.osafe.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun <T> Dropdown(
  selected: T,
  options: List<T>,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }

  @OptIn(ExperimentalMaterial3Api::class)
  (ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    TextField(
      value = selected.toString(),
      readOnly = true,
      singleLine = true,
      onValueChange = {},
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      colors = ExposedDropdownMenuDefaults.textFieldColors(),
      modifier = modifier.menuAnchor(),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      for (option in options) {
        DropdownMenuItem(
          text = { Text(option.toString()) },
          onClick = {
            if (option != selected) onSelected(option)
            expanded = false
          },
          contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
        )
      }
    }
  })
}

@Preview(heightDp = 300)
@Composable
fun DropdownPreview() {
  var selected by remember { mutableStateOf("One") }
  OSafeTheme {
    Dropdown(
      selected = selected,
      options = listOf("One", "Two", "Three"),
      onSelected = { selected = it },
    )
  }
}
