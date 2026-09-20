package cloud.shadowmonarchbooks.intakeedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkflowHelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workflow & QA Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HelpCard("The four chapter states") {
                    HelpLine("Pending Review", "You are still editing/reviewing it. QA ignores this chapter.")
                    HelpLine("Pending QA", "You used Tag for QA & Commit. The chapter is waiting for full-chapter QA.")
                    HelpLine("Ready for Approval", "A current reusable semantic QA pass exists and there are zero active findings. The chapter still needs your explicit approval.")
                    HelpLine("Approved", "You approved the chapter and a current approval matches this exact chapter and reader output. Only Approved chapters are Completed.")
                }
            }
            item {
                HelpCard("What happens after Tag for QA") {
                    Text("Pending QA → one full semantic QA pass. After that, the recorded pass is reused while you address only the flagged paragraphs:")
                    HelpLine("No findings", "The chapter becomes Ready for Approval. QA does not create the reader XHTML or approve the chapter by itself.")
                    HelpLine("Any finding", "The chapter returns to Pending Review. Correct it and press Resolve, or press Override when the finding allows it.")
                    Text("After a return, cards needing attention show a ! button. It combines QA findings and endnote suggestions. Resolve requires that the flagged paragraph was actually changed. Override means you intentionally accept it as-is and requires a reason when allowed. Once every QA finding is closed, commit the corrected chapter for approval; semantic QA does not run again.")
                }
            }
            item {
                HelpCard("Ready filter and approval") {
                    Text("The chapter list keeps Active, Pending QA, Ready, and Completed filters visible across the top.")
                    Text("Ready contains chapters whose editor review is complete, whose semantic QA pass is still reusable, and whose active QA finding count is zero.")
                    Text("Open one Ready chapter to use Approve Chapter, or use the Approve all floating button in the Ready filter to start approval for every Ready chapter in the selected volume.")
                    Text("Bulk approval is sequential. Intake Edit dispatches one uniquely identified finalizer run, monitors that exact GitHub Actions run until it finishes, and only then starts the next Ready chapter.")
                    Text("A completed failed or cancelled run is reported without creating overlap. If the app cannot confirm that a dispatched run has finished, it stops the remaining queue rather than risk starting another finalizer concurrently.")
                    Text("Approval dispatches the trusted PureLove GitHub Actions finalizer. The finalizer verifies the exact editor-content hash and current reusable QA pass, then reconstructs reader XHTML/endnotes and writes the approval artifact. PureLove also serializes finalizers repository-wide as a second safety layer.")
                    Text("Your GitHub token therefore needs both Contents write access for editing and Actions write access to start approval.")
                }
            }
            item {
                HelpCard("Attention (!) counts") {
                    Text("The bottom ! badge counts unresolved attention items: active QA findings plus pending endnote suggestions. Each Resolve, Override, Use, or Dismiss decision reduces that count when it closes an item.")
                    Text("When the unresolved count reaches zero, the badge disappears and the bottom ! navigator is disabled.")
                    Text("A paragraph's own ! button stays available as history even after its QA findings are Resolved/Overridden or its endnote suggestions are Used/Dismissed. Opening it shows those closed items and their status, but they no longer count toward the badge.")
                    Text("Count badges are hidden whenever their value is zero.")
                }
            }
            item {
                HelpCard("QA severity") {
                    HelpLine("WARNING", "Needs a human look, but the intended meaning is usually still understandable. Normally overrideable when the wording is intentional.")
                    HelpLine("ERROR", "A clear translation, continuity, attribution, or terminology defect that normally should be corrected.")
                    HelpLine("BLOCKING", "A severe or systemic problem that makes the chapter unsafe to approve as-is. Normally not overrideable.")
                    Text("Severity describes seriousness. Every active finding returns the chapter to Pending Review.")
                }
            }
            item {
                HelpCard("QA flags and their usual severity") {
                    QaRuleLine("Translation accuracy", "ERROR", "Wrong meaning, polarity, relationship, nuance, action, or factual content. Can become BLOCKING when pervasive.")
                    QaRuleLine("Omission / unsupported addition", "ERROR", "Meaningful Japanese content is missing, or English invents unsupported meaning. Can become BLOCKING when substantial/repeated.")
                    QaRuleLine("Speaker / subject attribution", "ERROR", "Wrong speaker, pronoun referent, addressee, subject, or ownership. Can become BLOCKING when pervasive.")
                    QaRuleLine("Terminology consistency", "WARNING", "Names, aliases, organizations, locations, nicknames, or recurring terms are inconsistent. ERROR when identity/meaning changes or an approved QA lock is violated.")
                    QaRuleLine("Tense consistency", "WARNING", "Unexplained tense shifts inside one temporal frame. ERROR when chronology/meaning changes or the problem is pervasive.")
                    QaRuleLine("Continuity consistency", "ERROR", "English creates an internal contradiction in facts, chronology, character state, relationships, or location. Can become BLOCKING for major/systemic contradictions.")
                    QaRuleLine("Readability / grammar", "WARNING", "Confusing or unidiomatic English that impedes normal reading. ERROR only when wording obscures or reverses meaning.")
                    QaRuleLine("Formatting / presentation", "WARNING", "Non-structural emphasis, spacing, or punctuation problems that survive schema validation. ERROR if meaning is obscured.")
                    QaRuleLine("Endnote accuracy", "ERROR", "An endnote is materially misleading, inaccurate, or attached to the wrong contextual idea. WARNING for non-factual wording/context improvements.")
                    QaRuleLine("Missing endnote context", "WARNING", "Cultural, historical, institutional, or linguistic context is missing enough to cause likely reader misunderstanding. Ordinary vocabulary is not flagged just to add notes.")
                    QaRuleLine("Chapter alignment", "BLOCKING", "English belongs to the wrong Japanese paragraph/scene or broad paragraph alignment has drifted.")
                }
            }
            item {
                HelpCard("Resolve and Override") {
                    HelpLine("Resolve", "Use this after correcting the flagged paragraph. Resolve is accepted only when that paragraph differs from the QA-reviewed baseline.")
                    HelpLine("Override", "Use this when you intentionally accept the finding as-is. It is available only when the finding is overrideable and requires a reason.")
                    Text("Both are paragraph-scoped audit records. Editing that same paragraph again makes its closure stale; editing another flagged paragraph does not.")
                    Text("If you edit a paragraph that QA did not flag, or other protected chapter content, the recorded QA pass becomes stale and a fresh semantic QA pass is required.")
                }
            }
            item {
                HelpCard("Hard pipeline failures") {
                    Text("Invalid schema, canonical-source mismatch, malformed inline markup, broken endnote references, or failed materialization are not ordinary QA flags. They must be fixed and cannot be bypassed with a QA override.")
                }
            }
        }
    }
}

@Composable
private fun HelpCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun HelpLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QaRuleLine(title: String, severity: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text("$title • $severity", fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
