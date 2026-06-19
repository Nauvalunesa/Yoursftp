package com.yoursftp.app.db

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Pembaca file CSV / TSV yang mendukung quoted fields sesuai RFC 4180.
 */
class CsvDbReader(private val path: String) : DbReader {

    override val formatName: String = "CSV"
    override val supportsCustomQuery: Boolean = false

    private var tableName: String = "data"
    private var headers: List<String> = emptyList()
    private val rows = ArrayList<List<String>>()

    override fun open() {
        rows.clear()
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("File tidak ditemukan: $path")
        tableName = file.nameWithoutExtension.ifEmpty { "data" }

        // Deteksi separator awal berdasarkan ekstensi file
        val lower = file.name.lowercase()
        val separator = when {
            lower.endsWith(".tsv") -> '\t'
            else -> ','
        }

        val text = readFileSafe(file)
        parseCsv(text, separator)
    }

    private fun readFileSafe(file: File): String {
        val maxBytes = 50L * 1024 * 1024 // 50 MB
        val size = minOf(file.length(), maxBytes)
        val sb = java.lang.StringBuilder(size.toInt())
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { br ->
            val buf = CharArray(8192)
            var total = 0L
            while (true) {
                val n = br.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
                total += n
                if (total >= maxBytes) break
            }
        }
        return sb.toString()
    }

    private fun parseCsv(text: String, defaultSeparator: Char) {
        var sep = defaultSeparator
        if (sep == ',') {
            val firstLine = text.lineSequence().firstOrNull() ?: ""
            if (!firstLine.contains(",") && firstLine.contains(";")) {
                sep = ';'
            } else if (!firstLine.contains(",") && firstLine.contains("\t")) {
                sep = '\t'
            }
        }

        val allRows = ArrayList<List<String>>()
        val currentField = StringBuilder()
        var inQuotes = false
        var currentRow = ArrayList<String>()

        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < len && text[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == sep && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.setLength(0)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.setLength(0)
                    if (currentRow.isNotEmpty() && !(currentRow.size == 1 && currentRow[0].isEmpty())) {
                        allRows.add(currentRow)
                    }
                    currentRow = ArrayList()
                    if (c == '\r' && i + 1 < len && text[i + 1] == '\n') {
                        i++
                    }
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentRow.isNotEmpty() || currentField.isNotEmpty()) {
            currentRow.add(currentField.toString())
            allRows.add(currentRow)
        }

        if (allRows.isNotEmpty()) {
            headers = allRows[0]
            for (rowIndex in 1 until minOf(allRows.size, MAX_ROWS)) {
                val row = allRows[rowIndex]
                val paddedRow = ArrayList<String>(headers.size)
                for (colIndex in headers.indices) {
                    if (colIndex < row.size) {
                        paddedRow.add(row[colIndex])
                    } else {
                        paddedRow.add("")
                    }
                }
                rows.add(paddedRow)
            }
        }
    }

    override fun close() {
        rows.clear()
        headers = emptyList()
    }

    override fun tables(): List<String> = listOf(tableName)

    override fun tableRows(table: String, limit: Int, offset: Int): QueryResult {
        if (table != tableName) return QueryResult(emptyList(), emptyList(), false)
        val paged = rows.drop(offset).take(limit)
        val truncated = offset + limit < rows.size
        return QueryResult(headers, paged, truncated)
    }

    override fun rowCount(table: String): Long =
        if (table == tableName) rows.size.toLong() else 0

    override fun query(sql: String, limit: Int): QueryResult {
        throw UnsupportedOperationException("CSV tidak mendukung query SQL")
    }

    companion object {
        private const val MAX_ROWS = 50_000
    }
}
