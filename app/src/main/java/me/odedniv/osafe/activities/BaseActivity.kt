package me.odedniv.osafe.activities

import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    companion object {
        const val PREF_IV = "iv"
        const val PREF_CONTENT = "content"
        const val PREF_KEY_TIMEOUT = "key_timeout"

        const val EXTRA_IV = "iv"
        const val EXTRA_KEY = "key"
        const val EXTRA_KEY_TIMEOUT = "key_timeout"
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