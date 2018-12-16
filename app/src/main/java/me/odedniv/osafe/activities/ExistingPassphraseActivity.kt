package me.odedniv.osafe.activities

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.tasks.Tasks
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import me.odedniv.osafe.R
import me.odedniv.osafe.databinding.ActivityExistingPassphraseBinding
import me.odedniv.osafe.extensions.PREF_BIOMETRIC_CREATED_AT
import me.odedniv.osafe.extensions.PREF_ENCRYPTION_TIMEOUT
import me.odedniv.osafe.extensions.logFailure
import me.odedniv.osafe.extensions.preferences
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.models.asSeconds
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.Message

@Suppress("PrivatePropertyName") // TODO: Migrate to new names.
class ExistingPassphraseActivity : BaseActivity() {
  companion object {
    private data class EncryptionTimeout(val timeout: Duration, val id: Int)

    private val ENCRYPTION_TIMEOUTS =
      arrayOf<EncryptionTimeout>(
        EncryptionTimeout(Duration.ofSeconds(5), R.string.encryption_timeout_immediately),
        EncryptionTimeout(Duration.ofMinutes(1), R.string.encryption_timeout_1minute),
        EncryptionTimeout(Duration.ofMinutes(5), R.string.encryption_timeout_5minutes),
        EncryptionTimeout(Duration.ofHours(1), R.string.encryption_timeout_1hour),
        EncryptionTimeout(Duration.ofHours(6), R.string.encryption_timeout_6hours),
        EncryptionTimeout(Duration.ofDays(1), R.string.encryption_timeout_1day),
        EncryptionTimeout(Duration.ofDays(7), R.string.encryption_timeout_1week),
        EncryptionTimeout(Duration.ofDays(3650), R.string.encryption_timeout_never),
      )
  }

  private lateinit var binding: ActivityExistingPassphraseBinding
  // TODO: Migrate to new names.
  private val seek_encryption_timeout
    get() = binding.seekEncryptionTimeout

  private val edit_passphrase
    get() = binding.editPassphrase

  private val button_submit
    get() = binding.buttonSubmit

  private val text_encryption_timeout
    get() = binding.textEncryptionTimeout

  private val button_biometric
    get() = binding.buttonBiometric

  private val message: Message by lazy {
    IntentCompat.getParcelableExtra(intent, EXTRA_MESSAGE, Message::class.java)!!
  }

  private val biometricKey: Key? by lazy {
    val label =
      Key.Label.Biometric(
        createdAt = Instant.ofEpochSecond(preferences.getLong(PREF_BIOMETRIC_CREATED_AT, 0))
      )
    message.keys.firstOrNull { it.label == label }
  }

  private val biometricDecryptCipher: Cipher? by lazy {
    biometricKey?.content?.biometricDecryptCipher()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val root = LinearLayout(this) // Otherwise content is not vertically centered.
    binding = ActivityExistingPassphraseBinding.inflate(layoutInflater, root, true)
    setContentView(root)

    seek_encryption_timeout.max = ENCRYPTION_TIMEOUTS.size - 1

    edit_passphrase.addTextChangedListener(
      object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
          updateSubmitEnabledState()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      }
    )
    edit_passphrase.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        button_submit.performClick()
        true
      } else {
        false
      }
    }

    seek_encryption_timeout.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          updateEncryptionTimeoutText()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      }
    )

    button_submit.setOnClickListener { usePassphrase() }

    if (biometricDecryptCipher != null) {
      button_biometric.setOnClickListener { useBiometric() }
    } else {
      button_biometric.visibility = View.INVISIBLE
    }
  }

  override fun onPause() {
    preferences.edit().putLong(PREF_ENCRYPTION_TIMEOUT, encryptionTimeout.asSeconds()).apply()
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    val encryptionTimeout = Duration.ofSeconds(preferences.getLong(PREF_ENCRYPTION_TIMEOUT, 0))
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
    button_submit.isEnabled = edit_passphrase.text.toString().isNotBlank()
  }

  private fun updateEncryptionTimeoutText() {
    text_encryption_timeout.setText(ENCRYPTION_TIMEOUTS[seek_encryption_timeout.progress].id)
  }

  private fun usePassphrase() {
    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
        message.keys.firstNotNullOfOrNull { key ->
          if (key.label !is Key.Label.Passphrase) return@firstNotNullOfOrNull null
          key.content
            .decrypt(key.content.decryptCipher(key.label.digest(edit_passphrase.text.toString())))
            ?.let { key to it }
        }
      }
      .addOnSuccessListener { keyAndBaseKey ->
        if (keyAndBaseKey == null) {
          Toast.makeText(this, R.string.wrong_passphrase, Toast.LENGTH_SHORT).show()
          return@addOnSuccessListener
        }
        val (key, baseKey) = keyAndBaseKey
        Encryption.instance =
          Encryption.Instance(
            value = Encryption(key, baseKey),
            owner = this,
            timeout = encryptionTimeout,
          )
        setResult(Activity.RESULT_OK)
        finish()
      }
      .logFailure(this, "UsePassphrase", "Failed decrypting.")
  }

  private fun useBiometric() {
    val biometricPrompt =
      BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
                biometricKey!!.content.decrypt(result.cryptoObject!!.cipher!!)
              }
              .addOnSuccessListener { baseKey ->
                if (baseKey == null) {
                  Toast.makeText(
                      this@ExistingPassphraseActivity,
                      R.string.existing_wrong_biometric,
                      Toast.LENGTH_SHORT,
                    )
                    .show()
                  preferences.edit().remove(PREF_BIOMETRIC_CREATED_AT).apply()
                  button_biometric.visibility = View.INVISIBLE
                  return@addOnSuccessListener
                }
                Encryption.instance =
                  Encryption.Instance(
                    value = Encryption(biometricKey!!, baseKey),
                    owner = this@ExistingPassphraseActivity,
                    timeout = encryptionTimeout,
                  )
                setResult(Activity.RESULT_OK)
                finish()
              }
              .logFailure(this@ExistingPassphraseActivity, "UseBiometric", "Failed decrypting.")
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Toast.makeText(
                applicationContext,
                getString(R.string.biometric_error, errString),
                Toast.LENGTH_SHORT,
              )
              .show()
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Toast.makeText(
                applicationContext,
                getString(R.string.biometric_failed),
                Toast.LENGTH_SHORT,
              )
              .show()
          }
        },
      )
    biometricPrompt.authenticate(
      BiometricPrompt.PromptInfo.Builder()
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setTitle(getString(R.string.existing_use_biometric_title))
        .setNegativeButtonText(getString(R.string.existing_use_biometric_cancel))
        .build(),
      BiometricPrompt.CryptoObject(biometricDecryptCipher!!),
    )
  }

  private val encryptionTimeout: Duration
    get() = ENCRYPTION_TIMEOUTS[seek_encryption_timeout.progress].timeout
}
