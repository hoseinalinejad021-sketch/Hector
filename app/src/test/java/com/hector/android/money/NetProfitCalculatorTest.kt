package com.hector.android.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetProfitCalculatorTest {
    private fun record(
        id: String,
        status: IncomeStatus,
        gross: Long,
        platform: Long = 0,
        payout: Long = 0,
        conversion: Long = 0,
        cost: Long = 0,
        currency: String = "USD"
    ) = IncomeRecord(
        id = id,
        sourceId = "test-provider",
        currency = currency,
        grossMinorUnits = gross,
        platformFeeMinorUnits = platform,
        payoutFeeMinorUnits = payout,
        conversionFeeMinorUnits = conversion,
        directCostMinorUnits = cost,
        status = status,
        createdAtEpochMillis = 1L
    )

    @Test
    fun pendingAndOpportunitiesAreNotReportedAsRealizedMoney() {
        val summary = NetProfitCalculator.summarize(
            listOf(
                record("1", IncomeStatus.PENDING, 5000),
                record("2", IncomeStatus.OPPORTUNITY, 9000),
                record("3", IncomeStatus.CONFIRMED_RECEIVED, 1200)
            ),
            "USD"
        )
        assertEquals(1200L, summary.realizedGrossMinorUnits)
        assertEquals(1, summary.pendingCount)
        assertEquals(1, summary.opportunityCount)
    }

    @Test
    fun netProfitSubtractsAllCosts() {
        val item = record(
            "1", IncomeStatus.CONFIRMED_RECEIVED, 10000,
            platform = 1000, payout = 200, conversion = 300, cost = 500
        )
        assertEquals(8000L, item.netMinorUnits())
    }

    @Test
    fun costsMayExceedIncomeWithoutInventingProfit() {
        val item = record("1", IncomeStatus.CONFIRMED_RECEIVED, 100, platform = 150)
        assertEquals(-50L, item.netMinorUnits())
    }

    @Test
    fun unconfirmedEntryIsNotRealized() {
        assertFalse(record("1", IncomeStatus.PENDING, 100).countsAsRealizedIncome())
        assertTrue(record("2", IncomeStatus.CONFIRMED_RECEIVED, 100).countsAsRealizedIncome())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeGrossIsRejected() {
        record("1", IncomeStatus.PENDING, -1)
    }
}
