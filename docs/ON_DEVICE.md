# AI offline di HP — tahap pertama

RetroBit tetap aplikasi Android native Kotlin/Compose. Provider **AI di HP
(offline)** memanggil llama.cpp melalui JNI, tidak membuka server lokal,
tidak menggunakan Termux, dan tidak mengirim prompt/model ke layanan cloud.
Auto tidak diam-diam memindahkan percakapan offline ke cloud.

## Menyiapkan dari HP saja

1. Unduh **model Instruct GGUF satu berkas** dari penerbit tepercaya melalui
   browser HP. Contoh awal: Qwen2.5-Coder-1.5B-Instruct dengan kuantisasi Q4_K_M.
   [Halaman model resmi Qwen](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/tree/main):
   pilih `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf` melalui browser HP.
   Perhatikan lisensi model. Model tidak termasuk dalam APK. Safetensors,
   model ZIP, model terpecah, dan file APK bukan berkas yang bisa diimpor.
2. Di AI Cloud, pilih **AI di HP (offline)**. Tekan **Periksa mesin** untuk
   memastikan pustaka native dapat dimuat. Ini belum menguji model.
3. Tekan **Impor GGUF**, pilih berkas di Downloads. Biarkan aplikasi terbuka.
   Model disalin ke penyimpanan privat; siapkan ruang untuk berkas asli dan
   salinannya. Ada batas impor 3 GiB dan cadangan ruang disk 64 MiB.
4. Mulai dengan konteks 2048 dan jawaban 512 token. **Simpan**, kemudian
   **Uji model** untuk memuat GGUF dan meminta jawaban pendek.
5. Buka tab **Chat**, buat obrolan, lalu kirim pesan. Setelah impor, chat bisa
   digunakan tanpa koneksi internet, API key, laptop atau komputer lain.

Untuk Infinix GT 30 Pro dengan 12 GB RAM fisik: model 1.5B Q4 adalah titik
awal, bukan benchmark yang sudah dibuktikan pada perangkat. Model 3B Q4 bisa
dicoba jika ukuran berkas sesuai. Extended RAM tidak menggantikan RAM fisik.
Mulai dengan konteks rendah; konteks, buffer kerja, dan Android ikut memakai RAM.
Model-format/arsitektur baru setelah versi backend yang dipin dapat ditolak.

## Perilaku dan batasan

- CPU saja, maksimum 4 thread. Tidak ada janji NPU/GPU MediaTek dipakai.
- Maksimum konteks 4096 (termasuk prompt dan jawaban), maksimum jawaban 1024.
  Percakapan yang terlalu panjang ditolak dengan pesan mulai chat baru;
  riwayat tidak diam-diam dipotong oleh backend.
- Streaming mempertahankan karakter UTF-8 walau token memotong emoji.
- Berhenti membatalkan di antara batch prefill/decode. Pemuatan model atau
  batch native yang sedang berjalan perlu selesai dahulu. Itu bukan hard kill.
- Satu inferensi/impor/hapus sekaligus. Model dan KV cache dilepas sesudah
  permintaan selesai/gagal/dibatalkan; permintaan berikutnya memuat ulang.
- Impor memeriksa magic/version dan ukuran. Itu **bukan** validasi penuh GGUF:
  keberhasilan impor tidak menjamin arsitektur/quantization/model muat di RAM.
  Impor hanya model tepercaya. File yang salah/rusak dibatalkan; salinan lama
  tetap ada jika proses salin gagal. File asli tidak dihapus oleh aplikasi.
- Model disimpan di `noBackupFilesDir`, tidak masuk backup Android/ZIP proyek.
  Hapus model dari panel bila tidak digunakan. Hapus aplikasi menghapus salinan.
- Chat biasa sudah tersedia. Eksekusi tool agen proyek dan inspeksi APK belum
  diaktifkan untuk provider offline ini. Tidak ada klaim reverse engineering
  universal atau bypass proteksi. Cloud dan Demo yang ada tetap terpisah.

## Apakah hasil aplikasi dapat langsung diinstal?

APK RetroBit sendiri bisa diinstal di HP. Tetapi **preview proyek saat ini
adalah HTML/CSS/JS**, dan **Ekspor kode ZIP bukan build APK**. Mengekspor HTML
sebagai ZIP tidak mengubahnya menjadi aplikasi Android native.

Aplikasi baru yang bisa diinstal memerlukan source Android yang sesuai,
Android SDK/compiler, proses build, penandatanganan, lalu persetujuan pengguna
pada installer Android. Mesin AI tidak menggantikan compiler. HP tidak perlu
menjadi mesin build: CI GitHub Actions dapat mengompilasi secara remote, lalu
APK diunduh lewat browser HP. Alur build aplikasi *hasil proyek* belum
terintegrasi di RetroBit. Workflow CI repositori ini membangun **RetroBit**,
bukan proyek pengguna. Kita tidak menjanjikan tombol "buat → pasang" yang
belum ada atau pemasangan diam-diam tanpa persetujuan pengguna.

## Pengujian

JVM tests memakai backend palsu untuk lifetime/cancellation/streaming dan
MockWebServer untuk cloud yang lama. Robolectric memeriksa impor dan kegagalan
penggantian berkas. C++ bridge juga diperiksa sintaks terhadap header upstream.
Smoke release emulator memuat pustaka native melalui **Periksa mesin**.
Pengujian tersebut tidak membuktikan kualitas/kecepatan inferensi model nyata
di Infinix. Model besar tidak disimpan di Git atau diunduh otomatis oleh CI.
