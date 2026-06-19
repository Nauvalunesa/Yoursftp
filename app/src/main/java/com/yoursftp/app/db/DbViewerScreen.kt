package com.yoursftp.app.db

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

    Scaffold(
        containerColor = bg,
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
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = formatBadgeColor(fmt).copy(alpha = 0.2f),
                                border = null
                            ) {
                                Text(
                                    text = fmt.label,
                                    color = formatBadgeColor(fmt),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
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

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Grid hasil (scroll horizontal + vertikal)
            if (state.columns.isNotEmpty()) {
                val hScroll = rememberScrollState()
                val columnWidths = remember(state.columns, state.rows) {
                    state.columns.associateWith { col ->
                        val colIndex = state.columns.indexOf(col)
                        val headerLength = col.length
                        val maxRowLength = state.rows.maxOfOrNull { row ->
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
                    // Header
                    Row(Modifier.background(headerBg)) {
                        state.columns.forEach { col ->
                            GridCell(col, header = true, width = columnWidths[col] ?: 160.dp)
                        }
                    }
                    // Baris data
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        state.rows.forEachIndexed { rIndex, row ->
                            Row(Modifier.background(if (rIndex % 2 == 0) bg else Color(0xFF252526))) {
                                row.forEachIndexed { colIndex, cell ->
                                    val colName = state.columns.getOrNull(colIndex)
                                    GridCell(cell, header = false, width = columnWidths[colName] ?: 160.dp)
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
}

@Composable
private fun GridCell(text: String, header: Boolean, width: androidx.compose.ui.unit.Dp) {
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
