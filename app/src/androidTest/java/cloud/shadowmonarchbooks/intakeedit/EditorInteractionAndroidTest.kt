package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                EditorScreen(initial, busy = false, notice = null, recoveryDrafts = emptyList(), onBack = {},
                    onDraft = { latest = it }, onCommit = { _, _ -> }, onOverride = { _, _, _ -> },
                    onResolve = { _, _ -> }, onApprove = {})
            }
        }
        compose.onNodeWithContentDescription("Bold selection").assertExists()
        compose.onNodeWithContentDescription("Italicize selection").assertExists()
        compose.onNodeWithContentDescription("Edit endnote for selection").assertExists()
        compose.onNodeWithText("English").performTextInput("Hello")
        compose.waitForIdle()
        assertTrue(latest.document.entries.single().english.contains("Hello"))
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
