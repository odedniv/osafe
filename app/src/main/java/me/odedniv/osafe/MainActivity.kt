package me.odedniv.osafe

import android.content.DialogInterface
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Base64
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.text.InputType
import java.security.MessageDigest
import javax.crypto.BadPaddingException


class MainActivity : AppCompatActivity() {
    private var ivSpec: IvParameterSpec? = null
    private var keySpec: SecretKeySpec? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        generateIv()
    }

    override fun onResume() {
        super.onResume()
        generateKey(this::load)
    }

    override fun onPause() {
        if (keySpec != null) dump()
        super.onPause()
    }

    private fun generateIv() {
        if (ivSpec != null) return
        val preferences = getPreferences(android.content.Context.MODE_PRIVATE)
        val ivBase64 = preferences.getString(getString(R.string.iv_key), null)
        val iv: ByteArray
        if (ivBase64 != null) {
            iv = Base64.decode(ivBase64, Base64.DEFAULT)
        } else {
            iv = ByteArray(16)
            Random().nextBytes(iv)
            preferences
                    .edit()
                    .putString(getString(R.string.iv_key), Base64.encodeToString(iv, Base64.DEFAULT))
                    .apply()
        }
        ivSpec = IvParameterSpec(iv)
    }

    private fun generateKey(onComplete: () -> Unit) {
        if (keySpec != null) {
            onComplete()
            return
        }

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
                .setView(input)
                .setPositiveButton(R.string.key_ok, object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        val sha = MessageDigest.getInstance("SHA-1")
                        keySpec = SecretKeySpec(
                                MessageDigest
                                        .getInstance("SHA-1")
                                        .digest(input.text.toString().toByteArray(Charsets.UTF_8))
                                        .copyOf(16),
                                "AES"
                        )
                        onComplete()
                    }
                })
                .setNegativeButton(R.string.key_exit, object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        this@MainActivity.finish()
                    }
                })
                .show()
    }

    private fun dump() {
        val preferences = getPreferences(android.content.Context.MODE_PRIVATE)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        preferences
                .edit()
                .putString(
                        getString(R.string.content_key),
                        // String -> UTF-8 -> Encrypt -> Base64
                        Base64.encodeToString(
                                cipher.doFinal(
                                        edit_content.text.toString().toByteArray(Charsets.UTF_8)
                                ),
                                Base64.DEFAULT
                        )
                )
                .apply()
    }

    private fun load() {
        val preferences = getPreferences(android.content.Context.MODE_PRIVATE)
        val contentBase64 = preferences.getString(getString(R.string.content_key), null)
        contentBase64 ?: return

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        try {
            edit_content.setText(
                    // Base64 -> Decrypt -> UTF-8 -> String
                    cipher.doFinal(
                            Base64.decode(
                                    contentBase64,
                                    Base64.DEFAULT
                            )
                    ).toString(Charsets.UTF_8)
            )
        } catch (e: BadPaddingException) {
            this.keySpec = null
            generateKey(this::load);
        }
    }
}
