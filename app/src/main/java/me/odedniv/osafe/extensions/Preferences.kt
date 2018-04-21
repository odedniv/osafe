package me.odedniv.osafe.extensions

import android.content.Context
import android.content.SharedPreferences
import android.support.v7.app.AppCompatActivity


val Context.preferences: SharedPreferences
    get() = getSharedPreferences(packageName, AppCompatActivity.MODE_PRIVATE)!!

const val PREF_ENCRYPTION_TIMEOUT = "encryption_timeout"
const val PREF_DRIVE_NEEDS_UPDATE = "drive_updated"
const val PREF_DRIVE_LAST_UPDATED_AT = "drive_version"