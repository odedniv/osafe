package me.odedniv.osafe.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AppCompatDialogFragment
import android.view.View
import android.widget.*
import me.odedniv.osafe.R
import me.odedniv.osafe.models.PassphraseRule
import me.odedniv.osafe.models.PassphraseType
import me.odedniv.osafe.models.generatePassphrase
import java.util.*

class GeneratePassphraseDialog : AppCompatDialogFragment() {
    companion object {
        private val PASSPHRASE_TYPES = arrayOf(
                PassphraseType.WORDS,
                PassphraseType.SYMBOLS,
                PassphraseType.LETTERS_AND_DIGITS,
                PassphraseType.LETTERS
        )
    }

    interface Listener {
        fun onInsertPassphrase(value: String)
    }

    var listener: Listener? = null
    override fun onAttach(context: Context?) {
        super.onAttach(context)
        listener = context as Listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = activity.layoutInflater.inflate(R.layout.dialog_generate_passphrase, null)
        val spinnerPassphraseType = layout.findViewById<Spinner>(R.id.spinner_passphrase_type)
        val seekPassphraseLength = layout.findViewById<SeekBar>(R.id.seek_passphrase_length)
        val checkPassphraseRuleThreeSymbolTypes = layout.findViewById<CheckBox>(R.id.check_passphrase_rule_three_symbol_types)
        val checkPassphraseRuleNotConsecutive = layout.findViewById<CheckBox>(R.id.check_passphrase_rule_not_consecutive)
        val textGeneratedPassphrase = layout.findViewById<TextView>(R.id.text_generated_passphrase)
        val buttonRegeneratePassphrase = layout.findViewById<Button>(R.id.button_regenerate_passphrase)

        fun regeneratePassphrase() {
            textGeneratedPassphrase.text = generatePassphrase(
                    type = PASSPHRASE_TYPES[spinnerPassphraseType.selectedItemPosition],
                    length = seekPassphraseLength.progress + 1,
                    rules = EnumSet.noneOf(PassphraseRule::class.java).apply {
                        if (checkPassphraseRuleThreeSymbolTypes.isChecked) add(PassphraseRule.THREE_SYMBOL_TYPES)
                        if (checkPassphraseRuleNotConsecutive.isChecked) add(PassphraseRule.NOT_CONSECUTIVE)
                    }
            )
        }

        spinnerPassphraseType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                regeneratePassphrase()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        seekPassphraseLength.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                regeneratePassphrase()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })
        checkPassphraseRuleThreeSymbolTypes.setOnCheckedChangeListener { _, _ -> regeneratePassphrase() }
        checkPassphraseRuleNotConsecutive.setOnCheckedChangeListener { _, _ -> regeneratePassphrase() }
        buttonRegeneratePassphrase.setOnClickListener { regeneratePassphrase() }

        return AlertDialog.Builder(activity)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, { _, _ ->
                    dismiss()
                })
                .setPositiveButton(R.string.passphrase_insert, { _, _ ->
                    listener?.onInsertPassphrase(textGeneratedPassphrase.text.toString())
                    dismiss()
                })
                .create()
    }
}
