package me.odedniv.osafe.models

import android.os.AsyncTask
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.models.encryption.Content
import me.odedniv.osafe.models.encryption.Key
import me.odedniv.osafe.models.encryption.Message
import java.security.MessageDigest
import java.util.concurrent.Callable

class Encryption private constructor(private val key: ByteArray) : Parcelable {
    private var original: Message? = null
    private var baseKey: ByteArray? = null

    constructor(passphrase: String) : this(
            key = MessageDigest
                    .getInstance("SHA-512")
                    .digest(passphrase.toByteArray(Charsets.UTF_8))
    )

    fun encrypt(content: String): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            if (baseKey == null) {
                baseKey = random(64)
            }
            original = Message(
                    keys = original?.keys ?: Array(1, {
                        Key(
                                label = Key.Label.PASSPHRASE,
                                content = Content.encrypt(
                                        key = key,
                                        content = baseKey!!
                                )
                        )
                    }),
                    content = Content.encrypt(
                            key = baseKey!!,
                            content = content.toByteArray(Charsets.UTF_8)
                    )
            )
            original!!
        })
    }

    fun addKey(): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            original = Message(
                    keys = original!!.keys + Array(1, {
                        Key(
                                label = Key.Label.PASSPHRASE,
                                content = Content.encrypt(
                                        key = key,
                                        content = baseKey!!
                                )
                        )
                    }),
                    content = original!!.content
            )
            original!!
        })
    }

    fun decrypt(message: Message): Task<String> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            val found = message.keys.any {
                if (it.label != Key.Label.PASSPHRASE) return@any false
                try {
                    baseKey = it.content.decrypt(key)
                } catch (e: Exception) {
                    return@any false
                }
                true
            }
            if (!found) throw RuntimeException("Decryption failed")

            val content = message.content.decrypt(baseKey!!).toString(Charsets.UTF_8)
            original = message
            content
        })
    }

    /*
    Parcelable implementation
     */

    private constructor(parcel: Parcel) : this(
            key = readParcelByteArray(parcel)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(key.size)
        parcel.writeByteArray(key)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Encryption> {
        override fun createFromParcel(parcel: Parcel): Encryption {
            return Encryption(parcel)
        }

        override fun newArray(size: Int): Array<Encryption?> {
            return arrayOfNulls(size)
        }
    }
}
