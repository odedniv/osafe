package me.odedniv.osafe.models

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.launch
import me.odedniv.osafe.models.encryption.Message
import me.odedniv.osafe.models.storage.DriveStorageFormat
import me.odedniv.osafe.models.storage.FileStorageFormat
import me.odedniv.osafe.models.storage.StorageFormat
import me.odedniv.osafe.models.storage.StorageFormat.Content

class Storage(context: Context, googleSignInAccount: GoogleSignInAccount?) {
  private val formats =
    buildList<StorageFormat> {
      add(FileStorageFormat(context))
      if (googleSignInAccount != null) add(DriveStorageFormat(context, googleSignInAccount))
    }

  fun read(): Flow<Message> =
    // List<StorageFormat>
    formats
      // List<Flow<Pair<StorageFormat, Content>>>
      .map { format ->
        flow<Pair<StorageFormat, Content>> { format.readAndLog()?.let { emit(format to it) } }
      }
      // Flow<Pair<StorageFormat, Content>>
      .merge()
      // Update all outdated formats, prioritizing newest.
      .onAll { all ->
        val sorted = all.sortedBy { (_, content) -> content.modifiedTime }
        val (newestFormat: StorageFormat, newestContent: Content) =
          sorted.lastOrNull() ?: return@onAll
        val map: Map<StorageFormat, Content> = all.toMap()
        coroutineScope {
          for (format in formats) {
            if (format != newestFormat && !map[format]?.bytes.contentEquals(newestContent.bytes)) {
              launch { format.writeAndLog(newestContent) }
            }
          }
        }
      }
      // Flow<Content>
      .map { (_, content) -> content }
      // Prioritizing newest content, skipping older ones.
      .runningReduce { newestContent, currentContent ->
        if (newestContent.modifiedTime > currentContent.modifiedTime) newestContent
        else currentContent
      }
      // Flow<Content> (newest)
      .distinctUntilChanged { old, new -> old.bytes contentEquals new.bytes }
      // Flow<Message>
      .map { Message.decode(it.bytes) }

  suspend fun write(message: Message): Unit = coroutineScope {
    val content = Content(message.encode(), Instant.now())
    for (format in formats) {
      launch { format.writeAndLog(content) }
    }
  }

  private suspend fun StorageFormat.readAndLog(): Content? {
    Log.d(TAG, "$this.read() start")
    return read().also { Log.d(TAG, "$this.read() end") }
  }

  private suspend fun StorageFormat.writeAndLog(content: Content) {
    Log.d(TAG, "$this.write() start")
    write(content)
    Log.d(TAG, "$this.write() end")
  }

  companion object {
    private const val TAG = "Storage"

    const val DEBUG = false
  }
}
