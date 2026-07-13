package com.yoursftp.app.clean

import java.io.File
import java.security.MessageDigest

/** Kategori temuan sampah. */
enum class JunkCategory(val label: String) {
    WHATSAPP_DB("Database WhatsApp"),
    APP_CACHE("Penyimpanan per Aplikasi"),
    TEMP_CACHE("File Sementara & Cache"),
    LOG("File Log"),
    EMPTY_FOLDER("Folder Kosong"),
    THUMBNAIL("Thumbnail"),
    APK("File APK"),
    LARGE_FILE("File Besar (>100 MB)"),
    DUPLICATE("File Duplikat")
}

/** Satu item hasil pemindaian. */
data class JunkItem(
    val path: String,
    val name: String,
    val size: Long,
    val category: JunkCategory,
    val isDirectory: Boolean,
    /** Nama paket aplikasi bila item ini terkait aplikasi tertentu (untuk kategori APP_CACHE). */
    val packageName: String? = null
)

/** Grup temuan per kategori. */
data class JunkGroup(
    val category: JunkCategory,
    val items: List<JunkItem>
) {
    val totalSize: Long get() = items.sumOf { it.size }
}

data class ScanResult(
    val groups: List<JunkGroup>
) {
    val totalSize: Long get() = groups.sumOf { it.totalSize }
    val totalCount: Int get() = groups.sumOf { it.items.size }
}

/**
 * Pemindai sampah pada penyimpanan lokal Android. Semua operasi memblok;
 * panggil dari dispatcher IO.
 */
class JunkScanner {

    /** Root penyimpanan yang dipindai, termasuk ruang clone bila ada. */
    private val roots: List<String> = buildList {
        add("/storage/emulated/0")
        // Ruang clone (mis. aplikasi ganda) biasanya di /storage/emulated/999, dst.
        for (n in intArrayOf(999, 998, 10, 95, 1)) {
            val p = "/storage/emulated/$n"
            if (File(p).let { it.exists() && it.isDirectory && it.canRead() }) add(p)
        }
    }

    private val tempExts = setOf("tmp", "temp", "cache", "part", "crdownload", "bak", "old")
    private val logExts = setOf("log")

    /** Folder database WhatsApp/WA Business (relatif dari root); isinya dianggap tak penting. */
    private val whatsappDbDirs = listOf(
        "Android/media/com.whatsapp/WhatsApp/Databases",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Databases"
    )

    /**
     * Pindai penyimpanan per aplikasi: folder cache/media/obb milik tiap paket di
     * Android/data, Android/media, Android/obb. Mengembalikan satu item per aplikasi
     * (folder) beserta total ukuran, sehingga bisa dipilih & dihapus per aplikasi.
     *
     * Catatan: pada Android 11+, isi Android/data/<pkg> sering diblokir OS meski ada
     * izin "All files access", jadi ukurannya bisa 0 di perangkat baru.
     */
    fun scanPerApp(onProgress: ((String) -> Unit)? = null): List<JunkItem> {
        // pkg -> (daftar folder milik app itu)
        val perApp = LinkedHashMap<String, MutableList<File>>()
        val containers = listOf("Android/data", "Android/media", "Android/obb")
        for (root in roots) {
            for (container in containers) {
                val base = File(root, container)
                if (!base.exists() || !base.isDirectory) continue
                base.listFiles()?.forEach { pkgDir ->
                    if (pkgDir.isDirectory && pkgDir.name.contains('.')) {
                        onProgress?.invoke(pkgDir.absolutePath)
                        perApp.getOrPut(pkgDir.name) { mutableListOf() }.add(pkgDir)
                    }
                }
            }
        }

        val items = perApp.mapNotNull { (pkg, dirs) ->
            val size = dirs.sumOf { dirSize(it) }
            if (size <= 0L) return@mapNotNull null
            // Path yang dihapus = semua folder milik app; simpan sbg daftar dipisah '\n'.
            JunkItem(
                path = dirs.joinToString("\n") { it.absolutePath },
                name = pkg,
                size = size,
                category = JunkCategory.APP_CACHE,
                isDirectory = true,
                packageName = pkg
            )
        }.sortedByDescending { it.size }
        return items
    }

    private fun dirSize(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { dirSize(it) } ?: 0L) else f.length()

    /**
     * Pindai. [onProgress] dipanggil dengan path folder yang sedang diproses (opsional).
     * [maxDepth] membatasi kedalaman rekursi umum agar tak terlalu lama.
     */
    fun scan(onProgress: ((String) -> Unit)? = null, maxDepth: Int = 6): ScanResult {
        val whatsapp = mutableListOf<JunkItem>()
        val temp = mutableListOf<JunkItem>()
        val logs = mutableListOf<JunkItem>()
        val empty = mutableListOf<JunkItem>()
        val thumbs = mutableListOf<JunkItem>()
        val apks = mutableListOf<JunkItem>()
        val large = mutableListOf<JunkItem>()

        // Untuk deteksi duplikat: kelompokkan berdasar (ukuran) lalu hash isi.
        val bySize = HashMap<Long, MutableList<File>>()

        for (root in roots) {
            // 1. Target khusus: database WhatsApp — semua file di dalamnya.
            for (rel in whatsappDbDirs) {
                val dir = File(root, rel)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            whatsapp.add(JunkItem(f.absolutePath, f.name, f.length(), JunkCategory.WHATSAPP_DB, false))
                        }
                    }
                }
            }

            // 2. Pemindaian umum.
            walk(File(root), 0, maxDepth, onProgress) { f ->
                val name = f.name
                val lower = name.lowercase()
                when {
                    f.isDirectory -> {
                        val children = f.listFiles()
                        if (children != null && children.isEmpty()) {
                            empty.add(JunkItem(f.absolutePath, name, 0L, JunkCategory.EMPTY_FOLDER, true))
                        }
                    }
                    else -> {
                        val ext = lower.substringAfterLast('.', "")
                        val size = f.length()
                        when {
                            lower == ".nomedia" -> {}
                            lower == "thumbs.db" || lower.endsWith(".thumbnail") ||
                                f.parentFile?.name?.equals(".thumbnails", ignoreCase = true) == true ->
                                thumbs.add(JunkItem(f.absolutePath, name, size, JunkCategory.THUMBNAIL, false))
                            ext in tempExts ->
                                temp.add(JunkItem(f.absolutePath, name, size, JunkCategory.TEMP_CACHE, false))
                            ext in logExts ->
                                logs.add(JunkItem(f.absolutePath, name, size, JunkCategory.LOG, false))
                            ext == "apk" ->
                                apks.add(JunkItem(f.absolutePath, name, size, JunkCategory.APK, false))
                        }
                        if (size >= LARGE_THRESHOLD) {
                            large.add(JunkItem(f.absolutePath, name, size, JunkCategory.LARGE_FILE, false))
                        }
                        if (size > 0) bySize.getOrPut(size) { mutableListOf() }.add(f)
                    }
                }
            }
        }

        // 3. Duplikat: hanya cek grup ukuran yang punya >1 file, lalu hash.
        val duplicates = mutableListOf<JunkItem>()
        for ((size, group) in bySize) {
            if (group.size < 2) continue
            val byHash = HashMap<String, MutableList<File>>()
            for (f in group) {
                val h = runCatching { hashFile(f) }.getOrNull() ?: continue
                byHash.getOrPut(h) { mutableListOf() }.add(f)
            }
            for ((_, same) in byHash) {
                if (same.size < 2) continue
                // Simpan yang pertama sebagai "asli", sisanya tandai duplikat.
                same.drop(1).forEach { f ->
                    duplicates.add(JunkItem(f.absolutePath, f.name, size, JunkCategory.DUPLICATE, false))
                }
            }
        }

        // 4. Rincian penyimpanan per aplikasi (cache/media/obb).
        val perApp = scanPerApp(onProgress)

        val groups = listOf(
            JunkGroup(JunkCategory.WHATSAPP_DB, whatsapp),
            JunkGroup(JunkCategory.APP_CACHE, perApp),
            JunkGroup(JunkCategory.TEMP_CACHE, temp),
            JunkGroup(JunkCategory.LOG, logs),
            JunkGroup(JunkCategory.THUMBNAIL, thumbs),
            JunkGroup(JunkCategory.APK, apks),
            JunkGroup(JunkCategory.LARGE_FILE, large),
            JunkGroup(JunkCategory.DUPLICATE, duplicates),
            JunkGroup(JunkCategory.EMPTY_FOLDER, empty)
        ).filter { it.items.isNotEmpty() }

        return ScanResult(groups)
    }

    private inline fun walk(
        root: File,
        depth: Int,
        maxDepth: Int,
        noinline onProgress: ((String) -> Unit)?,
        visit: (File) -> Unit
    ) {
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to depth)
        while (stack.isNotEmpty()) {
            val (dir, d) = stack.removeLast()
            onProgress?.invoke(dir.absolutePath)
            val children = dir.listFiles() ?: continue
            for (c in children) {
                // Lewati simlink & path sistem yang tak relevan.
                if (c.isDirectory) {
                    visit(c)
                    if (d < maxDepth) stack.addLast(c to (d + 1))
                } else {
                    visit(c)
                }
            }
        }
    }

    private fun hashFile(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        // Untuk file besar, hash sebagian (awal + ukuran) demi kecepatan.
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            var total = 0L
            while (read != -1 && total < HASH_CAP) {
                md.update(buffer, 0, read)
                total += read
                read = input.read(buffer)
            }
        }
        md.update(file.length().toString().toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LARGE_THRESHOLD = 100L * 1024 * 1024 // 100 MB
        private const val HASH_CAP = 8L * 1024 * 1024   // hash maksimal 8 MB per file
    }
}
