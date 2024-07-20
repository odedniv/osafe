package me.odedniv.osafe.activities

import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_ENCRYPTION = "encryption"
    const val EXTRA_STORAGE = "storage"
    const val EXTRA_ENCRYPTION_TIMEOUT = "encryption_timeout"
  }
}
