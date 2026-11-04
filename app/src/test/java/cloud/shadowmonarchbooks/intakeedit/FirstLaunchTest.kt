package cloud.shadowmonarchbooks.intakeedit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchTest {
    @Test fun freshInstallWithoutTokenOpensSetup() {
        assertTrue(shouldOpenFirstLaunchSettings(100, 100, ""))
    }

    @Test fun upgradingOrRestoringExistingSetupDoesNotForceSetup() {
        assertFalse(shouldOpenFirstLaunchSettings(100, 200, ""))
        assertFalse(shouldOpenFirstLaunchSettings(100, 100, "saved token"))
    }
}
