package me.odedniv.osafe.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_content.*
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.R
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.services.EncryptionStorageService
import javax.crypto.BadPaddingException

class ContentActivity : BaseActivity() {
    companion object {
        private const val REQUEST_ENCRYPTION = 1
    }


    private var storage = Storage(this)
    private var encryption: Encryption? = null
    private var encryptionStorage : EncryptionStorageService.EncryptionStorageBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_content)

        startService(encryptionStorageIntent)
    }

    override fun onResume() {
        super.onResume()
        bindService(
                encryptionStorageIntent,
                encryptionStorageConnection,
                Context.BIND_AUTO_CREATE
        )
    }

    override fun onPause() {
        if (encryption != null) dump()
        encryption = null
        unbindService(encryptionStorageConnection)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ENCRYPTION -> {
                if (resultCode != Activity.RESULT_OK) {
                    finish()
                    return
                }
                data!!
                encryption = data.getParcelableExtra(EXTRA_ENCRYPTION)
                encryptionStorage?.set(
                        encryption = encryption!!,
                        timeout = data.getLongExtra(EXTRA_ENCRYPTION_TIMEOUT, 0)
                )
                load()
            }
        }
    }

    private fun getEncryption() {
        encryption = encryptionStorage?.encryption
        if (encryption != null) {
            // from EncryptionStorageService
            load()
        } else {
            // either timed out, never set
            startActivityForResult(
                    Intent(
                            this,
                            if (storage.message == null)
                                NewPassphraseActivity::class.java
                            else
                                ExistingPassphraseActivity::class.java
                    ),
                    REQUEST_ENCRYPTION
            )
        }
    }

    private fun dump() {
        storage.message = encryption!!.encrypt(edit_content.text.toString())
    }

    private fun load() {
        if (storage.message == null) return
        try {
            edit_content.setText(encryption!!.decrypt(storage.message!!))
        } catch (e: BadPaddingException) {
            encryption = null
            Toast.makeText(this, R.string.wrong_passphrase, Toast.LENGTH_SHORT).show()
            getEncryption()
        }
    }

    private val encryptionStorageConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            encryptionStorage = service as EncryptionStorageService.EncryptionStorageBinder
            // may have been received from onActivityResult,
            // meaning the content was already loaded
            if (encryption == null) getEncryption()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            encryptionStorage = null
        }
    }

    private val encryptionStorageIntent: Intent
        get() = Intent(this, EncryptionStorageService::class.java)
}
