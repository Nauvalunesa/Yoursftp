package com.yoursftp.app.db

import android.database.sqlite.SQLiteDatabase

/**
 * Pembaca file SQLite (read-only) memakai mesin SQLite bawaan Android.
 * Tidak butuh library tambahan.
 */
class SqliteReader(private val path: String) : DbReader {

    private var db: SQLiteDatabase? = null

    override val formatName: String = "SQLite"
    override val supportsCustomQuery: Boolean = true

    override fun open() {
        db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
    }

    override fun close() {
        runCatching { db?.close() }
        db = null
    }

    /** Daftar nama tabel (dan view) pada database. */
    override fun tables(): List<String> {
        val d = db ?: return emptyList()
        val out = ArrayList<String>()
        d.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','view') " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name", null
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    /** Jumlah baris sebuah tabel. */
    override fun rowCount(table: String): Long {
        val d = db ?: return 0
        return d.rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    /** Ambil isi tabel (dibatasi [limit] baris, dengan offset). */
    override fun tableRows(table: String, limit: Int, offset: Int): QueryResult {
        val safe = table.replace("\"", "\"\"")
        return query("SELECT * FROM \"$safe\" LIMIT $limit OFFSET $offset", limit)
    }

    /** Jalankan query SELECT sembarang. */
    override fun query(sql: String, limit: Int): QueryResult {
        val d = db ?: return QueryResult(emptyList(), emptyList(), false)
        d.rawQuery(sql, null).use { c ->
            val cols = c.columnNames.toList()
            val rows = ArrayList<List<String>>()
            var truncated = false
            while (c.moveToNext()) {
                if (rows.size >= limit) { truncated = true; break }
                val row = ArrayList<String>(cols.size)
                for (i in cols.indices) {
                    row.add(
                        when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "[blob]"
                            else -> c.getString(i) ?: "NULL"
                        }
                    )
                }
                rows.add(row)
            }
            return QueryResult(cols, rows, truncated)
        }
    }
}
