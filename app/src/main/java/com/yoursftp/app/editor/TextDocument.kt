package com.yoursftp.app.editor

import java.nio.charset.Charset

/** Satu baris teks dengan id stabil (dipakai sebagai key LazyColumn). */
class LineEntry(
    @JvmField var text: String,
    @JvmField val id: Long
)

/** Posisi caret: indeks baris + kolom. */
data class Caret(val index: Int, val col: Int)

/** Hasil operasi merge: posisi baris aktif baru + kolom caret. */
data class MergeResult(val newIndex: Int, val caretCol: Int)

/**
 * Satu langkah edit yang bisa dibalik. Hanya menyimpan baris yang berubah (bukan
 * snapshot seluruh file), jadi memori tetap kecil walau filenya 10MB.
 * Membalik = mengganti rentang [inserted] kembali jadi [removed], dan sebaliknya.
 */
private class Edit(
    val start: Int,
    val removed: List<String>,
    var inserted: List<String>,
    val caretUndo: Caret,
    var caretRedo: Caret,
    val coalescable: Boolean
)

/**
 * Model dokumen berbasis baris untuk editor file besar.
 *
 * Menyimpan `ArrayList<LineEntry>` biasa (bukan SnapshotStateList) karena UI sudah
 * ter-virtualisasi (LazyColumn hanya merender baris yang terlihat). Perubahan diberi
 * tahu ke UI lewat penghitung `revision`/`lineCount` di ViewModel.
 *
 * Separator baris dan ada/tidaknya newline penutup dipertahankan agar simpan-ulang
 * menghasilkan byte yang identik bila tidak ada perubahan.
 */
class TextDocument private constructor(
    private val lines: ArrayList<LineEntry>,
    val lineSeparator: String,
    val hadTrailingNewline: Boolean,
    val charset: Charset
) {
    private var nextId: Long = lines.size.toLong()

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var savedDepth = 0

    /** Dirty bila kedalaman undo berbeda dari saat terakhir disimpan. */
    val dirty: Boolean get() = undoStack.size != savedDepth
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Panjang baris terpanjang saat dimuat — dipakai untuk warning baris tak-bisa-diedit. */
    val maxLineLength: Int = lines.maxOfOrNull { it.text.length } ?: 0

    val lineCount: Int get() = lines.size
    fun getLine(index: Int): String = lines[index].text
    fun lineId(index: Int): Long = lines[index].id

    /** Ganti isi satu baris (in-place agar id & fokus stabil saat mengetik). */
    fun replaceLine(index: Int, newText: String): Boolean {
        val entry = lines[index]
        val old = entry.text
        if (old == newText) return false
        entry.text = newText
        // Gabungkan dengan langkah sebelumnya bila masih mengetik di baris yang sama.
        val top = undoStack.lastOrNull()
        if (top != null && top.coalescable && top.start == index &&
            top.inserted.size == 1 && top.removed.size == 1
        ) {
            top.inserted = listOf(newText)
            top.caretRedo = Caret(index, newText.length)
        } else {
            pushEdit(
                Edit(index, listOf(old), listOf(newText),
                    Caret(index, old.length), Caret(index, newText.length), coalescable = true)
            )
        }
        return true
    }

    /** Pecah baris [index] pada kolom [col]. Mengembalikan indeks baris baru. */
    fun splitLine(index: Int, col: Int): Int {
        val entry = lines[index]
        val original = entry.text
        val safeCol = col.coerceIn(0, original.length)
        val left = original.substring(0, safeCol)
        val right = original.substring(safeCol)
        entry.text = left
        lines.add(index + 1, LineEntry(right, nextId++))
        pushEdit(
            Edit(index, listOf(original), listOf(left, right),
                Caret(index, safeCol), Caret(index + 1, 0), coalescable = false)
        )
        return index + 1
    }

    /** Gabungkan baris [index] ke baris sebelumnya. */
    fun mergeWithPrevious(index: Int): MergeResult {
        if (index <= 0 || index >= lines.size) return MergeResult(index, 0)
        val prevOld = lines[index - 1].text
        val curOld = lines[index].text
        val caret = prevOld.length
        lines[index - 1].text = prevOld + curOld
        lines.removeAt(index)
        pushEdit(
            Edit(index - 1, listOf(prevOld, curOld), listOf(prevOld + curOld),
                Caret(index, 0), Caret(index - 1, caret), coalescable = false)
        )
        return MergeResult(index - 1, caret)
    }

    /**
     * Terapkan teks baris aktif yang mengandung newline (Enter/paste multi-baris):
     * baris [index] dipecah jadi beberapa baris. Mengembalikan caret akhir.
     */
    fun applyMultiline(index: Int, fullText: String, caretAbs: Int): MergeResult {
        val original = lines[index].text
        val parts = fullText.split("\n")
        lines[index].text = parts[0]
        var at = index + 1
        for (i in 1 until parts.size) {
            lines.add(at, LineEntry(parts[i], nextId++))
            at++
        }
        // Petakan caret absolut ke (baris, kolom).
        var rem = caretAbs.coerceIn(0, fullText.length)
        var li = 0
        while (li < parts.size && rem > parts[li].length) { rem -= parts[li].length + 1; li++ }
        if (li >= parts.size) { li = parts.size - 1; rem = parts[li].length }
        val caretRedo = Caret(index + li, rem)
        pushEdit(
            Edit(index, listOf(original), parts, Caret(index, original.length), caretRedo, coalescable = false)
        )
        return MergeResult(index + li, rem)
    }

    /**
     * Ganti satu rentang baris sekaligus (dipakai replace-all). Mengembalikan caret akhir.
     * removeCount baris mulai [start] diganti dengan [newLines].
     */
    fun replaceRangeLines(start: Int, removeCount: Int, newLines: List<String>): MergeResult {
        val removed = ArrayList<String>(removeCount)
        for (i in 0 until removeCount) removed.add(lines[start + i].text)
        applyRange(start, removeCount, newLines)
        val lastIdx = start + newLines.size - 1
        val caretRedo = Caret(lastIdx.coerceAtLeast(start), newLines.lastOrNull()?.length ?: 0)
        pushEdit(
            Edit(start, removed, newLines, Caret(start, 0), caretRedo, coalescable = false)
        )
        return MergeResult(caretRedo.index, caretRedo.col)
    }

    fun undo(): Caret? {
        val edit = undoStack.removeLastOrNull() ?: return null
        applyRange(edit.start, edit.inserted.size, edit.removed)
        redoStack.addLast(edit)
        return edit.caretUndo
    }

    fun redo(): Caret? {
        val edit = redoStack.removeLastOrNull() ?: return null
        applyRange(edit.start, edit.removed.size, edit.inserted)
        undoStack.addLast(edit)
        return edit.caretRedo
    }

    private fun pushEdit(edit: Edit) {
        undoStack.addLast(edit)
        redoStack.clear()
        // Batasi tinggi stack agar tak tumbuh tanpa batas pada sesi edit sangat panjang.
        if (undoStack.size > MAX_UNDO) {
            undoStack.removeFirst()
            if (savedDepth > 0) savedDepth--
        }
    }

    /** Ganti [removeCount] baris mulai [start] dengan [insert] (memberi id baru). */
    private fun applyRange(start: Int, removeCount: Int, insert: List<String>) {
        repeat(removeCount) { if (start < lines.size) lines.removeAt(start) }
        var at = start
        for (t in insert) { lines.add(at, LineEntry(t, nextId++)); at++ }
        if (lines.isEmpty()) lines.add(LineEntry("", nextId++))
    }

    fun serialize(): ByteArray {
        val sb = StringBuilder()
        for (i in lines.indices) {
            sb.append(lines[i].text)
            if (i < lines.size - 1) sb.append(lineSeparator)
        }
        if (hadTrailingNewline) sb.append(lineSeparator)
        return sb.toString().toByteArray(charset)
    }

    fun markClean() { savedDepth = undoStack.size }

    /**
     * Apakah baris [index] dimulai di dalam komentar blok. Dihitung dengan lookback
     * terbatas ([COMMENT_LOOKBACK] baris) agar biayanya terbatas pada jendela terlihat,
     * bukan seluruh file. Bila lookback habis, diasumsikan tidak di dalam komentar.
     */
    fun startsInBlockComment(index: Int, ext: String): Boolean {
        if (index <= 0) return false
        val from = (index - COMMENT_LOOKBACK).coerceAtLeast(0)
        var inComment = false
        for (i in from until index) {
            inComment = endsInBlockComment(lines[i].text, ext, inComment)
        }
        return inComment
    }

    /** Cari kemunculan [query] mulai dari (fromIndex, fromCol). Mengembalikan posisi atau null. */
    fun find(query: String, fromIndex: Int, fromCol: Int, forward: Boolean, ignoreCase: Boolean): Caret? {
        if (query.isEmpty() || lines.isEmpty()) return null
        val n = lines.size
        if (forward) {
            var i = fromIndex.coerceIn(0, n - 1)
            var startCol = fromCol
            for (step in 0..n) {
                val idx = ((i % n) + n) % n
                val hay = lines[idx].text
                val from = if (step == 0) startCol.coerceAtMost(hay.length) else 0
                val pos = hay.indexOf(query, from, ignoreCase)
                if (pos >= 0) return Caret(idx, pos)
                i++
            }
        } else {
            var i = fromIndex.coerceIn(0, n - 1)
            for (step in 0..n) {
                val idx = ((i % n) + n) % n
                val hay = lines[idx].text
                val before = if (step == 0) (fromCol - 1).coerceAtMost(hay.length - 1) else hay.length
                if (before >= 0) {
                    val pos = hay.lastIndexOf(query, before, ignoreCase)
                    if (pos >= 0) return Caret(idx, pos)
                }
                i--
            }
        }
        return null
    }

    /** Ganti semua kemunculan [query] dengan [replacement]. Mengembalikan jumlah penggantian. */
    fun replaceAll(query: String, replacement: String, ignoreCase: Boolean): Int {
        if (query.isEmpty()) return 0
        var first = -1
        var last = -1
        var count = 0
        for (i in lines.indices) {
            if (lines[i].text.contains(query, ignoreCase)) {
                if (first < 0) first = i
                last = i
            }
        }
        if (first < 0) return 0
        val newLines = ArrayList<String>(last - first + 1)
        for (i in first..last) {
            val old = lines[i].text
            val replaced = replaceAllInLine(old, query, replacement, ignoreCase)
            count += countOccurrences(old, query, ignoreCase)
            newLines.add(replaced)
        }
        replaceRangeLines(first, last - first + 1, newLines)
        return count
    }

    private fun replaceAllInLine(s: String, q: String, r: String, ic: Boolean): String {
        if (!ic) return s.replace(q, r)
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val pos = s.indexOf(q, i, ignoreCase = true)
            if (pos < 0) { sb.append(s, i, s.length); break }
            sb.append(s, i, pos).append(r)
            i = pos + q.length
        }
        return sb.toString()
    }

    private fun countOccurrences(s: String, q: String, ic: Boolean): Int {
        var c = 0; var i = 0
        while (true) {
            val pos = s.indexOf(q, i, ic); if (pos < 0) break; c++; i = pos + q.length
        }
        return c
    }

    companion object {
        private const val MAX_UNDO = 500
        private const val COMMENT_LOOKBACK = 400

        fun fromBytes(bytes: ByteArray, charset: Charset = Charsets.UTF_8): TextDocument {
            val text = String(bytes, charset)
            val separator = detectSeparator(text)
            val hadTrailing = text.endsWith("\n")
            val raw = text.split("\r\n", "\n")
            val list = ArrayList<LineEntry>(raw.size.coerceAtLeast(1))
            val end = if (hadTrailing && raw.isNotEmpty() && raw.last().isEmpty()) raw.size - 1 else raw.size
            var id = 0L
            for (i in 0 until end) list.add(LineEntry(raw[i], id++))
            if (list.isEmpty()) list.add(LineEntry("", id))
            return TextDocument(list, separator, hadTrailing, charset)
        }

        private fun detectSeparator(text: String): String {
            val crlf = text.indexOf("\r\n")
            val lf = text.indexOf('\n')
            return if (crlf >= 0 && (lf < 0 || crlf <= lf)) "\r\n" else "\n"
        }
    }
}
