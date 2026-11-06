package cloud.shadowmonarchbooks.intakeedit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftProcessRestartAndroidTest {
    @Test fun writeOrVerifyAcrossSeparateInstrumentationProcesses() {
        val phase = InstrumentationRegistry.getArguments().getString("draft_phase") ?: return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DraftStore(context)
        val settings = RepoSettings(owner = "restart-test", repo = "restart-test", branch = "main")
        val path = "restart-test/chapter.yml"
        when (phase) {
            "write" -> assertTrue(store.save(settings, path, "base", "draft survives restart"))
            "verify" -> {
                assertEquals("draft survives restart", store.load(settings, path)?.raw)
                assertTrue(store.delete(settings, path))
            }
            else -> error("Unknown test phase")
        }
    }
}
