# YoursFTP 🌐📱

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Gradle](https://img.shields.io/badge/Build-Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org)

**YoursFTP** adalah aplikasi Android tangguh berbasis **Kotlin + Jetpack Compose** dan **Material 3** untuk mengelola server remote Anda dengan mudah. Tidak hanya sekadar browser file FTP/SFTP biasa, YoursFTP dilengkapi dengan berbagai alat bantu pengembang (*Developer Tools*) terintegrasi yang premium seperti **Terminal Emulator interaktif dengan pintasan perintah**, **Editor Teks dengan status bar VSCode**, dan **Penampil Database SQL & NoSQL multi-format dengan pencarian instan**.

---

## 🚀 Fitur Utama

### 1. Manajemen Berkas & Multi-Protokol
* Dukungan koneksi lengkap untuk berbagai protokol industri:
  * **SFTP** (via JSch)
  * **FTP & FTPS** (via Apache Commons Net)
  * **Amazon S3** (Penyimpanan Objek Cloud)
  * **Local File System** (Penyimpanan Lokal Android)
* Manajemen profil koneksi tanpa batas menggunakan database **Room DB** lokal yang aman.
* Operasi file standar: navigasi direktori cepat, buat berkas/folder baru, ganti nama (*rename*), dan hapus (*delete*).
* **Salin Path Folder/File:** Salin alamat absolut folder aktif atau path file individu ke clipboard dalam satu kali ketuk.
* **Detail File Lengkap:** Tampilkan info detail file (ukuran bytes/human-readable, path absolut, modifikasi terakhir).

### 2. Uji Koneksi Instan (Test Connection)
* Validasi host, port, username, password, atau SSH Private Key Anda langsung sebelum disimpan.
* Memberikan status indikasi real-time berupa banner status (Warna hijau sukses terhubung atau warna merah jika gagal beserta detail pesan kesalahannya).

### 3. Terminal Emulator Interaktif dengan Pintasan Perintah (VT100/xterm)
* Jalankan perintah shell langsung di server remote Anda.
* Mendukung gamut warna penuh: **16-color, 256-color, hingga Truecolor (24-bit)**.
* Mendukung mode tampilan alternatif (alternate screen buffer) untuk aplikasi berbasis terminal seperti `vim`, `htop`, atau `less`.
* Memiliki parser state-machine tangguh dengan riwayat scrollback hingga **5000 baris**.
* **Pintasan Perintah (Quick Commands Snippets):** Eksekusi perintah linux umum (`ls -la`, `df -h`, `free -m`, `top`, dll.) instan dengan sekali tap.
* **Snippets Kustom:** Tambahkan perintah favorit Anda sendiri ke dalam daftar pintasan terminal untuk menghemat waktu mengetik di mobile keyboard.

### 4. Editor Teks Lanjutan (*Large File Editor*)
* Buka, edit, dan simpan kembali berkas teks berukuran besar secara instan ke server remote.
* Dilengkapi dengan **Syntax Highlighting** otomatis untuk berbagai bahasa pemrograman:
  * Kotlin, Java, Python, C/C++, Go, JavaScript, TypeScript, HTML, CSS, XML, JSON, CSV, Shell Script (`.sh`), YAML, dan konfigurasi lainnya.
* **IDE-Style Status Bar:** Tampilan status bar di bagian bawah editor yang menunjukkan **Jumlah Baris**, **Jumlah Karakter**, dan **Ekstensi Dokumen** secara dinamis (seperti VSCode).

### 5. Penampil Database Multi-Format (SQL & NoSQL)
Membaca berbagai format database langsung dari browser berkas tanpa aplikasi pihak ketiga:
* **SQLite (`.db`, `.sqlite`, `.sqlite3`, `.db3`, `.sqlitedb`)**: Menampilkan daftar tabel, baris data secara paginasi, dan eksekusi query SQL kustom penuh.
* **SQL Dump (`.sql`)**: Secara cerdas memuat isi dump SQL ke dalam database SQLite *in-memory* secara dinamis.
* **JSON / NoSQL (`.json`, `.jsonl`, `.ndjson`, `.geojson`)**: Ekstraksi struktur baris-kolom otomatis dari JSON array, nested arrays, atau skema key-value global.
* **CSV / TSV / BSON / XML**: Membaca CSV presisi, file biner BSON terstruktur, dan parsing XML otomatis.
* **Pencarian & Filter Instan:** Cari kata kunci apapun di dalam tabel yang aktif; baris akan disaring secara instan seiring Anda mengetik.
* **Skalabilitas Grid & Zoom Kolom:** Sesuaikan lebar kolom (+/-) dan tap sel manapun untuk melihat detail teks penuh serta menyalinnya.

### 6. Pembaruan OTA (Over-The-Air) Otomatis via GitHub
* Pemeriksaan versi latar belakang otomatis saat startup ke repositori GitHub.
* Mengunduh file APK rilis resmi terbaru secara otomatis ke cache direktori dan memicu sistem instalasi Android internal secara aman.

---

## 📐 Arsitektur Sistem

Aplikasi ini menggunakan pola arsitektur **MVVM (Model-View-ViewModel)** dengan alur data satu arah (*Unidirectional Data Flow*) yang clean:

```mermaid
graph TD
    UI["MainActivity (NavHost)"]
    UI --> ConnScr["ConnectionsScreen"]
    UI --> EditConnScr["EditConnectionScreen"]
    UI --> BrowserScr["BrowserScreen"]
    UI --> EditorScr["EditorScreen"]
    UI --> TermScr["TerminalScreen"]
    UI --> DbViewerScr["DbViewerScreen"]

    BrowserScr --> BrowserVM["BrowserViewModel"]
    EditorScr --> EditorVM["LargeEditorViewModel"]
    TermScr --> TermVM["TerminalViewModel"]
    DbViewerScr --> DbVM["DbViewModel"]

    BrowserVM --> SessionMgr["SessionManager"]
    SessionMgr --> FileClientFactory["FileClientFactory"]
    FileClientFactory --> SftpClient["SftpFileClient"]
    FileClientFactory --> FtpClient["FtpFileClient"]
    FileClientFactory --> S3Client["S3FileClient"]
    FileClientFactory --> LocalClient["LocalFileClient"]

    ConnScr --> ConnVM["ConnectionViewModel"]
    ConnVM --> Repo["ConnectionRepository"]
    Repo --> DAO["ConnectionDao"]
    DAO --> RoomDB["AppDatabase (Room DB)"]
```

---

## 📂 Struktur Direktori Proyek

```text
app/src/main/java/com/yoursftp/app/
├── MainActivity.kt                 # Navigasi & Entry Point UI utama
├── YoursFtpApp.kt                  # Inisialisasi Database Room & Aplikasi
├── data/                           # Layer Data (Room DB)
│   ├── AppDatabase.kt              # Room Database utama
│   ├── Connection.kt               # Model entitas profil koneksi
│   ├── ConnectionDao.kt            # Data Access Object untuk Room
│   ├── ConnectionRepository.kt     # Repositori akses data koneksi
│   ├── Protocol.kt                 # Enum protokol yang didukung
│   └── RemoteFile.kt               # Representasi berkas remote/lokal
├── transfer/                       # Logika Transfer Berkas & Klien
│   ├── FileClient.kt               # Antarmuka klien transfer
│   ├── FileClientFactory.kt        # Factory pembuat instance protokol
│   ├── FtpFileClient.kt            # Implementasi klien FTP & FTPS
│   ├── LocalFileClient.kt          # Klien untuk File System lokal
│   ├── S3FileClient.kt             # Klien penyimpanan objek AWS S3
│   ├── SftpFileClient.kt           # Klien protokol SFTP (via JSch)
│   ├── SessionManager.kt           # Pengelola sesi aktif
│   └── PathUtils.kt                # Utilitas pemrosesan path direktori
├── terminal/                       # Emulator Terminal
│   └── TerminalEmulator.kt         # Logic emulator terminal VT100/xterm
├── editor/                         # Editor Teks Lanjutan
│   ├── LargeEditorScreen.kt        # UI Komponen Editor Teks
│   ├── LargeEditorViewModel.kt     # VM pengelolaan dokumen teks besar
│   ├── SyntaxHighlight.kt          # Algoritma pewarnaan kode otomatis
│   └── TextDocument.kt             # Model representasi baris dokumen
├── db/                             # Fitur Penampil Database
│   ├── DbReader.kt                 # Abstraksi parser format DB
│   ├── SqliteReader.kt             # Pembaca SQLite
│   ├── JsonDbReader.kt             # Pembaca JSON & BSON
│   ├── CsvDbReader.kt              # Pembaca CSV & TSV
│   ├── SqlDumpReader.kt            # Pembaca berkas SQL Dump
│   ├── XmlDbReader.kt              # Pembaca berkas XML
│   ├── DbViewModel.kt              # ViewModel penampil database
│   └── DbViewerScreen.kt           # Layar tabel database Material 3
├── ota/                            # Pembaruan OTA (Over-The-Air)
│   ├── OtaUpdater.kt               # Logic parser rilis API GitHub & Download
│   └── OtaViewModel.kt             # VM status pembaruan
└── ui/                             # UI Global
    ├── screens/                    # Layar UI (Browser, Connections, Terminal)
    ├── theme/                      # Skema Warna & Tema Material 3
    └── *ViewModel                  # ViewModel untuk UI global
```

---

## 🛠️ Cara Membangun (Build) Proyek

Proyek ini menggunakan Gradle modern dan memerlukan setidaknya **JDK 17** serta **Android SDK (compileSdk 34)**.

### Metode 1: Menggunakan Android Studio (Sangat Direkomendasikan)
1. Buka Android Studio (versi Ladybug / Koala ke atas).
2. Pilih opsi **Open** dan arahkan ke folder root repositori ini.
3. Biarkan Android Studio menyelesaikan sinkronisasi Gradle (*Gradle Sync*).
4. Hubungkan perangkat Android fisik (lewat USB/Wi-Fi Debugging) atau gunakan Emulator.
5. Klik ikon **Run / Play** untuk memasang aplikasi.

### Metode 2: Menggunakan Terminal Command Line
Jika Anda ingin mengompilasi APK langsung dari terminal, jalankan perintah berikut:
```bash
# Membuat Gradle Wrapper jika belum ada
gradle wrapper --gradle-version 8.9

# Mengompilasi aplikasi ke format Debug APK
./gradlew assembleDebug
```
Setelah proses selesai, berkas APK terbaru dapat Anda temukan di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚠️ Catatan Keamanan & Batasan Penting
* **Koneksi SFTP**: Menggunakan konfigurasi `StrictHostKeyChecking=no` untuk mempercepat demonstrasi. Di lingkungan produksi, Anda disarankan untuk mendaftarkan *known hosts* demi mencegah serangan Man-in-the-Middle (MitM).
* **Penyimpanan Kredensial**: Password disimpan dalam database Room lokal. Untuk keamanan produksi, disarankan menggunakan database terenkripsi seperti **SQLCipher** atau memanfaatkan **Android Keystore**.
* **Keamanan Jaringan**: `usesCleartextTraffic=true` diaktifkan di manifest agar koneksi FTP standar (tanpa enkripsi SSL/TLS) tetap dapat diakses di Android versi modern.

---

## 💬 Hubungi & Dukung Kami
Jika Anda membutuhkan bantuan, ingin melaporkan masalah, atau ingin memberikan dukungan donasi untuk pengembangan lebih lanjut:
* **WhatsApp:** [Hubungi via WhatsApp (081776348790)](https://wa.me/6281776348790)
* **Telegram:** [Hubungi via Telegram (Nvlunesa)](https://t.me/Nvlunesa)
* **Link Donasi & Support:** [Mustika Payment Donation Link](https://mustikapayment.com/l/Payment)

*YoursFTP dikembangkan dengan cinta untuk komunitas pengembang!* ❤️
