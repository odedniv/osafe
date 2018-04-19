package me.odedniv.osafe.models.storage

import com.google.android.gms.tasks.Task

interface StorageFormat {

    fun exists(): Task<Boolean>
    fun read(): Task<ByteArray?>
    fun write(content: ByteArray?): Task<Unit>
}