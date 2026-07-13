package com.yoursftp.app.transfer

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import com.yoursftp.app.data.KnownHost
import com.yoursftp.app.data.KnownHostDao
import kotlinx.coroutines.runBlocking
import android.util.Base64

class HostKeyChangedException(val host: String, val newKey: String) : RuntimeException("Kunci host server berubah! Kemungkinan VPS di-reinstall atau ada serangan MITM.")

class DbHostKeyRepository(private val dao: KnownHostDao) : HostKeyRepository {

    override fun check(host: String, key: ByteArray): Int {
        val hostKeyStr = Base64.encodeToString(key, Base64.NO_WRAP)
        
        return runBlocking {
            val known = dao.getHost(host, 22) // Simplified port handling
            if (known == null) {
                HostKeyRepository.NOT_INCLUDED
            } else if (known.hostKey == hostKeyStr) {
                HostKeyRepository.OK
            } else {
                throw HostKeyChangedException(host, hostKeyStr)
            }
        }
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) {
        val hostKeyStr = hostkey.key
        runBlocking {
            val existing = dao.getHost(hostkey.host, 22)
            if (existing != null) {
                dao.deleteHost(hostkey.host, 22)
            }
            dao.insert(
                KnownHost(
                    host = hostkey.host,
                    port = 22,
                    hostKey = hostKeyStr,
                    trustedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun remove(host: String?, type: String?) {}

    override fun remove(host: String?, type: String?, key: ByteArray?) {}

    override fun getKnownHostsRepositoryID(): String = "YoursFtpDbHostKeyRepository"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
