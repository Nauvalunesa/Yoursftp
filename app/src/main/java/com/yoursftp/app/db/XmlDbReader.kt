package com.yoursftp.app.db

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Pembaca file XML data yang memetakan elemen anak root sebagai baris tabel.
 */
class XmlDbReader(private val path: String) : DbReader {

    override val formatName: String = "XML"
    override val supportsCustomQuery: Boolean = false

    private val tableData = LinkedHashMap<String, MutableList<Map<String, String>>>()

    override fun open() {
        tableData.clear()
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("File tidak ditemukan: $path")

        FileInputStream(file).use { fis ->
            val parser = Xml.newPullParser()
            // Mengaktifkan namespace processing secara eksplisit jika didukung
            runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) }
            parser.setInput(InputStreamReader(fis, Charsets.UTF_8))
            parseXml(parser)
        }
    }

    private fun parseXml(parser: XmlPullParser) {
        var eventType = parser.eventType
        var rootElementParsed = false
        var currentTable: String? = null
        var currentRow: LinkedHashMap<String, String>? = null
        val currentText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val rawName = parser.name
            val name = if (rawName != null && rawName.contains(':')) rawName.substringAfter(':') else rawName

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name != null) {
                        if (!rootElementParsed) {
                            rootElementParsed = true
                        } else if (currentRow == null) {
                            currentTable = name
                            currentRow = LinkedHashMap()
                            // Baca atribut
                            for (i in 0 until parser.attributeCount) {
                                val attrRawName = parser.getAttributeName(i)
                                val attrName = if (attrRawName.contains(':')) attrRawName.substringAfter(':') else attrRawName
                                currentRow[attrName] = parser.getAttributeValue(i)
                            }
                        } else {
                            currentText.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentRow != null) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name != null) {
                        if (name == currentTable && currentRow != null) {
                            val rows = tableData.getOrPut(name) { ArrayList() }
                            rows.add(currentRow)
                            currentRow = null
                            currentTable = null
                        } else if (currentRow != null && currentTable != null) {
                            val textValue = currentText.toString().trim()
                            if (textValue.isNotEmpty()) {
                                currentRow[name] = textValue
                            }
                            currentText.setLength(0)
                        }
                    }
                }
            }
            eventType = parser.next()
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
        throw UnsupportedOperationException("XML tidak mendukung query SQL")
    }

    private fun collectColumns(rows: List<Map<String, String>>): List<String> {
        val set = LinkedHashSet<String>()
        for (row in rows) set.addAll(row.keys)
        return set.toList()
    }
}
