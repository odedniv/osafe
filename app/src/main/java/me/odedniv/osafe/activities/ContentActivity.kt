package me.odedniv.osafe.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_content.*
import me.odedniv.osafe.R
import me.odedniv.osafe.services.KeyStorageService
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ContentActivity : BaseActivity() {
    companion object {
        private const val REQUEST_KEY = 1
    }

    private var ivSpec: IvParameterSpec? = null
    private var keySpec: SecretKeySpec? = null
    private var keyStorage : KeyStorageService.KeyStorageBinder? = null
    private var clearKeyOnResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_content)

        val keyStorageServiceIntent = Intent(this, KeyStorageService::class.java)
        startService(keyStorageServiceIntent)
        bindService(
                keyStorageServiceIntent,
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        keyStorage = service as KeyStorageService.KeyStorageBinder
                        getKey()
                    }
                    override fun onServiceDisconnected(name: ComponentName?) { }
                },
                Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        ivSpec = null
        keySpec = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_KEY -> {
                if (resultCode != Activity.RESULT_OK) {
                    finish()
                    return
                }
                data!!
                rememberKey(
                        iv = data.getByteArrayExtra(EXTRA_IV),
                        key = data.getByteArrayExtra(EXTRA_KEY),
                        keyTimeout = data.getLongExtra(EXTRA_KEY_TIMEOUT, 0)
                )
                clearKeyOnResume = false
                load()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (keySpec != null && clearKeyOnResume) {
            ivSpec = null
            keySpec = null
            getKey()
        } else {
            clearKeyOnResume = true
        }
    }

    override fun onPause() {
        if (keySpec != null) dump()
        super.onPause()
    }

    private fun getKey() {
        ivSpec = keyStorage!!.ivSpec
        keySpec = keyStorage!!.keySpec
        // either timed out, never set, or timed out between retrieval (very unlikely!)
        if (keySpec != null) {
            load()
        } else {
            startActivityForResult(
                    Intent(
                            this,
                            if (preferences.contains(PREF_CONTENT))
                                ExistingKeyActivity::class.java
                            else
                                NewKeyActivity::class.java
                    ),
                    REQUEST_KEY
            )
        }
    }

    private fun rememberKey(iv: ByteArray, key: ByteArray, keyTimeout: Long) {
        ivSpec = IvParameterSpec(iv)
        keySpec = SecretKeySpec(key, "AES")
        keyStorage!!.set(ivSpec!!, keySpec!!, keyTimeout)
    }

    private fun dump() {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        preferences
                .edit()
                .putString(
                        PREF_CONTENT,
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
        val contentBase64 = preferences.getString(PREF_CONTENT, null)
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
            this.ivSpec = null
            this.keySpec = null
            Toast.makeText(this, R.string.wrong_key, Toast.LENGTH_SHORT).show()
            getKey()
        }
    }
}
