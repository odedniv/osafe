package me.odedniv.osafe.models.storage

import java.time.Instant

interface StorageFormat {
  companion object {
    const val FILENAME = "osafe.json"
  }

  class Content(val bytes: ByteArray, val modifiedTime: Instant)

  suspend fun read(): Content?

  suspend fun write(content: Content)
}
