package me.odedniv.osafe.extensions

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity


val Context.preferences: SharedPreferences
    get() = getSharedPreferences(packageName, AppCompatActivity.MODE_PRIVATE)!!

const val PREF_ENCRYPTION_TIMEOUT = "encryption_timeout"
const val PREF_DRIVE_NEEDS_UPDATE = "drive_updated"
const val PREF_DRIVE_LAST_UPDATED_AT = "drive_version"
const val PREF_GENERATE_TYPE = "generate_type"
const val PREF_GENERATE_LENGTH = "generate_length"
const val PREF_GENERATE_RULE_THREE_SYMBOL_TYPES = "generate_rule_three_symbol_types"
const val PREF_GENERATE_RULE_NOT_CONSECUTIVE = "generate_rule_not_consecutive"