package com.cramsan.hirsh.detekt.rules

import java.io.File

private const val SCREEN_SUFFIX = "Screen.kt"

/** True for e.g. "PatientListScreen.kt". False for "Screen.kt" itself (no base name) or anything else. */
internal fun isScreenFile(fileName: String): Boolean =
    fileName.endsWith(SCREEN_SUFFIX) && fileName != SCREEN_SUFFIX

/** "PatientListScreen.kt" -> "PatientList" */
internal fun screenBaseName(fileName: String): String = fileName.removeSuffix(SCREEN_SUFFIX)

internal fun expectedViewModelFileName(screenFileName: String): String =
    screenBaseName(screenFileName) + "ViewModel.kt"

internal fun expectedPreviewsFileName(screenFileName: String): String =
    screenBaseName(screenFileName) + "Previews.kt"

/**
 * Whether [siblingFileName] exists next to the file at [screenFilePath]. Returns true (i.e.
 * doesn't trigger a finding) when [screenFilePath] isn't a real path on disk -- e.g. a rule
 * running against in-memory code in a unit test -- since there's nothing to check in that case.
 */
internal fun siblingFileExists(screenFilePath: String, siblingFileName: String): Boolean {
    val dir = File(screenFilePath).parentFile ?: return true
    if (!dir.isDirectory) return true
    return File(dir, siblingFileName).isFile
}
