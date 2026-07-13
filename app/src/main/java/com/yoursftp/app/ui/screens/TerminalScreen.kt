package com.yoursftp.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

// Catppuccin Mocha Palette
private val defaultFg = Color(0xFFCDD6F4)
private val termBg = Color(0xFF1E1E2E)
private val panelBg = Color(0xFF181825)
private val buttonBg = Color(0xFF313244)
private val accentColor = Color(0xFFF5C2E7)

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

    var fontSizeSp by remember { mutableStateOf(12f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        fontSizeSp = (fontSizeSp * zoomChange).coerceIn(8f, 24f)
    }

    var input by remember { mutableStateOf(TextFieldValue("")) }

    // Dialog state
    var showShortcutDialog by remember { mutableStateOf(false) }

    val defaultShortcuts = remember {
        listOf(
            "ls -la", "cd ..", "pwd", "df -h", "free -m", "top", "uname -a", "history", "ping -c 4 1.1.1.1"
        )
    }
    var customShortcuts by remember { mutableStateOf(listOf<String>()) }
    var newShortcutText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    // Efek kursor berkedip (blinking cursor)
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha"
    )

    // Indikator koneksi berkedip
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    LaunchedEffect(connectionId) { vm.connect(connectionId) }

    val snapshot = state.snapshot
    val lineCount = snapshot?.lines?.size ?: 0

    // Auto-scroll ke bawah secara cerdas jika posisi scroll di bawah
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(snapshot?.revision) {
        if (lineCount > 0 && atBottom) listState.scrollToItem(lineCount - 1)
    }

    Scaffold(
        containerColor = termBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = panelBg,
                    titleContentColor = defaultFg,
                    navigationIconContentColor = defaultFg,
                    actionIconContentColor = defaultFg
                ),
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
                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {

                    // Zoom Out & In Font
                    IconButton(onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(1f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil Font", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(24f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar Font", modifier = Modifier.size(18.dp))
                    }
                    // Salin layar & Bersihkan
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setText(AnnotatedString(vm.screenText()))
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Salin layar", modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { vm.clearConsole() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Bersihkan", modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(termBg)
        ) {
            // Kontainer Terminal Emulator
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .transformable(transformableState)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        focusRequester.requestFocus()
                        keyboard?.show()
                    }
                    .padding(4.dp)
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
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(count = snapshot.lines.size, key = { it }) { rowIdx ->
                            val line = snapshot.lines[rowIdx]
                            val cursorHere = showCursor && rowIdx == cursorRow
                            val annotated = remember(line, cursorHere, cursorCol, snapshot.revision, cursorAlpha) {
                                renderLine(line, if (cursorHere) cursorCol else -1, cursorAlpha)
                            }
                            Text(text = annotated, style = textStyle, softWrap = false, maxLines = 1)
                        }
                    }
                }

                // Keyboard input catcher (transparan)
                BasicTextField(
                    value = input,
                    onValueChange = { newVal ->
                        handleInput(input.text, newVal.text, vm)
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

            // Keyboard Dock
            ArrowRow(vm, { showShortcutDialog = true })
            AccessoryRow(vm)
        }
    }

    // Dialog Pintasan Perintah Biasa
    if (showShortcutDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutDialog = false },
            title = { Text("Pintasan Perintah", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Pilih perintah untuk dijalankan langsung:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        val allCmds = defaultShortcuts + customShortcuts
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(allCmds) { cmd ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.sendText(cmd + "\r")
                                            showShortcutDialog = false
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    if (cmd in customShortcuts) {
                                        IconButton(
                                            onClick = { customShortcuts = customShortcuts.filter { it != cmd } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Tambah Perintah Kustom:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newShortcutText,
                            onValueChange = { newShortcutText = it },
                            placeholder = { Text("e.g. git status", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newShortcutText.isNotBlank()) {
                                    customShortcuts = customShortcuts + newShortcutText.trim()
                                    newShortcutText = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Tambah", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShortcutDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
@Composable
private fun ArrowRow(vm: TerminalViewModel, onShowShortcuts: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(panelBg).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalContext.current
        IconButton(onClick = { vm.sendKey(TermKey.UP) }) {
            Icon(Icons.Default.KeyboardArrowUp, "Atas", tint = defaultFg)
        }
        IconButton(onClick = { vm.sendKey(TermKey.DOWN) }) {
            Icon(Icons.Default.KeyboardArrowDown, "Bawah", tint = defaultFg)
        }
        IconButton(onClick = { vm.sendKey(TermKey.LEFT) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Kiri", tint = defaultFg)
        }
        IconButton(onClick = { vm.sendKey(TermKey.RIGHT) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Kanan", tint = defaultFg)
        }
        Spacer(Modifier.weight(1f))
        
        // Pintasan Perintah (List) Button
        IconButton(onClick = onShowShortcuts) {
            Icon(Icons.Default.List, "Pintasan Perintah", tint = accentColor)
        }

        IconButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                vm.paste(clip.getItemAt(0).text?.toString() ?: "")
            }
        }) { Icon(Icons.Default.ContentPaste, "Tempel", tint = accentColor) }
    }
}

@Composable
private fun AccessoryRow(vm: TerminalViewModel) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().background(panelBg).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(accessoryKeys) { key ->
            Button(
                onClick = { key.send(vm) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBg, contentColor = defaultFg
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

private fun handleInput(old: String, new: String, vm: TerminalViewModel) {
    if (new.contains('\n')) {
        val idx = new.indexOf('\n')
        val tail = new.substring(commonPrefixLen(old, new), idx.coerceAtLeast(commonPrefixLen(old, new)))
        if (tail.isNotEmpty()) vm.sendText(tail)
        vm.sendText("\r")
        return
    }
    val common = commonPrefixLen(old, new)
    val removed = old.length - common
    if (removed > 0) repeat(removed) { vm.sendText("\u007F") }
    if (new.length > common) vm.sendText(new.substring(common))
}

private fun commonPrefixLen(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

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

private fun renderLine(line: TermLine, cursorCol: Int, cursorAlpha: Float): AnnotatedString {
    return buildAnnotatedString {
        val cells = line.cells
        var i = 0
        val n = cells.size
        var end = n
        while (end > 0 && isBlank(cells[end - 1]) && (cursorCol < 0 || end - 1 != cursorCol)) end--
        if (cursorCol in 0 until n && end <= cursorCol) end = cursorCol + 1

        while (i < end) {
            val cell = cells[i]
            val isCursor = i == cursorCol
            val (fg, bg) = resolveColors(cell, isCursor, cursorAlpha)
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
        if (end == 0 && cursorCol < 0) append(' ')
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

private fun resolveColors(cell: Cell, isCursor: Boolean, cursorAlpha: Float): Pair<Color, Color> {
    var fg = colorOf(cell.fg, true)
    var bg = colorOf(cell.bg, false)
    if (cell.attr and ATTR_DIM != 0) fg = fg.copy(alpha = 0.6f)
    if (cell.attr and ATTR_REVERSE != 0) { val t = fg; fg = if (bg == termBg) defaultFg else bg; bg = t }
    if (isCursor) { 
        val t = fg
        fg = termBg
        bg = (t.takeIf { it != termBg } ?: defaultFg).copy(alpha = cursorAlpha)
    }
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
    Color(0xFF1E293B),
    Color(0xFFEF4444),
    Color(0xFF22C55E),
    Color(0xFFEAB308),
    Color(0xFF3B82F6),
    Color(0xFFD946EF),
    Color(0xFF06B6D4),
    Color(0xFFE2E8F0),
    Color(0xFF64748B),
    Color(0xFFF87171),
    Color(0xFF4ADE80),
    Color(0xFFFACC15),
    Color(0xFF60A5FA),
    Color(0xFFF472B6),
    Color(0xFF22D3EE),
    Color(0xFFFFFFFF)
)

private object ToastUtil {
    fun show(context: Context, msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
