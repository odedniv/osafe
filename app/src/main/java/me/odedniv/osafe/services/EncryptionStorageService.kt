package me.odedniv.osafe.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import me.odedniv.osafe.models.Encryption

class EncryptionStorageService : Service() {
    companion object {
        private const val EXTRA_EXPIRE = "expire"
    }

    private var _encryption: Encryption? = null
    private var expiration: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_EXPIRE, false) == true) expired // check expiration
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return EncryptionStorageBinder()
    }

    inner class EncryptionStorageBinder : Binder() {
        fun set(encryption: Encryption, timeout: Long) {
            if (timeout == 0L) {
                _encryption = null
                expiration = 0L
                return
            }
            _encryption = encryption
            expiration = System.currentTimeMillis() + timeout
            // ensuring clearing of encryption from memory
            alarmManager.set(
                    AlarmManager.RTC,
                    expiration,
                    PendingIntent.getService(
                            this@EncryptionStorageService,
                            0,
                            Intent(this@EncryptionStorageService, EncryptionStorageService::class.java)
                                    .putExtra(EXTRA_EXPIRE, true),
                            PendingIntent.FLAG_CANCEL_CURRENT
                    )
            )
        }

        val encryption: Encryption?
            get() = if (!expired) _encryption else null
    }

    private val expired: Boolean
        get() {
            return if (System.currentTimeMillis() > expiration) {
                _encryption = null
                true
            } else {
                false
            }
        }

    private val alarmManager: AlarmManager
        get() = getSystemService(ALARM_SERVICE) as AlarmManager
}
