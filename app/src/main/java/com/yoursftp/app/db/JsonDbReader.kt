package com.yoursftp.app.db

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Membaca file JSON / JSONL / NDJSON dan menyajikannya sebagai tabel.
 *
 * Strategi:
 *  - JSON array of objects → tabel "data", tiap objek = baris
 *  - JSON object, key → array → tiap key = tabel
 *  - JSON object tanpa nested array → tabel "root" key/value
 *  - JSONL (satu JSON per baris) → tabel "data"
 */
class JsonDbReader(private val path: String) : DbReader {

    override val formatName: String = "JSON"
    override val supportsCustomQuery: Boolean = false

    /** Parsed tables: name → list of row-maps. */
    private val tableData = LinkedHashMap<String, List<Map<String, String>>>()

    override fun open() {
        tableData.clear()
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("File tidak ditemukan: $path")

        val text = readFileSafe(file)
        val trimmed = text.trimStart()

        when {
            trimmed.startsWith("[") -> parseRootArray(trimmed)
            trimmed.startsWith("{") -> parseRootObject(trimmed)
            else -> parseJsonl(text)   // Assume JSONL / NDJSON
        }

        if (tableData.isEmpty()) {
            // Fallback: treat as JSONL if root parsing produced nothing
            parseJsonl(text)
        }
    }

    override fun close() {
        tableData.clear()
    }

    override fun tables(): List<String> = tableData.keys.toList()

    override fun tableRows(table: String, limit: Int, offset: Int): QueryResult {
        val rows = tableData[table] ?: return QueryResult(emptyList(), emptyList(), false)
        val columns = collectColumns(rows)
        val paged = rows.drop(offset).take(limit)
        val truncated = offset + limit < rows.size
        return QueryResult(
            columns,
            paged.map { row -> columns.map { col -> row[col] ?: "" } },
            truncated
        )
    }

    override fun rowCount(table: String): Long =
        tableData[table]?.size?.toLong() ?: 0

    override fun query(sql: String, limit: Int): QueryResult {
        throw UnsupportedOperationException("JSON tidak mendukung query SQL")
    }

    // ─── parsing helpers ──────────────────────────────────────────

    private fun readFileSafe(file: File): String {
        val maxBytes = 50L * 1024 * 1024 // 50 MB
        val size = minOf(file.length(), maxBytes)
        val sb = StringBuilder(size.toInt())
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

    private fun parseRootArray(text: String) {
        try {
            val arr = JSONArray(text)
            val rows = jsonArrayToRows(arr)
            if (rows.isNotEmpty()) tableData["data"] = rows
        } catch (e: Exception) {
            Log.w(TAG, "parseRootArray gagal, coba JSONL", e)
        }
    }

    private fun parseRootObject(text: String) {
        try {
            val obj = JSONObject(text)
            var hasArrayChild = false
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.opt(key)
                if (value is JSONArray) {
                    hasArrayChild = true
                    val rows = jsonArrayToRows(value)
                    if (rows.isNotEmpty()) tableData[key] = rows
                }
            }
            // Jika tidak ada child array, tampilkan key/value
            if (!hasArrayChild) {
                val rows = mutableListOf<Map<String, String>>()
                val objKeys = obj.keys()
                while (objKeys.hasNext()) {
                    val k = objKeys.next()
                    rows.add(mapOf("key" to k, "value" to stringifyValue(obj.opt(k))))
                }
                if (rows.isNotEmpty()) tableData["[root]"] = rows
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseRootObject gagal", e)
        }
    }

    private fun parseJsonl(text: String) {
        val rows = mutableListOf<Map<String, String>>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            try {
                val obj = JSONObject(t)
                rows.add(jsonObjectToRow(obj))
            } catch (_: Exception) {
                // Skip non-JSON lines
            }
            if (rows.size >= MAX_JSONL_ROWS) break
        }
        if (rows.isNotEmpty()) tableData["data"] = rows
    }

    private fun jsonArrayToRows(arr: JSONArray): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        for (i in 0 until minOf(arr.length(), MAX_JSONL_ROWS)) {
            val item = arr.opt(i)
            when (item) {
                is JSONObject -> rows.add(jsonObjectToRow(item))
                else -> rows.add(mapOf("value" to stringifyValue(item)))
            }
        }
        return rows
    }

    private fun jsonObjectToRow(obj: JSONObject): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = stringifyValue(obj.opt(k))
        }
        return map
    }

    private fun stringifyValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    /** Gabungan semua key yang muncul di seluruh baris, urut stabil. */
    private fun collectColumns(rows: List<Map<String, String>>): List<String> {
        val set = LinkedHashSet<String>()
        for (row in rows) set.addAll(row.keys)
        return set.toList()
    }

    companion object {
        private const val TAG = "JsonDbReader"
        private const val MAX_JSONL_ROWS = 50_000
    }
}
