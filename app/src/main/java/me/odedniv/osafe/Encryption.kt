package me.odedniv.osafe

import java.security.MessageDigest
import java.util.*

class Encryption {
    companion object {
        fun generateIv(): ByteArray {
            val iv = ByteArray(16)
            Random().nextBytes(iv)
            return iv
        }

        fun generateKey(passphrase: String): ByteArray {
            return MessageDigest
                    .getInstance("SHA-1")
                    .digest(passphrase.toByteArray(Charsets.UTF_8))
                    .copyOf(16)
        }
    }
}