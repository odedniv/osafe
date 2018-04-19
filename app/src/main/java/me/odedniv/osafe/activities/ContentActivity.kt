package me.odedniv.osafe.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_content.*
import me.odedniv.osafe.models.Encryption
import me.odedniv.osafe.R
import me.odedniv.osafe.models.Storage
import me.odedniv.osafe.services.EncryptionStorageService
import org.jetbrains.anko.doAsync
import java.util.*
import javax.crypto.BadPaddingException

class ContentActivity : BaseActivity() {
    companion object {
        private const val REQUEST_ENCRYPTION = 1
    }

    private var storage = Storage(this)
    private var encryption: Encryption? = null
    private var encryptionStorage : EncryptionStorageService.EncryptionStorageBinder? = null
    private var lastStored: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_content)
        setSupportActionBar(toolbar_content)

        startService(encryptionStorageIntent)

        edit_content.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                dumpLater()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
        })
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
        dumpNow()
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
            doAsync {
                val activity =
                        if (storage.messageExists)
                            ExistingPassphraseActivity::class.java
                        else
                            NewPassphraseActivity::class.java
                runOnUiThread {
                    startActivityForResult(
                            Intent(this@ContentActivity, activity),
                            REQUEST_ENCRYPTION
                    )
                }
            }

        }
    }

    private var dumpLaterTimer: Timer? = null

    private fun dumpLater() {
        encryption ?: return

        if (dumpLaterTimer != null) {
            dumpLaterTimer?.cancel()
            dumpLaterTimer = null
        }

        if (lastStored == edit_content.text.toString()) return

        dumpLaterTimer = Timer()
        dumpLaterTimer!!.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread { progress_spinner.visibility = View.VISIBLE }
                dumpNow()
                runOnUiThread { progress_spinner.visibility = View.GONE }
            }
        }, 2000)
    }

    private fun dumpNow() {
        encryption ?: return

        if (dumpLaterTimer != null) {
            dumpLaterTimer?.cancel()
            dumpLaterTimer = null
        }

        val content = edit_content.text.toString()
        if (lastStored == content) return

        storage.message = encryption!!.encrypt(content, storage.message)
        lastStored = content
    }

    private fun load() {
        progress_spinner.visibility = View.VISIBLE
        storage.getMessage {
            edit_content.isEnabled = true
            progress_spinner.visibility = View.GONE
            it ?: return@getMessage
            var content: String? = null
            try {
                content = encryption!!.decrypt(it)
            } catch (e: BadPaddingException) {
                encryption = null
                Toast.makeText(this, R.string.wrong_passphrase, Toast.LENGTH_SHORT).show()
                getEncryption()
                return@getMessage
            }
            lastStored = content
            edit_content.setText(content)
        }
    }

    private val encryptionStorageConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            encryptionStorage = service as EncryptionStorageService.EncryptionStorageBinder
            // may have been received from onActivityResult,
            // meaning the content was already loaded
            encryption ?: getEncryption()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            encryptionStorage = null
        }
    }

    private val encryptionStorageIntent: Intent
        get() = Intent(this, EncryptionStorageService::class.java)
}
