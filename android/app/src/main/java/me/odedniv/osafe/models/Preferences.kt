package me.odedniv.osafe.models

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

val Context.preferences: SharedPreferences
  get() = getSharedPreferences(packageName, AppCompatActivity.MODE_PRIVATE)!!

const val PREF_TIMEOUT = "timeout"
const val PREF_BIOMETRIC_CREATED_AT = "biometric_created_at"
const val PREF_GENERATE_TYPE = "generate_type"
const val PREF_GENERATE_LENGTH = "generate_length"
const val PREF_GENERATE_RULES = "generate_rules"
