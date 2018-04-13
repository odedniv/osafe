package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_new_key.*
import me.odedniv.osafe.Encryption
import me.odedniv.osafe.R

class NewKeyActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_key)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            override fun afterTextChanged(p0: Editable?) { updateSaveEnabledState() }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
        }
        edit_key.addTextChangedListener(textWatcher)
        edit_key_confirm.addTextChangedListener(textWatcher)
        button_save.setOnClickListener { save() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        updateSaveEnabledState()
    }

    private fun updateSaveEnabledState() {
        button_save.isEnabled =
                !edit_key.text.toString().isBlank()
                && edit_key.text.toString().equals(edit_key_confirm.text.toString())
    }

    private fun save() {
        setResult(
                Activity.RESULT_OK,
                Intent()
                        .putExtra(EXTRA_IV, iv)
                        .putExtra(EXTRA_KEY, key)
        )
        finish()
    }

    private val iv: ByteArray
        get() {
            val iv = Encryption.generateIv()
            preferences
                    .edit()
                    .putString(PREF_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                    .apply()
            return iv
        }

    private val key: ByteArray
        get() = Encryption.generateKey(edit_key.text.toString())
}
