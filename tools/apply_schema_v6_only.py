from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise AssertionError(f"marker not found: {label}")
    return text.replace(old, new, 1)


models = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/Models.kt")
s = models.read_text()

s = replace_once(
    s,
    '''        val schemaVersion = root.optInt("schema_version", -1)\n        require(schemaVersion == 5 || schemaVersion == 6) {\n            "This app version edits schema-v5 and schema-v6 chapter documents only."\n        }\n        val endnotes = if (schemaVersion == 6) {\n            val array = root.optJSONArray("endnotes") ?: error("Schema-v6 document is missing top-level endnotes array.")\n            buildList {\n                for (i in 0 until array.length()) {\n                    val item = array.getJSONObject(i)\n                    val allowed = setOf("id", "locator", "content")\n                    val unexpected = buildList {\n                        val keys = item.keys()\n                        while (keys.hasNext()) {\n                            val key = keys.next()\n                            if (key !in allowed) add(key)\n                        }\n                    }\n                    require(unexpected.isEmpty()) { "Endnote $i has unsupported field(s): ${unexpected.joinToString()}" }\n                    add(\n                        EndnoteDefinition(\n                            id = item.getString("id"),\n                            locator = item.getString("locator"),\n                            content = item.getString("content"),\n                        ),\n                    )\n                }\n            }\n        } else {\n            require(!root.has("endnotes")) { "Schema-v5 documents cannot contain top-level endnotes." }\n            emptyList()\n        }\n''',
    '''        val schemaVersion = root.optInt("schema_version", -1)\n        require(schemaVersion == 6) {\n            "This app version edits schema-v6 chapter documents only."\n        }\n        val endnoteArray = root.optJSONArray("endnotes")\n            ?: error("Schema-v6 document is missing top-level endnotes array.")\n        val endnotes = buildList {\n            for (i in 0 until endnoteArray.length()) {\n                val item = endnoteArray.getJSONObject(i)\n                val allowed = setOf("id", "locator", "content")\n                val unexpected = buildList {\n                    val keys = item.keys()\n                    while (keys.hasNext()) {\n                        val key = keys.next()\n                        if (key !in allowed) add(key)\n                    }\n                }\n                require(unexpected.isEmpty()) { "Endnote $i has unsupported field(s): ${unexpected.joinToString()}" }\n                add(\n                    EndnoteDefinition(\n                        id = item.getString("id"),\n                        locator = item.getString("locator"),\n                        content = item.getString("content"),\n                    ),\n                )\n            }\n        }\n''',
    "JSON schema compatibility",
)

s = replace_once(
    s,
    '''        val schemaVersion = requiredInt("schema_version")\n        require(schemaVersion == 5 || schemaVersion == 6) {\n            "This app version edits schema-v5 and schema-v6 chapter documents only."\n        }\n        require(sawEntries && entryMaps.isNotEmpty()) { "Missing or empty top-level entries array." }\n        if (schemaVersion == 5) require(!sawEndnotes) { "Schema-v5 documents cannot contain top-level endnotes." }\n        if (schemaVersion == 6) require(sawEndnotes) { "Schema-v6 document is missing top-level endnotes list." }\n''',
    '''        val schemaVersion = requiredInt("schema_version")\n        require(schemaVersion == 6) {\n            "This app version edits schema-v6 chapter documents only."\n        }\n        require(sawEntries && entryMaps.isNotEmpty()) { "Missing or empty top-level entries array." }\n        require(sawEndnotes) { "Schema-v6 document is missing top-level endnotes list." }\n''',
    "YAML schema compatibility",
)

s = replace_once(
    s,
    '''        if (document.schemaVersion == 6) {\n            val notes = JSONArray()\n            document.endnotes.forEach { note ->\n                notes.put(\n                    JSONObject()\n                        .put("id", note.id)\n                        .put("locator", note.locator)\n                        .put("content", note.content),\n                )\n            }\n            root.put("endnotes", notes)\n        } else {\n            root.remove("endnotes")\n        }\n''',
    '''        require(document.schemaVersion == 6) { "Only schema-v6 documents can be written." }\n        val notes = JSONArray()\n        document.endnotes.forEach { note ->\n            notes.put(\n                JSONObject()\n                    .put("id", note.id)\n                    .put("locator", note.locator)\n                    .put("content", note.content),\n            )\n        }\n        root.put("endnotes", notes)\n''',
    "JSON write compatibility",
)

s = replace_once(
    s,
    '''    fun patchDocument(raw: String, document: EditorDocument): String {\n        EndnoteIntegrity.validate(document)\n        if (isJson(raw)) return patchJsonEndnotes(raw, document)\n        var patched = patchReviewComplete(patchEnglish(raw, document.entries), document.editorReviewComplete)\n        patched = patchSchemaVersion(patched, document.schemaVersion)\n        if (document.schemaVersion == 6) {\n            patched = patchEndnotesYaml(patched, document.endnotes)\n        } else {\n            require(document.endnotes.isEmpty()) { "Schema-v5 documents cannot contain endnotes." }\n        }\n        return patched\n    }\n''',
    '''    fun patchDocument(raw: String, document: EditorDocument): String {\n        require(document.schemaVersion == 6) { "Only schema-v6 documents can be written." }\n        EndnoteIntegrity.validate(document)\n        if (isJson(raw)) return patchJsonEndnotes(raw, document)\n        var patched = patchReviewComplete(patchEnglish(raw, document.entries), document.editorReviewComplete)\n        patched = patchSchemaVersion(patched, 6)\n        patched = patchEndnotesYaml(patched, document.endnotes)\n        return patched\n    }\n''',
    "document write compatibility",
)
models.write_text(s)

endnotes = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/Endnotes.kt")
s = endnotes.read_text()
s = replace_once(
    s,
    '''    fun validate(document: EditorDocument) {\n        if (document.schemaVersion == 5) {\n            require(document.endnotes.isEmpty()) { "Schema-v5 documents cannot contain endnotes." }\n            document.entries.forEach { entry ->\n                require(InlineMarkup.referencedEndnoteIds(entry.english).isEmpty()) {\n                    "${entry.locator}: schema-v5 English cannot contain endnote anchors."\n                }\n            }\n            return\n        }\n        require(document.schemaVersion == 6) { "This app version edits schema-v5 and schema-v6 chapter documents only." }\n''',
    '''    fun validate(document: EditorDocument) {\n        require(document.schemaVersion == 6) { "This app version edits schema-v6 chapter documents only." }\n''',
    "endnote validator compatibility",
)
s = s.replace('        if (document.schemaVersion < 6) return document\n', '')
s = s.replace('            schemaVersion = 6,\n', '')
endnotes.write_text(s)

parser_test = Path("app/src/test/java/cloud/shadowmonarchbooks/intakeedit/IntakeParserTest.kt")
s = parser_test.read_text()
s = s.replace("schema_version: 5", "schema_version: 6")
s = s.replace(
    "editor_review_complete: false\n        entries:",
    "editor_review_complete: false\n        endnotes: []\n        entries:",
)
s = s.replace("fun parsesSchemaV5Yaml()", "fun parsesSchemaV6Yaml()")
s = s.replace("assertEquals(5, document.schemaVersion)", "assertEquals(6, document.schemaVersion)")
s = s.replace('assertTrue(patched.startsWith("schema_version: 5"))', 'assertTrue(patched.startsWith("schema_version: 6"))')
insert = '''\n    @Test\n    fun rejectsSchemaV5Yaml() {\n        val legacy = yaml.replace("schema_version: 6", "schema_version: 5")\n        assertTrue(IntakeParser.validate(legacy).isFailure)\n    }\n'''
marker = '\n    @Test\n    fun rejectsOldSchemaFields() {'
if insert.strip() not in s:
    if marker not in s:
        raise AssertionError("parser test insertion marker not found")
    s = s.replace(marker, insert + marker, 1)
parser_test.write_text(s)

editor = Path("app/src/main/java/cloud/shadowmonarchbooks/intakeedit/EditorScreen.kt")
# This file is patched by apply_endnotes_editor_ui.py first. Normalize its whole-file label afterward.
if editor.exists():
    s = editor.read_text()
    s = s.replace("Direct schema-v5/v6 YAML editor", "Direct schema-v6 YAML editor")
    s = s.replace("Direct schema-v5 YAML editor", "Direct schema-v6 YAML editor")
    editor.write_text(s)
