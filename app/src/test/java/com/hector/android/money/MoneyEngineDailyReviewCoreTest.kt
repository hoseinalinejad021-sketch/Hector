package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyEngineDailyReviewCoreTest {
    private fun record(id: String, source: String, amount: Long, status: IncomeStatus) =
        IncomeRecord(id, source, "USD", amount, status = status, createdAtEpochMillis = 1L)

    @Test fun comparesOnlyConfirmedIncomeAndIdentifiesSourceChanges() {
        val review = MoneyEngineDailyReviewCore.review(
            listOf(record("a", "source-a", 1000, IncomeStatus.CONFIRMED_RECEIVED)),
            listOf(
                record("b", "source-a", 700, IncomeStatus.CONFIRMED_RECEIVED),
                record("c", "source-b", 500, IncomeStatus.PENDING),
                record("d", "source-c", 300, IncomeStatus.PAID_OUT)
            ),
            "USD"
        )
        assertEquals(1000L, review.previousDayNetMinorUnits)
        assertEquals(1000L, review.currentDayNetMinorUnits)
        assertEquals(0L, review.changeMinorUnits)
        assertTrue(review.sourceChanges.any { it.sourceId == "source-a" && it.changeMinorUnits == -300L })
        assertTrue(review.sourceChanges.any { it.sourceId == "source-c" && it.changeMinorUnits == 300L })
    }

    @Test fun reportsMissingEvidenceInsteadOfInventingGrowth() {
        val review = MoneyEngineDailyReviewCore.review(emptyList(), emptyList(), "USD")
        assertEquals(0L, review.changeMinorUnits)
        assertTrue(review.diagnosis.startsWith("NO_CONFIRMED_INCOME_DATA"))
        assertTrue(!review.hasConfirmedIncomeEvidence)
    }
}
