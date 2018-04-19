package me.odedniv.osafe.models.storage

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import me.odedniv.osafe.models.Storage
import java.io.FileNotFoundException
import java.util.concurrent.Callable

class FileStorageFormat(private val context: Context) : StorageFormat {
    private var _read = false
    private var _content: ByteArray? = null

    override fun exists(): Task<Boolean> {
        return if (_read) {
            Tasks.forResult(_content != null)
        } else {
            Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<Boolean> {
                val file = context.getFileStreamPath(Storage.FILENAME)
                file != null && file.exists()
            })
        }
    }

    override fun read(): Task<ByteArray?> {
        if (_read) return Tasks.forResult(_content)

        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<ByteArray?> {
            try {
                context.openFileInput(Storage.FILENAME).use {
                    _content = it.readBytes()
                }
            }
            catch (e: FileNotFoundException) { }
            _read = true
            _content
        })
    }

    @Synchronized
    override fun write(content: ByteArray?): Task<Unit> {
        _content = content
        _read = true

        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<Unit> {
            if (content != null) {
                context.openFileOutput(Storage.FILENAME, Context.MODE_PRIVATE).use {
                    it.write(content)
                }
            } else {
                context.deleteFile(Storage.FILENAME)
            }
        })
    }
}