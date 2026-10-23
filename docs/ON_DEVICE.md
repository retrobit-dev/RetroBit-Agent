# AI offline di HP — Chat, agen proyek dan GGUF

RetroBit tetap aplikasi Android native Kotlin/Compose. Provider **AI di HP
(offline)** memanggil llama.cpp melalui JNI, tidak membuka server lokal,
tidak menggunakan Termux, dan tidak mengirim prompt/model ke layanan cloud.
Auto tidak diam-diam memindahkan percakapan offline ke cloud.

## Menyiapkan dari HP saja

1. Buka **AI Cloud → AI di HP (offline)**. Tekan **Periksa mesin** untuk
   memuat pustaka native dan memeriksa grammar tool, tanpa mengunduh model.
2. Tekan **Pakai rekomendasi Qwen 1.5B Q4** (atau masukkan URL HTTPS GGUF
   satu berkas tepercaya). Isi SHA-256 penerbit jika tersedia, lalu tekan
   **Download & impor GGUF**. Gunakan Wi-Fi dan biarkan aplikasi terbuka.
   [Halaman model resmi Qwen](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/tree/main).
   Model tidak disertakan APK; periksa lisensinya. Ada batas 3 GiB dan cadangan
   disk 64 MiB. Safetensors, ZIP, APK, dan model terpecah tidak didukung.
3. Jika sudah punya berkas dari browser HP, pilih **Impor GGUF/Ganti model GGUF**
   dan pilih dari Downloads. Aplikasi menyalin ke penyimpanan privat; siapkan
   ruang untuk berkas asli dan salinannya. Tidak perlu komputer/Termux.
4. Pilih **Setelan agen: 4096 / 1024 token**, tekan **Simpan**, lalu **Uji model**
   untuk memuat GGUF dan meminta jawaban pendek. Untuk Chat saja, konteks 2048
   dan keluaran 256–512 lebih ringan. Pemuatan pertama bisa lama.
5. Untuk obrolan biasa, buka tab **Chat**. Untuk membangun/mengedit proyek,
   buat proyek/template di Beranda lalu gunakan **Chat di dalam proyek**.
   Menu proyek → **Tool agen** berisi katalog 15 tool. Setelah model tersedia,
   inferensi dan tool lokal tidak memerlukan internet/API key/komputer.

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
- Satu inferensi aktif; model dapat ditransfer ke file parsial saat inferensi
  berjalan, tetapi penggantian/hapus menunggu inferensi selesai. KV cache dan
  model yang dimuat dilepas sesudah
  permintaan selesai/gagal/dibatalkan; permintaan berikutnya memuat ulang.
- Impor memeriksa magic/version dan ukuran. Itu **bukan** validasi penuh GGUF:
  keberhasilan impor tidak menjamin arsitektur/quantization/model muat di RAM.
  Impor hanya model tepercaya. File yang salah/rusak dibatalkan; salinan lama
  tetap ada jika proses salin gagal. File asli tidak dihapus oleh aplikasi.
- Model disimpan di `noBackupFilesDir`, tidak masuk backup Android/ZIP proyek.
  Hapus model dari panel bila tidak digunakan. Hapus aplikasi menghapus salinan.
- Chat dan tool agen proyek offline tersedia (lihat bagian v0.7 di bawah).
  Inspeksi APK dan reverse engineering universal/bypass proteksi tidak diklaim.
  Cloud dan Demo yang ada tetap terpisah.

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
MockWebServer untuk cloud dan download/redirect/error GGUF. Robolectric memeriksa
impor/checksum/ukuran, penggantian berkas, tool SQLite, serta AgentEngine →
LocalInference fake backend → parser → tool file sungguhan. C++ bridge diperiksa
sintaks terhadap header upstream. Grammar native diuji dengan 12 acceptance case
tanpa weights. Smoke release emulator memuat pustaka native dan memeriksa grammar
melalui **Periksa mesin**, lalu memotret katalog tool dan kontrol download.
Pengujian tersebut tidak membuktikan kualitas/kecepatan inferensi model nyata
di Infinix. Model besar tidak disimpan di Git atau diunduh otomatis oleh CI.

## v0.7: tool proyek dan download langsung

**Download** tersedia di panel AI di HP: pilih URL rekomendasi Qwen atau masukkan
URL HTTPS langsung ke satu berkas GGUF publik. SHA-256 penerbit opsional;
bila diberikan harus cocok. SHA-256 salinan selalu dihitung dan ditampilkan.
Download besar memakai kuota internet. Tetap buka aplikasi: belum ada resume
atau layanan background. Pembatalan saat menunggu body jaringan dapat menunggu
read-timeout (20 detik). Retry dimulai dari awal, bukan menambahkan ke parsial.

HTTP selain 200, redirect ke HTTP/server lokal, lebih dari 5 redirect, ukuran
lebih dari 3 GiB, header salah, transfer terpotong, atau checksum salah tidak
mengganti model aktif. File parsial dibersihkan pada kegagalan/pembatalan atau
startup berikutnya. Model lama tetap bisa digunakan selama transfer; penggantian
menunggu inferensi aktif selesai. Hanya satu slot model aktif: download/impor
berhasil mengganti salinan sebelumnya. Header + HTTPS tanpa hash penerbit bukan
jaminan keaslian model; pilih penerbit tepercaya.

**Agen offline** kini diaktifkan untuk workspace proyek. Buat proyek/template
dari Beranda, pilih provider AI di HP dan simpan, lalu kirim tugas dari Chat
proyek (bukan Chat biasa). Menu proyek → **Tool agen** menampilkan katalog:

- `project_info`, `list_files`, `search_files`, `read_file`;
- `write_file`, `append_file`, `edit_file`, `rename_file`, `delete_file`;
- `update_design` (grayscale/Pixel Operator);
- `create_table`, `run_sql`, `inspect_database`;
- `request_secret` (permintaan/placeholder saja, bukan nilai rahasia);
- `validate_project` (entry point + JSON, bukan compiler atau tes runtime).

Default konfigurasi baru adalah konteks 4096; konfigurasi lama tidak ditimpa.
Tombol **Setelan agen: 4096 / 1024 token** menyiapkan batas yang disarankan
(dan masih perlu Simpan). Pesan/persona yang panjang tetap bisa melampaui batas.

Backend menerapkan grammar GBNF untuk satu envelope JSON per giliran. Envelope
lengkap harus lolos whitelist nama tool dan skema argumen sebelum dieksekusi.
JSON terpotong/fenced/prosa tidak diambil paksa menjadi panggilan tool. File dan
hasil tool adalah data, bukan pesan system. Argumen yang valid tetap tidak
menjamin model mengambil keputusan yang benar: periksa hasil perubahan.

Maksimum 14 langkah/sesi; disarankan konteks 4096 dan tugas kecil. Konteks agen
mempertahankan permintaan saat ini serta dua siklus tool terakhir, bukan seluruh
riwayat. Argumen tool lama dihilangkan secara eksplisit; baca ulang file jika
perlu. Pesan persona pengguna tetap dipertahankan. Berkas dibatasi 400KB/tool.
Snapshot **file** dibuat sebelum sesi, bisa dipulihkan lewat menu proyek;
snapshot tersebut tidak mencakup database. Gagal/dibatalkan tidak otomatis
membatalkan tool yang sudah selesai—lihat daftar langkah dan pulihkan jika perlu.

Path dibatasi ke proyek, termasuk alias root/_meta dan symlink yang keluar.
SQL tool menerima satu statement; ATTACH/DETACH/PRAGMA/VACUUM/extension loading
serta SQL rekursif diblokir. Tidak ada tool shell, root, instalasi diam-diam,
atau build APK. Source Kotlin/Gradle bisa ditulis/diekspor, tetapi pipeline
kompilasi/signing APK hasil proyek tetap belum terintegrasi.
