package cloud.shadowmonarchbooks.intakeedit

import org.json.JSONArray
import org.json.JSONObject

private val glossaryTranslatedRegex = Regex("^(.*?)(?: \\[([^]]+)])?$")

data class GlossaryEntry(
    val id: String,
    val section: String,
    val sourceAliases: List<String>,
    val translatedName: String,
    val gender: String? = null,
    val description: String = "",
    val qaLock: Boolean = false,
    val caseSensitive: Boolean = false,
    val origin: String = "restored-volume-1-5",
)

data class GlossaryOverride(
    val sourceAliases: List<String>? = null,
    val translatedName: String? = null,
    val gender: String? = null,
    val description: String? = null,
    val qaLock: Boolean? = null,
    val caseSensitive: Boolean? = null,
)

data class GlossaryProposal(
    val id: String,
    val status: String = "pending",
    val action: String,
    val reason: String,
    val targetId: String? = null,
    val section: String? = null,
    val sourceAliases: List<String> = emptyList(),
    val translatedName: String? = null,
    val gender: String? = null,
    val description: String = "",
    val recommendQaLock: Boolean = false,
    val sourceVolume: Int? = null,
    val sourceChapter: Int? = null,
    val sourceLocator: String? = null,
)

data class GlossaryDocuments(
    val baseEntries: List<GlossaryEntry>,
    val additions: List<GlossaryEntry>,
    val governance: Map<String, GlossaryOverride>,
    val proposals: List<GlossaryProposal>,
) {
    val effectiveEntries: List<GlossaryEntry> get() = GlossaryParser.merge(baseEntries, additions, governance)
    val pendingProposals: List<GlossaryProposal> get() = proposals.filter { it.status == "pending" }
}

object GlossaryParser {
    fun entryId(section: String, aliases: List<String>): String = "${section.trim().lowercase()}:${aliases.joinToString(" / ").trim()}"

    fun parseBaseFile(raw: String): List<GlossaryEntry> {
        var section = ""
        val entries = mutableListOf<GlossaryEntry>()
        raw.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("===") && line.endsWith("===")) {
                section = line.trim('=', ' ').lowercase()
                return@forEach
            }
            if (!line.startsWith("* ")) return@forEach
            val body = line.removePrefix("* ")
            val split = body.indexOf(" = ")
            require(split >= 0) { "Malformed glossary line: $line" }
            val rawName = body.substring(0, split).trim()
            val rest = body.substring(split + 3)
            val descriptionAt = rest.indexOf(": ")
            val translatedPart = if (descriptionAt >= 0) rest.substring(0, descriptionAt) else rest
            val description = if (descriptionAt >= 0) rest.substring(descriptionAt + 2).trim() else ""
            val match = glossaryTranslatedRegex.matchEntire(translatedPart.trim()) ?: error("Malformed glossary translation: $line")
            val aliases = rawName.split(" / ").map { it.trim() }.filter { it.isNotBlank() }
            entries += GlossaryEntry(
                id = entryId(section, aliases),
                section = section,
                sourceAliases = aliases,
                translatedName = match.groupValues[1].trim(),
                gender = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() },
                description = description,
            )
        }
        return entries
    }

    fun parseAdditions(raw: String): List<GlossaryEntry> {
        val root = JSONObject(raw)
        require(root.optInt("schema_version") == 1) { "Unsupported glossary additions schema." }
        val array = root.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) add(parseEntry(array.getJSONObject(i), "editor-approved"))
        }
    }

    fun serializeAdditions(entries: List<GlossaryEntry>): String {
        val array = JSONArray()
        entries.forEach { array.put(entryJson(it)) }
        return JSONObject().put("schema_version", 1).put("entries", array).toString(2) + "\n"
    }

    fun parseGovernance(raw: String): Map<String, GlossaryOverride> {
        val root = JSONObject(raw)
        require(root.optInt("schema_version") == 1) { "Unsupported glossary governance schema." }
        val entries = root.optJSONObject("entries") ?: JSONObject()
        return buildMap {
            entries.keys().forEach { id ->
                val item = entries.getJSONObject(id)
                put(
                    id,
                    GlossaryOverride(
                        sourceAliases = item.optJSONArray("source_aliases")?.toStringList(),
                        translatedName = item.optStringOrNull("translated_name"),
                        gender = item.optStringOrNull("gender"),
                        description = item.optStringOrNull("description"),
                        qaLock = item.optBooleanOrNull("qa_lock"),
                        caseSensitive = item.optBooleanOrNull("case_sensitive"),
                    ),
                )
            }
        }
    }

    fun serializeGovernance(entries: Map<String, GlossaryOverride>): String {
        val body = JSONObject()
        entries.toSortedMap().forEach { (id, item) ->
            val json = JSONObject()
            item.sourceAliases?.let { json.put("source_aliases", JSONArray(it)) }
            item.translatedName?.let { json.put("translated_name", it) }
            item.gender?.let { json.put("gender", it) }
            item.description?.let { json.put("description", it) }
            item.qaLock?.let { json.put("qa_lock", it) }
            item.caseSensitive?.let { json.put("case_sensitive", it) }
            body.put(id, json)
        }
        return JSONObject().put("schema_version", 1).put("entries", body).toString(2) + "\n"
    }

    fun parseProposals(raw: String): List<GlossaryProposal> {
        val root = JSONObject(raw)
        require(root.optInt("schema_version") == 1) { "Unsupported glossary proposal schema." }
        val array = root.optJSONArray("proposals") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    GlossaryProposal(
                        id = item.getString("id"),
                        status = item.optString("status", "pending"),
                        action = item.getString("action"),
                        reason = item.getString("reason"),
                        targetId = item.optStringOrNull("target_id"),
                        section = item.optStringOrNull("section"),
                        sourceAliases = item.optJSONArray("source_aliases")?.toStringList().orEmpty(),
                        translatedName = item.optStringOrNull("translated_name"),
                        gender = item.optStringOrNull("gender"),
                        description = item.optString("description"),
                        recommendQaLock = item.optBoolean("recommend_qa_lock", false),
                        sourceVolume = item.optIntOrNull("source_volume"),
                        sourceChapter = item.optIntOrNull("source_chapter"),
                        sourceLocator = item.optStringOrNull("source_locator"),
                    ),
                )
            }
        }
    }

    fun serializeProposals(proposals: List<GlossaryProposal>): String {
        val array = JSONArray()
        proposals.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("status", p.status)
                    .put("action", p.action)
                    .put("reason", p.reason)
                    .put("target_id", p.targetId ?: JSONObject.NULL)
                    .put("section", p.section ?: JSONObject.NULL)
                    .put("source_aliases", JSONArray(p.sourceAliases))
                    .put("translated_name", p.translatedName ?: JSONObject.NULL)
                    .put("gender", p.gender ?: JSONObject.NULL)
                    .put("description", p.description)
                    .put("recommend_qa_lock", p.recommendQaLock)
                    .put("source_volume", p.sourceVolume ?: JSONObject.NULL)
                    .put("source_chapter", p.sourceChapter ?: JSONObject.NULL)
                    .put("source_locator", p.sourceLocator ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("schema_version", 1).put("proposals", array).toString(2) + "\n"
    }

    fun merge(base: List<GlossaryEntry>, additions: List<GlossaryEntry>, governance: Map<String, GlossaryOverride>): List<GlossaryEntry> {
        val combined = base + additions
        return combined.map { entry ->
            val override = governance[entry.id] ?: return@map entry
            entry.copy(
                sourceAliases = override.sourceAliases ?: entry.sourceAliases,
                translatedName = override.translatedName ?: entry.translatedName,
                gender = override.gender ?: entry.gender,
                description = override.description ?: entry.description,
                qaLock = override.qaLock ?: entry.qaLock,
                caseSensitive = override.caseSensitive ?: entry.caseSensitive,
            )
        }
    }

    fun fullOverride(entry: GlossaryEntry): GlossaryOverride = GlossaryOverride(
        sourceAliases = entry.sourceAliases,
        translatedName = entry.translatedName,
        gender = entry.gender,
        description = entry.description,
        qaLock = entry.qaLock,
        caseSensitive = entry.caseSensitive,
    )

    private fun parseEntry(item: JSONObject, defaultOrigin: String): GlossaryEntry {
        val aliases = item.optJSONArray("source_aliases")?.toStringList().orEmpty()
        val section = item.optString("section", "terms")
        return GlossaryEntry(
            id = item.optString("id").ifBlank { entryId(section, aliases) },
            section = section,
            sourceAliases = aliases,
            translatedName = item.getString("translated_name"),
            gender = item.optStringOrNull("gender"),
            description = item.optString("description"),
            qaLock = item.optBoolean("qa_lock", false),
            caseSensitive = item.optBoolean("case_sensitive", false),
            origin = item.optString("origin", defaultOrigin),
        )
    }

    private fun entryJson(entry: GlossaryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("section", entry.section)
        .put("raw_name", entry.sourceAliases.joinToString(" / "))
        .put("source_aliases", JSONArray(entry.sourceAliases))
        .put("translated_name", entry.translatedName)
        .put("gender", entry.gender ?: JSONObject.NULL)
        .put("description", entry.description)
        .put("qa_lock", entry.qaLock)
        .put("case_sensitive", entry.caseSensitive)
        .put("origin", entry.origin)

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) getString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.optBooleanOrNull(key: String): Boolean? = if (!has(key) || isNull(key)) null else getBoolean(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
}
