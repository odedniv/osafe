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
import kotlinx.parcelize.Parcelize

@Parcelize
class Message(val keys: Array<Key>, val content: Content) : Parcelable {
  companion object {
    val GSON: Gson =
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

    fun decode(encoded: ByteArray): Message {
      return GSON.fromJson(encoded.toString(Charsets.UTF_8), Message::class.java)
    }
  }

  fun encode(): ByteArray {
    return GSON.toJson(this).toByteArray(Charsets.UTF_8)
  }
}
