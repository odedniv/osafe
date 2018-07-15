package me.odedniv.osafe.models.encryption

import android.util.Base64
import com.google.gson.*
import java.lang.reflect.Type

class Message(val keys: Array<Key>, val content: Content) {
    companion object {
        val GSON = GsonBuilder()
                .registerTypeAdapter(ByteArray::class.java, object : JsonSerializer<ByteArray>, JsonDeserializer<ByteArray> {
                    override fun serialize(src: ByteArray?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                        return JsonPrimitive(Base64.encodeToString(src!!, Base64.NO_WRAP))
                    }

                    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ByteArray {
                        return Base64.decode(json!!.asString, Base64.NO_WRAP)
                    }

                })
                .create()

        fun decode(encoded: ByteArray): Message {
            return GSON.fromJson(encoded.toString(Charsets.UTF_8), Message::class.java)
        }
    }

    fun encode(): ByteArray {
        return GSON.toJson(this).toByteArray(Charsets.UTF_8)
    }
}