package me.odedniv.osafe.activities

import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    companion object {
        const val PREF_ENCRYPTION_TIMEOUT = "encryption_timeout"

        const val EXTRA_ENCRYPTION = "encryption"
        const val EXTRA_ENCRYPTION_TIMEOUT = "encryption_timeout"
    }

    protected val preferences by lazy { getSharedPreferences(packageName, MODE_PRIVATE)!! }
}