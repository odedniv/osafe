package me.odedniv.osafe.activities

import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.odedniv.osafe.models.Encryption

abstract class BaseActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_STORAGE = "storage"
    const val EXTRA_MESSAGE = "content"
  }

  override fun onStart() {
    super.onStart()

    Encryption.instance = Encryption.instance?.withOwner(this)
  }

  override fun onStop() {
    super.onStop()

    val instance = Encryption.instance ?: return
    if (instance.owner != this) return

    CoroutineScope(Dispatchers.Main).launch {
      delay(instance.timeout.toMillis())
      if (Encryption.instance?.owner == this) return@launch
      Encryption.instance = null
    }
  }
}
