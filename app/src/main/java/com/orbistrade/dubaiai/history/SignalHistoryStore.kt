package com.orbistrade.dubaiai.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
                outcome TEXT
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE signals ADD COLUMN outcome TEXT")
    }

    fun insert(signal: StrategySignal, asset: String) {
        writableDatabase.insert("signals", null, ContentValues().apply {
            put("created_at", signal.timestamp)
            put("direction", signal.direction.name)
            put("score", signal.score)
            put("confidence", signal.confidence)
            put("reason", signal.reason)
            put("asset", asset.ifBlank { "ATIVO NÃO IDENTIFICADO" })
            putNull("outcome")
        })
    }

    fun setOutcome(id: Long, outcome: String?) {
        require(outcome == null || outcome == "WIN" || outcome == "LOSS")
        writableDatabase.update("signals", ContentValues().apply {
            if (outcome == null) putNull("outcome") else put("outcome", outcome)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun recent(limit: Int = 500): List<SignalHistoryItem> {
        val result = mutableListOf<SignalHistoryItem>()
        readableDatabase.query(
            "signals",
            arrayOf("id", "created_at", "direction", "score", "confidence", "reason", "asset", "outcome"),
            null, null, null, null, "created_at DESC", limit.coerceIn(1, 2000).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SignalHistoryItem(
                    id = cursor.getLong(0), timestamp = cursor.getLong(1), direction = cursor.getString(2),
                    score = cursor.getInt(3), confidence = cursor.getString(4), reason = cursor.getString(5),
                    asset = cursor.getString(6), outcome = if (cursor.isNull(7)) null else cursor.getString(7)
                )
            }
        }
        return result
    }

    fun csv(): String = buildString {
        appendLine("id;data;direcao;score;confianca;ativo;resultado;motivo")
        recent(2000).asReversed().forEach { item ->
            fun clean(value: String) = value.replace(";", ",").replace("\n", " ")
            appendLine("${item.id};${item.timestamp};${item.direction};${item.score};${clean(item.confidence)};${clean(item.asset)};${item.outcome.orEmpty()};${clean(item.reason)}")
        }
    }

    companion object {
        private const val DB_NAME = "orbis_trade.db"
        private const val DB_VERSION = 2
    }
}

data class SignalHistoryItem(
    val id: Long,
    val timestamp: Long,
    val direction: String,
    val score: Int,
    val confidence: String,
    val reason: String,
    val asset: String,
    val outcome: String? = null
)
