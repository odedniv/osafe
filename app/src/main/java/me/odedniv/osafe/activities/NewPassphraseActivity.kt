package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import kotlinx.android.synthetic.main.activity_new_passphrase.*
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.R

class NewPassphraseActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_passphrase)

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSaveEnabledState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        }
        edit_passphrase.addTextChangedListener(textWatcher)
        edit_passphrase_confirm.addTextChangedListener(textWatcher)
        button_save.setOnClickListener { save() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        updateSaveEnabledState()
    }

    private fun updateSaveEnabledState() {
        button_save.isEnabled =
                !edit_passphrase.text.toString().isBlank()
                && edit_passphrase.text.toString() == edit_passphrase_confirm.text.toString()
    }

    private fun save() {
        setResult(
                Activity.RESULT_OK,
                Intent()
                        .putExtra(EXTRA_ENCRYPTION, encryption)
        )
        finish()
    }

    private val encryption: Encryption
        get() = Encryption(passphrase = edit_passphrase.text.toString())
}
