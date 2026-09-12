package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue

enum class InlineStyle { BOLD, ITALIC }

data class RichInlineState(
    val text: String,
    val selection: TextRange,
    private val bold: List<Boolean>,
    private val italic: List<Boolean>,
) {
    init {
        require(bold.size == text.length)
        require(italic.size == text.length)
    }

    fun asTextFieldValue(): TextFieldValue {
        val builder = AnnotatedString.Builder(text)
        addRuns(builder, bold) { SpanStyle(fontWeight = FontWeight.Bold) }
        addRuns(builder, italic) { SpanStyle(fontStyle = FontStyle.Italic) }
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
        val nextBold = MutableList(nextText.length) { false }
        val nextItalic = MutableList(nextText.length) { false }

        for (i in 0 until prefix) {
            nextBold[i] = bold[i]
            nextItalic[i] = italic[i]
        }
        for (i in 0 until insertedLength) {
            val target = prefix + i
            nextBold[target] = inheritedBold
            nextItalic[target] = inheritedItalic
        }
        for (i in 0 until suffix) {
            val oldIndex = text.length - suffix + i
            val newIndex = nextText.length - suffix + i
            nextBold[newIndex] = bold[oldIndex]
            nextItalic[newIndex] = italic[oldIndex]
        }

        return RichInlineState(nextText, next.selection.coerce(nextText.length), nextBold, nextItalic)
    }

    fun toggle(style: InlineStyle): RichInlineState {
        val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
        val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
        if (start == end) return this

        val source = if (style == InlineStyle.BOLD) bold else italic
        val remove = (start until end).all { source[it] }
        val updated = source.toMutableList()
        for (i in start until end) updated[i] = !remove
        return if (style == InlineStyle.BOLD) copy(bold = updated) else copy(italic = updated)
    }

    fun moveCaretToEnd(): RichInlineState = copy(selection = TextRange(text.length))

    fun hasSelection(): Boolean = selection.start != selection.end

    fun toMarkup(): String {
        if (text.isEmpty()) return ""
        val out = StringBuilder(text.length + 16)
        var current = emptyList<InlineStyle>()
        for (index in 0..text.length) {
            val desired = if (index == text.length) {
                emptyList()
            } else {
                buildList {
                    if (bold[index]) add(InlineStyle.BOLD)
                    if (italic[index]) add(InlineStyle.ITALIC)
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

    companion object {
        fun fromMarkup(markup: String, selection: TextRange? = null): RichInlineState {
            InlineMarkup.validationError(markup)?.let { throw IllegalArgumentException(it) }
            val visible = StringBuilder()
            val bold = mutableListOf<Boolean>()
            val italic = mutableListOf<Boolean>()
            var boldDepth = 0
            var italicDepth = 0
            var index = 0
            while (index < markup.length) {
                when {
                    markup.startsWith("[b]", index) -> { boldDepth++; index += 3 }
                    markup.startsWith("[/b]", index) -> { boldDepth--; index += 4 }
                    markup.startsWith("[i]", index) -> { italicDepth++; index += 3 }
                    markup.startsWith("[/i]", index) -> { italicDepth--; index += 4 }
                    else -> {
                        visible.append(markup[index])
                        bold.add(boldDepth > 0)
                        italic.add(italicDepth > 0)
                        index++
                    }
                }
            }
            val text = visible.toString()
            return RichInlineState(text, (selection ?: TextRange(text.length)).coerce(text.length), bold, italic)
        }

        fun fromClipboard(text: String): RichInlineState =
            if (InlineMarkup.validationError(text) == null) fromMarkup(text) else plain(text)

        fun plain(text: String): RichInlineState = RichInlineState(
            text = text,
            selection = TextRange(text.length),
            bold = List(text.length) { false },
            italic = List(text.length) { false },
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

        private fun openTag(style: InlineStyle) = if (style == InlineStyle.BOLD) "[b]" else "[i]"
        private fun closeTag(style: InlineStyle) = if (style == InlineStyle.BOLD) "[/b]" else "[/i]"
    }
}

private fun TextRange.coerce(length: Int): TextRange = TextRange(
    start.coerceIn(0, length),
    end.coerceIn(0, length),
)
