package com.cramsan.hirsh.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * Every `*Screen.kt` should have a matching `*Previews.kt` next to it with `@Preview`
 * composables, so the screen can be reviewed without running the app. `PatientListScreen.kt`
 * needs a `PatientListPreviews.kt` in the same directory.
 */
class ScreenMissingPreviews(config: Config) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "A *Screen.kt file has no matching *Previews.kt next to it.",
        Debt.TWENTY_MINS,
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // See the comment in ScreenMissingViewModel: KtFile.name isn't reliably the leaf
        // file name here, so derive it from virtualFilePath instead.
        val fileName = File(file.virtualFilePath).name
        if (!isScreenFile(fileName)) return
        val expected = expectedPreviewsFileName(fileName)
        if (!siblingFileExists(file.virtualFilePath, expected)) {
            report(
                CodeSmell(
                    issue,
                    Entity.atPackageOrFirstDecl(file),
                    "Expected a matching '$expected' next to '$fileName' but didn't find one.",
                ),
            )
        }
    }
}
