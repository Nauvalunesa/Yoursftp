package com.yoursftp.app.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Pembaca file SQL Dump yang memuat isi file ke database SQLite in-memory
 * dan mengekspos tabel/view yang berhasil di-load.
 */
class SqlDumpReader(private val path: String) : DbReader {

    override val formatName: String = "SQL Dump"
    override val supportsCustomQuery: Boolean = true

    private var db: SQLiteDatabase? = null

    override fun open() {
        close()
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("File tidak ditemukan: $path")

        val inMemoryDb = SQLiteDatabase.create(null)
        db = inMemoryDb

        val text = readFileSafe(file)
        val statements = parseSqlStatements(text)

        for (statement in statements) {
            val cleaned = cleanSqlDialect(statement)
            if (cleaned.isBlank()) continue
            try {
                inMemoryDb.execSQL(cleaned)
            } catch (e: Exception) {
                Log.w("SqlDumpReader", "Gagal mengeksekusi statement: $cleaned", e)
            }
        }
    }

    private fun readFileSafe(file: File): String {
        val maxBytes = 50L * 1024 * 1024 // Batasan 50 MB
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

    private fun parseSqlStatements(sqlText: String): List<String> {
        val statements = ArrayList<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inBacktick = false
        var i = 0
        val len = sqlText.length
        while (i < len) {
            val c = sqlText[i]
            when {
                // Line comment --
                c == '-' && i + 1 < len && sqlText[i + 1] == '-' -> {
                    while (i < len && sqlText[i] != '\n' && sqlText[i] != '\r') {
                        i++
                    }
                }
                // Block comment /* */
                c == '/' && i + 1 < len && sqlText[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < len && !(sqlText[i] == '*' && sqlText[i + 1] == '/')) {
                        i++
                    }
                    i += 2
                }
                c == '\'' && !inDoubleQuote && !inBacktick -> {
                    inSingleQuote = !inSingleQuote
                    current.append(c)
                }
                c == '"' && !inSingleQuote && !inBacktick -> {
                    inDoubleQuote = !inDoubleQuote
                    current.append(c)
                }
                c == '`' && !inSingleQuote && !inDoubleQuote -> {
                    inBacktick = !inBacktick
                    current.append(c)
                }
                c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick -> {
                    val stmt = current.toString().trim()
                    if (stmt.isNotEmpty()) {
                        statements.add(stmt)
                    }
                    current.setLength(0)
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) {
            statements.add(last)
        }
        return statements
    }

    private fun cleanSqlDialect(sql: String): String {
        var cleaned = sql.replace('`', '"')

        // Hapus MySQL conditional comments (seperti /*!40101 SET ... */)
        cleaned = cleaned.replace("/\\*!\\d*[^\\*]*\\*/".toRegex(), "")

        // Hapus AUTO_INCREMENT
        cleaned = cleaned.replace("(?i)\\bAUTO_INCREMENT\\b".toRegex(), "")

        // Hapus opsi tabel MySQL di akhir CREATE TABLE
        cleaned = cleaned.replace("(?i)\\bENGINE\\s*=\\s*\\w+".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\bDEFAULT\\s+CHARSET\\s*=\\s*\\w+".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\bCOLLATE\\s*=\\s*\\w+".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\bCHARACTER\\s+SET\\s*=\\s*\\w+".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\bAUTO_INCREMENT\\s*=\\s*\\d+".toRegex(), "")
        cleaned = cleaned.replace("(?i)\\bROW_FORMAT\\s*=\\s*\\w+".toRegex(), "")

        // Bersihkan baris kolom pada pernyataan CREATE TABLE
        if (cleaned.trimStart().startsWith("CREATE TABLE", ignoreCase = true)) {
            cleaned = cleanCreateTableStatement(cleaned)
        }

        return cleaned
    }

    private fun cleanCreateTableStatement(sql: String): String {
        val firstParen = sql.indexOf('(')
        val lastParen = sql.lastIndexOf(')')
        if (firstParen == -1 || lastParen == -1 || firstParen >= lastParen) return sql

        val prefix = sql.substring(0, firstParen + 1)
        val body = sql.substring(firstParen + 1, lastParen)
        val suffix = sql.substring(lastParen)

        val parts = splitByTopLevelCommas(body)
        val cleanParts = ArrayList<String>()

        for (part in parts) {
            val trimmed = part.trim()
            val lower = trimmed.lowercase()

            // Skip MySQL keys/indexes yang tidak didukung di dalam CREATE TABLE SQLite
            if (lower.startsWith("key ") ||
                lower.startsWith("unique key ") ||
                lower.startsWith("index ") ||
                lower.startsWith("fulltext ") ||
                lower.startsWith("spatial ") ||
                lower.startsWith("constraint ")
            ) {
                continue
            }
            cleanParts.add(part)
        }

        var joinedBody = cleanParts.joinToString(", ")
        joinedBody = joinedBody.trim().removeSuffix(",")

        return prefix + joinedBody + suffix
    }

    private fun splitByTopLevelCommas(text: String): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var parenDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            when (c) {
                '\'' -> {
                    if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                    current.append(c)
                }
                '"' -> {
                    if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                    current.append(c)
                }
                '(' -> {
                    if (!inSingleQuote && !inDoubleQuote) parenDepth++
                    current.append(c)
                }
                ')' -> {
                    if (!inSingleQuote && !inDoubleQuote) parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    current.append(c)
                }
                ',' -> {
                    if (!inSingleQuote && !inDoubleQuote && parenDepth == 0) {
                        parts.add(current.toString())
                        current.setLength(0)
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString())
        }
        return parts
    }

    override fun close() {
        runCatching { db?.close() }
        db = null
    }

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

    override fun rowCount(table: String): Long {
        val d = db ?: return 0
        return d.rawQuery("SELECT COUNT(*) FROM \"${table.replace("\"", "\"\"")}\"", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0
        }
    }

    override fun tableRows(table: String, limit: Int, offset: Int): QueryResult {
        val safe = table.replace("\"", "\"\"")
        return query("SELECT * FROM \"$safe\" LIMIT $limit OFFSET $offset", limit)
    }

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
