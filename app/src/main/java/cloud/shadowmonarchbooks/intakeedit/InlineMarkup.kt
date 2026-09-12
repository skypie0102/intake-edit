package cloud.shadowmonarchbooks.intakeedit

/** Controlled inline formatting shared with the reader materializer. */
object InlineMarkup {
    private val tokenRegex = Regex("""\[(\/)?(b|i)]""")

    fun validationError(text: String): String? {
        val stack = ArrayDeque<String>()
        tokenRegex.findAll(text).forEach { match ->
            val closing = match.groupValues[1].isNotEmpty()
            val tag = match.groupValues[2]
            if (!closing) stack.addLast(tag) else {
                val expected = stack.lastOrNull()
                if (expected != tag) return "Misnested inline formatting: closing [/$tag] while ${expected?.let { "[$it]" } ?: "nothing"} is open."
                stack.removeLast()
            }
        }
        if (stack.isNotEmpty()) return "Unclosed inline formatting: ${stack.joinToString(", ") { "[$it]" }}."
        return null
    }
    fun visibleText(text: String): String = tokenRegex.replace(text, "")
    fun hasVisibleText(text: String): Boolean = validationError(text) == null && visibleText(text).isNotBlank()
}
