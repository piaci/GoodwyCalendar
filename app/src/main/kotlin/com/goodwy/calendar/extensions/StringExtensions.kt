package com.goodwy.calendar.extensions

private val EMOJI_REGEX = Regex(
    "[\uD83C\uDF00-\uD83D\uDDFF]|" +   // misc symbols & pictographs
        "[\uD83D\uDE00-\uD83D\uDE4F]|" +   // emoticons
        "[\uD83D\uDE80-\uD83D\uDEFF]|" +   // transport
        "[\uD83E\uDD00-\uD83E\uDDFF]|" +   // supplemental symbols
        "[\u2600-\u27BF]|" +                // misc symbols
        "[\uD83C\uDDE6-\uD83C\uDDFF]"      // regional indicators (flags)
)
private val LINK_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private val DOUBLE_SPACE_REGEX = Regex("\\s{2,}")

private fun String.normalizeWhitespace(): String =
    replace(DOUBLE_SPACE_REGEX, " ").trim()

fun String.stripEmojis(): String =
    replace(EMOJI_REGEX, "").normalizeWhitespace()

fun String.stripLinks(): String =
    replace(LINK_REGEX, "").normalizeWhitespace()


