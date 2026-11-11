package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorInteractionAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorAcceptsTextAndSwitchesBetweenCardsAndWholeFile() {
        val raw = """
            schema_version: 6
            volume: 1
            chapter: 1
            source_href: canonical/vol-01/ch_0001.xhtml
            source_sha256: source
            reader_file: reader/vol-01/ch_0001.xhtml
            english_title: Chapter
            instructions: Edit English.
            editor_review_complete: false
            endnotes: []
            entries:
            - locator: P1
              source_japanese: テストです。
              english: ''
        """.trimIndent()
        val file = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        val remote = FileSnapshot(file.path, "sha", raw)
        val initial = OpenChapter(file, remote, raw, IntakeParser.parse(raw), null)
        var latest = initial
        compose.setContent {
            MaterialTheme {
                EditorScreen(initial, busy = false, notice = null, recoveryDrafts = emptyList(),
                    onDismissSavedDraft = {}, onBack = {},
                    onDraft = { latest = it }, onCommit = { _, _ -> }, onOverride = { _, _, _ -> },
                    onResolve = { _, _ -> }, onApprove = {})
            }
        }
        compose.onNodeWithContentDescription("Bold selection").assertExists()
        compose.onNodeWithContentDescription("Paste English").assertExists()
        compose.onNodeWithText("Copy ref").assertDoesNotExist()
        // Paste is a fixed right-side action; formatting icons scroll to its left.
        val pasteLeft = compose.onNodeWithContentDescription("Paste English").fetchSemanticsNode().boundsInRoot.left
        val boldRight = compose.onNodeWithContentDescription("Bold selection").fetchSemanticsNode().boundsInRoot.right
        assertTrue("Paste must stay to the right of formatting controls", pasteLeft >= boldRight)
        compose.onNodeWithContentDescription("Include translation in copied text").assertExists()
        compose.onNodeWithContentDescription("Italicize selection").assertExists()
        compose.onNodeWithContentDescription("Edit endnote for selection").assertExists()
        compose.onNodeWithText("English").performTextInput("Hello")
        compose.waitForIdle()
        assertTrue(latest.document.entries.single().english.contains("Hello"))
        compose.onNodeWithText("English").performTextInputSelection(TextRange(0, 5))
        compose.onNodeWithContentDescription("Bold selection").performClick()
        compose.onNodeWithText("English").assertIsFocused()
        assertTrue(latest.document.entries.single().english.contains("[b]Hello[/b]"))
        compose.onNodeWithContentDescription("Editor actions").performClick()
        compose.onNodeWithText("Whole file mode").performClick()
        compose.onNodeWithText("Whole file YAML").assertIsDisplayed()
        compose.onNodeWithContentDescription("Editor actions").performClick()
        compose.onNodeWithText("Cards mode").performClick()
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithContentDescription("Editor filters").performClick()
        compose.onNodeWithText("QA findings").performClick()
        Espresso.pressBack()
        compose.onNodeWithText("No machine-readable QA findings have been recorded for this chapter yet.").assertIsDisplayed()
    }

    @Test fun workspaceButtonsRemainReachableAtLargeTextOnNarrowScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) { WorkspaceHomeScreen(onOpenChapters = {}, onOpenGlossary = {}, onOpenHelp = {}) }
                }
            }
        }
        compose.onNodeWithText("Open Glossary").performScrollTo().assertIsDisplayed()
    }

    @Test fun chapterListOpensTheSelectedChapter() {
        val file = ChapterFile("editor_input/vol-01/chapters/ch_0001.yml", 1, 1)
        var selected: ChapterFile? = null
        compose.setContent {
            var opened by remember { mutableStateOf(false) }
            MaterialTheme {
                if (opened) Text("Chapter opened") else ChapterListScreen(
                    files = listOf(file), progressByPath = emptyMap(), availableVolumes = listOf(1), selectedVolume = 1,
                    refreshing = false, dataSource = ChapterDataSource.INDEX, bulkApproving = false,
                    approvalRequestedPaths = emptySet(), openingPath = null, notice = null, onBack = {}, onRefresh = {},
                    onSettings = {}, onSelectVolume = {}, onOpen = { selected = it; opened = true }, onApproveAll = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Open chapter").performClick()
        compose.onNodeWithText("Chapter opened").assertIsDisplayed()
        assertTrue(selected == file)
    }

    @Test fun workspaceButtonsNavigateToRequestedSection() {
        var destination = ""
        compose.setContent {
            MaterialTheme { WorkspaceHomeScreen(onOpenChapters = { destination = "chapters" },
                onOpenGlossary = { destination = "glossary" }, onOpenHelp = { destination = "help" }) }
        }
        compose.onNodeWithText("Open Chapter Intake").performClick()
        assertTrue(destination == "chapters")
    }
}
