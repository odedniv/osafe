package me.odedniv.osafe.models.storage

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.drive.*
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.R
import me.odedniv.osafe.extensions.*
import java.util.*

class DriveStorageFormat(private val context: Context,
                         googleSignInAccount: GoogleSignInAccount) : StorageFormat {
    private val client = Drive.getDriveClient(context, googleSignInAccount)
    private val resourceClient = Drive.getDriveResourceClient(context, googleSignInAccount)

    override val stringId: Int
        get() = R.string.storage_format_drive

    override fun exists(): Task<Boolean> {
        return query.onSuccessTask {
            Tasks.forResult(it != null)
        }
    }

    override fun conflicts(): Task<Boolean> {
        if (!context.preferences.getBoolean(PREF_DRIVE_NEEDS_UPDATE, false))
            return Tasks.forResult(false)
        return query
                .onSuccessTask { driveFileMetadata ->
                    // drive needs update, and remote file was updated since the last update from this resourceClient
                    Tasks.forResult(
                            driveFileMetadata != null
                                    && driveFileMetadata.modifiedDate.time > context.preferences.getLong(PREF_DRIVE_LAST_UPDATED_AT, 0L)
                    )
                }
    }

    override fun read(): Task<ByteArray?> {
        if (context.preferences.getBoolean(PREF_DRIVE_NEEDS_UPDATE, false)) return Tasks.forResult(null)
        return query
                .onSuccessTask<ByteArray?> { driveFileMetadata ->
                    driveFileMetadata ?: return@onSuccessTask Tasks.forResult(null)
                    resourceClient
                            .openFile(driveFileMetadata.driveFile, DriveFile.MODE_READ_ONLY)
                            .onSuccessTask {
                                it?.inputStream.use {
                                    Tasks.forResult(it?.readBytes())
                                }
                            }
                }
    }

    override fun write(content: ByteArray): Task<Unit> {
        context.preferences
                .edit()
                .putBoolean(PREF_DRIVE_NEEDS_UPDATE, true)
                .apply()

        _queried = false
        _driveFileMetadata = null

        return query
                .onSuccessTask { driveFileMetadata ->
                    if (driveFileMetadata == null) {
                        create(content)
                    } else {
                        update(driveFileMetadata.driveFile, content)
                    }
                }
                .onSuccessTask { driveFile ->
                    resourceClient.getMetadata(driveFile!!)
                }
                .addOnSuccessListener { metadata ->
                    _queried = true
                    _driveFileMetadata = DriveFileMetadata(
                            driveFile = metadata.driveId.asDriveFile(),
                            modifiedDate = metadata.modifiedDate
                    )
                    context.preferences
                            .edit()
                            .putBoolean(PREF_DRIVE_NEEDS_UPDATE, false)
                            .putLong(PREF_DRIVE_LAST_UPDATED_AT, metadata.modifiedDate.time)
                            .apply()
                }.toResult(Unit)
    }

    private fun create(content: ByteArray): Task<DriveFile> {
        val driveFolderTask = driveFolder
        val contentsTask = resourceClient.createContents()
        return Tasks.whenAll(driveFolderTask, contentsTask)
                .onSuccessTask {
                    val driveFolder = driveFolderTask.result
                    val driveContents = contentsTask.result

                    driveContents.outputStream.use {
                        it.write(content)
                    }

                    resourceClient.createFile(
                            driveFolder!!,
                            MetadataChangeSet.Builder()
                                    .setTitle(StorageFormat.FILENAME)
                                    .setMimeType("application/json")
                                    .build(),
                            driveContents
                    )
                }
    }

    private fun update(driveFile: DriveFile, content: ByteArray): Task<DriveFile> {
        return resourceClient
                .openFile(driveFile, DriveFile.MODE_WRITE_ONLY)
                .onSuccessTask { driveContents ->
                    driveContents!!.outputStream.use {
                        it.write(content)
                    }
                    resourceClient.commitContents(
                            driveContents,
                            MetadataChangeSet.Builder()
                                    .setTitle(StorageFormat.FILENAME)
                                    .setMimeType("application/json")
                                    .build(),
                            ExecutionOptions.Builder()
                                    .setConflictStrategy(ExecutionOptions.CONFLICT_STRATEGY_OVERWRITE_REMOTE)
                                    .build()
                    )
                }.toResult(driveFile)
    }

    override fun clear(): Task<Unit> {
        _driveFileMetadata = null
        _queried = true

        return query
                .onSuccessTask { driveFileMetadata -> resourceClient.delete(driveFileMetadata!!.driveFile) }
                .addOnSuccessListener {
                    context.preferences
                            .edit()
                            .remove(PREF_DRIVE_NEEDS_UPDATE)
                            .remove(PREF_DRIVE_LAST_UPDATED_AT)
                            .apply()
                }.toResult(Unit)
    }

    private data class DriveFileMetadata(
            val driveFile: DriveFile,
            val modifiedDate: Date
    )
    private var _queried = false
    private var _driveFileMetadata: DriveFileMetadata? = null
    private val query: Task<DriveFileMetadata?>
        get() {
            if (_queried) return Tasks.forResult(_driveFileMetadata)
            return driveFolder
                    .onSuccessTask { driveFolder ->
                        client.requestSync()
                                .ignoreFailure()
                                .onSuccessTask {
                                    resourceClient.query(
                                            Query.Builder()
                                                    .addFilter(Filters.`in`(SearchableField.PARENTS, driveFolder!!.driveId))
                                                    .addFilter(Filters.eq(SearchableField.TITLE, StorageFormat.FILENAME))
                                                    .addFilter(Filters.eq(SearchableField.TRASHED, false))
                                                    .build()
                                    )
                                }
                    }
                    .onSuccessTask { metadataBuffer ->
                        _queried = true
                        _driveFileMetadata =
                                if (metadataBuffer!!.count > 0)
                                    DriveFileMetadata(
                                            driveFile = metadataBuffer[0].driveId.asDriveFile(),
                                            modifiedDate = metadataBuffer[0].modifiedDate
                                    )
                                else
                                    null
                        metadataBuffer.release()
                        Tasks.forResult(_driveFileMetadata)
                    }
        }

    private var _driveFolder: DriveFolder? = null
    private val driveFolder: Task<DriveFolder>
        get() {
            if (_driveFolder != null) return Tasks.forResult(_driveFolder)
            return resourceClient.rootFolder
                    .addOnSuccessListener { driveFolder ->
                        _driveFolder = driveFolder
                    }
        }
}