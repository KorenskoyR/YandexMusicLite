package com.korenskoy.yandexmusic.presentation

import com.korenskoy.yandexmusic.domain.repository.SettingsRepository
import com.korenskoy.yandexmusic.presentation.update.UpdatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Issue #32: automatic update checks can be turned off while manual checks from Settings stay active. */
class UpdatePolicyTest {

    @Test
    fun autoCheckSettingGatesSilentChecksOnly() {
        assertTrue("manual check always runs when auto-check is off", UpdatePolicy.allowAutoCheck(autoCheckEnabled = false, silent = false))
        assertTrue("manual check always runs when auto-check is on", UpdatePolicy.allowAutoCheck(autoCheckEnabled = true, silent = false))
        assertFalse("silent check is blocked when auto-check is off", UpdatePolicy.allowAutoCheck(autoCheckEnabled = false, silent = true))
        assertTrue("silent check runs when auto-check is on", UpdatePolicy.allowAutoCheck(autoCheckEnabled = true, silent = true))
        assertTrue("auto-check defaults to enabled", SettingsRepository.DEFAULT_AUTO_CHECK_UPDATES)
    }

    @Test
    fun allFourAutomaticUpdateTriggersRespectSetting() {
        val sourceDir = File("src/main/java/com/korenskoy/yandexmusic")
        val viewModel = File(sourceDir, "presentation/update/UpdateViewModel.kt").readText()
        val worker = File(sourceDir, "data/updater/UpdateCheckWorker.kt").readText()

        assertTrue("UpdateViewModel must guard checks via UpdatePolicy.allowAutoCheck", viewModel.contains("UpdatePolicy.allowAutoCheck("))
        assertTrue("UpdateViewModel must react to autoCheckUpdates and cancel worker", viewModel.contains("UpdateCheckWorker.cancel(context)"))
        assertTrue("UpdateCheckWorker must abort doWork when autoCheckUpdates is false", worker.contains("autoCheckUpdates.first()"))
        assertTrue("UpdateCheckWorker must expose cancel(context)", worker.contains("fun cancel(context: Context)"))
    }
}
