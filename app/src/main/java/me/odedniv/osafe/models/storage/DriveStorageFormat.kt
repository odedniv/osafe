package me.odedniv.osafe.models.storage

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.ExecutionOptions
import com.google.android.gms.drive.MetadataChangeSet
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.models.Storage

class DriveStorageFormat(context: Context,
                         googleSignInAccount: GoogleSignInAccount) : StorageFormat {
    private val client = Drive.getDriveResourceClient(context, googleSignInAccount)

    private var _read = false
    private var _content: ByteArray? = null

    override fun exists(): Task<Boolean> {
        return driveFile
                .onSuccessTask { driveFile ->
                    Tasks.forResult(driveFile != null)
                }
    }

    override fun read(): Task<ByteArray?> {
        if (_read) return Tasks.forResult(_content)

        return driveFile
                .onSuccessTask<ByteArray?> { driveFile ->
                    driveFile ?: return@onSuccessTask Tasks.forResult(null)
                    client
                            .openFile(driveFile, DriveFile.MODE_READ_ONLY)
                            .onSuccessTask {
                                it?.inputStream.use {
                                    _content = it?.readBytes()
                                }
                                _read = true
                                Tasks.forResult(_content)
                            }
                }
    }

    @Synchronized
    override fun write(content: ByteArray?): Task<Unit> {
        _content = content
        _read = true

        return driveFile
                .onSuccessTask { driveFile ->
                    if (driveFile == null) {
                        create(content)
                    } else {
                        update(driveFile, content)
                    }
                }

    }

    private fun create(content: ByteArray?): Task<Unit> {
        content ?: return Tasks.forResult(Unit)
        val rootFolderTask = client.rootFolder
        val contentsTask = client.createContents()
        return Tasks.whenAll(rootFolderTask, contentsTask)
                .onSuccessTask {
                    val rootFolder = rootFolderTask.result
                    val driveContents = contentsTask.result

                    driveContents.outputStream.use {
                        it.write(content)
                    }

                    client.createFile(
                            rootFolder!!,
                            MetadataChangeSet.Builder()
                                    .setTitle(Storage.FILENAME)
                                    .setMimeType("application/json")
                                    .build(),
                            driveContents
                    )
                }
                .onSuccessTask { driveFile ->
                    _driveFile = driveFile
                    Tasks.forResult(Unit)
                }
    }

    private fun update(driveFile: DriveFile, content: ByteArray?): Task<Unit> {
        if (content != null) {
            return client
                    .openFile(driveFile, DriveFile.MODE_WRITE_ONLY)
                    .onSuccessTask { driveContents ->
                        driveContents!!.outputStream.use {
                            it.write(content)
                        }
                        client.commitContents(
                                driveContents,
                                MetadataChangeSet.Builder()
                                        .setTitle(Storage.FILENAME)
                                        .setMimeType("application/json")
                                        .build(),
                                ExecutionOptions.Builder()
                                        .setConflictStrategy(ExecutionOptions.CONFLICT_STRATEGY_OVERWRITE_REMOTE)
                                        .build()
                        )
                    }
                    .onSuccessTask { Tasks.forResult(Unit) }
        } else {
            _driveFile = null
            return client.delete(driveFile)
                    .onSuccessTask { Tasks.forResult(Unit) }
        }
    }

    private var _driveFile: DriveFile? = null
    private val driveFile: Task<DriveFile?>
        get() {
            if (_driveFile != null) return Tasks.forResult(_driveFile)
            return client.query(
                    Query.Builder()
                            .addFilter(Filters.eq(SearchableField.TITLE, Storage.FILENAME))
                            .build()
            ).onSuccessTask {
                val task = Tasks.forResult(it?.get(0)?.driveId?.asDriveFile())
                it?.release()
                task
            }
        }
}