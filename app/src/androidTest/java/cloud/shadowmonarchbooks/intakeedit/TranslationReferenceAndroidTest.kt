package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TranslationReferenceAndroidTest {
    @Test fun importedReferencesAreScopedAndLegacyRequiresExplicitCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val first = draftScopeKey(RepoSettings(owner = "one", repo = "repo"), "")
        val second = draftScopeKey(RepoSettings(owner = "two", repo = "repo"), "")
        val overlay = ImportedTranslationOverlay(path, "source", "reference.xliff", mapOf("P1" to "Hello"), 1, 1, 1, 1, "Exact")
        val store = TranslationImportStore(context)
        assertTrue(store.save(overlay))
        assertNull(store.load(path, "source", projectKey = first))
        assertEquals("Hello", store.load(path, "source")?.translationFor("P1"))
        assertTrue(store.save(overlay, projectKey = first))
        assertEquals("Hello", store.load(path, "source", projectKey = first)?.translationFor("P1"))
        assertNull(store.load(path, "source", projectKey = second))
        assertNull(store.load(path, "different-source", projectKey = first))
        assertTrue(store.delete(path, projectKey = first))
        assertEquals("Hello", store.load(path, "source")?.translationFor("P1"))
        assertTrue(store.delete(path))
    }
}
