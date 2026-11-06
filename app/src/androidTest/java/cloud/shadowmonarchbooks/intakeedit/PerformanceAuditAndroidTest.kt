package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PerformanceAuditAndroidTest {
    @Test fun measuresLargeChapterImportAndStatusCache() {
        val entries = (1..1000).map { EditorEntry("P$it", "日本語の文章$it", "") }
        val document = EditorDocument(6, 1, 1, "source", "sha", "reader", "title", "", false, entries)
        val xliff = buildString {
            append("<xliff version=\"1.2\"><file><body>")
            entries.forEachIndexed { index, entry -> append("<trans-unit id=\"$index\"><source>${entry.sourceJapanese}</source><target>English $index</target></trans-unit>") }
            append("</body></file></xliff>")
        }
        val importStart = System.nanoTime()
        val overlay = XliffTranslationImporter.parse(xliff, "synthetic.xliff", "chapter.yml", document)
        val importMs = (System.nanoTime() - importStart) / 1_000_000
        assertEquals(1000, overlay.matchedCount)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = RepoSettings(owner = "benchmark", repo = UUID.randomUUID().toString())
        val files = (1..1400).map { ChapterFile("editor_input/vol-01/chapters/ch_$it.yml", 1, it) }
        val snapshot = ChapterListCacheSnapshot(files, files.associate { it.path to ChapterProgress(1, 2, false) })
        val store = SharedPreferencesChapterProgressCacheStore(context)
        val cacheStart = System.nanoTime()
        store.save(settings, snapshot)
        val restored = store.load(settings)
        val cacheMs = (System.nanoTime() - cacheStart) / 1_000_000
        assertEquals(1400, restored?.files?.size)
        val key = "${settings.owner}/${settings.repo}@${settings.branch}:${settings.intakeRoot}"
        val preferences = context.getSharedPreferences("chapter_progress_cache", Context.MODE_PRIVATE)
        val bytes = preferences.getString(key, "").orEmpty().toByteArray(Charsets.UTF_8).size
        preferences.edit().remove(key).commit()
        Log.i("IntakePerfAudit", "1000 XLIFF entries: ${importMs}ms; 1400 chapter cache save/load: ${cacheMs}ms; cache JSON: $bytes bytes")
    }

    @Test fun recordsCompanionPackLoadWithoutDroppingFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pssBefore = Debug.getPss()
        val start = System.nanoTime()
        val animation = CompanionAnimationPack.load(context, PetMood.HAPPY)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        val pssAfter = Debug.getPss()
        assertTrue(animation.frames.isNotEmpty())
        assertTrue(animation.idleActions.all { it.frames.isNotEmpty() })
        Log.i("IntakePerfAudit", "Happy companion load: ${elapsed}ms; process PSS before=${pssBefore}KB after=${pssAfter}KB (not an isolated memory measurement)")
    }
}
