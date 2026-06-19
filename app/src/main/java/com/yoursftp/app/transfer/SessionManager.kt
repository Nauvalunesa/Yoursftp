package com.yoursftp.app.transfer

/**
 * Memegang satu sesi FileClient aktif agar dapat dipakai bersama antara
 * layar Browser dan Editor. Sederhana (single-session) sesuai kebutuhan app.
 */
object SessionManager {
    @Volatile
    var client: FileClient? = null
        private set

    @Volatile
    var editingClient: FileClient? = null

    fun set(client: FileClient) {
        close()
        this.client = client
    }

    fun require(): FileClient =
        editingClient ?: client ?: throw IllegalStateException("Tidak ada sesi aktif")

    fun close() {
        runCatching { client?.disconnect() }
        client = null
        editingClient = null
    }
}
