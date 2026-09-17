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
                HelpCard("The three chapter states") {
                    HelpLine("Pending Review", "You are still editing/reviewing it. QA ignores this chapter.")
                    HelpLine("Pending QA", "You used Tag for QA & Commit. The chapter is waiting for full-chapter QA.")
                    HelpLine("Approved", "QA finished with zero active findings and a current approval matches this exact chapter and reader output. Only Approved chapters are Completed.")
                }
            }
            item {
                HelpCard("What happens after Tag for QA") {
                    Text("Pending QA → materialize reader output → full semantic QA → one of two outcomes:")
                    HelpLine("No active findings", "The chapter becomes Approved.")
                    HelpLine("Any active finding", "The chapter automatically returns to Pending Review. This includes warnings, errors, and blockers.")
                    Text("After a return, cards needing attention show a ! button. It combines active QA findings and endnote suggestions in one sheet; the bottom ! button cycles through cards with unresolved attention. Fix the text/endnote or override an eligible finding, then Tag for QA & Commit again.")
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
                HelpCard("Overrides") {
                    Text("A finding can be overridden only when it is marked overrideable. The app requires a reason and keeps the override as an audit record.")
                    Text("Overrides are bound to the authoritative English/endnote content. Editing that content makes an old override stale and the finding active again.")
                    Text("Moving between Pending Review and Pending QA by itself does not stale an override.")
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
