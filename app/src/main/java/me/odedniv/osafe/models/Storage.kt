package me.odedniv.osafe.models

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.extensions.toResult
import me.odedniv.osafe.models.encryption.Message
import me.odedniv.osafe.models.storage.DriveStorageFormat
import me.odedniv.osafe.models.storage.FileStorageFormat
import me.odedniv.osafe.models.storage.StorageFormat

class Storage(private val context: Context) {
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

    val exists: Task<Boolean>
        get() {
            val tasks = storageFormats.map { it.exists() }
            return Tasks.whenAll(tasks)
                    .onSuccessTask {
                        Tasks.forResult(tasks.any { it.result })
                    }
        }

    val conflicts: Task<List<StorageFormat>>
        get() {
            val tasks = storageFormats.associateBy({ it }, { it.conflicts() })
            return Tasks.whenAll(tasks.values)
                    .onSuccessTask {
                        Tasks.forResult(
                                storageFormats.filter { tasks[it]!!.result }
                        )
                    }
        }

    fun resolveConflictWith(storageFormat: StorageFormat): Task<Unit> {
        return storageFormat.read()
                .onSuccessTask { content ->
                    Tasks.whenAll(
                            storageFormats
                                    .filter { it !== storageFormat }
                                    .map { it.write(content!!) }
                    )
                }.toResult(Unit)
    }

    fun get(receiver: (message: Message?) -> Unit): Task<Unit> {
        // prefer the last storage format (in the list of storage formats)
        var lastStorageFormatIndex: Int = -1
        var lastContent: ByteArray? = null
        val allContents = HashMap<StorageFormat, ByteArray>()
        return Tasks.whenAll(
                storageFormats.mapIndexed { index, storageFormat ->
                    storageFormat.read()
                            .addOnSuccessListener { content ->
                                if (content == null) return@addOnSuccessListener
                                allContents[storageFormat] = content
                                if (lastStorageFormatIndex < index) {
                                    lastStorageFormatIndex = index
                                    lastContent = content
                                }
                                if (lastStorageFormatIndex == index) {
                                    receiver(Message.decode(content))
                                }
                            }
                }
        ).onSuccessTask {
            if (lastContent == null) return@onSuccessTask Tasks.forResult(Unit)
            Tasks.whenAll(
                    storageFormats
                            .filter { !allContents.containsKey(it) || !lastContent!!.contentEquals(allContents[it]!!) }
                            .map { storageFormat -> storageFormat.write(lastContent!!) }
            ).toResult(Unit)
        }.toResult(Unit)
    }

    fun set(message: Message): Task<Unit> {
        val content = message.encoded
        return Tasks.whenAll(storageFormats.map { it.write(content) }).toResult(Unit)
    }

    fun reset(): Task<Unit> {
        return Tasks.whenAll(
                storageFormats.map { it.clear() }
        ).toResult(Unit)
    }
}