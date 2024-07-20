package me.odedniv.osafe.models

import java.util.EnumSet

enum class PassphraseType {
  WORDS,
  SYMBOLS,
  LETTERS_AND_DIGITS,
  LETTERS,
}

enum class PassphraseRule {
  THREE_SYMBOL_TYPES,
  NOT_CONSECUTIVE,
}

private enum class SymbolType(val range: CharRange) {
  LOWER_CASE('a'..'z'),
  UPPER_CASE('A'..'Z'),
  DIGITS('0'..'9'),
  SYMBOLS(33.toChar()..126.toChar()), // includes the above
}

fun generatePassphrase(type: PassphraseType, length: Int, rules: EnumSet<PassphraseRule>): String {
  var result: String? = null
  while (result == null) {
    result =
      when (type) {
        PassphraseType.WORDS -> {
          var r = Array(length) { WORDS[RANDOM.nextInt(WORDS.size)] }.joinToString(" ")
          if (rules.contains(PassphraseRule.THREE_SYMBOL_TYPES)) {
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
        PassphraseType.SYMBOLS -> String(CharArray(length) { randomSymbol(SymbolType.SYMBOLS) })
        PassphraseType.LETTERS_AND_DIGITS ->
          String(
            CharArray(length) {
              randomSymbol(SymbolType.LOWER_CASE, SymbolType.UPPER_CASE, SymbolType.DIGITS)
            }
          )
        PassphraseType.LETTERS -> {
          var r =
            String(CharArray(length) { randomSymbol(SymbolType.LOWER_CASE, SymbolType.UPPER_CASE) })
          if (rules.contains(PassphraseRule.THREE_SYMBOL_TYPES)) {
            r = r.slice(0..length - 2) + randomSymbol(SymbolType.SYMBOLS).toString()
          }
          r
        }
      }
    if (!checkRules(result, rules)) result = null
  }
  return result
}

private fun randomSymbol(vararg symbolTypes: SymbolType): Char {
  var result = RANDOM.nextInt(symbolTypes.sumBy { it.range.count() })
  symbolTypes.forEach {
    if (result < it.range.count()) {
      return@randomSymbol it.range.first + result
    } else {
      result -= it.range.count()
    }
  }
  throw Exception("Should never happen")
}

private fun checkRules(result: String, rules: EnumSet<PassphraseRule>): Boolean {
  if (rules.contains(PassphraseRule.THREE_SYMBOL_TYPES) && result.length >= 3) {
    val symbolTypes = EnumSet.noneOf(SymbolType::class.java)
    result.forEach { char ->
      symbolTypes.add(
        SymbolType.values().find { symbolType -> symbolType.range.contains(char) }
          ?: SymbolType.SYMBOLS
      )
    }
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
