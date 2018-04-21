package me.odedniv.osafe.models

import android.os.AsyncTask
import android.os.Parcel
import android.os.Parcelable
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Callable
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Encryption private constructor(private val key: ByteArray) : Parcelable {
    @Suppress("ArrayInDataClass")
    data class Message(val format: Format, val iv: ByteArray, val content: ByteArray) {
        enum class Format(val code: Byte) {
            AES_128(1);

            companion object {
                private val map = Format.values().associateBy(Format::code)
                fun from(format: Byte) = map[format]!!
            }

            val keySize by lazy {
                when (this) {
                    AES_128 -> 16
                }
            }
        }

        companion object {
            fun decode(encoded: ByteArray): Message {
                val stream = encoded.inputStream()
                val format = Format.from(stream.read().toByte())
                return Message(
                        format = format,
                        iv = stream.readBytes(format.keySize),
                        content = stream.readBytes()
                )
            }
        }

        val encoded by lazy {
            byteArrayOf(format.code) + iv.copyOf(format.keySize) + content
        }
    }

    private object Utils {
        fun generateKey(passphrase: String): ByteArray {
            return MessageDigest
                    .getInstance("SHA-512")
                    .digest(passphrase.toByteArray(Charsets.UTF_8))
        }

        fun generateIv(): ByteArray {
            val iv = ByteArray(64)
            SecureRandom().nextBytes(iv)
            return iv
        }

        fun readParcelByteArray(parcel: Parcel): ByteArray {
            val result = ByteArray(parcel.readInt())
            parcel.readByteArray(result)
            return result
        }
    }

    constructor(passphrase: String) : this(
            key = Utils.generateKey(passphrase)
    )

    fun encrypt(content: String): Task<Message> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            val format = Message.Format.AES_128
            val iv: ByteArray = Utils.generateIv().copyOf(format.keySize)
            Message(
                    format = format,
                    iv = iv,
                    content = cipher(Cipher.ENCRYPT_MODE, format, iv)
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
                        key.copyOf(format.keySize),
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
