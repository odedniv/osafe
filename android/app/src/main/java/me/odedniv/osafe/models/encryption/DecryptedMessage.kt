package me.odedniv.osafe.models.encryption

import android.os.Parcelable
import java.time.Duration
import java.util.Objects
import javax.crypto.Cipher
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import me.odedniv.osafe.models.random

@Parcelize
data class DecryptedMessage(
  val message: Message,
  private val baseKey: ByteArray,
  val content: String,
) : Parcelable {
  override fun equals(other: Any?) =
    other is DecryptedMessage &&
      message == other.message &&
      baseKey contentEquals other.baseKey &&
      content == other.content

  override fun hashCode() = Objects.hash(message, baseKey, content)

  suspend fun updateContent(content: String) =
    copy(
      message =
        message.copy(
          content =
            Content.encrypt(Content.encryptCipher(baseKey), content.toByteArray(Charsets.UTF_8))
        ),
      content = content,
    )

  suspend fun changePassphrase(passphrase: String): DecryptedMessage {
    val keyLabel = Key.Label.Passphrase()
    return copy(
      message =
        message.copy(
          keys =
            (message.keys.filter { it.label !is Key.Label.Passphrase } +
                Key(
                  label = keyLabel,
                  content =
                    Content.encrypt(Content.encryptCipher(keyLabel.digest(passphrase)), baseKey),
                ))
              .toTypedArray()
        )
    )
  }

  suspend fun addKey(keyLabel: Key.Label, cipher: Cipher): DecryptedMessage {
    val key = Key(label = keyLabel, content = Content.encrypt(cipher, baseKey))
    return copy(message = message.copy(keys = message.keys + key))
  }

  fun removeKeys(keys: Set<Key>) =
    copy(message = message.copy(keys = (message.keys.toSet() - keys).toTypedArray()))

  suspend fun remember(timeout: Duration) {
    instance = this
    delay(timeout.toKotlinDuration())
    instance = null
  }

  companion object {
    var instance: DecryptedMessage? = null
      private set

    suspend fun create(passphrase: String): DecryptedMessage {
      val baseKey = random(64)
      val keyLabel = Key.Label.Passphrase()
      return DecryptedMessage(
        message =
          Message(
            keys =
              arrayOf(
                Key(
                  label = keyLabel,
                  content =
                    Content.encrypt(Content.encryptCipher(keyLabel.digest(passphrase)), baseKey),
                )
              ),
            content =
              Content.encrypt(Content.encryptCipher(baseKey), "".toByteArray(Charsets.UTF_8)),
          ),
        baseKey = baseKey,
        content = "",
      )
    }
  }
}
