package me.odedniv.osafe.models.storage

import android.content.Context
import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.io.FileNotFoundException
import java.util.concurrent.Callable

class FileStorageFormat(private val context: Context) : StorageFormat {
    private var _read = false
    private var _content: ByteArray? = null

    override val stringId: Int
        get() = 0

    override fun exists(): Task<Boolean> {
        return if (_read) {
            Tasks.forResult(_content != null)
        } else {
            Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<Boolean> {
                val file = context.getFileStreamPath(StorageFormat.FILENAME)
                file != null && file.exists()
            })
        }
    }

    override fun conflicts(): Task<Boolean> {
        return Tasks.forResult(false)
    }

    override fun read(): Task<ByteArray?> {
        if (_read) return Tasks.forResult(_content)

        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable {
            try {
                context.openFileInput(StorageFormat.FILENAME).use {
                    _content = it.readBytes()
                }
            }
            catch (e: FileNotFoundException) { }
            _read = true
            _content
        })
    }

    override fun write(content: ByteArray): Task<Unit> {
        _content = content
        _read = true

        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<Unit> {
            context.openFileOutput(StorageFormat.FILENAME, Context.MODE_PRIVATE).use {
                it.write(content)
            }
        })
    }

    override fun clear(): Task<Unit> {
        _content = null
        _read = true

        return Tasks.call(AsyncTask.THREAD_POOL_EXECUTOR, Callable<Unit> {
            context.deleteFile(StorageFormat.FILENAME)
        })
    }
}