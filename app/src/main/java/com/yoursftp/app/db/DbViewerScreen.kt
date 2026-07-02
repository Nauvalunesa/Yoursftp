package com.yoursftp.app.db

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFF1E1E1E)
private val headerBg = Color(0xFF252526)
private val cellBorder = Color(0xFF333333)
private val fg = Color(0xFFD4D4D4)
private val accent = Color(0xFF0E639C)

/** Warna badge berdasarkan format. */
private fun formatBadgeColor(format: DbFormat?): Color = when (format) {
    DbFormat.SQLITE -> Color(0xFF3B82F6)
    DbFormat.JSON -> Color(0xFFF59E0B)
    DbFormat.CSV -> Color(0xFF10B981)
    DbFormat.SQL_DUMP -> Color(0xFF8B5CF6)
    DbFormat.XML -> Color(0xFFEF4444)
    DbFormat.BSON -> Color(0xFFEC4899)
    null -> Color(0xFF64748B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbViewerScreen(
    vm: DbViewModel,
    localPath: String,
    title: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(localPath) { vm.openFile(localPath, title) }

    var columnWidthScale by remember { mutableStateOf(1f) }
    var selectedCellText by remember { mutableStateOf<String?>(null) }
    var filterText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        containerColor = bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF323233),
                    titleContentColor = Color(0xFFE7E7E7),
                    navigationIconContentColor = Color(0xFFCCCCCC),
                    actionIconContentColor = Color(0xFFCCCCCC)
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        state.format?.let { fmt ->
                            Spacer(Modifier.width(8.dp))
                            var showFormatMenu by remember { mutableStateOf(false) }
                            Box {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = formatBadgeColor(fmt).copy(alpha = 0.2f),
                                    border = null,
                                    modifier = Modifier.clickable { showFormatMenu = true }
                                ) {
                                    Text(
                                        text = "${fmt.label} ▾",
                                        color = formatBadgeColor(fmt),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFormatMenu,
                                    onDismissRequest = { showFormatMenu = false },
                                    modifier = Modifier.background(headerBg)
                                ) {
                                    DbFormat.values().forEach { formatOption ->
                                        DropdownMenuItem(
                                            text = { Text(formatOption.label, fontSize = 12.sp, color = fg) },
                                            onClick = {
                                                vm.setFormatOverride(formatOption)
                                                showFormatMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(bg)) {

            // Daftar tabel
            if (state.tables.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().background(headerBg).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.tables) { t ->
                        FilterChip(
                            selected = t == state.selectedTable,
                            onClick = { vm.selectTable(t) },
                            label = { Text(t, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Kotak query SELECT — hanya tampil jika format mendukung query
            if (state.supportsQuery) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.weight(1f).background(headerBg, MaterialTheme.shapes.small).padding(10.dp)
                    ) {
                        BasicTextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            textStyle = TextStyle(color = fg, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                            cursorBrush = SolidColor(fg),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { vm.runQuery() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent)
                    ) { Icon(Icons.Default.PlayArrow, contentDescription = "Jalankan") }
                }
            }

            state.error?.let {
                Text(it, color = Color(0xFFEF4444), fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            if (state.rowInfo.isNotEmpty()) {
                Text(state.rowInfo, color = Color(0xFF858585), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }

            // Pagination Bar
            if (state.selectedTable != null && state.totalRows > 0) {
                val totalPages = if (state.totalRows > 0) {
                    java.lang.Math.ceil(state.totalRows.toDouble() / state.pageSize).toInt()
                } else 0
                val currentPage = state.page + 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { vm.prevPage() },
                            enabled = state.page > 0,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Sebelumnya", fontSize = 11.sp, color = if (state.page > 0) Color(0xFF4FC1FF) else Color(0xFF666666))
                        }
                        
                        Text(
                            text = "Hal $currentPage / $totalPages",
                            color = fg,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        TextButton(
                            onClick = { vm.nextPage() },
                            enabled = (state.page + 1) * state.pageSize < state.totalRows,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Berikutnya", fontSize = 11.sp, color = if ((state.page + 1) * state.pageSize < state.totalRows) Color(0xFF4FC1FF) else Color(0xFF666666))
                        }
                    }

                    // Page size selector
                    var expandedPageSize by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { expandedPageSize = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Baris/Hal: ${state.pageSize} ▾", fontSize = 11.sp, color = Color(0xFF4FC1FF))
                        }
                        DropdownMenu(
                            expanded = expandedPageSize,
                            onDismissRequest = { expandedPageSize = false },
                            modifier = Modifier.background(headerBg)
                        ) {
                            listOf(50, 100, 200, 500).forEach { size ->
                                DropdownMenuItem(
                                    text = { Text("$size baris", fontSize = 12.sp, color = fg) },
                                    onClick = {
                                        vm.setPageSize(size)
                                        expandedPageSize = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Controls for Column Zoom Scale & Tips
            if (state.columns.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Lebar Kolom: ", color = fg, fontSize = 11.sp)
                        IconButton(
                            onClick = { columnWidthScale = (columnWidthScale - 0.2f).coerceAtLeast(0.5f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Perkecil", tint = Color(0xFF4FC1FF), modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${(columnWidthScale * 100).toInt()}%",
                            color = fg,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(
                            onClick = { columnWidthScale = (columnWidthScale + 0.2f).coerceAtMost(3.0f) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Perbesar", tint = Color(0xFF4FC1FF), modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = "Tip: Ketuk sel untuk teks lengkap",
                        color = Color(0xFF858585),
                        fontSize = 10.sp
                    )
                }
            }

            // Search/Filter Bar
            if (state.columns.isNotEmpty()) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Cari di tabel ini...", fontSize = 12.sp, color = fg.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4FC1FF)) },
                    trailingIcon = {
                        if (filterText.isNotEmpty()) {
                            IconButton(onClick = { filterText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Bersihkan", modifier = Modifier.size(16.dp), tint = fg)
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 12.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4FC1FF),
                        unfocusedBorderColor = cellBorder,
                        focusedContainerColor = headerBg,
                        unfocusedContainerColor = headerBg
                    )
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Grid hasil (scroll horizontal + vertikal smooth menggunakan LazyColumn)
            if (state.columns.isNotEmpty()) {
                val hScroll = rememberScrollState()
                val filteredRows = remember(state.rows, filterText) {
                    if (filterText.isEmpty()) {
                        state.rows
                    } else {
                        state.rows.filter { row ->
                            row.any { cell -> cell.contains(filterText, ignoreCase = true) }
                        }
                    }
                }
                val columnWidths = remember(state.columns, filteredRows) {
                    state.columns.associateWith { col ->
                        val colIndex = state.columns.indexOf(col)
                        val headerLength = col.length
                        val maxRowLength = filteredRows.maxOfOrNull { row ->
                            if (colIndex in row.indices) row[colIndex].length else 0
                        } ?: 0
                        val maxChars = maxOf(headerLength, maxRowLength)
                        // Pemetaan karakter ke dp: 8.dp per karakter + 24.dp padding.
                        // Batasi lebar antara 60.dp dan 300.dp agar tetap proporsional.
                        (maxChars * 8 + 24).coerceIn(60, 300).dp
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .horizontalScroll(hScroll)
                        .border(0.5.dp, cellBorder)
                ) {
                    // Header Row
                    Row(Modifier.background(headerBg)) {
                        state.columns.forEach { col ->
                            val colWidth = (columnWidths[col] ?: 160.dp) * columnWidthScale
                            GridCell(
                                text = col,
                                header = true,
                                width = colWidth,
                                onClick = { selectedCellText = col }
                            )
                        }
                    }
                    // Baris Data menggunakan LazyColumn agar render cepat & scroll lancar
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredRows) { rIndex, row ->
                            Row(Modifier.background(if (rIndex % 2 == 0) bg else Color(0xFF252526))) {
                                state.columns.forEachIndexed { colIndex, colName ->
                                    val cell = if (colIndex in row.indices) row[colIndex] else ""
                                    val colWidth = (columnWidths[colName] ?: 160.dp) * columnWidthScale
                                    GridCell(
                                        text = cell,
                                        header = false,
                                        width = colWidth,
                                        onClick = { selectedCellText = cell }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pesan kosong jika tidak ada data
            if (!state.loading && state.columns.isEmpty() && state.error == null && state.tables.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, contentDescription = null,
                            tint = Color(0xFF555555), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Tidak ada data", color = Color(0xFF858585), fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Dialog untuk melihat isi sel secara lengkap & menyalinnya
    selectedCellText?.let { cellText ->
        AlertDialog(
            onDismissRequest = { selectedCellText = null },
            title = { Text("Isi Detail Sel", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .background(headerBg, MaterialTheme.shapes.small)
                            .border(0.5.dp, cellBorder, MaterialTheme.shapes.small)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = cellText.ifEmpty { "(kosong)" },
                            color = fg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCellText = null }) {
                    Text("Tutup")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(cellText))
                        selectedCellText = null
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salin Teks")
                }
            }
        )
    }
}

@Composable
private fun GridCell(
    text: String,
    header: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(38.dp)
            .drawBehind {
                // Batas kanan sel
                drawLine(
                    color = cellBorder,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
                // Batas bawah sel
                drawLine(
                    color = cellBorder,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text.ifEmpty { "-" },
            color = if (header) Color(0xFF4FC1FF) else if (text.isEmpty()) Color(0xFF666666) else fg,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
