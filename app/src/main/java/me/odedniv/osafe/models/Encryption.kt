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

class Encryption: Parcelable {
    private var key: ByteArray? = null
    private var original: Message? = null
    private var baseKey: ByteArray? = null

    private constructor(key: ByteArray) {
        this.key = key
    }

    constructor(passphrase: String) {
        setPassphrase(passphrase)
    }

    private fun setPassphrase(passphrase: String) {
        key = MessageDigest
                .getInstance("SHA-512")
                .digest(passphrase.toByteArray(Charsets.UTF_8))
    }

    fun encrypt(content: String): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            if (baseKey == null) {
                baseKey = random(64)
            }
            original = Message(
                    keys = original?.keys ?: Array(1) {
                        Key(
                                label = Key.Label.PASSPHRASE,
                                content = Content.encrypt(
                                        key = key!!,
                                        content = baseKey!!
                                )
                        )
                    },
                    content = Content.encrypt(
                            key = baseKey!!,
                            content = content.toByteArray(Charsets.UTF_8)
                    )
            )
            original!!
        })
    }

    /*
     Checks the encryption against the message's keys, and assumes the baseKey after successful decryption.
     Returns the key's index in the message, fails the task if the key is invalid.
     */
    private fun check(message: Message): Task<Int> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            var keyIndex: Int? = null
            message.keys.withIndex().any {
                if (it.value.label != Key.Label.PASSPHRASE) return@any false
                try {
                    baseKey = it.value.content.decrypt(key!!)
                } catch (e: Exception) {
                    return@any false
                }
                keyIndex = it.index
                true
            }
            if (keyIndex == null) throw RuntimeException("Decryption failed")
            original = message
            keyIndex!!
        })
    }

    fun decrypt(message: Message): Task<String> {
        return check(message)
                .onSuccessTask {
                    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
                        message.content.decrypt(baseKey!!).toString(Charsets.UTF_8)
                    })
                }
    }

    fun changeKey(message: Message, passphrase: String): Task<Message> {
        return check(message)
                .onSuccessTask { keyIndex ->
                    Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
                        setPassphrase(passphrase)
                        val keys = message.keys.copyOf()
                        keys[keyIndex!!] = Key(
                                label = Key.Label.PASSPHRASE,
                                content = Content.encrypt(
                                        key = key!!,
                                        content = baseKey!!
                                )
                        )
                        original = Message(
                                keys = keys,
                                content = message.content
                        )
                        original!!
                    })
                }
    }

    /*
    Parcelable implementation
     */

    private constructor(parcel: Parcel) : this(
            key = readParcelByteArray(parcel)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(key!!.size)
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
