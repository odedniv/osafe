package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.widget.SeekBar
import kotlinx.android.synthetic.main.activity_existing_key.*
import me.odedniv.osafe.Encryption
import me.odedniv.osafe.R

class ExistingKeyActivity : BaseActivity() {
    companion object {
        private val KEY_TIMEOUTS = arrayOf(
                0 to R.string.key_timeout_immediately,
                1 * 60 * 1000 to R.string.key_timeout_1minute,
                5 * 60 * 1000 to R.string.key_timeout_5minutes,
                1 * 60 * 60 * 1000 to R.string.key_timeout_1hour,
                6 * 60 * 60 * 1000 to R.string.key_timeout_6hours,
                1 * 24 * 60 * 60 * 1000 to R.string.key_timeout_1day,
                7 * 24 * 60 * 60 * 1000 to R.string.key_timeout_1week,
                -1 to R.string.key_timeout_never
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_existing_key)

        seek_key_timeout.max = KEY_TIMEOUTS.size - 1

        edit_key.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSubmitEnabledState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })

        seek_key_timeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKeyTimeoutText()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        button_submit.setOnClickListener { submit() }
    }

    override fun onPause() {
        preferences
                .edit()
                .putLong(PREF_KEY_TIMEOUT, keyTimeout)
                .apply()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val keyTimeout = preferences.getLong(PREF_KEY_TIMEOUT, 0)
        val progress =
                if (keyTimeout >= 0)
                    KEY_TIMEOUTS.indexOfLast { it.first in 0..keyTimeout }
                else
                    seek_key_timeout.max
        seek_key_timeout.progress = if (progress in 0..seek_key_timeout.max) progress else 0
        updateKeyTimeoutText()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        updateSubmitEnabledState()
        updateKeyTimeoutText()
    }

    private fun updateSubmitEnabledState() {
        button_submit.isEnabled = !edit_key.text.toString().isBlank()
    }

    private fun updateKeyTimeoutText() {
        text_key_timeout.setText(KEY_TIMEOUTS[seek_key_timeout.progress].second)
    }

    private fun submit() {
        setResult(
                Activity.RESULT_OK,
                Intent()
                        .putExtra(EXTRA_IV, iv)
                        .putExtra(EXTRA_KEY, key)
                        .putExtra(EXTRA_KEY_TIMEOUT, keyTimeout)
        )
        finish()
    }

    private val iv: ByteArray
        get() = Base64.decode(preferences.getString(PREF_IV, null), Base64.DEFAULT)

    private val key: ByteArray
        get() = Encryption.generateKey(edit_key.text.toString())

    private val keyTimeout: Long
        get() = KEY_TIMEOUTS[seek_key_timeout.progress].first.toLong()
}
