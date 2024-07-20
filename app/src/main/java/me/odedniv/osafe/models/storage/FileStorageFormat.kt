package me.odedniv.osafe.models.storage

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.io.FileNotFoundException
import java.util.concurrent.Callable

class FileStorageFormat(private val context: Context) : StorageFormat {
  override val stringId: Int
    get() = 0

  override fun exists(): Task<Boolean> {
    return Tasks.call(
      AsyncTask.THREAD_POOL_EXECUTOR,
      Callable<Boolean> {
        val file = context.getFileStreamPath(StorageFormat.FILENAME)
        file != null && file.exists()
      },
    )
  }

  override fun conflicts(): Task<Boolean> {
    return Tasks.forResult(false)
  }

  override fun read(): Task<ByteArray?> {
    return Tasks.call(
      AsyncTask.THREAD_POOL_EXECUTOR,
      Callable {
        try {
          context.openFileInput(StorageFormat.FILENAME).use {
            return@Callable it.readBytes()
          }
        } catch (e: FileNotFoundException) {
          return@Callable null
        }
      },
    )
  }

  override fun write(content: ByteArray): Task<Unit> {
    return Tasks.call(
      AsyncTask.THREAD_POOL_EXECUTOR,
      Callable<Unit> {
        context.openFileOutput(StorageFormat.FILENAME, Context.MODE_PRIVATE).use {
          it.write(content)
        }
      },
    )
  }

  override fun clear(): Task<Unit> {
    return Tasks.call(
      AsyncTask.THREAD_POOL_EXECUTOR,
      Callable<Unit> { context.deleteFile(StorageFormat.FILENAME) },
    )
  }
}
