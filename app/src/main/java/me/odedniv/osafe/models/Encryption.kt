package me.odedniv.osafe.models

import android.os.AsyncTask
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Callable
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Encryption private constructor(private val key: ByteArray) : Parcelable {
    @Suppress("ArrayInDataClass")
    data class Message(val format: Format, val iv: ByteArray, val content: ByteArray) {
        enum class Format(val code: Byte) {
            AES_128(0);

            companion object {
                private val map = Format.values().associateBy(Format::code)
                fun from(format: Byte) = map[format]
            }
        }

        constructor(encoded: ByteArray) : this(
                format = Format.from(encoded[0])!!,
                iv = encoded.copyOfRange(1, 17),
                content = encoded.copyOfRange(17, encoded.size)
        )

        val encoded by lazy { byteArrayOf(format.code) + iv + content }
    }

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

    fun encrypt(content: String): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            val iv: ByteArray = Utils.generateIv()
            Message(
                    format = Message.Format.AES_128,
                    iv = iv,
                    content = cipher(Cipher.ENCRYPT_MODE, Message.Format.AES_128, iv)
                            .doFinal(content.toByteArray(Charsets.UTF_8))
            )
        })
    }

    fun decrypt(message: Message): Task<String> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            cipher(Cipher.DECRYPT_MODE, message.format, message.iv)
                    .doFinal(message.content)
                    .toString(Charsets.UTF_8)
        })
    }

    private fun cipher(mode: Int, format: Message.Format, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(
                when (format) {
                    Message.Format.AES_128 -> "AES/CBC/PKCS5Padding"
                }
        )
        cipher.init(
                mode,
                SecretKeySpec(
                        key,
                        when (format) {
                            Message.Format.AES_128 -> "AES"
                        }
                ),
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
