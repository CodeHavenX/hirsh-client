package com.cramsan.hirsh.detekt.rules

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenFileConventionsTest {

    @Test
    fun `recognizes Screen files`() {
        assertTrue(isScreenFile("PatientListScreen.kt"))
        assertFalse(isScreenFile("PatientListViewModel.kt"))
        assertFalse(isScreenFile("Screen.kt"))
    }

    @Test
    fun `derives the expected sibling file names from the Screen file name`() {
        assertEquals("PatientListViewModel.kt", expectedViewModelFileName("PatientListScreen.kt"))
        assertEquals("PatientListPreviews.kt", expectedPreviewsFileName("PatientListScreen.kt"))
        assertEquals("LoginViewModel.kt", expectedViewModelFileName("LoginScreen.kt"))
    }

    @Test
    fun `sibling check finds a file that exists next to the screen`() {
        val dir = createTempDirectory().toFile()
        File(dir, "PatientListViewModel.kt").createNewFile()
        val screenPath = File(dir, "PatientListScreen.kt").path

        assertTrue(siblingFileExists(screenPath, "PatientListViewModel.kt"))
        assertFalse(siblingFileExists(screenPath, "PatientListPreviews.kt"))
    }

    @Test
    fun `sibling check does not report when the path is not on disk`() {
        assertTrue(siblingFileExists("/does/not/exist/PatientListScreen.kt", "PatientListViewModel.kt"))
    }
}
