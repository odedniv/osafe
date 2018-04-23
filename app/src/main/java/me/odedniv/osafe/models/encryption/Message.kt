package me.odedniv.osafe.models.encryption

import android.util.Base64
import com.squareup.moshi.*

class Message(val keys: Array<Key>, val content: Content) {
    companion object {
        val ADAPTER = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .add(object {
                    @FromJson fun fromJson(base64: String): ByteArray? {
                        return Base64.decode(base64, Base64.NO_WRAP)
                    }

                    @ToJson fun toJson(value: ByteArray?): String? {
                        return Base64.encodeToString(value, Base64.NO_WRAP)
                    }
                })
                .build()
                .adapter(Message::class.java)!!

        fun decode(encoded: ByteArray): Message {
            return ADAPTER.fromJson(encoded.toString(Charsets.UTF_8))!!
        }
    }

    val encoded by lazy { ADAPTER.toJson(this)!!.toByteArray(Charsets.UTF_8) }
}