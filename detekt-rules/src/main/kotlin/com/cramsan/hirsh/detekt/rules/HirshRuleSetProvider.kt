package com.cramsan.hirsh.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class HirshRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "hirsh"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ScreenMissingViewModel(config),
            ScreenMissingPreviews(config),
        ),
    )
}
