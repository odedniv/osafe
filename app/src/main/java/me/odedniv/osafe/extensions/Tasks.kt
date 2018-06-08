package me.odedniv.osafe.extensions

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

fun <T, U> Task<T>.toResult(result: U) = onSuccessTask { Tasks.forResult(result) }
fun <T> Task<T>.logFailure(context: Context, tag: String, msg: String) = addOnFailureListener {
    Log.e(tag, msg, it)
    var exc: Throwable = it
    while (exc.cause != null) exc = exc.cause!!
    Toast.makeText(context, msg + "\n" + exc, Toast.LENGTH_LONG).show()
}
fun <T> Task<T>.ignoreFailure() = continueWithTask { Tasks.forResult(Unit) }
