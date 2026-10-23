# Inspeksi APK di HP (v0.8 preview)

Buka **Menu → Inspeksi APK**. Pilih satu APK lewat pemilih dokumen Android,
atau tekan **Analisis APK RetroBit** untuk memeriksa base APK RetroBit yang
sedang terpasang. Tidak perlu komputer, API key atau server. Tidak ada izin
akses penyimpanan menyeluruh, QUERY_ALL_PACKAGES, atau izin memasang aplikasi.

## Yang diperiksa

- Ukuran dan SHA-256 seluruh berkas APK yang disalin.
- Package ID, versi/code, minimum/target SDK dan flag debuggable dari Android.
- Permission **diminta**, bukan permission yang sudah diberikan.
- Komponen activity/service/receiver/provider: nama, exported dan enabled.
  Exported tidak otomatis berarti rentan; intent-filter/kode tidak dianalisis.
- SHA-256 DER sertifikat signer yang **dilaporkan Android**, subject/issuer dan
  tanggal validitas, serta riwayat rotasi jika tersedia. Ini bukan implementasi
  apksigner, verifikasi kriptografi penuh APK, validasi chain kepercayaan,
  penilaian malware, Play Protect, atau bukti aplikasi aman/asli.
- Entry ZIP, ukuran terkompresi/deklarasi, jumlah classes*.dex di root,
  resource/asset, ABI library native dan keberadaan blok signing modern.
  Keberadaan blok signing tidak membuktikan tanda tangannya valid.

**Berkas** mencatat nama res/ dan resources.arsc, bukan merekonstruksi layout
XML/source Android asli. Tidak ada instalasi, eksekusi DEX/.so, pengambilan
source server, dekripsi, dekompilasi atau bypass proteksi. Split APK, XAPK,
APKS/APKM dan paket terproteksi tertentu tidak didukung sebagai satu aplikasi
lengkap. Android mungkin hanya melaporkan metadata base atau menolak arsip.

## Batas sumber daya

- File maksimum **256 MiB**, cadangan disk 32 MiB saat menyalin.
- ZIP maksimum 20.000 entry; central directory 16 MiB; nama 512 karakter,
  tanpa traversal/absolute path, karakter kontrol atau duplikat.
- Maksimum deklarasi isi 2 GiB; satu entry maksimum 256 MiB.
- Manifest maksimum 8 MiB, resources.arsc 32 MiB, setiap entry META-INF 4 MiB,
  total metadata yang dipindai 64 MiB; ukuran aktual dan CRC metadata dicek.
- Blok signing modern maksimum 8 MiB. ZIP64/multi-disk/offset tidak didukung
  ditolak sebelum ZipFile/PackageManager melakukan parsing utama.
- Tampilan dan JSON menyertakan maksimum **2.000 entry**, **500 permission**,
  **200 komponen**, dan maksimum 16 signer + 16 riwayat. Total tetap dicatat.
  Pencarian berkas hanya mencari pada 2.000 entry yang ditampilkan.

Batas ini mengurangi penggunaan sumber daya; bukan janji bahwa semua APK tak
tepercaya aman diparse. Pilih APK tepercaya dan gunakan Android yang diperbarui.

## Privasi, pembatalan dan laporan

APK disalin ke `noBackupFilesDir/apk-inspector` lalu dihapus dalam `finally`
pada sukses/gagal/pembatalan. Sisa `.partial.apk` setelah proses dimatikan
dibersihkan pada pembukaan aplikasi berikutnya. Berkas asli tidak ditulis atau
dihapus. Tidak ada upload ke AI/server dan tidak ada model yang dimuat oleh fitur
ini. Provider dokumen Android sendiri bisa mengambil file dari penyimpanan cloud
jika pengguna memilih file cloud.

**Batalkan inspeksi** bersifat kooperatif saat copy/scan; I/O provider dan
parsing Android yang sedang berjalan perlu selesai dahulu, bukan hard kill.
Tidak ada layanan background atau resume setelah proses mati.

**Salin ringkasan** menyalin metadata ringkas (bukan seluruh daftar entry) ke
clipboard sistem. **Simpan laporan JSON** menggunakan pemilih lokasi Android;
APK asli tidak ikut laporan. Ekspor JSON bisa parsial jika provider gagal/proses
mati. Laporan memuat identifier/fingerprint aplikasi—bagikan hanya jika sesuai.
Hapus laporan dari layar untuk melepas hasil yang masih berada di memori.

## Pengujian

JVM memeriksa ZIP/EOCD/signing block, path/duplikat, header binary manifest,
ukuran metadata, pembatasan daftar dan pembatalan scan. Robolectric memeriksa
copy/hash/temp cleanup/cancel dan pemetaan PackageInfo melalui reader palsu;
fixture manifest pada tes tersebut bukan APK nyata. Smoke emulator memakai
**base APK release RetroBit yang benar-benar terpasang**, Android PackageManager,
dan UI signer. Belum diuji langsung pada Infinix GT 30 Pro/Android 16.

## Bukan pembuat APK hasil proyek

Fitur ini tidak menambah compiler/build/signing proyek. Preview proyek masih
HTML/CSS/JS dan ekspor ZIP source. APK RetroBit sendiri tetap dibangun lewat CI.
