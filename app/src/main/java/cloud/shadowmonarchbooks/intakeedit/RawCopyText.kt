package cloud.shadowmonarchbooks.intakeedit

internal fun rawCopyText(raw: String, reference: String?, machineReference: Boolean): String {
    val imported = if (machineReference) null else reference
    return "RAW:\n$raw" + (imported?.let { "\n\nIMPORTED TRANSLATION:\n$it" } ?: "")
}
