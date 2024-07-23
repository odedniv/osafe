package me.odedniv.osafe.models

import android.content.SharedPreferences
import android.os.Parcelable
import androidx.core.content.edit
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeneratePassphraseConfig(
  val type: PassphraseType,
  val length: Int,
  val rules: Set<PassphraseRule>,
) : Parcelable {
  fun writePreferences(preferences: SharedPreferences) {
    preferences.edit {
      putString(PREF_GENERATE_TYPE, type.name)
      putInt(PREF_GENERATE_LENGTH, length)
      putStringSet(PREF_GENERATE_RULES, rules.map { it.name }.toSet())
    }
  }

  companion object {
    fun readPreferences(preferences: SharedPreferences): GeneratePassphraseConfig {
      val type =
        PassphraseType.valueOf(
          preferences.getString(PREF_GENERATE_TYPE, PassphraseType.entries.first().name)!!
        )
      return GeneratePassphraseConfig(
        type = type,
        length = preferences.getInt(PREF_GENERATE_LENGTH, type.defaultLength),
        rules =
          preferences
            .getStringSet(PREF_GENERATE_RULES, setOf())!!
            .map { PassphraseRule.valueOf(it) }
            .toSet(),
      )
    }
  }
}

@Parcelize
enum class PassphraseType(val label: String) : Parcelable {
  WORDS("Words"),
  SYMBOLS("Letters, digits, and symbols"),
  LETTERS_AND_DIGITS("Letters and digits"),
  LETTERS("Letters");

  @IgnoredOnParcel
  val defaultLength: Int
    get() = if (this == WORDS) 4 else 8

  @IgnoredOnParcel
  val maxLength: Int
    get() = if (this == WORDS) 10 else 20

  override fun toString() = label
}

@Parcelize
enum class PassphraseRule(val label: String) : Parcelable {
  THREE_SYMBOL_TYPES("3 symbol types"),
  NOT_CONSECUTIVE("No consecutive symbols");

  override fun toString() = label
}

fun generatePassphrase(config: GeneratePassphraseConfig): String {
  var result: String? = null
  while (result == null) {
    result =
      when (config.type) {
        PassphraseType.WORDS -> {
          var r = Array(config.length) { WORDS[RANDOM.nextInt(WORDS.size)] }.joinToString(" ")
          if (config.rules.contains(PassphraseRule.THREE_SYMBOL_TYPES)) {
            r +=
              String(
                charArrayOf(
                  ' ',
                  randomSymbol(SymbolType.DIGITS),
                  randomSymbol(SymbolType.UPPER_CASE),
                )
              )
          }
          r
        }
        PassphraseType.SYMBOLS ->
          String(CharArray(config.length) { randomSymbol(SymbolType.SYMBOLS) })
        PassphraseType.LETTERS_AND_DIGITS ->
          String(
            CharArray(config.length) {
              randomSymbol(SymbolType.LOWER_CASE, SymbolType.UPPER_CASE, SymbolType.DIGITS)
            }
          )
        PassphraseType.LETTERS -> {
          var r =
            String(
              CharArray(config.length) {
                randomSymbol(SymbolType.LOWER_CASE, SymbolType.UPPER_CASE)
              }
            )
          if (config.rules.contains(PassphraseRule.THREE_SYMBOL_TYPES)) {
            r = r.slice(0..config.length - 2) + randomSymbol(SymbolType.SYMBOLS).toString()
          }
          r
        }
      }
    if (!checkRules(result, config.rules)) result = null
  }
  return result
}

private fun randomSymbol(vararg symbolTypes: SymbolType): Char {
  var result = RANDOM.nextInt(symbolTypes.sumOf { it.range.count() })
  symbolTypes.forEach {
    if (result < it.range.count()) {
      return@randomSymbol it.range.first + result
    } else {
      result -= it.range.count()
    }
  }
  throw Exception("Should never happen")
}

private fun checkRules(result: String, rules: Set<PassphraseRule>): Boolean {
  if (rules.contains(PassphraseRule.THREE_SYMBOL_TYPES) && result.length >= 3) {
    val symbolTypes: Set<SymbolType> =
      result
        .map { char ->
          SymbolType.entries.find { symbolType -> symbolType.range.contains(char) }
            ?: SymbolType.SYMBOLS
        }
        .toSet()
    if (symbolTypes.size < 3) return false
  }
  if (rules.contains(PassphraseRule.NOT_CONSECUTIVE)) {
    if (
      result.withIndex().any {
        it.index < result.length - 1 && isConsecutive(it.value, result[it.index + 1])
      }
    )
      return false
  }
  return true
}

private fun isConsecutive(a: Char, b: Char): Boolean {
  return a == b || a == b + 1 || a == b - 1
}

private enum class SymbolType(val range: CharRange) {
  LOWER_CASE('a'..'z'),
  UPPER_CASE('A'..'Z'),
  DIGITS('0'..'9'),
  SYMBOLS(33.toChar()..126.toChar()), // includes the above
}
