# Kunci penandatangan (demo)

`retrobit-release.p12` adalah kunci **contoh** yang ikut di-commit supaya APK
rilis dari GitHub Actions punya tanda tangan yang stabil — artinya versi baru
bisa dipasang sebagai *update* tanpa harus menghapus aplikasi lebih dulu.

> **Peringatan keamanan.** Password-nya publik (lihat `keystore.properties`).
> Siapa pun bisa membuat APK yang ditandatangani dengan kunci ini dan diterima
> Android sebagai update untuk `dev.retrobit.agent`. Jangan pakai kunci ini
> untuk aplikasi yang serius.

## Rotasi cepat

```bash
bash scripts/rotate-keystore.sh
```

Script itu membuat keystore 4096-bit **di luar repo** (`~/retrobit-signing`),
menulis `keystore.b64`, dan mencetak empat perintah `gh secret set` yang harus
dijalankan sendiri — karena rotasi hanya berguna kalau kunci baru tidak ikut
di-commit.

> **Kenapa belum dilakukan otomatis di sini.** Token di lingkungan kerja ini
> tidak punya izin `secrets: write` (`gh secret set` → 403). Mengganti file di
> `signing/` dengan kunci baru hanya akan memindahkan masalah: kuncinya tetap
> publik, tapi semua APK lama jadi tidak bisa di-update. Jadi kunci lama
> dibiarkan sampai secret benar-benar bisa dipasang.

## Cara rotasi tanpa akses `gh` (lewat browser)

Kalau `gh secret set` ditolak 403, unggah manual — hasilnya sama, karena secret
repository hanya bisa ditulis oleh pemilik repo, bukan oleh token aplikasi.

**1. Buat kunci (di komputer sendiri, butuh JDK 17).**

```bash
git clone https://github.com/retrobit-dev/RetroBit-Agent.git
cd RetroBit-Agent
bash scripts/rotate-keystore.sh
```

Script mencetak lima hal dan berhenti: path keystore, path `keystore.b64`,
password keystore, password kunci, dan alias. Simpan password di tempat aman —
tanpa itu APK berikutnya tidak bisa memperbarui yang sudah terpasang.

**2. Unggah empat secret.**

Buka `Settings → Secrets and variables → Actions → New repository secret` di
repo, lalu buat satu per satu dengan nama **persis** seperti ini:

| Nama secret | Nilainya |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | seluruh isi `~/retrobit-signing/keystore.b64` (satu baris panjang) |
| `RELEASE_KEYSTORE_PASSWORD` | password keystore dari script |
| `RELEASE_KEY_ALIAS` | `retrobit` |
| `RELEASE_KEY_PASSWORD` | password kunci dari script |

Untuk menyalin base64: `pbcopy < ~/retrobit-signing/keystore.b64` (macOS),
`xclip -sel clip < ~/retrobit-signing/keystore.b64` (Linux), atau
`type %USERPROFILE%\retrobit-signing\keystore.b64` (Windows).

**3. Picu build dan periksa.**

Jalankan ulang workflow (Actions → Android CI → Run workflow, atau push apa
pun). Dua baris di log menjawab apakah rotasi berhasil:

```
Signing: repository secret (RELEASE_KEYSTORE_BASE64 set)
Signer #1 certificate SHA-256 digest: <sidik jari baru>
```

Bandingkan sidik jari itu dengan APK lama — kalau berbeda, kunci baru sudah
dipakai.

**4. Setelah yakin, buang kunci demo dari git.**

```bash
git rm signing/retrobit-release.p12 signing/keystore.properties
git commit -m "Remove the public demo signing key"
```

Jangan lakukan ini sebelum langkah 3 hijau: tanpa secret dan tanpa file demo,
CI tidak punya kunci sama sekali dan APK rilis jadi tidak bertanda tangan.

> **Peringatan sekali lagi.** Kunci baru berarti identitas baru. APK yang sudah
> terpasang dengan kunci demo tidak bisa di-update dan harus di-uninstall dulu
> (`adb uninstall dev.retrobit.agent`), jadi umumkan sebelum rotasi.

## Mengganti dengan kunci sendiri

Lokal (Android Studio):

```bash
keytool -genkeypair -v -keystore my-release.jks -alias retrobit \
  -keyalg RSA -keysize 2048 -validity 10000
```

lalu arahkan `signing/keystore.properties` ke file itu (dan tambahkan
`my-release.jks` ke `.gitignore`).

Di CI, workflow memakai env var bila tersedia:

| Env | Isi |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | isi keystore (JKS/PKCS12) yang di-base64 |
| `RELEASE_KEYSTORE_PASSWORD` | password keystore |
| `RELEASE_KEY_ALIAS` | alias kunci |
| `RELEASE_KEY_PASSWORD` | password kunci |

Tambahkan sebagai **Repository secrets**; workflow akan mendekode ke
`$RUNNER_TEMP` dan mengabaikan file demo di atas.
