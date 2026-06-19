package com.yoursftp.app.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.concurrent.ConcurrentHashMap

/**
 * Highlighter cepat berbasis tokenizer SATU-LEWAT (tanpa regex). Memindai tiap baris
 * sekali (O(n)) dan menempelkan warna per token. Ini menggantikan versi lama yang
 * menjalankan banyak regex findAll per baris (penyebab utama UI freeze pada file besar).
 */

/** Di atas panjang ini, baris dirender polos agar tetap ringan. */
const val MAX_HIGHLIGHT_LINE = 2_000

// Palette VSCode Dark+ (default)
private val commentColor = Color(0xFF6A9955)   // hijau komentar
private val stringColor = Color(0xFFCE9178)    // oranye string
private val keywordColor = Color(0xFF569CD6)   // biru keyword
private val numberColor = Color(0xFFB5CEA8)    // hijau muda angka
private val annotationColor = Color(0xFFDCDCAA) // kuning fungsi/anotasi

private val keywordStyle = SpanStyle(color = keywordColor)
private val numberStyle = SpanStyle(color = numberColor)
private val stringStyle = SpanStyle(color = stringColor)
private val commentStyle = SpanStyle(color = commentColor)
private val annotationStyle = SpanStyle(color = annotationColor)

/** Karakter pembuka komentar satu-baris per kelompok bahasa. */
private class Lang(
    val keywords: Set<String>,
    val hashComment: Boolean,   // '#'
    val dashComment: Boolean,   // '--' (sql)
    val slashComment: Boolean   // '//' dan '/* */'
)

private val langCache = ConcurrentHashMap<String, Lang>()

private fun langFor(ext: String): Lang = langCache.getOrPut(ext) {
    when (ext) {
        "py" -> Lang(PY, hashComment = true, dashComment = false, slashComment = false)
        "sh", "bash" -> Lang(SH, hashComment = true, dashComment = false, slashComment = false)
        "yml", "yaml", "toml", "gitignore", "properties", "conf", "ini", "env" ->
            Lang(emptySet(), hashComment = true, dashComment = false, slashComment = false)
        "sql" -> Lang(SQL, hashComment = false, dashComment = true, slashComment = false)
        else -> Lang(GENERIC, hashComment = false, dashComment = false, slashComment = true)
    }
}

fun extOf(filePath: String): String = filePath.substringAfterLast('.', "").lowercase()

/** Daftar keyword bahasa untuk ekstensi tertentu (dipakai prediksi kode). */
fun keywordsForExt(ext: String): Set<String> = langFor(ext).keywords

fun isIdentifierStart(c: Char) = isIdentStart(c)
fun isIdentifierPart(c: Char) = isIdentPart(c)

private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

/**
 * Tokenizer satu-lewat. [startInComment] = baris ini dimulai di tengah komentar blok.
 * Mengembalikan AnnotatedString berwarna.
 */
fun highlightLine(text: String, ext: String, startInComment: Boolean = false): AnnotatedString {
    val n = text.length
    if (n == 0) return AnnotatedString(text)
    if (n > MAX_HIGHLIGHT_LINE) return AnnotatedString(text)

    val lang = langFor(ext)
    val b = AnnotatedString.Builder(text)
    var i = 0
    var inBlock = startInComment

    if (inBlock) {
        val close = text.indexOf("*/")
        if (close < 0) { b.addStyle(commentStyle, 0, n); return b.toAnnotatedString() }
        b.addStyle(commentStyle, 0, close + 2)
        i = close + 2
        inBlock = false
    }

    while (i < n) {
        val c = text[i]
        when {
            // komentar blok /* */
            lang.slashComment && c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                val close = text.indexOf("*/", i + 2)
                val end = if (close < 0) n else close + 2
                b.addStyle(commentStyle, i, end)
                i = end
            }
            // komentar // sampai akhir
            lang.slashComment && c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                b.addStyle(commentStyle, i, n); i = n
            }
            // komentar # sampai akhir
            lang.hashComment && c == '#' -> { b.addStyle(commentStyle, i, n); i = n }
            // komentar -- sampai akhir (sql)
            lang.dashComment && c == '-' && i + 1 < n && text[i + 1] == '-' -> {
                b.addStyle(commentStyle, i, n); i = n
            }
            // string "..." atau '...'
            c == '"' || c == '\'' -> {
                val end = scanString(text, i, c)
                b.addStyle(stringStyle, i, end)
                i = end
            }
            // anotasi @nama
            c == '@' && i + 1 < n && isIdentStart(text[i + 1]) -> {
                var j = i + 1
                while (j < n && isIdentPart(text[j])) j++
                b.addStyle(annotationStyle, i, j)
                i = j
            }
            // angka
            c.isDigit() -> {
                var j = i + 1
                while (j < n && (text[j].isDigit() || text[j] == '.')) j++
                b.addStyle(numberStyle, i, j)
                i = j
            }
            // identifier / keyword
            isIdentStart(c) -> {
                var j = i + 1
                while (j < n && isIdentPart(text[j])) j++
                if (lang.keywords.isNotEmpty() && text.substring(i, j) in lang.keywords) {
                    b.addStyle(keywordStyle, i, j)
                }
                i = j
            }
            else -> i++
        }
    }
    return b.toAnnotatedString()
}

/** Pindai literal string mulai dari kutip di [start]; kembalikan indeks setelah penutup. */
private fun scanString(text: String, start: Int, quote: Char): Int {
    var j = start + 1
    val n = text.length
    while (j < n) {
        val ch = text[j]
        if (ch == '\\') { j += 2; continue }
        if (ch == quote) return j + 1
        j++
    }
    return n
}

/** Apakah akhir baris berada di dalam komentar blok (untuk carry antar-baris). */
fun endsInBlockComment(text: String, ext: String, startInComment: Boolean): Boolean {
    if (!langFor(ext).slashComment) return false
    var inBlock = startInComment
    var i = 0
    val n = text.length
    while (i < n) {
        if (inBlock) {
            val c = text.indexOf("*/", i)
            if (c < 0) return true
            i = c + 2; inBlock = false
        } else {
            val o = text.indexOf("/*", i)
            if (o < 0) {
                // baris // mematikan sisanya, bukan komentar blok
                return false
            }
            // jika ada // sebelum /*, sisanya komentar baris (bukan blok)
            val line = text.indexOf("//", i)
            if (line in 0 until o) return false
            i = o + 2; inBlock = true
        }
    }
    return inBlock
}

/** Untuk editor file kecil: highlight seluruh teks (baris demi baris). */
fun buildHighlightedText(text: String, filePath: String): AnnotatedString {
    val ext = extOf(filePath)
    val b = AnnotatedString.Builder(text)
    var lineStart = 0
    var inBlock = false
    val n = text.length
    var i = 0
    while (i <= n) {
        if (i == n || text[i] == '\n') {
            val line = text.substring(lineStart, i)
            val styled = highlightLine(line, ext, inBlock)
            styled.spanStyles.forEach { s ->
                b.addStyle(s.item, lineStart + s.start, lineStart + s.end)
            }
            inBlock = endsInBlockComment(line, ext, inBlock)
            lineStart = i + 1
        }
        i++
    }
    return b.toAnnotatedString()
}

/** Highlight satu baris aktif on-the-fly. */
class LineHighlightTransformation(
    private val ext: String,
    private val startInComment: Boolean = false
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = highlightLine(text.text, ext, startInComment)
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Menerapkan hasil highlight pra-komputasi tanpa kerja saat render. */
class PrecomputedHighlightTransformation(
    private val highlighted: AnnotatedString
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = if (highlighted.text == text.text) highlighted else text
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private val GENERIC = setOf(
    "package","import","class","interface","object","enum","fun","function","var","val",
    "let","const","def","public","private","protected","internal","static","final","abstract",
    "override","void","int","long","short","byte","float","double","char","boolean","string",
    "if","else","for","while","do","return","break","continue","when","switch","case","default",
    "try","catch","finally","throw","throws","new","this","super","null","true","false","as","is","in"
)
private val PY = setOf(
    "def","class","import","from","as","if","elif","else","for","while","return","in","is","not",
    "and","or","try","except","finally","raise","pass","lambda","with","global","nonlocal",
    "None","True","False"
)
private val SH = setOf(
    "if","then","elif","else","fi","for","while","in","do","done","case","esac","function",
    "exit","return","local","export","alias","echo","read"
)
private val SQL = setOf(
    "select","insert","update","delete","from","where","join","left","right","inner","outer","on",
    "group","by","order","having","limit","create","table","alter","drop","index","primary","key",
    "foreign","references","into","values","set","and","or","not","in","is","null","like","between",
    "SELECT","INSERT","UPDATE","DELETE","FROM","WHERE","JOIN","LEFT","RIGHT","INNER","OUTER","ON",
    "GROUP","BY","ORDER","HAVING","LIMIT","CREATE","TABLE","ALTER","DROP","INDEX","PRIMARY","KEY",
    "FOREIGN","REFERENCES","INTO","VALUES","SET","AND","OR","NOT","IN","IS","NULL","LIKE","BETWEEN"
)
