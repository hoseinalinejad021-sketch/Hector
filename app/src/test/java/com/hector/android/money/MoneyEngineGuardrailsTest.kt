package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyEngineGuardrailsTest {
    @Test
    fun firstMilestoneIsReceiveOnlyAndCannotSendFunds() {
        assertTrue(MoneyEngineGuardrails.RECEIVE_ONLY_WALLET)
        assertFalse(MoneyEngineGuardrails.WITHDRAWALS_ENABLED)
        assertFalse(MoneyEngineGuardrails.TRANSACTION_SIGNING_ENABLED)
        assertFalse(MoneyEngineGuardrails.AUTOMATIC_SPENDING_ENABLED)
    }

    @Test
    fun regionRestrictionBypassIsDisabled() {
        assertFalse(MoneyEngineGuardrails.REGION_RESTRICTION_BYPASS_ENABLED)
    }

    @Test
    fun dailyGrowthIsAnOptimizationGoalNotAGuarantee() {
        assertTrue(MoneyEngineGuardrails.OPTIMIZE_FOR_DAILY_NET_INCOME_GROWTH)
        assertFalse(MoneyEngineGuardrails.GUARANTEED_DAILY_GROWTH)
    }

    @Test
    fun dailyComparisonUsesActualNetAmounts() {
        val comparison = DailyIncomeComparison(
            previousDayNetMinorUnits = 1200,
            currentDayNetMinorUnits = 1500
        )
        assertEquals(300L, comparison.changeMinorUnits)
        assertTrue(comparison.improved)
    }

    @Test
    fun flatOrLowerDailyIncomeIsNotMisreportedAsGrowth() {
        assertFalse(DailyIncomeComparison(1000, 1000).improved)
        assertFalse(DailyIncomeComparison(1000, 900).improved)
    }
}
