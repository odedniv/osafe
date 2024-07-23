package me.odedniv.osafe.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun SearchField(text: String, onFind: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
  val keywords = text.split(WORD_SEPARATOR_PATTERN)
  var query by rememberSaveable { mutableStateOf("") }
  var active by rememberSaveable { mutableStateOf(false) }
  var matches by rememberSaveable { mutableStateOf(listOf<Int>()) }
  var currentMatchIndex by rememberSaveable { mutableIntStateOf(-1) }

  fun triggerOnFind() {
    val startPosition = matches[currentMatchIndex]
    onFind(
      TextFieldValue(
        text,
        selection = TextRange(start = startPosition, end = startPosition + query.length),
      )
    )
  }

  // Perform search
  LaunchedEffect(query) {
    if (query.isBlank()) {
      matches = listOf()
      currentMatchIndex = -1
      return@LaunchedEffect
    }
    matches = text.findAllPositions(query)
    val previousCurrentMatchIndex = currentMatchIndex
    currentMatchIndex = if (matches.isEmpty()) -1 else 0
    // Avoid double trigger (from changed currentMatchIndex).
    if (previousCurrentMatchIndex == currentMatchIndex) triggerOnFind()
  }
  // Change match
  LaunchedEffect(currentMatchIndex) {
    if (currentMatchIndex == -1) return@LaunchedEffect
    triggerOnFind()
  }

  @OptIn(ExperimentalMaterial3Api::class)
  (DockedSearchBar(
    query = query,
    onQueryChange = { query = it },
    onSearch = {
      query = it
      active = false
    },
    active = active,
    onActiveChange = { active = it },
    placeholder = { Text("Search...") },
    enabled = text.isNotBlank(),
    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
    trailingIcon = {
      if (currentMatchIndex == -1) return@DockedSearchBar
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = {
            currentMatchIndex =
              if (currentMatchIndex > 0) currentMatchIndex - 1 else matches.size - 1
          }
        ) {
          Icon(Icons.Default.KeyboardArrowUp, "Previous")
        }
        Text("${currentMatchIndex + 1}/${matches.size}")
        IconButton(
          onClick = {
            currentMatchIndex =
              if (currentMatchIndex < matches.size - 1) currentMatchIndex + 1 else 0
          }
        ) {
          Icon(Icons.Default.KeyboardArrowDown, "Next")
        }
      }
    },
    modifier = modifier,
  ) {
    val relevantKeywords =
      keywords.asSequence().filter { query.lowercase() in it.lowercase() }.distinct()
    for (keyword in relevantKeywords) {
      ListItem(
        headlineContent = { Text(keyword) },
        modifier =
          Modifier.clickable {
              query = keyword
              active = false
            }
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
      )
      HorizontalDivider()
    }
  })
}

/** Returns all start positions of query (ignore case) in this. */
private fun String.findAllPositions(query: String) =
  buildList<Int> {
    var last = -1
    while (true) {
      val next = indexOf(query, startIndex = last + 1, ignoreCase = true)
      if (next == -1) break
      add(next)
      last = next
    }
  }

private val WORD_SEPARATOR_PATTERN = "\\W+".toRegex()

@Preview
@Composable
fun SearchFieldPreview() {
  OSafeTheme { SearchField(text = "There are many. are options", onFind = {}) }
}
