package me.odedniv.osafe.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.odedniv.osafe.theme.OSafeTheme

@Composable
fun LoadingScaffold() {
  Scaffold { innerPadding ->
    Column(
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(innerPadding).fillMaxSize(),
    ) {
      CircularProgressIndicator(modifier = Modifier.size(128.dp))
    }
  }
}

@Preview
@Composable
fun LoadingScaffoldPreview() {
  OSafeTheme { LoadingScaffold() }
}
