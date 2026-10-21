package com.korenskoy.yandexmusic.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Layering rules from the plan (B1): screens and ViewModels reach the network only through repositories. */
class ArchitectureTest {

    private val sourceDir = File("src/main/java/com/korenskoy/yandexmusic")

    @Test
    fun presentationDoesNotCallTheApi() {
        val offenders = File(sourceDir, "presentation").walk()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("YandexMusicApi") }
            .map { it.relativeTo(sourceDir).path }
            .toList()
        assertEquals("presentation files using YandexMusicApi", emptyList<String>(), offenders)
    }

    @Test
    fun accountChangesResetPerUserDataInOnePlace() {
        val resets = sourceDir.walk()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("playlistsRepository.resetForAccountChange()") }
            .map { it.name }
            .toList()
        assertEquals(listOf("AccountSessionManager.kt"), resets)
        assertTrue(File(sourceDir, "presentation/main/MainViewModel.kt").readText().contains("session.syncAll()"))
    }
}
