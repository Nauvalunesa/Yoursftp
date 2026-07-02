package com.yoursftp.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind

private val gutterColor = Color(0xFF858585)        // editorLineNumber.foreground
private val gutterActiveColor = Color(0xFFC6C6C6)  // editorLineNumber.activeForeground
private val bgColor = Color(0xFF1E1E1E)            // editor.background (VSCode Dark+)
private val gutterBg = Color(0xFF1E1E1E)
private val activeLineBg = Color(0xFF2A2D2E)       // editor.lineHighlightBackground
private val dividerColor = Color(0xFF333333)
private val topBarBg = Color(0xFF323233)           // titlebar
private val accentColor = Color(0xFF007ACC)        // VSCode blue accent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeEditorScreen(
    vm: LargeEditorViewModel,
    path: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    var confirmBack by remember { mutableStateOf(false) }
    var pendingWord by remember { mutableStateOf<String?>(null) }

    var fontSizeSp by remember { mutableStateOf(13f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        fontSizeSp = (fontSizeSp * zoomChange).coerceIn(8f, 30f)
    }

    val currentEditorTextStyle = remember(fontSizeSp) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.5f).sp,
            color = Color(0xFFD4D4D4)
        )
    }

    LaunchedEffect(path) { vm.load(path) }

    LaunchedEffect(state.savedMessage, state.error, state.warning) {
        (state.savedMessage ?: state.error ?: state.warning)?.let {
            snackbar.showSnackbar(it)
            vm.clearMessages()
        }
    }

    // Lompat ke baris hasil find / undo-redo.
    LaunchedEffect(state.scrollTo) {
        state.scrollTo?.let { target ->
            val first = listState.firstVisibleItemIndex
            val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            if (target < first + 1 || target > first + visible - 2) {
                listState.scrollToItem(target.coerceAtLeast(0))
            }
            vm.consumeScroll()
        }
    }

    val backAction = { if (state.dirty) confirmBack = true else onBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBg,
                    titleContentColor = Color(0xFFE7E7E7),
                    navigationIconContentColor = Color(0xFFCCCCCC),
                    actionIconContentColor = Color(0xFFCCCCCC)
                ),
                title = {
                    Text(
                        (if (state.dirty) "● " else "") + path.substringAfterLast('/'),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace, fontSize = 15.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Zoom Buttons
                    IconButton(onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(8f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(30f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { vm.undo() }, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan")
                    }
                    IconButton(onClick = { vm.redo() }, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi")
                    }
                    IconButton(onClick = { vm.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    }
                    IconButton(onClick = { vm.save() }, enabled = !state.saving && !state.loading && state.dirty) {
                        Icon(Icons.Default.Save, contentDescription = "Simpan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
        ) {
            if (state.searchOpen) {
                SearchBar(
                    query = state.query,
                    replacement = state.replacement,
                    ignoreCase = state.ignoreCase,
                    onQuery = vm::setQuery,
                    onReplacement = vm::setReplacement,
                    onToggleCase = vm::toggleIgnoreCase,
                    onNext = vm::findNext,
                    onPrev = vm::findPrev,
                    onReplace = vm::replaceCurrent,
                    onReplaceAll = vm::replaceAll,
                    onClose = vm::toggleSearch
                )
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .transformable(transformableState)
            ) {
                if (state.loading) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.loadProgress in 0f..1f) {
                            CircularProgressIndicator(progress = { state.loadProgress })
                        } else {
                            CircularProgressIndicator()
                        }
                        if (state.loadStatus.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(state.loadStatus, color = Color(0xFFABB2BF))
                        }
                    }
                } else {
                    val gutterWidth = ((state.lineCount.coerceAtLeast(1).toString().length) * 10 + 20).dp
                    val rowMinWidth = maxWidth
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                    ) {
                        items(count = state.lineCount, key = { it }) { index ->
                            val active = state.activeLine == index
                            val rowBg = if (active) activeLineBg else bgColor
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(min = rowMinWidth)
                                    .background(rowBg)
                                    .drawBehindLines()
                            ) {
                                // Gutter nomor baris (lebar tetap).
                                Box(
                                    Modifier
                                        .width(gutterWidth)
                                        .padding(end = 10.dp, top = 1.dp, bottom = 1.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = currentEditorTextStyle.copy(
                                            color = if (active) gutterActiveColor else gutterColor
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                                // Konten baris.
                                Box(
                                    Modifier
                                        .padding(end = 12.dp, top = 1.dp, bottom = 1.dp)
                                ) {
                                    if (active) {
                                        ActiveLine(
                                            initialText = vm.lineText(index),
                                            initialCaret = state.pendingCaret,
                                            ext = vm.ext,
                                            canMergeUp = index > 0,
                                            applyWord = pendingWord,
                                            textStyle = currentEditorTextStyle,
                                            onChange = { vm.commitLine(index, it) },
                                            onMultiline = { full, caret -> vm.applyMultiline(index, full, caret) },
                                            onMergeUp = { vm.mergeWithPrevious(index) },
                                            onCaretChange = { t, c -> vm.updateSuggestions(t, c) },
                                            onWordApplied = { pendingWord = null },
                                            computeApply = { t, c, w -> vm.applySuggestion(t, c, w) }
                                        )
                                    } else {
                                        val text = vm.lineText(index)
                                        val annotated = remember(text, vm.ext) {
                                            highlightLine(text, vm.ext, false)
                                        }
                                        Text(
                                            text = if (annotated.isEmpty()) AnnotatedString(" ") else annotated,
                                            style = currentEditorTextStyle,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .widthIn(min = 24.dp)
                                                .clickable { vm.setActiveLine(index, vm.lineText(index).length) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.saving) {
                    CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(16.dp))
                }
            }
            // Bar prediksi kode (saran kata).
            if (state.suggestions.isNotEmpty()) {
                SuggestionBar(state.suggestions) { word -> pendingWord = word }
            }

            // Bottom Status Bar
            Surface(
                color = Color(0xFF007ACC),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Baris: ${state.lineCount} | Skala: ${(fontSizeSp * 100 / 13).toInt()}% | Mesin: Editor Besar (Large)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = vm.ext.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("Perubahan belum disimpan") },
            text = { Text("Buang perubahan dan keluar?") },
            confirmButton = {
                TextButton(onClick = { confirmBack = false; onBack() }) { Text("Buang") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBack = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ActiveLine(
    initialText: String,
    initialCaret: Int,
    ext: String,
    canMergeUp: Boolean,
    applyWord: String?,
    textStyle: TextStyle,
    onChange: (String) -> Unit,
    onMultiline: (String, Int) -> Unit,
    onMergeUp: () -> Unit,
    onCaretChange: (String, Int) -> Unit,
    onWordApplied: () -> Unit,
    computeApply: (String, Int, String) -> Pair<String, Int>
) {
    val focusRequester = remember { FocusRequester() }
    var tfv by remember {
        mutableStateOf(
            TextFieldValue(initialText, selection = TextRange(initialCaret.coerceIn(0, initialText.length)))
        )
    }
    val transformation = remember(ext) { LineHighlightTransformation(ext, false) }

    LaunchedEffect(applyWord) {
        if (applyWord != null) {
            val (newText, newCaret) = computeApply(tfv.text, tfv.selection.start, applyWord)
            tfv = TextFieldValue(newText, selection = TextRange(newCaret))
            onWordApplied()
        }
    }

    BasicTextField(
        value = tfv,
        onValueChange = { new ->
            if (new.text.contains('\n')) {
                onMultiline(new.text, new.selection.start)
            } else {
                tfv = new
                onChange(new.text)
                onCaretChange(new.text, new.selection.start)
            }
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(Color(0xFFAEAFAD)),
        visualTransformation = transformation,
        singleLine = false,
        modifier = Modifier
            .widthIn(min = 200.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace &&
                    canMergeUp && tfv.selection == TextRange(0, 0)
                ) {
                    onMergeUp(); true
                } else false
            }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SuggestionBar(words: List<String>, onPick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252526))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(words) { word ->
            Button(
                onClick = { onPick(word) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007ACC),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(word, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    replacement: String,
    ignoreCase: Boolean,
    onQuery: (String) -> Unit,
    onReplacement: (String) -> Unit,
    onToggleCase: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(tonalElevation = 3.dp, color = gutterBg) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("Cari") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Sebelumnya")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Berikutnya")
                }
                FilterChip(
                    selected = !ignoreCase,
                    onClick = onToggleCase,
                    label = { Text("Aa") }
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = onReplacement,
                    placeholder = { Text("Ganti dengan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReplace) { Text("Ganti") }
                TextButton(onClick = onReplaceAll) { Text("Semua") }
            }
        }
    }
}

private fun Modifier.drawBehindLines(): Modifier = this.drawBehind {
    drawLine(
        color = dividerColor,
        start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
        end = androidx.compose.ui.geometry.Offset(this.size.width, this.size.height),
        strokeWidth = 1f
    )
}
