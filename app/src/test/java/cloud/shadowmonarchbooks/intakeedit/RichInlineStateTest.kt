package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class RichInlineStateTest {
    @Test
    fun markupRoundTripPreservesNestedStyles() {
        val markup = "[b]Bold [i]and italic[/i][/b] plain"
        val state = RichInlineState.fromMarkup(markup)

        assertEquals("Bold and italic plain", state.text)
        assertEquals(markup, state.toMarkup())
    }

    @Test
    fun pressingBoldTwiceOnSameSelectionTogglesItOff() {
        var state = RichInlineState.plain("test").copy(selection = TextRange(0, 4))

        state = state.toggle(InlineStyle.BOLD)
        assertEquals("[b]test[/b]", state.toMarkup())

        state = state.toggle(InlineStyle.BOLD)
        assertEquals("test", state.toMarkup())
    }

    @Test
    fun overlappingBoldAndItalicSerializeWithoutCrossedTags() {
        var state = RichInlineState.plain("abcdef").copy(selection = TextRange(0, 4))
        state = state.toggle(InlineStyle.BOLD)
        state = state.copy(selection = TextRange(2, 6)).toggle(InlineStyle.ITALIC)

        val markup = state.toMarkup()
        assertEquals("[b]ab[i]cd[/i][/b][i]ef[/i]", markup)
        assertEquals(null, InlineMarkup.validationError(markup))
        assertEquals(markup, RichInlineState.fromMarkup(markup).toMarkup())
    }

    @Test
    fun typedTextInsideFormattedRangeInheritsFormatting() {
        var state = RichInlineState.plain("abc").copy(selection = TextRange(0, 3))
        state = state.toggle(InlineStyle.BOLD)
        state = state.copy(selection = TextRange(1))

        state = state.edited(TextFieldValue("aXbc", selection = TextRange(2)))

        assertEquals("[b]aXbc[/b]", state.toMarkup())
    }
}
