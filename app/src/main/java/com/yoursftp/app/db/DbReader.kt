package com.yoursftp.app.db

/** Hasil sebuah query: nama kolom + baris (tiap sel sebagai String). */
class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val truncated: Boolean
)

/** Format database yang didukung. */
enum class DbFormat(val label: String) {
    SQLITE("SQLite"),
    JSON("JSON / NoSQL"),
    CSV("CSV"),
    SQL_DUMP("SQL Dump"),
    XML("XML Data"),
    BSON("BSON")
}

/**
 * Antarmuka umum untuk membaca berbagai format database.
 * Semua implementasi menghasilkan QueryResult yang seragam.
 */
interface DbReader {
    val formatName: String
    val supportsCustomQuery: Boolean
    fun open()
    fun close()
    fun tables(): List<String>
    fun tableRows(table: String, limit: Int = 200, offset: Int = 0): QueryResult
    fun rowCount(table: String): Long
    fun query(sql: String, limit: Int = 500): QueryResult
}

/** Deteksi format database berdasarkan nama file. */
fun detectDbFormat(name: String): DbFormat {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".json") || lower.endsWith(".jsonl") ||
            lower.endsWith(".ndjson") || lower.endsWith(".geojson") -> DbFormat.JSON
        lower.endsWith(".csv") || lower.endsWith(".tsv") -> DbFormat.CSV
        lower.endsWith(".sql") -> DbFormat.SQL_DUMP
        lower.endsWith(".xml") && !lower.endsWith(".plist") -> DbFormat.XML
        lower.endsWith(".bson") -> DbFormat.BSON
        else -> DbFormat.SQLITE
    }
}

/** Buat DbReader yang sesuai untuk format tertentu. */
fun createDbReader(path: String, format: DbFormat): DbReader {
    return when (format) {
        DbFormat.SQLITE -> SqliteReader(path)
        DbFormat.JSON -> JsonDbReader(path)
        DbFormat.CSV -> CsvDbReader(path)
        DbFormat.SQL_DUMP -> SqlDumpReader(path)
        DbFormat.XML -> XmlDbReader(path)
        DbFormat.BSON -> JsonDbReader(path) // BSON fallback: try reading as JSON
    }
}
