package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.SeekBar
import me.odedniv.osafe.databinding.ActivityExistingPassphraseBinding
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.R
import me.odedniv.osafe.extensions.PREF_ENCRYPTION_TIMEOUT
import me.odedniv.osafe.extensions.preferences

@Suppress("PrivatePropertyName") // TODO: Migrate to new names.
class ExistingPassphraseActivity : BaseActivity() {
    companion object {
        private data class EncryptionTimeout(val timeout: Long, val id: Int)

        private val ENCRYPTION_TIMEOUTS = arrayOf(
                EncryptionTimeout(0, R.string.encryption_timeout_immediately),
                EncryptionTimeout(1 * 60 * 1000, R.string.encryption_timeout_1minute),
                EncryptionTimeout(5 * 60 * 1000, R.string.encryption_timeout_5minutes),
                EncryptionTimeout(1 * 60 * 60 * 1000, R.string.encryption_timeout_1hour),
                EncryptionTimeout(6 * 60 * 60 * 1000, R.string.encryption_timeout_6hours),
                EncryptionTimeout(1 * 24 * 60 * 60 * 1000, R.string.encryption_timeout_1day),
                EncryptionTimeout(7 * 24 * 60 * 60 * 1000, R.string.encryption_timeout_1week),
                EncryptionTimeout(10L * 365 * 24 * 60 * 60 * 1000, R.string.encryption_timeout_never)
        )
    }

    private lateinit var binding: ActivityExistingPassphraseBinding
    // TODO: Migrate to new names.
    private val seek_encryption_timeout get() = binding.seekEncryptionTimeout
    private val edit_passphrase get() = binding.editPassphrase
    private val button_submit get() = binding.buttonSubmit
    private val text_encryption_timeout get() = binding.textEncryptionTimeout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this) // Otherwise content is not vertically centered.
        binding = ActivityExistingPassphraseBinding.inflate(layoutInflater, root, true)
        setContentView(root)

        seek_encryption_timeout.max = ENCRYPTION_TIMEOUTS.size - 1

        edit_passphrase.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSubmitEnabledState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })
        edit_passphrase.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                button_submit.performClick()
                true
            } else {
                false
            }
        }

        seek_encryption_timeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateEncryptionTimeoutText()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        button_submit.setOnClickListener { submit() }
    }

    override fun onPause() {
        preferences
                .edit()
                .putLong(PREF_ENCRYPTION_TIMEOUT, encryptionTimeout)
                .apply()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val encryptionTimeout = preferences.getLong(PREF_ENCRYPTION_TIMEOUT, 0)
        val progress = ENCRYPTION_TIMEOUTS.indexOfLast { it.timeout <= encryptionTimeout }
        seek_encryption_timeout.progress = if (progress >= 0) progress else 0
        updateEncryptionTimeoutText()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        updateSubmitEnabledState()
        updateEncryptionTimeoutText()
    }

    private fun updateSubmitEnabledState() {
        button_submit.isEnabled = !edit_passphrase.text.toString().isBlank()
    }

    private fun updateEncryptionTimeoutText() {
        text_encryption_timeout.setText(ENCRYPTION_TIMEOUTS[seek_encryption_timeout.progress].id)
    }

    private fun submit() {
        setResult(
                Activity.RESULT_OK,
                Intent()
                        .putExtra(EXTRA_ENCRYPTION, encryption)
                        .putExtra(EXTRA_ENCRYPTION_TIMEOUT, encryptionTimeout)
        )
        finish()
    }

    private val encryption: Encryption
        get() = Encryption(passphrase = edit_passphrase.text.toString())

    private val encryptionTimeout: Long
        get() = ENCRYPTION_TIMEOUTS[seek_encryption_timeout.progress].timeout
}
