package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun endnoteRoundTripUsesCanonicalOuterRange() {
        val markup = "Read [en=en-P1-01][b]Comi[i]ket[/i][/b][/en]."
        val state = RichInlineState.fromMarkup(markup)

        assertEquals("Read Comiket.", state.text)
        assertEquals(markup, state.toMarkup())
        assertEquals("Comiket", state.anchorForEndnote("en-P1-01"))
    }

    @Test
    fun applyingAndRemovingEndnotePreservesVisibleText() {
        var state = RichInlineState.plain("Comiket").copy(selection = TextRange(0, 7))
        state = state.applyEndnote("en-P1-01")
        assertEquals("[en=en-P1-01]Comiket[/en]", state.toMarkup())

        state = state.removeEndnote("en-P1-01")
        assertEquals("Comiket", state.toMarkup())
    }

    @Test
    fun replacingEndnoteAnchorPreservesEndnoteAndFormatting() {
        var state = RichInlineState.fromMarkup("Read [en=en-P1-01][b]Comiket[/b][/en] today.")
        state = state.copy(selection = state.rangeForEndnote("en-P1-01")!!)

        state = state.replaceSelection("Comic Market")

        assertEquals("Read Comic Market today.", state.text)
        assertEquals("Comic Market", state.anchorForEndnote("en-P1-01"))
        assertEquals("Read [en=en-P1-01][b]Comic Market[/b][/en] today.", state.toMarkup())
    }

    @Test
    fun replacingPlainSelectionCanBecomeNewEndnoteAnchor() {
        var state = RichInlineState.plain("Visit Comiket today.").copy(selection = TextRange(6, 13))

        state = state.replaceSelection("Comic Market").applyEndnote("en-P1-01")

        assertEquals("Visit [en=en-P1-01]Comic Market[/en] today.", state.toMarkup())
    }

    @Test
    fun typingInsideEndnoteKeepsRangeButTypingAtEndDoesNotExtendIt() {
        var state = RichInlineState.fromMarkup("[en=en-P1-01]abc[/en]")
        state = state.copy(selection = TextRange(1))
        state = state.edited(TextFieldValue("aXbc", selection = TextRange(2)))
        assertEquals("[en=en-P1-01]aXbc[/en]", state.toMarkup())

        state = state.copy(selection = TextRange(4))
        state = state.edited(TextFieldValue("aXbc!", selection = TextRange(5)))
        assertEquals("[en=en-P1-01]aXbc[/en]!", state.toMarkup())
    }

    @Test
    fun clipboardStripsEndnoteIdentityButKeepsInlineStyle() {
        val state = RichInlineState.fromClipboard("[en=en-P1-01][b]Comiket[/b][/en]")
        assertEquals("[b]Comiket[/b]", state.toMarkup())
        assertNull(state.endnoteIdAtCaret())
    }
}
