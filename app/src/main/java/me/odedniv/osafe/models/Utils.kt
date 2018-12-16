package me.odedniv.osafe.models

import java.security.SecureRandom
import java.time.Duration

val RANDOM = SecureRandom()

fun random(size: Int): ByteArray {
  val iv = ByteArray(size)
  RANDOM.nextBytes(iv)
  return iv
}

fun Duration.asSeconds() = toMillis() / 1000
