package com.orbistrade.dubaiai.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.orbistrade.dubaiai.lab.MarketContext
import com.orbistrade.dubaiai.lab.MarketMode
import com.orbistrade.dubaiai.lab.MarketRegime
import com.orbistrade.dubaiai.strategy.StrategySignal

class SignalHistoryStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE signals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                direction TEXT NOT NULL,
                score INTEGER NOT NULL,
                confidence TEXT NOT NULL,
                reason TEXT NOT NULL,
                asset TEXT NOT NULL,
                outcome TEXT,
                market_mode TEXT NOT NULL DEFAULT 'OTC',
                payout REAL,
                expiry_seconds INTEGER,
                entry_price REAL,
                exit_price REAL,
                regime TEXT NOT NULL DEFAULT 'LOW_QUALITY',
                calibrated_probability REAL,
                expected_value REAL,
                screenshot_entry TEXT,
                screenshot_exit TEXT,
                audit_note TEXT
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE signals ADD COLUMN outcome TEXT")
        if (oldVersion < 3) {
            listOf(
                "ALTER TABLE signals ADD COLUMN market_mode TEXT NOT NULL DEFAULT 'OTC'",
                "ALTER TABLE signals ADD COLUMN payout REAL",
                "ALTER TABLE signals ADD COLUMN expiry_seconds INTEGER",
                "ALTER TABLE signals ADD COLUMN entry_price REAL",
                "ALTER TABLE signals ADD COLUMN exit_price REAL",
                "ALTER TABLE signals ADD COLUMN regime TEXT NOT NULL DEFAULT 'LOW_QUALITY'",
                "ALTER TABLE signals ADD COLUMN calibrated_probability REAL",
                "ALTER TABLE signals ADD COLUMN expected_value REAL",
                "ALTER TABLE signals ADD COLUMN screenshot_entry TEXT",
                "ALTER TABLE signals ADD COLUMN screenshot_exit TEXT",
                "ALTER TABLE signals ADD COLUMN audit_note TEXT"
            ).forEach(db::execSQL)
        }
    }

    fun insert(
        signal: StrategySignal,
        context: MarketContext,
        regime: MarketRegime = MarketRegime.LOW_QUALITY,
        calibratedProbability: Double? = null,
        expectedValue: Double? = null,
        entryScreenshot: String? = null
    ) {
        writableDatabase.insert("signals", null, ContentValues().apply {
            put("created_at", signal.timestamp)
            put("direction", signal.direction.name)
            put("score", signal.score)
            put("confidence", signal.confidence)
            put("reason", signal.reason)
            put("asset", context.asset.ifBlank { "ATIVO NÃO IDENTIFICADO" })
            putNull("outcome")
            put("market_mode", context.mode.name)
            context.payoutPercent?.let { put("payout", it) }
            context.expirySeconds?.let { put("expiry_seconds", it) }
            context.currentPrice?.let { put("entry_price", it) }
            put("regime", regime.name)
            calibratedProbability?.let { put("calibrated_probability", it) }
            expectedValue?.let { put("expected_value", it) }
            entryScreenshot?.let { put("screenshot_entry", it) }
        })
    }

    fun insert(signal: StrategySignal, asset: String) = insert(
        signal,
        MarketContext(mode = MarketMode.OTC, asset = asset.ifBlank { "ATIVO NÃO IDENTIFICADO" })
    )

    fun setOutcome(id: Long, outcome: String?, exitPrice: Double? = null, auditNote: String? = null) {
        require(outcome == null || outcome in setOf("WIN", "LOSS", "DRAW", "INVALIDATED"))
        writableDatabase.update("signals", ContentValues().apply {
            if (outcome == null) putNull("outcome") else put("outcome", outcome)
            exitPrice?.let { put("exit_price", it) }
            auditNote?.let { put("audit_note", it) }
        }, "id = ?", arrayOf(id.toString()))
    }

    fun recent(limit: Int = 500): List<SignalHistoryItem> {
        val result = mutableListOf<SignalHistoryItem>()
        readableDatabase.query(
            "signals",
            arrayOf("id","created_at","direction","score","confidence","reason","asset","outcome","market_mode","payout","expiry_seconds","entry_price","exit_price","regime","calibrated_probability","expected_value","audit_note"),
            null, null, null, null, "created_at DESC", limit.coerceIn(1, 5000).toString()
        ).use { c ->
            while (c.moveToNext()) result += SignalHistoryItem(
                id = c.getLong(0), timestamp = c.getLong(1), direction = c.getString(2), score = c.getInt(3),
                confidence = c.getString(4), reason = c.getString(5), asset = c.getString(6),
                outcome = if (c.isNull(7)) null else c.getString(7), marketMode = c.getString(8),
                payout = if (c.isNull(9)) null else c.getDouble(9), expirySeconds = if (c.isNull(10)) null else c.getInt(10),
                entryPrice = if (c.isNull(11)) null else c.getDouble(11), exitPrice = if (c.isNull(12)) null else c.getDouble(12),
                regime = c.getString(13), calibratedProbability = if (c.isNull(14)) null else c.getDouble(14),
                expectedValue = if (c.isNull(15)) null else c.getDouble(15), auditNote = if (c.isNull(16)) null else c.getString(16)
            )
        }
        return result
    }

    fun csv(): String = buildString {
        appendLine("id;data;mercado;ativo;direcao;score;confianca;payout;vencimento_s;entrada;saida;regime;probabilidade;ev;resultado;motivo;auditoria")
        recent(5000).asReversed().forEach { i ->
            fun clean(v: String?) = v.orEmpty().replace(";", ",").replace("\n", " ")
            appendLine(listOf(i.id,i.timestamp,i.marketMode,clean(i.asset),i.direction,i.score,clean(i.confidence),i.payout?:"",i.expirySeconds?:"",i.entryPrice?:"",i.exitPrice?:"",i.regime,i.calibratedProbability?:"",i.expectedValue?:"",i.outcome?:"",clean(i.reason),clean(i.auditNote)).joinToString(";"))
        }
    }

    companion object { private const val DB_NAME = "orbis_trade.db"; private const val DB_VERSION = 3 }
}

data class SignalHistoryItem(
    val id: Long,
    val timestamp: Long,
    val direction: String,
    val score: Int,
    val confidence: String,
    val reason: String,
    val asset: String,
    val outcome: String? = null,
    val marketMode: String = MarketMode.OTC.name,
    val payout: Double? = null,
    val expirySeconds: Int? = null,
    val entryPrice: Double? = null,
    val exitPrice: Double? = null,
    val regime: String = MarketRegime.LOW_QUALITY.name,
    val calibratedProbability: Double? = null,
    val expectedValue: Double? = null,
    val auditNote: String? = null
)
