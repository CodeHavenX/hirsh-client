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
 * Every `*Screen.kt` should keep its state and logic in a matching `*ViewModel.kt` next to it,
 * rather than inline (direct repository/service calls, mutable state) in the composable.
 * `PatientListScreen.kt` needs a `PatientListViewModel.kt` in the same directory.
 */
class ScreenMissingViewModel(config: Config) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "A *Screen.kt file has no matching *ViewModel.kt next to it.",
        Debt.TWENTY_MINS,
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // KtFile.name isn't reliably just the leaf file name in this (Gradle CLI, light-tree)
        // analysis context -- it can come back as the full path -- so derive it from
        // virtualFilePath instead, which is unambiguously a real filesystem path.
        val fileName = File(file.virtualFilePath).name
        if (!isScreenFile(fileName)) return
        val expected = expectedViewModelFileName(fileName)
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
