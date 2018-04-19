package me.odedniv.osafe.models

import android.content.Context
import android.os.AsyncTask
import android.util.Base64
import android.util.Base64DataException
import android.util.Log
import com.beust.klaxon.Converter
import com.beust.klaxon.JsonValue
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.models.storage.DriveStorageFormat
import me.odedniv.osafe.models.storage.FileStorageFormat
import me.odedniv.osafe.models.storage.StorageFormat
import org.jetbrains.anko.runOnUiThread
import java.util.concurrent.Callable

class Storage(private val context: Context) {
    companion object {
        const val FILENAME = "osafe.aes"
    }

    private val storageFormats = ArrayList<StorageFormat>()

    init {
        storageFormats.add(FileStorageFormat(context))
    }

    fun setGoogleSignInAccount(googleSignInAccount: GoogleSignInAccount?) {
        storageFormats.removeAll { it is DriveStorageFormat }
        if (googleSignInAccount != null) {
            storageFormats.add(DriveStorageFormat(context, googleSignInAccount))
        }
    }

    private var _message: Encryption.Message? = null
    val message: Encryption.Message?
        get() = _message

    val messageExists: Task<Boolean>
        get() {
            if (_message != null) Tasks.forResult(true)
            val tasks = storageFormats.map { it.exists() }
            return Tasks.whenAll(tasks)
                    .onSuccessTask {
                        Tasks.forResult(tasks.any { it.result })
                    }
        }

    fun getMessage(receiver: (message: Encryption.Message?) -> Unit): Task<Unit> {
        val finished = ArrayList<StorageFormat>(storageFormats.size)
        return Tasks.whenAll(
                storageFormats.map { storageFormat ->
                    storageFormat.read()
                            .onSuccessTask { content ->
                                decode(content)
                                        .onSuccessTask { message ->
                                            useBestStorage(
                                                    storageFormat,
                                                    message,
                                                    content,
                                                    finished,
                                                    receiver
                                            )
                                        }
                            }
                }
        ).onSuccessTask { Tasks.forResult(Unit) }
    }

    fun setMessage(message: Encryption.Message?): Task<Unit> {
        _message = message
        return encode(message)
                .onSuccessTask { content ->
                    Tasks.whenAll(storageFormats.map { it.write(content) })
                            .onSuccessTask { Tasks.forResult(Unit) }
                }
    }

    @Synchronized
    private fun useBestStorage(storageFormat: StorageFormat,
                               message: Encryption.Message?,
                               content: ByteArray?,
                               finished: ArrayList<StorageFormat>,
                               receiver: (message: Encryption.Message?) -> Unit): Task<Unit> {
        val task =
                if (finished.size == 0) {
                    // using the first one anyway
                    _message = message
                    context.runOnUiThread { receiver(message) }
                    Tasks.forResult(Unit)
                } else {
                    // comparing with the current best
                    if (message == null) {
                        if (_message != null) {
                            // updating current storage format
                            storageFormat.write(content)
                        } else {
                            Tasks.forResult(Unit)
                        }
                    } else {
                        if (_message == null || _message!!.version < message.version) {
                            // current is better than previous, updating all previous
                            _message = message
                            context.runOnUiThread { receiver(message) }
                            Tasks.whenAll(finished.map { it.write(content) })
                                    .onSuccessTask { Tasks.forResult(Unit) }
                        } else if (_message!!.version > message.version) {
                            // previous is better than current, updating current
                            storageFormat.write(content)
                        } else {
                            Tasks.forResult(Unit)
                        }
                    }
                }
        finished.add(storageFormat)
        return task
    }

    private val klaxon = Klaxon()
            .converter(object : Converter {
                override fun canConvert(cls: Class<*>)
                        = cls == ByteArray::class.java

                override fun toJson(value: Any)
                        = "\"${Base64.encodeToString(value as ByteArray, Base64.DEFAULT)}\""

                override fun fromJson(jv: JsonValue)
                        = Base64.decode(jv.string!!, Base64.DEFAULT)
            })

    private fun decode(content: ByteArray?): Task<Encryption.Message?> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            if (content != null) {
                try {
                    klaxon.parse<Encryption.Message>(content.toString(Charsets.UTF_8))!!
                } catch (e: KlaxonException) {
                    Log.e("ParseMessage", "Failed parsing message", e)
                    null
                } catch (e: Base64DataException) {
                    Log.e("ParseMessage", "Failed parsing message", e)
                    null
                } catch (e: NullPointerException) {
                    Log.e("ParseMessage", "Failed parsing message", e)
                    null
                }
            } else {
                null
            }
        })
    }

    private fun encode(message: Encryption.Message?): Task<ByteArray?> {
        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            if (message != null)
                klaxon.toJsonString(message).toByteArray(Charsets.UTF_8)
            else
                null
        })
    }
}