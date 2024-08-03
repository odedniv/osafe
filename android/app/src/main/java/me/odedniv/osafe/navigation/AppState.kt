package me.odedniv.osafe.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.odedniv.osafe.models.encryption.DecryptedMessage
import me.odedniv.osafe.models.encryption.Message

sealed interface AppState : Parcelable {
  @Parcelize
  data object Unloaded : AppState

  @Parcelize
  data class Encrypted(var message: Message) : AppState

  @Parcelize
  data class Ready(var decrypted: DecryptedMessage) : AppState

  val asEncrypted
    get() = this as Encrypted

  val asReady
    get() = this as Ready
}

