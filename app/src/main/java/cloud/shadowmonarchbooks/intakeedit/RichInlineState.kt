package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration

enum class InlineStyle { BOLD, ITALIC }

private data class MarkupTag(val kind: String, val endnoteId: String? = null)

data class RichInlineState(
    val text: String,
    val selection: TextRange,
    private val bold: List<Boolean>,
    private val italic: List<Boolean>,
    private val endnote: List<String?>,
) {
    init {
        require(bold.size == text.length)
        require(italic.size == text.length)
        require(endnote.size == text.length)
    }

    fun asTextFieldValue(): TextFieldValue {
        val builder = AnnotatedString.Builder(text)
        addRuns(builder, bold) { SpanStyle(fontWeight = FontWeight.Bold) }
        addRuns(builder, italic) { SpanStyle(fontStyle = FontStyle.Italic) }
        addEndnoteRuns(builder, endnote)
        return TextFieldValue(builder.toAnnotatedString(), selection = selection.coerce(text.length))
    }

    fun edited(next: TextFieldValue): RichInlineState {
        val nextText = next.text
        if (nextText == text) return copy(selection = next.selection.coerce(nextText.length))

        val prefix = commonPrefix(text, nextText)
        val suffix = commonSuffix(text, nextText, prefix)
        val oldChangedEnd = text.length - suffix
        val newChangedEnd = nextText.length - suffix
        val insertedLength = (newChangedEnd - prefix).coerceAtLeast(0)

        val inheritedBold = inheritedStyle(bold, prefix, oldChangedEnd)
        val inheritedItalic = inheritedStyle(italic, prefix, oldChangedEnd)
        val inheritedEndnote = inheritedEndnote(endnote, prefix, oldChangedEnd)
        val nextBold = MutableList(nextText.length) { false }
        val nextItalic = MutableList(nextText.length) { false }
        val nextEndnote = MutableList<String?>(nextText.length) { null }

        for (i in 0 until prefix) {
            nextBold[i] = bold[i]
            nextItalic[i] = italic[i]
            nextEndnote[i] = endnote[i]
        }
        for (i in 0 until insertedLength) {
            val target = prefix + i
            nextBold[target] = inheritedBold
            nextItalic[target] = inheritedItalic
            nextEndnote[target] = inheritedEndnote
        }
        for (i in 0 until suffix) {
            val oldIndex = text.length - suffix + i
            val newIndex = nextText.length - suffix + i
            nextBold[newIndex] = bold[oldIndex]
            nextItalic[newIndex] = italic[oldIndex]
            nextEndnote[newIndex] = endnote[oldIndex]
        }

        return RichInlineState(
            nextText,
            next.selection.coerce(nextText.length),
            nextBold,
            nextItalic,
            nextEndnote,
        )
    }

    fun replaceSelection(replacement: String): RichInlineState {
        val range = selectedBounds() ?: return this
        val start = range.first
        val endExclusive = range.last + 1
        val nextText = text.replaceRange(start, endExclusive, replacement)
        val nextSelection = TextRange(start, start + replacement.length)
        return copy(selection = TextRange(start, endExclusive)).edited(
            TextFieldValue(nextText, selection = nextSelection),
        )
    }

    fun toggle(style: InlineStyle): RichInlineState {
        val range = selectedBounds() ?: return this
        val source = if (style == InlineStyle.BOLD) bold else italic
        val remove = range.all { source[it] }
        val updated = source.toMutableList()
        for (i in range) updated[i] = !remove
        return if (style == InlineStyle.BOLD) copy(bold = updated) else copy(italic = updated)
    }

    fun applyEndnote(id: String): RichInlineState {
        require(InlineMarkup.isValidEndnoteId(id)) { "Invalid endnote id: $id" }
        val range = selectedBounds() ?: return this
        require(range.none { endnote[it] != null }) { "Selection overlaps an existing endnote." }
        val updated = endnote.toMutableList()
        for (i in range) updated[i] = id
        return copy(endnote = updated)
    }

    fun removeEndnote(id: String): RichInlineState = copy(
        endnote = endnote.map { if (it == id) null else it },
    )

    fun endnoteIdsInSelection(): Set<String> {
        val range = selectedBounds() ?: return emptySet()
        return range.mapNotNullTo(linkedSetOf()) { endnote[it] }
    }

    fun selectionIsEntirelyEndnote(id: String): Boolean {
        val range = selectedBounds() ?: return false
        return range.all { endnote[it] == id }
    }

    fun selectionOverlapsEndnote(): Boolean {
        val range = selectedBounds() ?: return false
        return range.any { endnote[it] != null }
    }

    fun selectedText(): String {
        val range = selectedBounds() ?: return ""
        return text.substring(range.first, range.last + 1)
    }

    fun endnoteIdAtCaret(): String? {
        if (selection.start != selection.end) return null
        val offset = selection.start.coerceIn(0, text.length)
        return endnote.getOrNull(offset) ?: endnote.getOrNull(offset - 1)
    }

    fun anchorForEndnote(id: String): String = buildString {
        text.indices.forEach { index -> if (endnote[index] == id) append(text[index]) }
    }

    fun rangeForEndnote(id: String): TextRange? {
        val start = endnote.indexOfFirst { it == id }
        if (start < 0) return null
        var end = start + 1
        while (end < endnote.size && endnote[end] == id) end++
        return TextRange(start, end)
    }

    fun moveCaretToEnd(): RichInlineState = copy(selection = TextRange(text.length))

    fun hasSelection(): Boolean = selection.start != selection.end

    fun toMarkup(): String {
        if (text.isEmpty()) return ""
        val out = StringBuilder(text.length + 32)
        var current = emptyList<MarkupTag>()
        for (index in 0..text.length) {
            val desired = if (index == text.length) {
                emptyList()
            } else {
                buildList {
                    endnote[index]?.let { add(MarkupTag("en", it)) }
                    if (bold[index]) add(MarkupTag("b"))
                    if (italic[index]) add(MarkupTag("i"))
                }
            }
            var common = 0
            while (common < current.size && common < desired.size && current[common] == desired[common]) common++
            for (i in current.lastIndex downTo common) out.append(closeTag(current[i]))
            for (i in common until desired.size) out.append(openTag(desired[i]))
            current = desired
            if (index < text.length) out.append(text[index])
        }
        return out.toString()
    }

    private fun selectedBounds(): IntRange? {
        val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val endExclusive = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        if (start == endExclusive) return null
        return start until endExclusive
    }

    companion object {
        private val endnoteOpenRegex = Regex("""\[en=(en-P\d+-\d{2,})]""")

        fun fromMarkup(markup: String, selection: TextRange? = null): RichInlineState {
            InlineMarkup.validationError(markup)?.let { throw IllegalArgumentException(it) }
            val visible = StringBuilder()
            val bold = mutableListOf<Boolean>()
            val italic = mutableListOf<Boolean>()
            val endnote = mutableListOf<String?>()
            var boldDepth = 0
            var italicDepth = 0
            var currentEndnote: String? = null
            var index = 0
            while (index < markup.length) {
                when {
                    markup.startsWith("[b]", index) -> { boldDepth++; index += 3 }
                    markup.startsWith("[/b]", index) -> { boldDepth--; index += 4 }
                    markup.startsWith("[i]", index) -> { italicDepth++; index += 3 }
                    markup.startsWith("[/i]", index) -> { italicDepth--; index += 4 }
                    markup.startsWith("[/en]", index) -> { currentEndnote = null; index += 5 }
                    markup.startsWith("[en=", index) -> {
                        val match = endnoteOpenRegex.find(markup, index)
                            ?.takeIf { it.range.first == index }
                            ?: throw IllegalArgumentException("Invalid endnote marker at position $index.")
                        currentEndnote = match.groupValues[1]
                        index = match.range.last + 1
                    }
                    else -> {
                        visible.append(markup[index])
                        bold.add(boldDepth > 0)
                        italic.add(italicDepth > 0)
                        endnote.add(currentEndnote)
                        index++
                    }
                }
            }
            val text = visible.toString()
            return RichInlineState(
                text,
                (selection ?: TextRange(text.length)).coerce(text.length),
                bold,
                italic,
                endnote,
            )
        }

        fun fromClipboard(text: String): RichInlineState {
            val withoutEndnotes = InlineMarkup.stripEndnoteTags(text)
            return if (InlineMarkup.validationError(withoutEndnotes) == null) fromMarkup(withoutEndnotes) else plain(text)
        }

        fun plain(text: String): RichInlineState = RichInlineState(
            text = text,
            selection = TextRange(text.length),
            bold = List(text.length) { false },
            italic = List(text.length) { false },
            endnote = List(text.length) { null },
        )

        private fun addRuns(builder: AnnotatedString.Builder, flags: List<Boolean>, style: () -> SpanStyle) {
            var start = -1
            for (i in 0..flags.size) {
                val active = i < flags.size && flags[i]
                if (active && start < 0) start = i
                if (!active && start >= 0) {
                    builder.addStyle(style(), start, i)
                    start = -1
                }
            }
        }

        private fun addEndnoteRuns(builder: AnnotatedString.Builder, ids: List<String?>) {
            var start = -1
            var activeId: String? = null
            for (i in 0..ids.size) {
                val id = ids.getOrNull(i)
                if (id != activeId) {
                    if (start >= 0) builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, i)
                    activeId = id
                    start = if (id != null) i else -1
                }
            }
        }

        private fun commonPrefix(a: String, b: String): Int {
            val limit = minOf(a.length, b.length)
            var i = 0
            while (i < limit && a[i] == b[i]) i++
            return i
        }

        private fun commonSuffix(a: String, b: String, prefix: Int): Int {
            val limit = minOf(a.length - prefix, b.length - prefix)
            var i = 0
            while (i < limit && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
            return i
        }

        private fun inheritedStyle(flags: List<Boolean>, insertionStart: Int, oldChangedEnd: Int): Boolean {
            if (flags.isEmpty()) return false
            if (insertionStart > 0 && flags[insertionStart - 1]) return true
            if (insertionStart < oldChangedEnd && insertionStart < flags.size) return flags[insertionStart]
            if (insertionStart < flags.size) return flags[insertionStart]
            return false
        }

        private fun inheritedEndnote(ids: List<String?>, insertionStart: Int, oldChangedEnd: Int): String? {
            if (ids.isEmpty()) return null
            val left = ids.getOrNull(insertionStart - 1)
            val right = ids.getOrNull(oldChangedEnd)
            if (left != null && left == right) return left
            if (insertionStart < oldChangedEnd) {
                val replaced = ids.subList(insertionStart, oldChangedEnd).filterNotNull().distinct()
                if (replaced.size == 1) return replaced.single()
            }
            return null
        }

        private fun openTag(tag: MarkupTag): String = when (tag.kind) {
            "b" -> "[b]"
            "i" -> "[i]"
            "en" -> "[en=${tag.endnoteId}]"
            else -> error("Unknown inline tag ${tag.kind}")
        }

        private fun closeTag(tag: MarkupTag): String = when (tag.kind) {
            "b" -> "[/b]"
            "i" -> "[/i]"
            "en" -> "[/en]"
            else -> error("Unknown inline tag ${tag.kind}")
        }
    }
}

private fun TextRange.coerce(length: Int): TextRange = TextRange(
    start.coerceIn(0, length),
    end.coerceIn(0, length),
)
