package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DraftStorageAndroidTest {
    @Test fun scopedDraftsSurviveStoreRecreationAndRemainIsolated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val first = RepoSettings(owner = "author", repo = "one", branch = "main")
        val second = first.copy(repo = "two")
        assertTrue(DraftStore(context).save(first, path, "base", "first draft"))
        assertTrue(DraftStore(context).save(second, path, "base", "second draft"))
        val reopened = DraftStore(context)
        assertEquals("first draft", reopened.load(first, path)?.raw)
        assertEquals("second draft", reopened.load(second, path)?.raw)
        assertTrue(reopened.delete(first, path))
        assertNull(reopened.load(first, path))
        assertEquals("second draft", reopened.load(second, path)?.raw)
        assertTrue(reopened.delete(second, path))
    }

    @Test fun archivedDraftSurvivesStoreRecreationAndRemainsProjectScoped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val first = RepoSettings(owner = "author", repo = "one", branch = "main")
        val second = first.copy(branch = "other")
        val draft = LocalDraft(path, "old", "preserved content", 1L)
        assertTrue(DraftStore(context).archive(first, draft))
        val reopened = DraftStore(context)
        assertEquals("preserved content", reopened.archived(first, path).single().raw)
        assertTrue(reopened.archived(second, path).isEmpty())
    }

    @Test fun dismissOnlySelectedArchivedDraftAndPreserveCurrentDraftAndOtherProject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val first = RepoSettings(owner = "author", repo = "one", branch = "main")
        val second = first.copy(repo = "two")
        val removed = LocalDraft(path, "base-old-one", "archived one", 1L)
        val kept = LocalDraft(path, "base-old-two", "archived two", 2L)
        val store = DraftStore(context)
        assertTrue(store.save(first, path, "current", "working content"))
        assertTrue(store.archive(first, removed))
        assertTrue(store.archive(first, kept))
        assertTrue(store.archive(second, removed))

        assertTrue(store.dismissArchived(first, removed))
        val reloaded = DraftStore(context)
        assertEquals(listOf(kept), reloaded.archived(first, path))
        assertEquals(listOf(removed), reloaded.archived(second, path))
        assertEquals("working content", reloaded.load(first, path)?.raw)
        assertTrue(reloaded.delete(first, path))
        assertTrue(reloaded.dismissArchived(first, kept))
        assertTrue(reloaded.dismissArchived(second, removed))
    }

    @Test fun dismissLegacyDraftRemovesMatchingFileAndPreferenceButNotScopedCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val settings = RepoSettings(owner = "author", repo = "one", branch = "main")
        val key = "draft_" + Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val legacy = LocalDraft(path, "old", "same recovery text", 1L, legacyUnassigned = true)
        val oldFile = File(File(context.filesDir, "intake_drafts").also { it.mkdirs() }, "$key.json")
        val prefs = context.getSharedPreferences("local_intake_drafts", Context.MODE_PRIVATE)
        val payload = JSONObject().put("path", path).put("base_sha", legacy.baseSha)
            .put("raw", legacy.raw).put("saved_at_ms", legacy.savedAtMillis).toString()
        oldFile.writeText(payload)
        assertTrue(prefs.edit().putString(key, payload).commit())
        val store = DraftStore(context)
        assertTrue(store.archive(settings, legacy.copy(legacyUnassigned = false)))
        try {
            assertEquals(2, store.archived(settings, path).size) // one scoped, one deduplicated legacy
            assertTrue(store.dismissArchived(settings, legacy))
            assertFalse(oldFile.exists())
            assertNull(prefs.getString(key, null))
            assertEquals(1, DraftStore(context).archived(settings, path).size)
            assertFalse(DraftStore(context).archived(settings, path).single().legacyUnassigned)
        } finally {
            oldFile.delete()
            prefs.edit().remove(key).commit()
            store.dismissArchived(settings, legacy.copy(legacyUnassigned = false))
        }
    }

    @Test fun legacyPreferenceDraftRemainsRecoverableAfterScopedSave() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val key = "draft_" + Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val prefs = context.getSharedPreferences("local_intake_drafts", Context.MODE_PRIVATE)
        val payload = JSONObject().put("path", path).put("base_sha", "old").put("raw", "preference draft").put("saved_at_ms", 2).toString()
        assertTrue(prefs.edit().putString(key, payload).commit())
        val settings = RepoSettings(owner = "owner", repo = "repo")
        val store = DraftStore(context)
        try {
            assertNull(store.load(settings, path))
            assertEquals("preference draft", store.archived(settings, path).single().raw)
            assertTrue(store.archived(settings, path).single().legacyUnassigned)
            assertTrue(store.save(settings, path, "new", "new scoped draft"))
            assertEquals(payload, prefs.getString(key, null))
        } finally {
            store.delete(settings, path)
            prefs.edit().remove(key).commit()
        }
    }

    @Test fun legacyDraftIsVisibleButNeverClaimedOrDeletedAutomatically() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = "test-${UUID.randomUUID()}/chapter.yml"
        val key = "draft_" + Base64.encodeToString(path.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val file = File(File(context.filesDir, "intake_drafts").also { it.mkdirs() }, "$key.json")
        file.writeText(JSONObject().put("path", path).put("base_sha", "old").put("raw", "legacy text").put("saved_at_ms", 1).toString())
        try {
            val first = RepoSettings(owner = "one", repo = "repo")
            val second = first.copy(owner = "two")
            val store = DraftStore(context)
            assertNull(store.load(first, path))
            assertEquals("legacy text", store.archived(first, path).single().raw)
            assertTrue(store.archived(first, path).single().legacyUnassigned)
            assertTrue(store.save(first, path, "new", "new draft"))
            assertNull(store.load(second, path))
            assertTrue(file.exists())
            assertTrue(store.delete(first, path))
            assertFalse(store.load(first, path) != null)
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
