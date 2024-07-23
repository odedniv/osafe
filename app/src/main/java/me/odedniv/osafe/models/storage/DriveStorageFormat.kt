package me.odedniv.osafe.models.storage

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.odedniv.osafe.models.storage.StorageFormat.Content

class DriveStorageFormat(
  private val context: Context,
  private val googleSignInAccount: GoogleSignInAccount,
) : StorageFormat {
  private val client =
    Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), getCredentials())
      .setApplicationName("OSafe")
      .build()

  private fun getCredentials(): GoogleAccountCredential {
    val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
    credential.selectedAccount = googleSignInAccount.account
    return credential
  }

  override suspend fun read(): Content? {
    val metadata = query() ?: return null
    return withContext(DISPATCHER) {
      client.files().get(metadata.id).executeMediaAsInputStream().use {
        Content(it.readBytes(), metadata.modifiedTime)
      }
    }
  }

  override suspend fun write(content: Content) {
    withContext(DISPATCHER) {
      val metadata = query()
      if (metadata == null) {
        create(content)
      } else {
        update(metadata.id, content)
      }
    }
  }

  private suspend fun create(content: Content) {
    withContext(DISPATCHER) {
      client
        .files()
        .create(
          File()
            .setName(StorageFormat.FILENAME)
            .setModifiedTime(DateTime(content.modifiedTime.toEpochMilli())),
          ByteArrayContent("application/json", content.bytes),
        )
        .execute()
    }
  }

  private suspend fun update(fileId: String, content: Content) {
    withContext(DISPATCHER) {
      client
        .files()
        .update(
          fileId,
          File()
            .setName(StorageFormat.FILENAME)
            .setModifiedTime(DateTime(content.modifiedTime.toEpochMilli())),
          ByteArrayContent("application/json", content.bytes),
        )
        .execute()
    }
  }

  private suspend fun query(): DriveFileMetadata? {
    return withContext(DISPATCHER) {
      val client = client
      val files =
        client
          .files()
          .list()
          .setQ("name = '${StorageFormat.FILENAME}' and 'root' in parents and trashed = false")
          .setFields("files(id, modifiedTime)")
          .execute()
      files.files ?: return@withContext null
      when (files.files.size) {
        0 -> null
        1 ->
          DriveFileMetadata(
            id = files.files[0].id,
            modifiedTime = Instant.ofEpochMilli(files.files[0].modifiedTime.value),
          )
        else -> throw RuntimeException("More than one ${StorageFormat.FILENAME} in Drive.")
      }
    }
  }

  private data class DriveFileMetadata(val id: String, val modifiedTime: Instant)

  companion object {
    private val DISPATCHER = Dispatchers.Default
  }
}
