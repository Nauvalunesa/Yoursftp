package com.yoursftp.app.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoursftp.app.transfer.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Di atas panjang ini, baris tidak boleh dijadikan baris aktif (akan membuat field choke). */
const val MAX_EDITABLE_LINE = 100_000

data class LargeEditorState(
    val path: String = "",
    val loading: Boolean = false,
    val loadProgress: Float = 0f,      // 0..1 saat mengunduh (-1 = tak diketahui)
    val loadStatus: String = "",       // teks status ("Mengunduh 45%", "Memproses…")
    val saving: Boolean = false,
    val lineCount: Int = 0,
    val revision: Long = 0L,
    val activeLine: Int = -1,
    val pendingCaret: Int = 0,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val savedMessage: String? = null,
    val error: String? = null,
    val warning: String? = null,
    // Pewarnaan sintaks (tokenizer satu-lewat, ringan — selalu aktif).
    val highlight: Boolean = true,
    // Find / replace
    val searchOpen: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val ignoreCase: Boolean = true,
    // Prediksi kode: kandidat untuk prefix yang sedang diketik.
    val suggestions: List<String> = emptyList(),
    // Permintaan agar daftar scroll ke baris tertentu (dikonsumsi UI lalu di-clear).
    val scrollTo: Int? = null
)

/**
 * Editor untuk file besar (10MB+) dengan model dokumen berbasis baris ter-virtualisasi.
 * Hanya satu baris yang dapat diedit pada satu waktu; baris lain dirender statis.
 */
class LargeEditorViewModel(application: Application) : AndroidViewModel(application) {

    private var doc: TextDocument? = null

    private val _state = MutableStateFlow(LargeEditorState())
    val state = _state.asStateFlow()

    val ext: String get() = extOf(_state.value.path)

    // Indeks kata untuk prediksi kode (kata dari dokumen + keyword bahasa).
    private val wordIndex = LinkedHashSet<String>()

    private fun syncFlags(s: LargeEditorState): LargeEditorState {
        val d = doc ?: return s
        return s.copy(dirty = d.dirty, canUndo = d.canUndo, canRedo = d.canRedo, lineCount = d.lineCount)
    }

    private fun bump(
        activeLine: Int = _state.value.activeLine,
        caret: Int = _state.value.pendingCaret,
        scrollTo: Int? = _state.value.scrollTo
    ) {
        val lineChanged = activeLine != _state.value.activeLine
        _state.value = syncFlags(
            _state.value.copy(
                revision = _state.value.revision + 1,
                activeLine = activeLine, pendingCaret = caret, scrollTo = scrollTo,
                suggestions = if (lineChanged) emptyList() else _state.value.suggestions
            )
        )
    }

    fun load(path: String) {
        if (doc != null && _state.value.path == path) return
        _state.value = LargeEditorState(
            path = path, loading = true, loadProgress = -1f, loadStatus = "Menghubungkan…"
        )
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    var lastPct = -1
                    val bytes = SessionManager.require().download(path) { transferred, total ->
                        if (total > 0) {
                            val pct = ((transferred * 100) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                _state.value = _state.value.copy(
                                    loadProgress = pct / 100f,
                                    loadStatus = "Mengunduh $pct%"
                                )
                            }
                        } else {
                            _state.value = _state.value.copy(
                                loadProgress = -1f,
                                loadStatus = "Mengunduh ${transferred / 1024} KB"
                            )
                        }
                    }
                    _state.value = _state.value.copy(loadProgress = -1f, loadStatus = "Memproses…")
                    if (looksBinary(bytes)) error("File tampak biner — tidak bisa diedit sebagai teks")
                    withContext(Dispatchers.Default) { TextDocument.fromBytes(bytes) }
                }
                doc = parsed
                withContext(Dispatchers.Default) { buildWordIndex(parsed) }
                val warn = if (parsed.maxLineLength > MAX_EDITABLE_LINE)
                    "Sebagian baris terlalu panjang untuk diedit (hanya bisa dilihat)" else null
                _state.value = syncFlags(
                    _state.value.copy(loading = false, loadStatus = "", revision = 1L, warning = warn)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal memuat file")
            }
        }
    }

    fun toggleHighlight() {
        _state.value = _state.value.copy(highlight = !_state.value.highlight)
    }

    fun lineText(index: Int): String = doc?.getLine(index) ?: ""
    fun lineKey(index: Int): Long = doc?.lineId(index) ?: index.toLong()
    fun isEditable(index: Int): Boolean = (doc?.getLine(index)?.length ?: 0) <= MAX_EDITABLE_LINE
    // O(1): tanpa lookback antar-baris (penyebab freeze). Komentar blok lintas-baris
    // tidak diwarnai mid-block, tapi //, #, dan /* */ satu-baris tetap berwarna.
    fun startsInComment(index: Int): Boolean = false

    fun setActiveLine(index: Int, caret: Int = 0) {
        if (index >= 0 && !isEditable(index)) {
            _state.value = _state.value.copy(warning = "Baris terlalu panjang untuk diedit")
            return
        }
        _state.value = _state.value.copy(activeLine = index, pendingCaret = caret, suggestions = emptyList())
    }

    // ---- Prediksi kode ----

    private fun buildWordIndex(d: TextDocument) {
        wordIndex.clear()
        wordIndex.addAll(keywordsForExt(extOf(_state.value.path)))
        // Pindai sampai 20k baris pertama agar pembentukan indeks tetap cepat.
        val limit = d.lineCount.coerceAtMost(20_000)
        val sb = StringBuilder()
        for (i in 0 until limit) {
            val line = d.getLine(i)
            var j = 0
            val n = line.length
            while (j < n) {
                val c = line[j]
                if (isIdentifierStart(c)) {
                    sb.setLength(0)
                    sb.append(c); j++
                    while (j < n && isIdentifierPart(line[j])) { sb.append(line[j]); j++ }
                    if (sb.length >= 3) wordIndex.add(sb.toString())
                } else j++
            }
            if (wordIndex.size > 8000) break
        }
    }

    /** Hitung prefix identifier sebelum caret pada teks baris aktif. */
    fun prefixBefore(text: String, caret: Int): String {
        var s = caret.coerceIn(0, text.length)
        while (s > 0 && isIdentifierPart(text[s - 1])) s--
        return text.substring(s, caret.coerceIn(0, text.length))
    }

    /** Perbarui daftar saran berdasarkan teks baris aktif & posisi caret. */
    fun updateSuggestions(text: String, caret: Int) {
        val prefix = prefixBefore(text, caret)
        if (prefix.length < 2) {
            if (_state.value.suggestions.isNotEmpty())
                _state.value = _state.value.copy(suggestions = emptyList())
            return
        }
        val lower = prefix.lowercase()
        val out = ArrayList<String>(8)
        for (w in wordIndex) {
            if (w.length > prefix.length && w.startsWith(prefix)) { out.add(w); if (out.size >= 6) break }
        }
        if (out.size < 6) {
            for (w in wordIndex) {
                if (w.length > prefix.length && !w.startsWith(prefix) &&
                    w.lowercase().startsWith(lower) && w !in out
                ) { out.add(w); if (out.size >= 6) break }
            }
        }
        _state.value = _state.value.copy(suggestions = out)
    }

    fun clearSuggestions() {
        if (_state.value.suggestions.isNotEmpty())
            _state.value = _state.value.copy(suggestions = emptyList())
    }

    /**
     * Terapkan saran [word]: ganti prefix sebelum caret dengan kata penuh.
     * Mengembalikan Pair(teks baru, caret baru) untuk dipasang field aktif.
     */
    fun applySuggestion(text: String, caret: Int, word: String): Pair<String, Int> {
        val start = caret - prefixBefore(text, caret).length
        val newText = text.substring(0, start) + word + text.substring(caret)
        val newCaret = start + word.length
        val idx = _state.value.activeLine
        if (idx >= 0) commitLine(idx, newText)
        _state.value = _state.value.copy(suggestions = emptyList())
        return newText to newCaret
    }

    fun commitLine(index: Int, newText: String) {
        val d = doc ?: return
        if (d.replaceLine(index, newText)) bump()
    }

    fun splitLine(index: Int, col: Int) {
        val d = doc ?: return
        val newIndex = d.splitLine(index, col)
        bump(activeLine = newIndex, caret = 0)
    }

    fun mergeWithPrevious(index: Int) {
        val d = doc ?: return
        if (index <= 0) return
        val r = d.mergeWithPrevious(index)
        bump(activeLine = r.newIndex, caret = r.caretCol)
    }

    /** Enter atau paste multi-baris pada baris aktif. */
    fun applyMultiline(index: Int, fullText: String, caret: Int) {
        val d = doc ?: return
        val r = d.applyMultiline(index, fullText, caret)
        bump(activeLine = r.newIndex, caret = r.caretCol)
    }

    fun undo() {
        val d = doc ?: return
        val c = d.undo() ?: return
        bump(activeLine = c.index, caret = c.col, scrollTo = c.index)
    }

    fun redo() {
        val d = doc ?: return
        val c = d.redo() ?: return
        bump(activeLine = c.index, caret = c.col, scrollTo = c.index)
    }

    // ---- Find / replace ----

    fun toggleSearch() {
        _state.value = _state.value.copy(searchOpen = !_state.value.searchOpen)
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
    fun setReplacement(r: String) { _state.value = _state.value.copy(replacement = r) }
    fun toggleIgnoreCase() { _state.value = _state.value.copy(ignoreCase = !_state.value.ignoreCase) }

    fun findNext() = find(forward = true)
    fun findPrev() = find(forward = false)

    private fun find(forward: Boolean) {
        val d = doc ?: return
        val s = _state.value
        if (s.query.isEmpty()) return
        val curIndex = if (s.activeLine >= 0) s.activeLine else 0
        val curCol = if (forward) s.pendingCaret + 1 else s.pendingCaret
        val hit = d.find(s.query, curIndex, curCol, forward, s.ignoreCase)
        if (hit == null) {
            _state.value = _state.value.copy(warning = "Tidak ditemukan")
        } else {
            _state.value = _state.value.copy(
                activeLine = hit.index, pendingCaret = hit.col, scrollTo = hit.index
            )
        }
    }

    fun replaceCurrent() {
        val d = doc ?: return
        val s = _state.value
        if (s.query.isEmpty() || s.activeLine < 0) return
        val line = d.getLine(s.activeLine)
        val pos = line.indexOf(s.query, s.pendingCaret, s.ignoreCase)
        if (pos < 0) { findNext(); return }
        val newLine = line.substring(0, pos) + s.replacement + line.substring(pos + s.query.length)
        d.replaceLine(s.activeLine, newLine)
        _state.value = syncFlags(_state.value.copy(
            revision = _state.value.revision + 1, pendingCaret = pos + s.replacement.length
        ))
        findNext()
    }

    fun replaceAll() {
        val d = doc ?: return
        val s = _state.value
        if (s.query.isEmpty()) return
        viewModelScope.launch {
            val count = withContext(Dispatchers.Default) {
                d.replaceAll(s.query, s.replacement, s.ignoreCase)
            }
            _state.value = syncFlags(_state.value.copy(
                revision = _state.value.revision + 1,
                savedMessage = if (count > 0) "$count diganti" else "Tidak ditemukan"
            ))
        }
    }

    fun consumeScroll() {
        if (_state.value.scrollTo != null) _state.value = _state.value.copy(scrollTo = null)
    }

    fun save() {
        val d = doc ?: return
        val path = _state.value.path
        _state.value = _state.value.copy(saving = true, error = null, savedMessage = null)
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) { d.serialize() }
                withContext(Dispatchers.IO) { SessionManager.require().upload(path, bytes) }
                d.markClean()
                _state.value = syncFlags(_state.value.copy(saving = false, savedMessage = "Tersimpan"))
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "Gagal menyimpan")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(savedMessage = null, error = null, warning = null)
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 8192)
        for (i in 0 until n) if (bytes[i].toInt() == 0) return true
        return false
    }
}
