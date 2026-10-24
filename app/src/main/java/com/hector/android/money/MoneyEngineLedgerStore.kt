package com.hector.android.money

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/**
 * Local-only ledger persistence for the daily review. Provider integrations must call upsert only
 * after independently verifying a permitted, provider-confirmed record.
 */
class MoneyEngineLedgerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun upsert(record: IncomeRecord): Boolean {
        val rows = readAll().toMutableList()
        val existing = rows.indexOfFirst { it.id == record.id }
        if (existing >= 0) {
            val old = rows[existing]
            require(old.sourceId == record.sourceId && old.currency == record.currency) {
                "A ledger id cannot be reused across sources or currencies"
            }
            rows[existing] = record
        } else {
            rows.add(record)
        }
        prefs.edit().putString(KEY_RECORDS, JSONArray().also { array ->
            rows.forEach { array.put(it.toJson()) }
        }.toString()).apply()
        return existing < 0
    }

    @Synchronized
    fun recordsForDay(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<IncomeRecord> =
        readAll().filter { Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zone).toLocalDate() == day }

    @Synchronized
    fun saveLatestReport(report: MoneyEngineRootCauseReport, day: LocalDate, currency: String = "UNKNOWN") {
        val json = JSONObject()
            .put("day", day.toString())
            .put("cause", report.cause.name)
            .put("evidence", JSONArray(report.evidence))
            .put("nextChecks", JSONArray(report.nextChecks))
            .put("mayProposeExperiment", report.mayProposeExperiment)
        val safeCurrency = currency.uppercase().replace(Regex("[^A-Z0-9_-]"), "_")
        prefs.edit().putString("${KEY_LAST_REPORT}_$safeCurrency", json.toString()).apply()
    }

    private fun readAll(): List<IncomeRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                IncomeRecord(
                    id = o.getString("id"),
                    sourceId = o.getString("sourceId"),
                    currency = o.getString("currency"),
                    grossMinorUnits = o.getLong("grossMinorUnits"),
                    platformFeeMinorUnits = o.optLong("platformFeeMinorUnits"),
                    payoutFeeMinorUnits = o.optLong("payoutFeeMinorUnits"),
                    conversionFeeMinorUnits = o.optLong("conversionFeeMinorUnits"),
                    directCostMinorUnits = o.optLong("directCostMinorUnits"),
                    status = IncomeStatus.valueOf(o.getString("status")),
                    createdAtEpochMillis = o.getLong("createdAtEpochMillis"),
                    providerReference = o.optString("providerReference").takeIf { it.isNotBlank() }
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun IncomeRecord.toJson() = JSONObject()
        .put("id", id)
        .put("sourceId", sourceId)
        .put("currency", currency)
        .put("grossMinorUnits", grossMinorUnits)
        .put("platformFeeMinorUnits", platformFeeMinorUnits)
        .put("payoutFeeMinorUnits", payoutFeeMinorUnits)
        .put("conversionFeeMinorUnits", conversionFeeMinorUnits)
        .put("directCostMinorUnits", directCostMinorUnits)
        .put("status", status.name)
        .put("createdAtEpochMillis", createdAtEpochMillis)
        .put("providerReference", providerReference ?: "")

    private companion object {
        const val PREFS = "hector_money_engine_ledger"
        const val KEY_RECORDS = "records_json"
        const val KEY_LAST_REPORT = "last_root_cause_report_json"
    }
}
