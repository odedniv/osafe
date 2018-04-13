package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import kotlinx.android.synthetic.main.activity_existing_key.*
import me.odedniv.osafe.Encryption
import me.odedniv.osafe.R

class ExistingKeyActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_existing_key)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            override fun afterTextChanged(p0: Editable?) { updateSubmitEnabledState() }
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
        }
        edit_key.addTextChangedListener(textWatcher)
        button_submit.setOnClickListener { submit() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        updateSubmitEnabledState()
    }

    private fun updateSubmitEnabledState() {
        button_submit.isEnabled = !edit_key.text.toString().isBlank()
    }

    private fun submit() {
        setResult(
                Activity.RESULT_OK,
                Intent()
                        .putExtra(EXTRA_IV, iv)
                        .putExtra(EXTRA_KEY, key)
        )
        finish()
    }

    private val iv: ByteArray
        get() = Base64.decode(preferences.getString(PREF_IV, null), Base64.DEFAULT)

    private val key: ByteArray
        get() = Encryption.generateKey(edit_key.text.toString())
}
