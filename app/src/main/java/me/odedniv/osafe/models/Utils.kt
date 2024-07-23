package me.odedniv.osafe.models

import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

val RANDOM = SecureRandom()

fun random(size: Int): ByteArray {
  val iv = ByteArray(size)
  RANDOM.nextBytes(iv)
  return iv
}

/** Adds or remove the value to/from the set based on the condition. */
fun <T> Set<T>.toggle(value: T, condition: Boolean): Set<T> =
  if (condition) this + value else this - value

fun <T> Flow<T>.onAll(action: suspend (List<T>) -> Unit): Flow<T> {
  val all = mutableListOf<T>()
  return onEach { all += it }.onCompletion { e -> if (e == null) action(all) }
}
