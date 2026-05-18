package com.mojentic.llm.tools.files

/**
 * Translate a shell-style glob pattern into a [Regex].
 *
 * Supported:
 * - `*`  matches any number of characters except `/`
 * - `**` matches any number of characters including `/`
 * - `?`  matches a single character except `/`
 * - `[abc]` matches one character in the set
 *
 * Everything else is treated as a literal. Paths are matched against
 * the forward-slash-normalised form so this works identically on every
 * platform.
 */
internal fun globToRegex(pattern: String): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when (c) {
            '*' -> {
                val isDouble = i + 1 < pattern.length && pattern[i + 1] == '*'
                if (isDouble) {
                    sb.append(".*")
                    i++
                } else {
                    sb.append("[^/]*")
                }
            }
            '?' -> sb.append("[^/]")
            '[' -> {
                val close = pattern.indexOf(']', startIndex = i + 1)
                if (close < 0) {
                    sb.append("\\[")
                } else {
                    sb.append(pattern.substring(i, close + 1))
                    i = close
                }
            }
            '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '\\' ->
                sb.append('\\').append(c)
            else -> sb.append(c)
        }
        i++
    }
    sb.append('$')
    return Regex(sb.toString())
}
