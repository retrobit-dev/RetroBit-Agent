# Audit hardening v0.8.1

Tidak ada target realistis “tidak ada perbaikan lagi selamanya”. Rilis ini
menutup temuan konkret pada batas berkas preview, pemulihan source, penulisan
JSON/source, respons timeout dan antrean operasi model. Lihat
[catatan perubahan dan kompatibilitas](releases/v0.8.1-preview.1.md).

## Matriks regresi

| Area | Bukti otomatis | Batas bukti |
|---|---|---|
| Preview path | JVM: encoding, literal +, meta/alias, traversal, symlink, origin per proyek | Bukan audit seluruh JavaScript proyek/CDN |
| LAN | Socket integration: GET/HEAD, MIME, 403/404/405/413, stop/restart | LAN bukan layanan publik terautentikasi; belum load test HP |
| Embedded WebView | Emulator release: Nimbus benar-benar merender konten halaman dari origin virtual | CSP/bridge belum pentest; renderer tergantung Android System WebView |
| Snapshot | ZIP rusak/truncated, limit, meta/duplikat/konflik, injected move failure, simulated interrupted/complete journal, source lock | Simulasi bukan power-cut/hardware failure test |
| Atomic writes | Replace normal + destination conflict; existing storage/chat/project suite | Tidak menjamin persist jika disk/media rusak; beberapa pemanggil lama masih best-effort |
| AI SSE | Timeout dilempar, streaming provider tests MockWebServer | Tidak membuktikan cloud live/key/quota/inferensi model nyata |
| APK inspector | Existing bounded archive + Robolectric + real installed APK API30 smoke | Bukan verifier signature penuh/scanner malware/decompiler |
| Model | Existing import/hash/rollback/transfer tests + native probe | Probe bukan inferensi dengan GGUF nyata |

## Proteksi operasional

- Preview publication menunggu test/build/signature/native checks serta smoke
  dan lint pada SHA source yang sama. Lint fatal/error atau laporan yang hilang
  menggagalkan job; tidak lagi ditutupi `|| true`/continue-on-error.
- Jangan menimpa tag rilis lama. Setelah lolos, gunakan APK signed prerelease
  baru. Simpan source/vault/database penting secara terpisah sebelum update.
- Uji ulang saat Android/System WebView/NDK/dependensi berubah; jangan mengklaim
  tes emulator API30 sama dengan validasi GT 30 Pro Android16.
- Pengujian OEM, inferensi GGUF nyata, soak/load test dan audit independen
  masih diperlukan. Compiler/build/sign APK hasil proyek belum tersedia.
