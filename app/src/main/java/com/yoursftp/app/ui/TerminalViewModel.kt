package com.yoursftp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.yoursftp.app.YoursFtpApp
import com.yoursftp.app.terminal.TermSnapshot
import com.yoursftp.app.terminal.TerminalEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.CodingErrorAction
import java.util.Properties

data class TerminalState(
    val connectionName: String = "",
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
    val statusLine: String = "Menghubungkan ke SSH...",
    val snapshot: TermSnapshot? = null
)

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as YoursFtpApp).repository

    private val _state = MutableStateFlow(TerminalState())
    val state = _state.asStateFlow()

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var outputStream: OutputStream? = null

    private val emulator = TerminalEmulator(80, 24)
    private val emuLock = Any()

    // Decoder UTF-8 streaming yang tahan multibyte terpotong antar-chunk.
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pendingBytes = ByteArray(0)

    private var lastRenderedRevision = -1L

    init {
        emulator.onResponse = { resp -> sendRaw(resp) }
    }

    fun connect(connectionId: Long) {
        if (_state.value.connected || _state.value.connecting) return
        _state.value = TerminalState(connecting = true, statusLine = "Menghubungkan ke SSH...")
        viewModelScope.launch {
            try {
                val conn = repo.getById(connectionId)
                    ?: throw IllegalStateException("Koneksi tidak ditemukan")
                _state.value = _state.value.copy(connectionName = conn.name)

                withContext(Dispatchers.IO) {
                    val jsch = JSch()
                    if (!conn.privateKey.isNullOrBlank()) {
                        val prvKeyBytes = conn.privateKey.toByteArray(Charsets.UTF_8)
                        val passBytes = if (!conn.passphrase.isNullOrEmpty()) conn.passphrase.toByteArray(Charsets.UTF_8) else null
                        jsch.addIdentity(conn.name, prvKeyBytes, null, passBytes)
                    }
                    val s = jsch.getSession(conn.username, conn.host, conn.port)
                    if (conn.privateKey.isNullOrBlank()) {
                        s.setPassword(conn.password)
                    }
                    s.setConfig(Properties().apply {
                        put("StrictHostKeyChecking", "no")
                        put("CheckHostIP", "no")
                        put("HashKnownHosts", "no")
                        put("server_host_key",
                            "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
                        put("PubkeyAcceptedAlgorithms",
                            "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
                        put("PreferredAuthentications", "publickey,password,keyboard-interactive")
                    })
                    s.timeout = 15_000
                    s.setServerAliveInterval(20_000)
                    s.setServerAliveCountMax(3)
                    s.connect()

                    val ch = s.openChannel("shell") as ChannelShell
                    ch.setPtyType("xterm-256color")
                    val (c, r) = synchronized(emuLock) { emulator.cols to emulator.rows }
                    ch.setPtySize(c, r, c * 8, r * 16)
                    ch.connect()

                    session = s
                    channel = ch
                    outputStream = ch.outputStream
                }

                _state.value = _state.value.copy(
                    connected = true, connecting = false, statusLine = "Terhubung"
                )
                startReading()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connecting = false,
                    error = e.message ?: "Gagal terhubung",
                    statusLine = "Gagal: ${e.message}"
                )
            }
        }
    }

    private fun startReading() {
        viewModelScope.launch(Dispatchers.IO) {
            val ch = channel ?: return@launch
            val input = ch.inputStream
            val buffer = ByteArray(16384)
            try {
                while (ch.isConnected) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    val text = decode(buffer, bytesRead)
                    if (text.isNotEmpty()) synchronized(emuLock) { emulator.feed(text) }
                    pushSnapshot()
                }
            } catch (e: Exception) {
                synchronized(emuLock) { emulator.feed("\r\n[Koneksi terputus: ${e.message}]\r\n") }
            } finally {
                pushSnapshot(force = true)
                _state.value = _state.value.copy(connected = false, statusLine = "Terputus")
            }
        }
    }

    /** Decode dengan menyimpan sisa byte multibyte yang belum lengkap. */
    private fun decode(buf: ByteArray, len: Int): String {
        val combined = if (pendingBytes.isEmpty()) buf.copyOf(len)
        else pendingBytes + buf.copyOf(len)
        val bb = java.nio.ByteBuffer.wrap(combined)
        val cb = java.nio.CharBuffer.allocate(combined.size + 1)
        decoder.reset()
        val res = decoder.decode(bb, cb, false)
        pendingBytes = if (bb.hasRemaining()) {
            ByteArray(bb.remaining()).also { bb.get(it) }
        } else ByteArray(0)
        cb.flip()
        return cb.toString()
    }

    private var pushScheduled = false
    private fun pushSnapshot(force: Boolean = false) {
        val rev = synchronized(emuLock) { emulator.revision }
        if (!force && rev == lastRenderedRevision) return
        // Throttle: jadwalkan satu update; baca snapshot terbaru saat dijalankan.
        if (pushScheduled && !force) return
        pushScheduled = true
        viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(if (force) 0 else 16)
            pushScheduled = false
            val snap = synchronized(emuLock) {
                lastRenderedRevision = emulator.revision
                emulator.snapshot(includeScrollback = true)
            }
            _state.value = _state.value.copy(snapshot = snap)
        }
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(emuLock) { emulator.resize(cols, rows) }
                channel?.takeIf { it.isConnected }?.setPtySize(cols, rows, cols * 8, rows * 16)
            }
            pushSnapshot(force = true)
        }
    }

    /** Kirim teks mentah (hasil ketikan langsung) ke server. */
    fun sendText(text: String) = sendRaw(text)

    /** Kirim baris perintah + newline. */
    fun sendCommand(cmd: String) = sendRaw(cmd + "\r")

    /** Tombol panah/aksesori yang menyesuaikan application-cursor-keys. */
    fun sendKey(key: TermKey) {
        val appMode = synchronized(emuLock) { emulator.applicationCursorKeys }
        sendRaw(key.sequence(appMode))
    }

    fun paste(text: String) {
        val bracketed = synchronized(emuLock) { emulator.bracketedPaste }
        if (bracketed) sendRaw("\u001B[200~$text\u001B[201~") else sendRaw(text)
    }

    private fun sendRaw(data: String) {
        val out = outputStream ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                out.write(data.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    fun screenText(): String = synchronized(emuLock) { emulator.screenPlainText() }

    fun clearConsole() {
        synchronized(emuLock) { emulator.clearScrollbackAndScreen() }
        pushSnapshot(force = true)
        // Minta shell menggambar ulang prompt.
        sendRaw("\u000C")
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

/** Tombol khusus yang punya bentuk berbeda di mode application-cursor-keys. */
enum class TermKey(private val normal: String, private val app: String) {
    UP("\u001B[A", "\u001BOA"),
    DOWN("\u001B[B", "\u001BOB"),
    RIGHT("\u001B[C", "\u001BOC"),
    LEFT("\u001B[D", "\u001BOD"),
    HOME("\u001B[H", "\u001BOH"),
    END("\u001B[F", "\u001BOF");

    fun sequence(appMode: Boolean): String = if (appMode) app else normal
}
