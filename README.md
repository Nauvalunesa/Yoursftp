# YoursFTP

Aplikasi Android (Kotlin + Jetpack Compose) untuk mengelola file di server **FTP / FTPS / SFTP**: menjelajah folder, upload/download, rename, hapus, buat folder/file, dan **mengedit file teks langsung** lalu menyimpannya kembali ke server.

## Fitur
- Simpan banyak profil koneksi (Room database)
- Protokol: FTP, FTPS (Commons Net), SFTP (JSch)
- Browser file: navigasi direktori, buat folder, buat file teks
- Operasi file: rename, hapus (file & folder)
- Editor teks built-in (monospace) — buka, edit, simpan ke server
- Material 3, mendukung mode gelap

## Struktur
```
app/src/main/java/com/yoursftp/app/
├─ data/          Room: Connection, RemoteFile, DAO, DB, Repository
├─ transfer/      FileClient + impl FTP/SFTP, factory, SessionManager
├─ ui/
│  ├─ screens/    Connections, EditConnection, Browser, Editor
│  ├─ theme/      Material 3 theme
│  └─ *ViewModel  state holders
├─ MainActivity   NavHost
└─ YoursFtpApp    Application (init repository)
```

## Build
Butuh **JDK 17** dan Android SDK (compileSdk 34).

### Cara 1 — Android Studio (paling mudah)
Buka folder ini di Android Studio (Koala+), biarkan Gradle sync, lalu Run.

### Cara 2 — Command line
Jika belum ada Gradle wrapper jar, generate dulu:
```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```
APK ada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan keamanan
- SFTP saat ini memakai `StrictHostKeyChecking=no` demi kemudahan. Untuk produksi, simpan & verifikasi host key (known_hosts).
- Password disimpan apa adanya di database lokal. Untuk produksi, pertimbangkan enkripsi (mis. SQLCipher / Android Keystore).
- `usesCleartextTraffic=true` diaktifkan agar FTP plaintext berfungsi.
