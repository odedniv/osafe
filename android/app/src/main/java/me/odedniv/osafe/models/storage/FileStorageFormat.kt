package me.odedniv.osafe.models.storage

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.odedniv.osafe.models.storage.StorageFormat.Content

class FileStorageFormat(private val context: Context) : StorageFormat {
  override suspend fun read(): Content? =
    withContext(DISPATCHER) {
      try {
        Content(
          context.openFileInput(StorageFormat.FILENAME).use { it.readBytes() },
          Instant.ofEpochMilli(file.lastModified()),
        )
      } catch (e: FileNotFoundException) {
        null
      }
    }

  override suspend fun write(content: Content) {
    withContext(DISPATCHER) {
      context.openFileOutput(StorageFormat.FILENAME, Context.MODE_PRIVATE).use {
        it.write(content.bytes)
      }
      file.setLastModified(content.modifiedTime.toEpochMilli())
    }
  }

  private val file: File
    get() = File(context.filesDir, StorageFormat.FILENAME)

  companion object {
    private val DISPATCHER = Dispatchers.Default
  }
}
