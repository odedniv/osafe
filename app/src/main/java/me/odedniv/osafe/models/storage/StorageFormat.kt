package me.odedniv.osafe.models.storage

import com.google.android.gms.tasks.Task

interface StorageFormat {
    companion object {
        const val FILENAME = "osafe.json"
    }

    val stringId: Int

    fun exists(): Task<Boolean>
    fun conflicts(): Task<Boolean>
    fun read(): Task<ByteArray?>
    fun write(content: ByteArray): Task<Unit>
    fun clear(): Task<Unit>
}