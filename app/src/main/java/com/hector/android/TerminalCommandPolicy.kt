package com.hector.android

import java.io.File

/**
 * Restrictive policy for the in-app terminal.
 *
 * Never passes user input to a shell. Only a small set of Android toolbox commands
 * is accepted, shell operators/expansion are rejected, and every file operand is
 * canonicalized inside the app-private terminal workspace.
 */
internal object TerminalCommandPolicy {
    data class ParsedCommand(val name: String, val args: List<String>, val internal: Boolean = false)

    private val allowedExternal = setOf("ls", "mkdir", "touch", "cat", "rm", "cp", "mv", "wc")
    private val forbiddenShellChars = setOf(';', '|', '&', '>', '<', '`', '$', '\n', '\r', '\u0000')

    fun parse(raw: String): ParsedCommand {
        require(raw.isNotBlank()) { "فرمان خالی است." }
        require(raw.length <= 2000) { "فرمان از ۲۰۰۰ نویسه بیشتر است." }
        require(raw.none { it in forbiddenShellChars }) {
            "برای ایمنی، عملگرهای پوسته، زنجیره‌سازی و گسترش متغیرها پشتیبانی نمی‌شوند."
        }
        val tokens = tokenize(raw)
        require(tokens.isNotEmpty()) { "فرمان خالی است." }
        val name = tokens.first()
        val args = tokens.drop(1)
        return when (name) {
            "help" -> ParsedCommand(name, emptyList(), internal = true)
            "pwd", "clear", "date", "id" -> {
                require(args.isEmpty()) { "این فرمان آرگومان نمی‌پذیرد." }
                ParsedCommand(name, args, internal = true)
            }
            "echo" -> ParsedCommand(name, args, internal = true)
            "cd" -> {
                require(args.size <= 1) { "cd فقط یک مسیر می‌پذیرد." }
                ParsedCommand(name, args, internal = true)
            }
            else -> {
                require(name in allowedExternal) { "این فرمان مجاز نیست: $name" }
                require(args.isNotEmpty() || name == "ls" || name == "wc") {
                    "برای این فرمان مسیر یا آرگومان لازم است."
                }
                validateArguments(name, args)
                ParsedCommand(name, args)
            }
        }
    }

    fun resolveWorkspacePath(workspace: File, current: File, value: String): File {
        val root = workspace.canonicalFile
        val supplied = File(value)
        val resolved = (if (supplied.isAbsolute) supplied else File(current, value)).canonicalFile
        require(resolved == root || resolved.path.startsWith(root.path + File.separator)) {
            "دسترسی به مسیر خارج از فضای کاری ترمینال ممنوع است."
        }
        return resolved
    }

    private fun validateArguments(name: String, args: List<String>) {
        require(args.none { it.startsWith("-") }) {
            "گزینه‌های فرمان پشتیبانی نمی‌شوند؛ فقط مسیرهای داخل فضای کاری مجازند."
        }
        when (name) {
            "cp", "mv" -> require(args.size == 2) { "$name به دقیقاً دو مسیر داخل فضای کاری نیاز دارد." }
            "mkdir", "touch", "rm", "cat" ->
                require(args.isNotEmpty()) { "$name به مسیر داخل فضای کاری نیاز دارد." }
            "ls" -> Unit
            "wc" -> require(args.isNotEmpty()) { "wc به مسیر داخل فضای کاری نیاز دارد." }
        }
    }

    private fun tokenize(raw: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false
        for (char in raw.trim()) {
            if (escaped) {
                token.append(char)
                escaped = false
                started = true
                continue
            }
            if (char == '\\' && quote != '\'') {
                escaped = true
                started = true
                continue
            }
            if (quote != null) {
                if (char == quote) quote = null else token.append(char)
                started = true
                continue
            }
            if (char == '\'' || char == '"') {
                quote = char
                started = true
            } else if (char.isWhitespace()) {
                if (started) {
                    result.add(token.toString())
                    token.setLength(0)
                    started = false
                }
            } else {
                token.append(char)
                started = true
            }
        }
        require(!escaped && quote == null) { "نقل‌قول یا escape ناقص است." }
        if (started) result.add(token.toString())
        return result
    }
}
