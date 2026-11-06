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
