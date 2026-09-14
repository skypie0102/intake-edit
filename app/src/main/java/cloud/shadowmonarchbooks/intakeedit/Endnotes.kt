package cloud.shadowmonarchbooks.intakeedit

data class EndnoteDefinition(
    val id: String,
    val locator: String,
    val content: String,
)

object EndnoteIntegrity {
    fun validate(document: EditorDocument) {
        if (document.schemaVersion == 5) {
            require(document.endnotes.isEmpty()) { "Schema-v5 documents cannot contain endnotes." }
            document.entries.forEach { entry ->
                require(InlineMarkup.referencedEndnoteIds(entry.english).isEmpty()) {
                    "${entry.locator}: schema-v5 English cannot contain endnote anchors."
                }
            }
            return
        }
        require(document.schemaVersion == 6) { "This app version edits schema-v5 and schema-v6 chapter documents only." }

        val byId = linkedMapOf<String, EndnoteDefinition>()
        document.endnotes.forEachIndexed { index, note ->
            require(InlineMarkup.isValidEndnoteId(note.id)) { "Endnote $index has an invalid id: ${note.id}" }
            require(note.locator.matches(Regex("P\\d+"))) { "${note.id}: invalid locator ${note.locator}." }
            require(note.content.isNotBlank()) { "${note.id}: endnote content must not be blank." }
            require(byId.put(note.id, note) == null) { "Duplicate endnote id: ${note.id}" }
        }

        val references = linkedMapOf<String, MutableList<Pair<String, String>>>()
        document.entries.forEach { entry ->
            InlineMarkup.endnoteReferences(entry.english).forEach { ref ->
                references.getOrPut(ref.id) { mutableListOf() } += entry.locator to ref.anchor
            }
        }

        references.forEach { (id, occurrences) ->
            val definition = byId[id] ?: error("Endnote reference $id has no definition.")
            require(occurrences.size == 1) { "$id must be referenced exactly once." }
            val (locator, anchor) = occurrences.single()
            require(locator == definition.locator) {
                "$id is defined for ${definition.locator} but referenced in $locator."
            }
            require(anchor.isNotBlank()) { "$id has a blank visible anchor." }
        }
        byId.keys.forEach { id ->
            require(references[id]?.size == 1) { "Endnote definition $id is orphaned." }
        }
    }

    fun prune(document: EditorDocument): EditorDocument {
        if (document.schemaVersion < 6) return document
        val referenced = document.entries.flatMapTo(linkedSetOf()) { entry ->
            InlineMarkup.referencedEndnoteIds(entry.english)
        }
        return document.copy(endnotes = document.endnotes.filter { it.id in referenced })
    }

    fun nextId(document: EditorDocument, locator: String): String {
        val prefix = "en-$locator-"
        val max = document.endnotes.mapNotNull { note ->
            if (!note.id.startsWith(prefix)) return@mapNotNull null
            note.id.removePrefix(prefix).toIntOrNull()
        }.maxOrNull() ?: 0
        return "$prefix${(max + 1).toString().padStart(2, '0')}"
    }

    fun upsert(
        document: EditorDocument,
        locator: String,
        english: String,
        note: EndnoteDefinition,
    ): EditorDocument {
        val entryIndex = document.entries.indexOfFirst { it.locator == locator }
        require(entryIndex >= 0) { "Editor entry not found: $locator" }
        require(note.locator == locator) { "Endnote locator does not match the edited entry." }
        val entries = document.entries.toMutableList()
        entries[entryIndex] = entries[entryIndex].copy(english = english)
        val notes = document.endnotes.filterNot { it.id == note.id } + note
        val updated = document.copy(
            schemaVersion = 6,
            entries = entries,
            endnotes = notes,
            editorReviewComplete = false,
        )
        validate(updated)
        return updated
    }

    fun remove(
        document: EditorDocument,
        locator: String,
        english: String,
        noteId: String,
    ): EditorDocument {
        val entryIndex = document.entries.indexOfFirst { it.locator == locator }
        require(entryIndex >= 0) { "Editor entry not found: $locator" }
        val entries = document.entries.toMutableList()
        entries[entryIndex] = entries[entryIndex].copy(english = english)
        val updated = document.copy(
            entries = entries,
            endnotes = document.endnotes.filterNot { it.id == noteId },
            editorReviewComplete = false,
        )
        validate(updated)
        return updated
    }
}
