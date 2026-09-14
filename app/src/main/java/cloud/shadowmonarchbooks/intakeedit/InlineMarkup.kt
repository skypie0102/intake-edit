package cloud.shadowmonarchbooks.intakeedit

/** Controlled inline formatting shared with the reader materializer. */
object InlineMarkup {
    private val endnoteIdRegex = Regex("""en-P\d+-\d{2,}""")
    private val tokenRegex = Regex("""\[(?:(/)(b|i|en)|(b|i)|en=(en-P\d+-\d{2,}))]""")

    data class EndnoteReference(val id: String, val anchor: String)

    private data class Token(val closing: Boolean, val tag: String, val endnoteId: String? = null)

    private fun token(match: MatchResult): Token {
        val closing = match.groupValues[1].isNotEmpty()
        if (closing) return Token(true, match.groupValues[2])
        val simple = match.groupValues[3]
        return if (simple.isNotEmpty()) Token(false, simple) else Token(false, "en", match.groupValues[4])
    }

    fun validationError(text: String): String? {
        val stack = ArrayDeque<Token>()
        tokenRegex.findAll(text).forEach { match ->
            val next = token(match)
            if (!next.closing) {
                if (next.tag == "en" && stack.any { it.tag == "en" }) return "Nested endnotes are not allowed."
                stack.addLast(next)
            } else {
                val expected = stack.lastOrNull()
                if (expected?.tag != next.tag) {
                    return "Misnested inline formatting: closing [/${next.tag}] while ${expected?.let { openLabel(it) } ?: "nothing"} is open."
                }
                stack.removeLast()
            }
        }
        if (stack.isNotEmpty()) return "Unclosed inline formatting: ${stack.joinToString(", ") { openLabel(it) }}."
        return null
    }

    fun visibleText(text: String): String = tokenRegex.replace(text, "")

    fun hasVisibleText(text: String): Boolean = validationError(text) == null && visibleText(text).isNotBlank()

    fun endnoteReferences(text: String): List<EndnoteReference> {
        validationError(text)?.let { throw IllegalArgumentException(it) }
        val result = mutableListOf<EndnoteReference>()
        var currentId: String? = null
        var anchor = StringBuilder()
        var cursor = 0
        tokenRegex.findAll(text).forEach { match ->
            if (currentId != null) anchor.append(text.substring(cursor, match.range.first))
            val next = token(match)
            if (next.tag == "en") {
                if (next.closing) {
                    val id = currentId ?: throw IllegalArgumentException("Closing endnote without an open endnote.")
                    result += EndnoteReference(id, anchor.toString())
                    currentId = null
                    anchor = StringBuilder()
                } else {
                    currentId = next.endnoteId
                    anchor = StringBuilder()
                }
            }
            cursor = match.range.last + 1
        }
        return result
    }

    fun referencedEndnoteIds(text: String): Set<String> = endnoteReferences(text).mapTo(linkedSetOf()) { it.id }

    fun stripEndnoteTags(text: String): String = tokenRegex.replace(text) { match ->
        val next = token(match)
        if (next.tag == "en") "" else match.value
    }

    fun isValidEndnoteId(id: String): Boolean = endnoteIdRegex.matches(id)

    private fun openLabel(token: Token): String =
        if (token.tag == "en") "[en=${token.endnoteId}]" else "[${token.tag}]"
}
