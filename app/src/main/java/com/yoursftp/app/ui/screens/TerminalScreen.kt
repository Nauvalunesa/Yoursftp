package com.yoursftp.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoursftp.app.terminal.*
import com.yoursftp.app.ui.TermKey
import com.yoursftp.app.ui.TerminalViewModel

private data class AccessoryKey(val label: String, val send: (TerminalViewModel) -> Unit)

private val accessoryKeys = listOf(
    AccessoryKey("ESC") { it.sendText("\u001B") },
    AccessoryKey("TAB") { it.sendText("\t") },
    AccessoryKey("CTRL+C") { it.sendText("\u0003") },
    AccessoryKey("CTRL+D") { it.sendText("\u0004") },
    AccessoryKey("CTRL+Z") { it.sendText("\u001A") },
    AccessoryKey("CTRL+L") { it.sendText("\u000C") },
    AccessoryKey("CTRL+R") { it.sendText("\u0012") },
    AccessoryKey("|") { it.sendText("|") },
    AccessoryKey("~") { it.sendText("~") },
    AccessoryKey("/") { it.sendText("/") },
    AccessoryKey("-") { it.sendText("-") },
    AccessoryKey("HOME") { it.sendKey(TermKey.HOME) },
    AccessoryKey("END") { it.sendKey(TermKey.END) }
)

private val defaultFg = Color(0xFFE2E8F0)
private val termBg = Color(0xFF0F172A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    vm: TerminalViewModel,
    connectionId: Long,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()

    var fontSizeSp by remember { mutableStateOf(13f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        fontSizeSp = (fontSizeSp * zoomChange).coerceIn(3f, 32f)
    }

    // Baseline untuk diff input (real-time typing).
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    LaunchedEffect(connectionId) { vm.connect(connectionId) }

    val snapshot = state.snapshot
    val lineCount = snapshot?.lines?.size ?: 0

    // Apakah daftar sedang berada di paling bawah (untuk auto-scroll cerdas).
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 1
        }
    }

    // Auto-scroll ke bawah HANYA jika user memang sedang di bawah; kalau menggulung
    // ke atas (lihat riwayat), jangan ditarik turun.
    LaunchedEffect(snapshot?.revision) {
        if (lineCount > 0 && atBottom) listState.scrollToItem(lineCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(
                                if (state.connected) Color(0xFF22C55E).copy(alpha = dotAlpha)
                                else Color(0xFFEF4444)
                            )
                        )
                        Text(
                            "SSH: ${state.connectionName}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setText(vm.screenText())
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Salin layar") }
                    IconButton(onClick = { vm.clearConsole() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Bersihkan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(termBg)
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Cubit 2 jari untuk zoom in/out (jalan bareng scroll & geser).
                    .transformable(transformableState)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
            ) {
                val charWidthDp = fontSizeSp * 0.6f
                val lineHeightDp = fontSizeSp * 1.3f
                val cols = (maxWidth.value / charWidthDp).toInt().coerceIn(20, 500)
                val rows = (maxHeight.value / lineHeightDp).toInt().coerceIn(6, 200)
                LaunchedEffect(cols, rows) { vm.resizeTerminal(cols, rows) }

                val textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.3f).sp,
                    color = defaultFg
                )

                if (snapshot == null) {
                    Text(
                        state.statusLine,
                        color = defaultFg,
                        style = textStyle,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    val cursorRow = snapshot.cursorRow
                    val cursorCol = snapshot.cursorCol
                    val showCursor = snapshot.showCursor
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                    ) {
                        items(count = snapshot.lines.size, key = { it }) { rowIdx ->
                            val line = snapshot.lines[rowIdx]
                            val cursorHere = showCursor && rowIdx == cursorRow
                            val annotated = remember(line, cursorHere, cursorCol, snapshot.revision) {
                                renderLine(line, if (cursorHere) cursorCol else -1)
                            }
                            Text(text = annotated, style = textStyle, softWrap = false, maxLines = 1)
                        }
                    }
                }

                // Field penangkap ketikan (transparan, di pojok) untuk input real-time.
                BasicTextField(
                    value = input,
                    onValueChange = { newVal ->
                        handleInput(input.text, newVal.text, vm)
                        // Reset baseline tiap baris dikirim atau bila terlalu panjang.
                        input = if (newVal.text.contains('\n') || newVal.text.length > 800)
                            TextFieldValue("") else newVal
                    },
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
                    modifier = Modifier
                        .size(1.dp)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { e -> handleHardwareKey(e, vm) }
                )
            }

            // Baris tombol panah + aksesori
            ArrowRow(vm)
            AccessoryRow(vm)
        }
    }
}

@Composable
private fun ArrowRow(vm: TerminalViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalContext.current
        IconButton(onClick = { vm.sendKey(TermKey.UP) }) {
            Icon(Icons.Default.KeyboardArrowUp, "Atas", tint = Color(0xFFCBD5E1))
        }
        IconButton(onClick = { vm.sendKey(TermKey.DOWN) }) {
            Icon(Icons.Default.KeyboardArrowDown, "Bawah", tint = Color(0xFFCBD5E1))
        }
        IconButton(onClick = { vm.sendKey(TermKey.LEFT) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Kiri", tint = Color(0xFFCBD5E1))
        }
        IconButton(onClick = { vm.sendKey(TermKey.RIGHT) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Kanan", tint = Color(0xFFCBD5E1))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                vm.paste(clip.getItemAt(0).text?.toString() ?: "")
            }
        }) { Icon(Icons.Default.ContentPaste, "Tempel", tint = Color(0xFF38BDF8)) }
    }
}

@Composable
private fun AccessoryRow(vm: TerminalViewModel) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(accessoryKeys) { key ->
            Button(
                onClick = { key.send(vm) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF334155), contentColor = Color(0xFFF1F5F9)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(key.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Kirim selisih ketikan ke server (append atau hapus). */
private fun handleInput(old: String, new: String, vm: TerminalViewModel) {
    if (new.contains('\n')) {
        // Kirim teks sebelum newline lalu Enter (carriage return).
        val idx = new.indexOf('\n')
        val tail = new.substring(commonPrefixLen(old, new), idx.coerceAtLeast(commonPrefixLen(old, new)))
        if (tail.isNotEmpty()) vm.sendText(tail)
        vm.sendText("\r")
        return
    }
    val common = commonPrefixLen(old, new)
    val removed = old.length - common
    if (removed > 0) repeat(removed) { vm.sendText("\u007F") } // DEL (backspace)
    if (new.length > common) vm.sendText(new.substring(common))
}

private fun commonPrefixLen(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

/** Tangani tombol fisik (keyboard luar) — panah, enter, backspace, tab, ctrl. */
private fun handleHardwareKey(e: KeyEvent, vm: TerminalViewModel): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when (e.key) {
        Key.Enter, Key.NumPadEnter -> { vm.sendText("\r"); true }
        Key.Backspace -> { vm.sendText("\u007F"); true }
        Key.Tab -> { vm.sendText("\t"); true }
        Key.DirectionUp -> { vm.sendKey(TermKey.UP); true }
        Key.DirectionDown -> { vm.sendKey(TermKey.DOWN); true }
        Key.DirectionLeft -> { vm.sendKey(TermKey.LEFT); true }
        Key.DirectionRight -> { vm.sendKey(TermKey.RIGHT); true }
        Key.Escape -> { vm.sendText("\u001B"); true }
        else -> false
    }
}

// ---------------- Render warna & atribut ----------------

private fun renderLine(line: TermLine, cursorCol: Int): AnnotatedString {
    return buildAnnotatedString {
        val cells = line.cells
        var i = 0
        val n = cells.size
        // Pangkas spasi kosong di akhir (tanpa atribut) agar ringan, kecuali ada kursor.
        var end = n
        while (end > 0 && isBlank(cells[end - 1]) && (cursorCol < 0 || end - 1 != cursorCol)) end--
        if (cursorCol in 0 until n && end <= cursorCol) end = cursorCol + 1

        while (i < end) {
            val cell = cells[i]
            val isCursor = i == cursorCol
            val (fg, bg) = resolveColors(cell, isCursor)
            val style = SpanStyle(
                color = fg,
                background = if (bg == termBg) Color.Unspecified else bg,
                fontWeight = if (cell.attr and ATTR_BOLD != 0) FontWeight.Bold else null,
                fontStyle = if (cell.attr and ATTR_ITALIC != 0) FontStyle.Italic else null,
                textDecoration = decorationFor(cell.attr)
            )
            pushStyle(style)
            append(if (cell.attr and ATTR_HIDDEN != 0) ' ' else cell.char)
            pop()
            i++
        }
        if (end == 0 && cursorCol < 0) append(' ') // baris kosong tetap punya tinggi
    }
}

private fun isBlank(c: Cell): Boolean =
    c.char == ' ' && c.bg == DEFAULT_BG && c.attr == 0

private fun decorationFor(attr: Int): TextDecoration? {
    val under = attr and ATTR_UNDERLINE != 0
    val strike = attr and ATTR_STRIKE != 0
    return when {
        under && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        under -> TextDecoration.Underline
        strike -> TextDecoration.LineThrough
        else -> null
    }
}

private fun resolveColors(cell: Cell, isCursor: Boolean): Pair<Color, Color> {
    var fg = colorOf(cell.fg, true)
    var bg = colorOf(cell.bg, false)
    if (cell.attr and ATTR_DIM != 0) fg = fg.copy(alpha = 0.6f)
    if (cell.attr and ATTR_REVERSE != 0) { val t = fg; fg = if (bg == termBg) defaultFg else bg; bg = t }
    if (isCursor) { val t = fg; fg = termBg; bg = t.takeIf { it != termBg } ?: defaultFg }
    return fg to bg
}

private fun colorOf(v: Int, isFg: Boolean): Color = when {
    v == DEFAULT_FG -> defaultFg
    v == DEFAULT_BG -> termBg
    TerminalEmulator.isTruecolor(v) -> Color(
        red = (v shr 16 and 0xFF) / 255f,
        green = (v shr 8 and 0xFF) / 255f,
        blue = (v and 0xFF) / 255f
    )
    else -> palette256(v)
}

private fun palette256(index: Int): Color = when (index) {
    in 0..15 -> ansi16[index]
    in 16..231 -> {
        val a = index - 16
        val r = (a / 36) * 51
        val g = ((a % 36) / 6) * 51
        val b = (a % 6) * 51
        Color(r, g, b)
    }
    in 232..255 -> { val gr = (index - 232) * 10 + 8; Color(gr, gr, gr) }
    else -> defaultFg
}

private val ansi16 = arrayOf(
    Color(0xFF1E293B), // 0 black (sedikit terang agar terlihat di tema gelap)
    Color(0xFFEF4444), // 1 red
    Color(0xFF22C55E), // 2 green
    Color(0xFFEAB308), // 3 yellow
    Color(0xFF3B82F6), // 4 blue
    Color(0xFFD946EF), // 5 magenta
    Color(0xFF06B6D4), // 6 cyan
    Color(0xFFE2E8F0), // 7 white
    Color(0xFF64748B), // 8 bright black
    Color(0xFFF87171), // 9 bright red
    Color(0xFF4ADE80), // 10 bright green
    Color(0xFFFACC15), // 11 bright yellow
    Color(0xFF60A5FA), // 12 bright blue
    Color(0xFFF472B6), // 13 bright magenta
    Color(0xFF22D3EE), // 14 bright cyan
    Color(0xFFFFFFFF)  // 15 bright white
)
