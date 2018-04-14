package me.odedniv.osafe.activities

import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    companion object {
        const val PREF_ENCRYPTION_TIMEOUT = "encryption_timeout"

        const val EXTRA_ENCRYPTION = "encryption"
        const val EXTRA_ENCRYPTION_TIMEOUT = "encryption_timeout"
    }

    private var _preferences: SharedPreferences? = null
    protected val preferences: SharedPreferences
        get() {
            if (_preferences == null) {
                _preferences = getSharedPreferences(packageName, MODE_PRIVATE)
            }
            return _preferences!!
        }
}