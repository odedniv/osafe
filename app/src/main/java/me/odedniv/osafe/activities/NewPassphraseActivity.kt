package me.odedniv.osafe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import kotlinx.android.synthetic.main.activity_new_passphrase.*
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.R
import me.odedniv.osafe.dialogs.GeneratePassphraseDialog
import android.view.inputmethod.InputMethodManager
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.models.encryption.Message

class NewPassphraseActivity : BaseActivity(), GeneratePassphraseDialog.Listener {
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
        edit_passphrase_confirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                button_save.performClick()
                true
            } else {
                false
            }
        }
        button_generate.setOnClickListener { generate() }
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

    private fun generate() {
        GeneratePassphraseDialog().show(supportFragmentManager, "GeneratePassphraseDialog")
    }

    override fun onInsertPassphrase(value: String) {
        edit_passphrase.setText(value)
        edit_passphrase_confirm.setText("")
        if (edit_passphrase_confirm.requestFocus()) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit_passphrase_confirm, InputMethodManager.SHOW_FORCED)
        }
    }

    private fun save() {
        button_save.isEnabled = false
        val passphrase = edit_passphrase.text.toString()
        val encryption = intent.getParcelableExtra<Encryption>(EXTRA_ENCRYPTION)
        encryption ?: return newEncryption(passphrase)

        // changing key on for existing encryption
        val storage = Storage(this)
        storage.state = intent.getParcelableExtra(EXTRA_STORAGE)
        storage.get()
                .onSuccessTask { oldMessage ->
                    oldMessage ?: return@onSuccessTask Tasks.forCanceled<Message>()
                    encryption.changeKey(oldMessage, passphrase)
                }.onSuccessTask { newMessage ->
                    newMessage ?: return@onSuccessTask Tasks.forCanceled<Unit>()
                    storage.set(newMessage)
                }
                .addOnCanceledListener { newEncryption(passphrase) } // no content to re-encrypt
                .addOnSuccessListener {
                    setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_ENCRYPTION, encryption)
                    )
                    finish()
                }
                .addOnFailureListener {
                    // TODO: handle nicely, the encryption was changed while in here
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
    }

    private fun newEncryption(passphrase: String) {
        setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_ENCRYPTION, Encryption(passphrase = passphrase))
        )
        finish()
    }
}
