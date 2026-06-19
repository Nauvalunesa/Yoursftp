package com.yoursftp.app.terminal

/**
 * Emulator terminal VT100/xterm yang cukup lengkap untuk pemakaian harian:
 * mendukung warna 16/256/truecolor, atribut (bold/dim/italic/underline/reverse/
 * hidden/strike), alternate screen (vim/htop/less), scroll region, scrollback,
 * dan parser state-machine yang tahan terhadap escape sequence terpotong antar-chunk.
 *
 * Semua mutasi terjadi di satu thread (loop pembaca). Snapshot dibuat untuk UI.
 */

const val DEFAULT_FG = -1   // warna teks default (mengikuti tema)
const val DEFAULT_BG = -2   // warna latar default (transparan)

const val ATTR_BOLD = 1
const val ATTR_DIM = 1 shl 1
const val ATTR_ITALIC = 1 shl 2
const val ATTR_UNDERLINE = 1 shl 3
const val ATTR_BLINK = 1 shl 4
const val ATTR_REVERSE = 1 shl 5
const val ATTR_HIDDEN = 1 shl 6
const val ATTR_STRIKE = 1 shl 7

private const val ESC = '\u001B'
private const val BEL = '\u0007'
private const val BS = '\u0008'
private const val LF = '\u000A'
private const val VT = '\u000B'
private const val FF = '\u000C'
private const val CR = '\u000D'
private const val TABCH = '\u0009'

/**
 * Satu sel grid. Warna disimpan sebagai Int:
 *  -1 = default fg, -2 = default bg, 0..255 = indeks palet, atau truecolor (bit 24 set).
 */
class Cell {
    @JvmField var char: Char = ' '
    @JvmField var fg: Int = DEFAULT_FG
    @JvmField var bg: Int = DEFAULT_BG
    @JvmField var attr: Int = 0

    fun set(c: Char, f: Int, b: Int, a: Int) { char = c; fg = f; bg = b; attr = a }
    fun reset() { char = ' '; fg = DEFAULT_FG; bg = DEFAULT_BG; attr = 0 }
    fun copyFrom(o: Cell) { char = o.char; fg = o.fg; bg = o.bg; attr = o.attr }
}

class TermLine(val cells: Array<Cell>)

class TermSnapshot(
    val lines: List<TermLine>,
    val cursorRow: Int,
    val cursorCol: Int,
    val showCursor: Boolean,
    val cols: Int,
    val scrollbackSize: Int,
    val revision: Long
)

class TerminalEmulator(cols: Int = 80, rows: Int = 24) {
    var cols = cols.coerceAtLeast(2); private set
    var rows = rows.coerceAtLeast(2); private set

    private var grid = newGrid(this.rows, this.cols)
    private var altGrid = newGrid(this.rows, this.cols)
    private val scrollback = ArrayDeque<Array<Cell>>()
    private var maxScrollback = 5000
    private var usingAlt = false

    private var curRow = 0
    private var curCol = 0
    private var curFg = DEFAULT_FG
    private var curBg = DEFAULT_BG
    private var curAttr = 0
    private var savedRow = 0
    private var savedCol = 0
    private var savedFg = DEFAULT_FG
    private var savedBg = DEFAULT_BG
    private var savedAttr = 0
    private var wrapPending = false

    private var scrollTop = 0
    private var scrollBottom = this.rows - 1

    var cursorVisible = true; private set
    private var autoWrap = true
    var applicationCursorKeys = false; private set
    var bracketedPaste = false; private set
    private var originMode = false

    @Volatile var revision = 0L; private set

    var onResponse: ((String) -> Unit)? = null

    private fun newGrid(r: Int, c: Int) = Array(r) { Array(c) { Cell() } }

    private enum class State { GROUND, ESC_S, CSI, OSC, DCS_IGNORE, CHARSET }
    private var state = State.GROUND
    private val params = StringBuilder()
    private val interm = StringBuilder()
    private var csiPrivate = false
    private val oscBuf = StringBuilder()
    private var oscEscPending = false
    private var dcsEscPending = false

    fun feed(text: String) {
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when (state) {
                State.GROUND -> groundChar(ch)
                State.ESC_S -> escChar(ch)
                State.CSI -> csiChar(ch)
                State.OSC -> oscChar(ch)
                State.DCS_IGNORE -> dcsChar(ch)
                State.CHARSET -> { state = State.GROUND } // telan 1 char penunjuk charset
            }
            i++
        }
        revision++
    }

    private fun groundChar(ch: Char) {
        when (ch) {
            ESC -> { state = State.ESC_S; interm.clear() }
            LF, VT, FF -> lineFeed()
            CR -> { curCol = 0; wrapPending = false }
            TABCH -> tab()
            BS -> { if (curCol > 0) curCol--; wrapPending = false }
            BEL -> {}
            else -> if (ch.code >= 32) putChar(ch)
        }
    }

    private fun escChar(ch: Char) {
        when (ch) {
            '[' -> { state = State.CSI; params.clear(); interm.clear(); csiPrivate = false }
            ']' -> { state = State.OSC; oscBuf.clear(); oscEscPending = false }
            'P', 'X', '^', '_' -> { state = State.DCS_IGNORE; dcsEscPending = false }
            '(', ')', '*', '+' -> { state = State.CHARSET }
            'M' -> { reverseIndex(); state = State.GROUND }
            'D' -> { lineFeed(); state = State.GROUND }
            'E' -> { curCol = 0; lineFeed(); state = State.GROUND }
            '7' -> { saveCursor(); state = State.GROUND }
            '8' -> { restoreCursor(); state = State.GROUND }
            '=' , '>' -> { state = State.GROUND }
            'c' -> { fullReset(); state = State.GROUND }
            else -> { state = State.GROUND }
        }
    }

    private fun csiChar(ch: Char) {
        when {
            ch == '?' || ch == '<' || ch == '=' || ch == '>' -> { if (ch == '?') csiPrivate = true }
            ch in '0'..'9' || ch == ';' || ch == ':' -> params.append(ch)
            ch in ' '..'/' -> interm.append(ch)
            ch in '@'..'~' -> { dispatchCsi(ch); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun oscChar(ch: Char) {
        when {
            ch == BEL -> state = State.GROUND
            oscEscPending && ch == '\\' -> state = State.GROUND
            ch == ESC -> oscEscPending = true
            else -> { oscEscPending = false }
        }
    }

    private fun dcsChar(ch: Char) {
        when {
            dcsEscPending && ch == '\\' -> state = State.GROUND
            ch == ESC -> dcsEscPending = true
            else -> dcsEscPending = false
        }
    }

    private fun intArgs(): List<Int> {
        if (params.isEmpty()) return emptyList()
        return params.toString().split(';').map { p ->
            p.substringBefore(':').toIntOrNull() ?: 0
        }
    }

    private fun dispatchCsi(cmd: Char) {
        val a = intArgs()
        fun p(i: Int, d: Int = 1) = a.getOrNull(i)?.let { if (it == 0) d else it } ?: d
        fun p0(i: Int, d: Int = 0) = a.getOrNull(i) ?: d
        when (cmd) {
            'A' -> { curRow = (curRow - p(0)).coerceAtLeast(topLimit()); wrapPending = false }
            'B', 'e' -> { curRow = (curRow + p(0)).coerceAtMost(botLimit()); wrapPending = false }
            'C', 'a' -> { curCol = (curCol + p(0)).coerceAtMost(cols - 1); wrapPending = false }
            'D' -> { curCol = (curCol - p(0)).coerceAtLeast(0); wrapPending = false }
            'E' -> { curRow = (curRow + p(0)).coerceAtMost(botLimit()); curCol = 0 }
            'F' -> { curRow = (curRow - p(0)).coerceAtLeast(topLimit()); curCol = 0 }
            'G', '`' -> { curCol = (p(0) - 1).coerceIn(0, cols - 1); wrapPending = false }
            'd' -> { curRow = absRow(p(0) - 1); wrapPending = false }
            'H', 'f' -> {
                curRow = absRow(p(0) - 1)
                curCol = (p(1) - 1).coerceIn(0, cols - 1)
                wrapPending = false
            }
            'J' -> eraseDisplay(p0(0))
            'K' -> eraseLine(p0(0))
            'L' -> insertLines(p(0))
            'M' -> deleteLines(p(0))
            'P' -> deleteChars(p(0))
            '@' -> insertChars(p(0))
            'X' -> eraseChars(p(0))
            'S' -> scrollRegionUp(p(0))
            'T' -> scrollRegionDown(p(0))
            'r' -> setScrollRegion(p0(0), p0(1))
            'm' -> applySgr(a)
            'h' -> setMode(true)
            'l' -> setMode(false)
            's' -> saveCursor()
            'u' -> restoreCursor()
            'n' -> { if (p0(0) == 6) reportCursor() }
            'c' -> onResponse?.invoke("$ESC[?62;c")
            else -> {}
        }
    }

    private fun topLimit() = if (originMode) scrollTop else 0
    private fun botLimit() = if (originMode) scrollBottom else rows - 1
    private fun absRow(r: Int) = if (originMode) (scrollTop + r).coerceIn(scrollTop, scrollBottom)
        else r.coerceIn(0, rows - 1)

    private fun applySgr(aIn: List<Int>) {
        val a = if (aIn.isEmpty()) listOf(0) else aIn
        var i = 0
        while (i < a.size) {
            when (val code = a[i]) {
                0 -> { curFg = DEFAULT_FG; curBg = DEFAULT_BG; curAttr = 0 }
                1 -> curAttr = curAttr or ATTR_BOLD
                2 -> curAttr = curAttr or ATTR_DIM
                3 -> curAttr = curAttr or ATTR_ITALIC
                4 -> curAttr = curAttr or ATTR_UNDERLINE
                5 -> curAttr = curAttr or ATTR_BLINK
                7 -> curAttr = curAttr or ATTR_REVERSE
                8 -> curAttr = curAttr or ATTR_HIDDEN
                9 -> curAttr = curAttr or ATTR_STRIKE
                21, 22 -> curAttr = curAttr and (ATTR_BOLD or ATTR_DIM).inv()
                23 -> curAttr = curAttr and ATTR_ITALIC.inv()
                24 -> curAttr = curAttr and ATTR_UNDERLINE.inv()
                25 -> curAttr = curAttr and ATTR_BLINK.inv()
                27 -> curAttr = curAttr and ATTR_REVERSE.inv()
                28 -> curAttr = curAttr and ATTR_HIDDEN.inv()
                29 -> curAttr = curAttr and ATTR_STRIKE.inv()
                in 30..37 -> curFg = code - 30
                in 40..47 -> curBg = code - 40
                in 90..97 -> curFg = code - 90 + 8
                in 100..107 -> curBg = code - 100 + 8
                39 -> curFg = DEFAULT_FG
                49 -> curBg = DEFAULT_BG
                38 -> { i = parseExtColor(a, i) { c -> curFg = c }; continue }
                48 -> { i = parseExtColor(a, i) { c -> curBg = c }; continue }
                else -> {}
            }
            i++
        }
    }

    private inline fun parseExtColor(a: List<Int>, start: Int, set: (Int) -> Unit): Int {
        return when (a.getOrNull(start + 1)) {
            5 -> { set(a.getOrNull(start + 2) ?: 0); start + 3 }
            2 -> {
                val r = a.getOrNull(start + 2) ?: 0
                val g = a.getOrNull(start + 3) ?: 0
                val b = a.getOrNull(start + 4) ?: 0
                set(truecolor(r, g, b)); start + 5
            }
            else -> start + 1
        }
    }

    private fun setMode(enable: Boolean) {
        val a = intArgs()
        if (csiPrivate) {
            for (code in a) when (code) {
                1 -> applicationCursorKeys = enable
                6 -> { originMode = enable; curRow = topLimit(); curCol = 0 }
                7 -> autoWrap = enable
                25 -> cursorVisible = enable
                2004 -> bracketedPaste = enable
                1049, 1047, 47 -> switchAltScreen(enable)
                else -> {}
            }
        }
    }

    private fun switchAltScreen(toAlt: Boolean) {
        if (toAlt == usingAlt) return
        if (toAlt) {
            saveCursor()
            usingAlt = true
            for (r in 0 until rows) for (c in 0 until cols) altGrid[r][c].reset()
            curRow = 0; curCol = 0
        } else {
            usingAlt = false
            restoreCursor()
        }
        resetScrollRegion()
    }

    private fun current() = if (usingAlt) altGrid else grid

    private fun putChar(ch: Char) {
        if (autoWrap && wrapPending) {
            curCol = 0
            lineFeed()
            wrapPending = false
        }
        val g = current()
        if (curRow in 0 until rows && curCol in 0 until cols) {
            g[curRow][curCol].set(ch, curFg, curBg, curAttr)
        }
        if (curCol >= cols - 1) {
            if (autoWrap) wrapPending = true
        } else curCol++
    }

    private fun tab() { curCol = (((curCol / 8) + 1) * 8).coerceAtMost(cols - 1) }

    private fun lineFeed() {
        wrapPending = false
        if (curRow == scrollBottom) scrollRegionUp(1)
        else if (curRow < rows - 1) curRow++
    }

    private fun reverseIndex() {
        if (curRow == scrollTop) scrollRegionDown(1) else if (curRow > 0) curRow--
    }

    private fun scrollRegionUp(count: Int) {
        val g = current()
        repeat(count) {
            val top = g[scrollTop]
            if (!usingAlt && scrollTop == 0) pushScrollback(top)
            for (r in scrollTop until scrollBottom) g[r] = g[r + 1]
            g[scrollBottom] = top
            for (c in 0 until cols) top[c].reset()
        }
    }

    private fun scrollRegionDown(count: Int) {
        val g = current()
        repeat(count) {
            val bottom = g[scrollBottom]
            for (r in scrollBottom downTo scrollTop + 1) g[r] = g[r - 1]
            g[scrollTop] = bottom
            for (c in 0 until cols) bottom[c].reset()
        }
    }

    private fun pushScrollback(line: Array<Cell>) {
        scrollback.addLast(Array(cols) { Cell().apply { copyFrom(line[it]) } })
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
    }

    private fun eraseDisplay(mode: Int) {
        val g = current()
        when (mode) {
            0 -> {
                for (c in curCol until cols) g[curRow][c].reset()
                for (r in curRow + 1 until rows) for (c in 0 until cols) g[r][c].reset()
            }
            1 -> {
                for (r in 0 until curRow) for (c in 0 until cols) g[r][c].reset()
                for (c in 0..curCol.coerceAtMost(cols - 1)) g[curRow][c].reset()
            }
            2, 3 -> for (r in 0 until rows) for (c in 0 until cols) g[r][c].reset()
        }
    }

    private fun eraseLine(mode: Int) {
        val g = current()
        if (curRow !in 0 until rows) return
        when (mode) {
            0 -> for (c in curCol until cols) g[curRow][c].reset()
            1 -> for (c in 0..curCol.coerceAtMost(cols - 1)) g[curRow][c].reset()
            2 -> for (c in 0 until cols) g[curRow][c].reset()
        }
    }

    private fun eraseChars(n: Int) {
        val g = current()
        var c = curCol
        repeat(n) { if (c < cols) { g[curRow][c].reset(); c++ } }
    }

    private fun insertChars(n: Int) {
        val row = current()[curRow]
        for (c in cols - 1 downTo curCol + n) row[c].copyFrom(row[c - n])
        for (c in curCol until (curCol + n).coerceAtMost(cols)) row[c].reset()
    }

    private fun deleteChars(n: Int) {
        val row = current()[curRow]
        for (c in curCol until cols) {
            if (c + n < cols) row[c].copyFrom(row[c + n]) else row[c].reset()
        }
    }

    private fun insertLines(n: Int) {
        if (curRow < scrollTop || curRow > scrollBottom) return
        val g = current()
        repeat(n) {
            val bottom = g[scrollBottom]
            for (r in scrollBottom downTo curRow + 1) g[r] = g[r - 1]
            g[curRow] = bottom
            for (c in 0 until cols) bottom[c].reset()
        }
    }

    private fun deleteLines(n: Int) {
        if (curRow < scrollTop || curRow > scrollBottom) return
        val g = current()
        repeat(n) {
            val top = g[curRow]
            for (r in curRow until scrollBottom) g[r] = g[r + 1]
            g[scrollBottom] = top
            for (c in 0 until cols) top[c].reset()
        }
    }

    private fun setScrollRegion(top: Int, bottom: Int) {
        val t = (if (top == 0) 1 else top) - 1
        val b = (if (bottom == 0) rows else bottom) - 1
        if (t < b && b < rows) {
            scrollTop = t.coerceIn(0, rows - 1)
            scrollBottom = b.coerceIn(0, rows - 1)
        } else resetScrollRegion()
        curRow = topLimit(); curCol = 0
    }

    private fun resetScrollRegion() { scrollTop = 0; scrollBottom = rows - 1 }

    private fun saveCursor() {
        savedRow = curRow; savedCol = curCol
        savedFg = curFg; savedBg = curBg; savedAttr = curAttr
    }

    private fun restoreCursor() {
        curRow = savedRow.coerceIn(0, rows - 1)
        curCol = savedCol.coerceIn(0, cols - 1)
        curFg = savedFg; curBg = savedBg; curAttr = savedAttr
        wrapPending = false
    }

    private fun reportCursor() { onResponse?.invoke("$ESC[${curRow + 1};${curCol + 1}R") }

    private fun fullReset() {
        curFg = DEFAULT_FG; curBg = DEFAULT_BG; curAttr = 0
        curRow = 0; curCol = 0
        resetScrollRegion()
        cursorVisible = true
        for (r in 0 until rows) for (c in 0 until cols) grid[r][c].reset()
    }

    fun resize(newCols: Int, newRows: Int) {
        val nc = newCols.coerceAtLeast(2)
        val nr = newRows.coerceAtLeast(2)
        if (nc == cols && nr == rows) return
        grid = resizeGrid(grid, nr, nc)
        altGrid = resizeGrid(altGrid, nr, nc)
        cols = nc; rows = nr
        scrollTop = 0; scrollBottom = rows - 1
        curRow = curRow.coerceIn(0, rows - 1)
        curCol = curCol.coerceIn(0, cols - 1)
        revision++
    }

    private fun resizeGrid(old: Array<Array<Cell>>, nr: Int, nc: Int): Array<Array<Cell>> =
        Array(nr) { r ->
            Array(nc) { c ->
                Cell().apply { if (r < old.size && c < old[0].size) copyFrom(old[r][c]) }
            }
        }

    fun snapshot(includeScrollback: Boolean): TermSnapshot {
        val g = current()
        val out = ArrayList<TermLine>(rows + if (includeScrollback) scrollback.size else 0)
        var cursorRowOffset = 0
        if (includeScrollback && !usingAlt) {
            for (line in scrollback) {
                out.add(TermLine(Array(cols) { Cell().apply { if (it < line.size) copyFrom(line[it]) } }))
            }
            cursorRowOffset = scrollback.size
        }
        for (r in 0 until rows) {
            val src = g[r]
            out.add(TermLine(Array(cols) { Cell().apply { copyFrom(src[it]) } }))
        }
        return TermSnapshot(
            lines = out,
            cursorRow = cursorRowOffset + curRow,
            cursorCol = curCol,
            showCursor = cursorVisible,
            cols = cols,
            scrollbackSize = if (includeScrollback && !usingAlt) scrollback.size else 0,
            revision = revision
        )
    }

    fun clearScrollbackAndScreen() {
        scrollback.clear()
        fullReset()
        revision++
    }

    fun screenPlainText(): String {
        val g = current()
        val sb = StringBuilder()
        for (r in 0 until rows) {
            val line = StringBuilder()
            for (c in 0 until cols) line.append(g[r][c].char)
            sb.append(line.toString().trimEnd())
            if (r < rows - 1) sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        fun truecolor(r: Int, g: Int, b: Int): Int =
            (1 shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

        fun isTruecolor(v: Int) = v and (1 shl 24) != 0
    }
}
