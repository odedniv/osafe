package me.odedniv.osafe.models

import android.os.Parcel
import java.security.SecureRandom

val RANDOM = SecureRandom()

fun random(size: Int): ByteArray {
    val iv = ByteArray(size)
    RANDOM.nextBytes(iv)
    return iv
}

fun readParcelByteArray(parcel: Parcel): ByteArray {
    val result = ByteArray(parcel.readInt())
    parcel.readByteArray(result)
    return result
}
