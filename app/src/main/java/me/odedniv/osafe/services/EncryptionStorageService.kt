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

  private var bounds = 0
  private var _encryption: Encryption? = null
  private var expiration: Long = 0

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.getBooleanExtra(EXTRA_EXPIRE, false) == true) {
      if (expired && bounds == 0) {
        stopSelf()
      }
    }
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onBind(intent: Intent): IBinder? {
    bounds++
    return EncryptionStorageBinder()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    bounds--
    if (bounds == 0 && expired) stopSelf()
    return super.onUnbind(intent)
  }

  inner class EncryptionStorageBinder : Binder() {
    fun clear() {
      this@EncryptionStorageService.clear()
    }

    fun set(encryption: Encryption, timeout: Long) {
      this@EncryptionStorageService.set(encryption, timeout)
    }

    val encryption: Encryption?
      get() = if (!expired) _encryption else null
  }

  private fun clear() {
    _encryption = null
    expiration = 0L
    alarmManager.cancel(pendingExpireIntent)
  }

  private fun set(encryption: Encryption, timeout: Long) {
    if (timeout == 0L) {
      clear()
      return
    }
    _encryption = encryption
    expiration = System.currentTimeMillis() + timeout
    // ensuring clearing of encryption from memory
    alarmManager.set(AlarmManager.RTC, expiration, pendingExpireIntent)
  }

  private val expired: Boolean
    get() {
      return if (System.currentTimeMillis() > expiration) {
        clear()
        true
      } else {
        false
      }
    }

  private val alarmManager: AlarmManager
    get() = getSystemService(ALARM_SERVICE) as AlarmManager

  private val pendingExpireIntent: PendingIntent
    get() =
      PendingIntent.getService(
        this@EncryptionStorageService,
        0,
        Intent(this@EncryptionStorageService, EncryptionStorageService::class.java)
          .putExtra(EXTRA_EXPIRE, true),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
}
