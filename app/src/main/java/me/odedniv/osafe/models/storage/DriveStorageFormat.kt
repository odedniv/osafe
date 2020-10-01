package me.odedniv.osafe.models.storage

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import me.odedniv.osafe.R
import me.odedniv.osafe.extensions.PREF_DRIVE_LAST_UPDATED_AT
import me.odedniv.osafe.extensions.PREF_DRIVE_NEEDS_UPDATE
import me.odedniv.osafe.extensions.preferences
import me.odedniv.osafe.extensions.toResult
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class DriveStorageFormat(private val context: Context,
                         private val googleSignInAccount: GoogleSignInAccount) : StorageFormat {
    private val client = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), getCredentials())
            .setApplicationName("OSafe")
            .build()
    private var executor: Executor = Executors.newSingleThreadExecutor()

    private fun getCredentials(): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(context,  listOf(DriveScopes.DRIVE_FILE))
        credential.selectedAccount = googleSignInAccount.account
        return credential
    }

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
                .onSuccessTask { metadata ->
                    // drive needs update, and remote file was updated since the last update from this resourceClient
                    Tasks.forResult(
                            metadata != null
                                    && metadata.modifiedDate.time > context.preferences.getLong(PREF_DRIVE_LAST_UPDATED_AT, 0L)
                    )
                }
    }

    override fun read(): Task<ByteArray?> {
        if (context.preferences.getBoolean(PREF_DRIVE_NEEDS_UPDATE, false)) return Tasks.forResult(null)

        return query
                .onSuccessTask<ByteArray?> { metadata ->
                    metadata ?: return@onSuccessTask Tasks.forResult(null)
                    Tasks.call(executor, Callable {
                        client.files().get(metadata.id).executeMediaAsInputStream().use {
                            it.readBytes()
                        }
                    })
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
                .onSuccessTask { metadata ->
                    if (metadata == null) {
                        create(content)
                    } else {
                        update(metadata.id, content)
                    }
                }
                .addOnSuccessListener { fileId ->
                    val now = Date()
                    _queried = true
                    _driveFileMetadata = DriveFileMetadata(
                            id = fileId,
                            modifiedDate = now
                    )
                    context.preferences
                            .edit()
                            .putBoolean(PREF_DRIVE_NEEDS_UPDATE, false)
                            .putLong(PREF_DRIVE_LAST_UPDATED_AT, now.time)
                            .apply()
                }.toResult(Unit)
    }

    private fun create(content: ByteArray): Task<String> {
        return Tasks.call(executor, Callable {
            client.files().create(
                    File()
                            .setParents(listOf("root"))
                            .setMimeType("application/json")
                            .setName(StorageFormat.FILENAME),
                    ByteArrayContent("application/json", content)
            ).execute().id
        })
    }

    private fun update(fileId: String, content: ByteArray): Task<String> {
        return Tasks.call(executor, Callable {
            client.files().update(
                    fileId,
                    File()
                            .setMimeType("application/json")
                            .setName(StorageFormat.FILENAME),
                    ByteArrayContent("application/json", content)
            ).execute().id
        })
    }

    override fun clear(): Task<Unit> {
        _driveFileMetadata = null
        _queried = true

        return query
                .onSuccessTask { metadata ->
                    Tasks.call(executor, Callable {
                        client.files().delete(metadata!!.id).execute()
                    })
                }
                .addOnSuccessListener {
                    context.preferences
                            .edit()
                            .remove(PREF_DRIVE_NEEDS_UPDATE)
                            .remove(PREF_DRIVE_LAST_UPDATED_AT)
                            .apply()
                }.toResult(Unit)
    }

    private data class DriveFileMetadata(
            val id: String,
            val modifiedDate: Date
    )
    private var _queried = false
    private var _driveFileMetadata: DriveFileMetadata? = null
    private val query: Task<DriveFileMetadata?>
        get() {
            if (_queried) return Tasks.forResult(_driveFileMetadata)
            return Tasks.call(executor, Callable {
                val files = client.files().list()
                        .setQ("name = '${StorageFormat.FILENAME}' and 'root' in parents and trashed = false")
                        .setFields("files(id, modifiedTime)")
                        .execute()
                files.files ?: return@Callable null
                when (files.files.size) {
                    0 -> null
                    1 ->
                        DriveFileMetadata(
                                id = files.files[0].id,
                                modifiedDate = Date(files.files[0].modifiedTime.value)
                        )
                    else -> throw RuntimeException("More than one ${StorageFormat.FILENAME} in Drive.")
                }
            })
        }
}