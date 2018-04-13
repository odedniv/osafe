package me.odedniv.osafe.services

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyStorageService : Service() {
    private var _ivSpec: IvParameterSpec? = null
    private var _keySpec: SecretKeySpec? = null
    private var _keyExpiration: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return KeyStorageBinder()
    }

    inner class KeyStorageBinder : Binder() {
        fun set(ivSpec: IvParameterSpec, keySpec: SecretKeySpec, keyTimeout: Long) {
            _ivSpec = ivSpec
            _keySpec = keySpec
            _keyExpiration = System.currentTimeMillis() + keyTimeout
        }

        val ivSpec: IvParameterSpec?
            get() = if (System.currentTimeMillis() < _keyExpiration) _ivSpec else null
        val keySpec: SecretKeySpec?
            get() = if (System.currentTimeMillis() < _keyExpiration) _keySpec else null
    }
}
