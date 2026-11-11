package cloud.shadowmonarchbooks.intakeedit

internal fun rawCopyText(
    raw: String,
    reference: String?,
    includeReference: Boolean,
    machineReference: Boolean,
): String {
    if (!includeReference || reference == null) return "RAW:\n$raw"
    val heading = if (machineReference) "MACHINE TRANSLATION" else "IMPORTED TRANSLATION"
    return "RAW:\n$raw\n\n$heading:\n$reference"
}
