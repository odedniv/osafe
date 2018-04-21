package me.odedniv.osafe.extensions

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

fun <T, U> Task<T>.toResult(result: U) = onSuccessTask { Tasks.forResult(result) }
fun <T> Task<T>.logFailure(tag: String, msg: String) = addOnFailureListener { Log.e(tag, msg, it) }
fun <T> Task<T>.ignoreFailure() = continueWithTask { Tasks.forResult(Unit) }
