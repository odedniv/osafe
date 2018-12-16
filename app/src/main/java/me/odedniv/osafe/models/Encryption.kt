package me.odedniv.osafe.models

import android.app.Activity
import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.time.Duration
import javax.crypto.Cipher
import me.odedniv.osafe.models.encryption.Content
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.Message

class Encryption(private val key: Key, private val baseKey: ByteArray) {
  @Volatile private var original: Message? = null

  fun encrypt(content: String): Task<Message> =
    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
      Message(
          keys = ((original?.keys ?: arrayOf()) + key).toSet().toTypedArray(),
          content =
            Content.encrypt(Content.encryptCipher(baseKey), content.toByteArray(Charsets.UTF_8)),
        )
        .also { original = it }
    }

  fun decrypt(message: Message): Task<String?> =
    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
      message.content
        .decrypt(message.content.decryptCipher(baseKey))
        ?.toString(Charsets.UTF_8)
        ?.also { original = message }
    }

  fun changeKey(message: Message, passphrase: String): Task<Message> =
    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
      val keyLabel = Key.Label.Passphrase()
      Message(
          keys =
            ((message.keys.toSet() - key) +
                Key(
                  label = keyLabel,
                  content =
                    Content.encrypt(Content.encryptCipher(keyLabel.digest(passphrase)), baseKey),
                ))
              .toTypedArray(),
          content = message.content,
        )
        .also { original = it }
    }

  fun addKey(message: Message, label: Key.Label, cipher: Cipher): Task<Message> =
    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR) {
      Message(
          keys =
            message.keys +
              Key(label = label, content = Content.encrypt(cipher = cipher, content = baseKey)),
          content = message.content,
        )
        .also { original = it }
    }

  fun removeKeys(message: Message, vararg keys: Key): Task<Message> {
    return Tasks.forResult(
      Message(
          keys = (message.keys.toSet() - keys.toSet()).toTypedArray(),
          content = message.content,
        )
        .also { original = it }
    )
  }

  class Instance
  private constructor(val value: Encryption, val ownerName: String, val timeout: Duration) {
    val owner =
      object {
        override fun equals(other: Any?): Boolean {
          return super.equals(other) || (other is Activity && other.localClassName == ownerName)
        }
      }

    constructor(
      value: Encryption,
      owner: Activity,
      timeout: Duration,
    ) : this(value = value, ownerName = owner.localClassName, timeout = timeout)

    fun withOwner(owner: Activity) = Instance(value, owner, timeout)
  }

  companion object {
    var instance: Instance? = null

    fun fromPassphrase(passphrase: String): Encryption {
      val baseKey = random(64)
      val keyLabel = Key.Label.Passphrase()
      val key =
        Key(
          label = keyLabel,
          content = Content.encrypt(Content.encryptCipher(keyLabel.digest(passphrase)), baseKey),
        )
      return Encryption(key, baseKey)
    }
  }
}
