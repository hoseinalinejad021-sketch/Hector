package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyEngineRootCauseAnalyzerTest {
    @Test
    fun missingLedgerDataDoesNotSuggestExperiment() {
        val review = MoneyEngineDailyReviewCore.review(emptyList(), emptyList(), "USD")

        val report = MoneyEngineRootCauseAnalyzer.analyze(review)

        assertEquals(MoneyEngineRootCause.MISSING_CONFIRMED_DATA, report.cause)
        assertFalse(report.mayProposeExperiment)
        assertTrue(report.nextChecks.any { it.contains("source", ignoreCase = true) })
    }

    @Test
    fun negativeNetChangeIdentifiesEvidenceAndChecksRatherThanClaimingCertainty() {
        val review = MoneyEngineDailyReview(
            currency = "USD",
            previousDayNetMinorUnits = 10_000,
            currentDayNetMinorUnits = 7_000,
            changeMinorUnits = -3_000,
            diagnosis = "NET_INCOME_DOWN: inspect source-level changes.",
            sourceChanges = listOf(SourceNetChange("provider-a", 10_000, 7_000))
        )

        val report = MoneyEngineRootCauseAnalyzer.analyze(review)

        assertEquals(MoneyEngineRootCause.NET_INCOME_DECLINED, report.cause)
        assertTrue(report.mayProposeExperiment)
        assertTrue(report.evidence.any { it.contains("provider-a") })
        assertTrue(report.nextChecks.any { it.contains("correlation", ignoreCase = true) })
    }

    @Test
    fun unchangedTotalWithChangingSourcesIsDiagnosedAsSourceMixShift() {
        val review = MoneyEngineDailyReview(
            currency = "USD",
            previousDayNetMinorUnits = 10_000,
            currentDayNetMinorUnits = 10_000,
            changeMinorUnits = 0,
            diagnosis = "NET_INCOME_UNCHANGED: inspect source-level shifts.",
            sourceChanges = listOf(
                SourceNetChange("provider-a", 10_000, 0),
                SourceNetChange("provider-b", 0, 10_000)
            )
        )

        val report = MoneyEngineRootCauseAnalyzer.analyze(review)

        assertEquals(MoneyEngineRootCause.SOURCE_MIX_SHIFT, report.cause)
        assertTrue(report.mayProposeExperiment)
    }

    @Test
    fun positiveChangeIsNotTreatedAsProofOfLongTermTrend() {
        val review = MoneyEngineDailyReview(
            currency = "USD",
            previousDayNetMinorUnits = 5_000,
            currentDayNetMinorUnits = 6_000,
            changeMinorUnits = 1_000,
            diagnosis = "NET_INCOME_UP: compare source-level changes.",
            sourceChanges = listOf(SourceNetChange("provider-a", 5_000, 6_000))
        )

        val report = MoneyEngineRootCauseAnalyzer.analyze(review)

        assertEquals(MoneyEngineRootCause.NET_INCOME_IMPROVED, report.cause)
        assertFalse(report.mayProposeExperiment)
        assertTrue(report.nextChecks.any { it.contains("additional completed periods") })
    }
}
