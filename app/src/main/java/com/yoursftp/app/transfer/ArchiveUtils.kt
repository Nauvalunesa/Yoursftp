package com.yoursftp.app.transfer

import java.io.*
import java.util.zip.*

object ArchiveUtils {

    /** Kompres daftar file/folder (dari [sourceBaseDir]) ke [zipFile]. */
    fun zip(sourceFiles: List<File>, sourceBaseDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            for (file in sourceFiles) {
                zipRecursive(file, sourceBaseDir, zos)
            }
        }
    }

    private fun zipRecursive(fileToZip: File, baseDir: File, zos: ZipOutputStream) {
        val entryName = fileToZip.absolutePath.substring(baseDir.absolutePath.length).removePrefix("/")
        if (fileToZip.isDirectory) {
            val entry = ZipEntry(if (entryName.endsWith("/")) entryName else "$entryName/")
            zos.putNextEntry(entry)
            zos.closeEntry()
            fileToZip.listFiles()?.forEach { child ->
                zipRecursive(child, baseDir, zos)
            }
        } else {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(fileToZip).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    /** Ekstrak [zipFile] ke dalam [destDir]. */
    fun unzip(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                // Cegah Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw SecurityException("Zip Slip terdeteksi pada ${entry.name}")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
