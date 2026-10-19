# Kunci penandatangan (demo)

`retrobit-release.p12` adalah kunci **contoh** yang ikut di-commit supaya APK
rilis dari GitHub Actions punya tanda tangan yang stabil — artinya versi baru
bisa dipasang sebagai *update* tanpa harus menghapus aplikasi lebih dulu.

> **Peringatan keamanan.** Password-nya publik (lihat `keystore.properties`).
> Siapa pun bisa membuat APK yang ditandatangani dengan kunci ini dan diterima
> Android sebagai update untuk `dev.retrobit.agent`. Jangan pakai kunci ini
> untuk aplikasi yang serius.

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
