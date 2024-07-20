package me.odedniv.osafe.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDialogFragment
import me.odedniv.osafe.R
import me.odedniv.osafe.extensions.*
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

    private var listener: Listener? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as Listener
    }

    private var spinnerPassphraseType: Spinner? = null
    private var seekPassphraseLength: SeekBar? = null
    private var textPassphraseLength: TextView? = null
    private var checkPassphraseRuleThreeSymbolTypes: CheckBox? = null
    private var checkPassphraseRuleNotConsecutive: CheckBox? = null
    private var textGeneratedPassphrase: TextView? = null
    private var buttonRegeneratePassphrase: Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val layout = requireActivity().layoutInflater.inflate(R.layout.dialog_generate_passphrase, null)
        spinnerPassphraseType = layout.findViewById(R.id.spinner_passphrase_type)
        seekPassphraseLength = layout.findViewById(R.id.seek_passphrase_length)
        textPassphraseLength = layout.findViewById(R.id.text_passphrase_length)
        checkPassphraseRuleThreeSymbolTypes = layout.findViewById(R.id.check_passphrase_rule_three_symbol_types)
        checkPassphraseRuleNotConsecutive = layout.findViewById(R.id.check_passphrase_rule_not_consecutive)
        textGeneratedPassphrase = layout.findViewById(R.id.text_generated_passphrase)
        buttonRegeneratePassphrase = layout.findViewById(R.id.button_regenerate_passphrase)

        setDefaults()
        regeneratePassphrase()
        initializeEvents()

        return AlertDialog.Builder(activity)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    dismiss()
                }
                .setPositiveButton(R.string.passphrase_insert) { _, _ ->
                    listener?.onInsertPassphrase(textGeneratedPassphrase!!.text.toString())
                    dismiss()
                }
                .create()
    }

    private fun setPassphraseLengthDefaults() {
        if (PASSPHRASE_TYPES[spinnerPassphraseType!!.selectedItemPosition] == PassphraseType.WORDS) {
            seekPassphraseLength!!.max = 10 - 1
            seekPassphraseLength!!.progress = 4 - 1
        } else {
            seekPassphraseLength!!.max = 20 -1
            seekPassphraseLength!!.progress = 8 - 1
        }
    }

    private fun setDefaults() {
        checkPassphraseRuleThreeSymbolTypes!!.isChecked = requireContext().preferences.getBoolean(PREF_GENERATE_RULE_THREE_SYMBOL_TYPES, false)
        checkPassphraseRuleNotConsecutive!!.isChecked = requireContext().preferences.getBoolean(PREF_GENERATE_RULE_NOT_CONSECUTIVE, false)
        try {
            spinnerPassphraseType!!.setSelection(
                    PASSPHRASE_TYPES.indexOf(
                            PassphraseType.valueOf(
                                    requireContext().preferences.getString(
                                            PREF_GENERATE_TYPE,
                                            PASSPHRASE_TYPES[spinnerPassphraseType!!.selectedItemPosition].toString()
                                    )!!
                            )
                    )
            )
        } catch (e: IllegalArgumentException) {
            // unsupported type from preference, saved length is irrelevant
            setPassphraseLengthDefaults()
            return
        }
        setPassphraseLengthDefaults()
        seekPassphraseLength!!.progress = requireContext().preferences.getInt(PREF_GENERATE_LENGTH, seekPassphraseLength!!.progress + 1) - 1
        textPassphraseLength!!.text = (seekPassphraseLength!!.progress + 1).toString()
    }

    private fun regeneratePassphrase() {
        textGeneratedPassphrase!!.text = generatePassphrase(
                type = PASSPHRASE_TYPES[spinnerPassphraseType!!.selectedItemPosition],
                length = seekPassphraseLength!!.progress + 1,
                rules = EnumSet.noneOf(PassphraseRule::class.java).apply {
                    if (checkPassphraseRuleThreeSymbolTypes!!.isChecked) add(PassphraseRule.THREE_SYMBOL_TYPES)
                    if (checkPassphraseRuleNotConsecutive!!.isChecked) add(PassphraseRule.NOT_CONSECUTIVE)
                }
        )
    }

    private fun initializeEvents() {
        var first = true
        spinnerPassphraseType!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (first) {
                    first = false
                    return
                }
                context!!.preferences.edit()
                        .putString(PREF_GENERATE_TYPE, PASSPHRASE_TYPES[spinnerPassphraseType!!.selectedItemPosition].toString())
                        .apply()
                setPassphraseLengthDefaults()
                regeneratePassphrase()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        seekPassphraseLength!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                context!!.preferences.edit()
                        .putInt(PREF_GENERATE_LENGTH, progress + 1)
                        .apply()
                textPassphraseLength!!.text = (progress + 1).toString()
                regeneratePassphrase()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })
        checkPassphraseRuleThreeSymbolTypes!!.setOnCheckedChangeListener { _, isChecked ->
            requireContext().preferences.edit()
                    .putBoolean(PREF_GENERATE_RULE_THREE_SYMBOL_TYPES, isChecked)
                    .apply()
            regeneratePassphrase()
        }
        checkPassphraseRuleNotConsecutive!!.setOnCheckedChangeListener { _, isChecked ->
            requireContext().preferences.edit()
                    .putBoolean(PREF_GENERATE_RULE_NOT_CONSECUTIVE, isChecked)
                    .apply()
            regeneratePassphrase()
        }
        buttonRegeneratePassphrase!!.setOnClickListener { regeneratePassphrase() }
    }
}
