package me.odedniv.osafe.models.encryption

import android.os.Parcelable
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Duration
import java.util.Objects
import javax.crypto.Cipher
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.delay
import kotlinx.parcelize.Parcelize
import me.odedniv.osafe.models.random

@Parcelize
data class Message(val keys: Array<Key>, val content: Content) : Parcelable {
  override fun equals(other: Any?) =
    other is Message && keys contentEquals other.keys && content == other.content

  override fun hashCode() = Objects.hash(keys, content)

  suspend fun decrypt(key: Key, cipher: Cipher): DecryptedMessage? {
    val baseKey: ByteArray = key.content.decrypt(cipher) ?: return null
    val decryptedContent: String =
      requireNotNull(content.decrypt(content.decryptCipher(baseKey))) { "Base key does not match." }
        .toString(Charsets.UTF_8)
    return DecryptedMessage(message = this, baseKey = baseKey, content = decryptedContent)
  }

  fun removeKeys(keys: Set<Key>) = copy(keys = (this.keys.toSet() - keys).toTypedArray())

  fun encode(): ByteArray {
    return GSON.toJson(this).toByteArray(Charsets.UTF_8)
  }

  companion object {
    fun decode(encoded: ByteArray): Message {
      return GSON.fromJson(encoded.toString(Charsets.UTF_8), Message::class.java)
    }
  }
}

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

  suspend fun addKey(keyLabel: Key.Label, cipher: Cipher): Pair<DecryptedMessage, Key> {
    val key = Key(label = keyLabel, content = Content.encrypt(cipher, baseKey))
    return copy(message = message.copy(keys = message.keys + key)) to key
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

private val GSON: Gson =
  GsonBuilder()
    .disableHtmlEscaping()
    .registerTypeAdapter(
      ByteArray::class.java,
      object : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
        override fun serialize(
          src: ByteArray?,
          typeOfSrc: Type?,
          context: JsonSerializationContext?,
        ): JsonElement = JsonPrimitive(Base64.encodeToString(src!!, Base64.NO_WRAP))

        override fun deserialize(
          json: JsonElement?,
          typeOfT: Type?,
          context: JsonDeserializationContext?,
        ): ByteArray = Base64.decode(json!!.asString, Base64.NO_WRAP)
      },
    )
    .registerTypeAdapter(
      Key.Label::class.java,
      object : JsonSerializer<Key.Label>, JsonDeserializer<Key.Label> {
        override fun serialize(
          src: Key.Label?,
          typeOfSrc: Type?,
          context: JsonSerializationContext?,
        ): JsonElement = JsonPrimitive(src!!.toString())

        override fun deserialize(
          json: JsonElement?,
          typeOfT: Type?,
          context: JsonDeserializationContext?,
        ) = Key.Label.fromString(json!!.asString)
      },
    )
    .create()
