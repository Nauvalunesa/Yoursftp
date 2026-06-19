package com.yoursftp.app.db

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pembaca file BSON (Binary JSON) MongoDB.
 * Mengurai format biner data BSON standar ke representasi tabel kolom-baris secara dinamis.
 */
class BsonDbReader(private val path: String) : DbReader {

    override val formatName: String = "BSON"
    override val supportsCustomQuery: Boolean = false

    private val rows = ArrayList<Map<String, String>>()
    private val headers = ArrayList<String>()

    override fun open() {
        rows.clear()
        headers.clear()
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("File tidak ditemukan: $path")

        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        try {
            while (buffer.hasRemaining()) {
                val doc = parseDocument(buffer) ?: break
                rows.add(doc)
            }
        } catch (e: Exception) {
            // Hentikan jika terjadi kerusakan biner tengah jalan, pakai baris data yang sukses diurai
        }

        // Kumpulkan kolom unik dari semua dokumen
        val columnsSet = LinkedHashSet<String>()
        for (row in rows) {
            columnsSet.addAll(row.keys)
        }
        headers.addAll(columnsSet)
    }

    private fun parseDocument(buf: ByteBuffer): Map<String, String>? {
        if (buf.remaining() < 5) return null
        val startPos = buf.position()
        val size = buf.int
        if (size <= 5 || buf.remaining() < size - 4) {
            // Ukuran tidak valid atau buffer tidak mencukupi
            return null
        }

        val map = LinkedHashMap<String, String>()
        while (buf.position() < startPos + size - 1) {
            val type = buf.get().toInt()
            if (type == 0) break // Pembatas akhir daftar elemen
            val name = readCString(buf)
            val value = readElementValue(buf, type)
            map[name] = value
        }

        // Lompat ke akhir dokumen untuk memastikan posisi pointer sejajar
        buf.position(startPos + size)
        return map
    }

    private fun readCString(buf: ByteBuffer): String {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val b = buf.get()
            if (b == 0.toByte()) break
            out.write(b.toInt())
        }
        return out.toString("UTF-8")
    }

    private fun readElementValue(buf: ByteBuffer, type: Int): String {
        return when (type) {
            1 -> buf.double.toString() // Double
            2 -> { // String UTF-8
                val len = buf.int
                val bytes = ByteArray(len - 1)
                buf.get(bytes)
                buf.get() // null terminator byte
                String(bytes, Charsets.UTF_8)
            }
            3 -> { // Embedded Document (Dokumen bersarang)
                val doc = parseDocument(buf)
                doc?.toString() ?: "{}"
            }
            4 -> { // Array
                val arr = parseDocument(buf)
                arr?.values?.toString() ?: "[]"
            }
            5 -> { // Binary data
                val len = buf.int
                val subType = buf.get()
                buf.position(buf.position() + len) // Lewati byte payload
                "[Binary subtype $subType]"
            }
            7 -> { // ObjectId (12 bytes)
                val bytes = ByteArray(12)
                buf.get(bytes)
                bytes.joinToString("") { "%02x".format(it) }
            }
            8 -> if (buf.get() == 1.toByte()) "true" else "false" // Boolean
            9 -> { // UTC datetime
                val time = buf.long
                java.util.Date(time).toString()
            }
            10 -> "null" // Null value
            16 -> buf.int.toString() // 32-bit Integer
            18 -> buf.long.toString() // 64-bit Integer
            else -> {
                // Tipe yang tidak dikenal, abaikan dengan aman jika memungkinkan.
                // Karena BSON terstruktur, tipe tak dikenal bisa merusak sinkronisasi pointer.
                // Beri penanda biner tak dikenal.
                "[BSON Type $type]"
            }
        }
    }

    override fun close() {
        rows.clear()
        headers.clear()
    }

    override fun tables(): List<String> = listOf("documents")

    override fun tableRows(table: String, limit: Int, offset: Int): QueryResult {
        if (table != "documents") return QueryResult(emptyList(), emptyList(), false)
        val paged = rows.drop(offset).take(limit)
        val truncated = offset + limit < rows.size
        return QueryResult(
            headers,
            paged.map { row -> headers.map { col -> row[col] ?: "" } },
            truncated
        )
    }

    override fun rowCount(table: String): Long =
        if (table == "documents") rows.size.toLong() else 0

    override fun query(sql: String, limit: Int): QueryResult {
        throw UnsupportedOperationException("BSON tidak mendukung query SQL")
    }
}
