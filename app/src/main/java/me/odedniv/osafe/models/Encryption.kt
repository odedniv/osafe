package me.odedniv.osafe.models

import android.os.AsyncTask
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.android.parcel.Parcelize
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Callable
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Encryption private constructor(private val key: ByteArray) : Parcelable {
    @Suppress("ArrayInDataClass")
    @Parcelize
    data class Message(
            val iv: ByteArray,
            val content: ByteArray,
            val version: Int
    ) : Parcelable

    constructor(passphrase: String) : this(
            key = Utils.generateKey(passphrase)
    )

    private object Utils {
        fun generateKey(passphrase: String): ByteArray {
            return MessageDigest
                    .getInstance("SHA-1")
                    .digest(passphrase.toByteArray(Charsets.UTF_8))
                    .copyOf(16)
        }

        fun generateIv(): ByteArray {
            val iv = ByteArray(16)
            Random().nextBytes(iv)
            return iv
        }

        fun readParcelByteArray(parcel: Parcel): ByteArray {
            val result = ByteArray(parcel.readInt())
            parcel.readByteArray(result)
            return result
        }
    }

    fun encrypt(content: String, previous: Encryption.Message?): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            val iv: ByteArray = Utils.generateIv()
            Message(
                    iv = iv,
                    content = cipher(Cipher.ENCRYPT_MODE, iv)
                            .doFinal(content.toByteArray(Charsets.UTF_8)),
                    version = if (previous != null) previous.version + 1 else 1
            )
        })
    }

    fun decrypt(message: Message): Task<String> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            cipher(Cipher.DECRYPT_MODE, message.iv)
                    .doFinal(message.content)
                    .toString(Charsets.UTF_8)
        })
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
                mode,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv)
        )
        return cipher
    }

    /*
    Parcelable implementation
     */

    private constructor(parcel: Parcel) : this(
            key = Utils.readParcelByteArray(parcel)
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