package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealQaPassCompatibilityTest {
    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing resource: $name" }.readText()

    @Test
    fun currentPureLoveQaPassesMatchAndroidFingerprinting() {
        for (chapter in listOf("0000", "0001")) {
            val editor = IntakeParser.parse(resource("qa-real/ch_$chapter.yml"))
            val qa = QaFindingsParser.parse(resource("qa-real/ch_$chapter.findings.json"))
            val mutable = qa.qaPass!!.mutableLocators.toSet()
            val actual = QaFindingsParser.protectedContentSha256(editor, mutable)
            assertEquals("chapter $chapter protected hash", qa.qaPass!!.protectedContentSha256, actual)
            assertTrue("chapter $chapter pass reusable", qa.qaPassReusable(editor))
        }
    }
}
