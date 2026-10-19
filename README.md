# RetroBit Agent

Aplikasi Android native (Kotlin + Jetpack Compose) untuk membangun aplikasi web kecil langsung dari HP: minta lewat chat, AI menulis file, lalu lihat hasilnya di preview. Terinspirasi alur kerja [rork.com](https://rork.com), tetapi berjalan **sepenuhnya di perangkat** — tanpa server perantara.

<p align="left">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12-4285F4?logo=jetpackcompose&logoColor=white" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/license-belum%20ditentukan-lightgrey" />
</p>

## Fitur

| Area | Yang bisa dilakukan |
| --- | --- |
| **Preview aplikasi** | WebView lokal dengan chrome HP/tablet/desktop, reload otomatis saat file berubah, console log, dan preview LAN untuk dibuka dari browser lain di jaringan yang sama. |
| **Design** | Design system berisi token warna, radius, dan font. Preset siap pakai (midnight, aurora, sunset, candy, mono, terminal) dan editor token manual. Perubahan langsung diterapkan ke preview tanpa reload. |
| **AI Cloud** | Hubungkan OpenRouter, OpenAI, Anthropic, Gemini, Groq, DeepSeek, Mistral, Together AI, Ollama lokal, atau endpoint OpenAI-compatible sendiri. Ada uji koneksi, pemilih model, dan estimasi pemakaian token/biaya. |
| **Secret** | Vault terenkripsi (AES-GCM + Android Keystore) untuk API key. Secret hanya masuk ke preview bila kamu centang "expose", dan AI hanya tahu **nama** secret — bukan nilainya. |
| **Database** | SQLite per proyek: buat tabel, tambah kolom, isi data contoh, jalankan query, lihat hasil. Kode di preview bisa mengaksesnya lewat `window.RetroBitDB`. |
| **Chat / Agent** | Agen dengan 11 tool (baca/tulis/edit/hapus file, rename, update design, tabel & SQL, minta secret). Setiap perintah AI otomatis membuat snapshot yang bisa dipulihkan. |
| **Template** | 7 starter: SaaS (Nimbus), tugas (Flow), toko online (Lumen), kesehatan (Pulse), chat (Relay), portofolio (Atelier), dan kanvas kosong. |
| **Ekspor** | Bagikan proyek sebagai ZIP lewat FileProvider (file `_meta` tidak ikut). |

Mode **Demo** aktif secara default sehingga seluruh aplikasi bisa dicoba tanpa API key: agen demo memilih template, tema, dan data contoh dari kata kunci permintaanmu.

## Persyaratan

- Android Studio **Ladybug (2024.2.1)** atau lebih baru — Kotlin 2.0 dan AGP 8.7.
- JDK 17 (JDK bawaan Android Studio sudah cukup).
- Android SDK Platform 35 + Build-Tools 35.
- Perangkat/emulator **Android 8.0 (API 26)** ke atas.
- Gradle 8.9 (langsung diunduh oleh wrapper — `gradlew`, `gradlew.bat`, dan `gradle/wrapper/gradle-wrapper.jar` sudah disertakan).

## Menjalankan

1. Buka folder root proyek ini di Android Studio (`File → Open`).
2. Tunggu Gradle Sync selesai. Jika diminta mengatur SDK, arahkan ke Android SDK Platform 35.
3. Jalankan konfigurasi `app` pada emulator atau perangkat fisik.

Atau lewat terminal:

```bash
./gradlew assembleDebug        # APK di app/build/outputs/apk/debug/
./gradlew installDebug         # pasang ke perangkat yang terhubung
```

> Build pertama membutuhkan internet untuk mengunduh Gradle dan dependensi Maven.

## Alur pertama kali

1. Aplikasi membuka tab **Proyek** dan otomatis membuat proyek contoh **Nimbus** dari template SaaS.
2. Ketuk proyek → masuk **Workspace** dengan enam tab: Chat, Preview, Kode, Design, Data, Vault.
3. Di tab Chat, minta sesuatu — misalnya *"buat aplikasi kebiasaan dengan grafik mingguan"*.
4. Tab **Preview** menampilkan hasilnya; tab **Design** untuk mengubah tema; tab **Vault** untuk mengisi secret.
5. Untuk memakai model sungguhan, buka **AI Cloud** → pilih provider → tempel API key → **Uji koneksi** → **Simpan**.

## Menghubungkan AI

| Provider | Base URL default | Contoh model |
| --- | --- | --- |
| Demo lokal | — | `retrobit-demo` (tanpa key) |
| OpenRouter | `https://openrouter.ai/api/v1` | `anthropic/claude-sonnet-4.5`, `qwen/qwen3-coder` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4.1`, `gpt-4.1-mini` |
| Anthropic | `https://api.anthropic.com` | `claude-sonnet-4-5` |
| Google Gemini | `https://generativelanguage.googleapis.com` | `gemini-2.5-flash` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| Mistral | `https://api.mistral.ai/v1` | `mistral-large-latest` |
| Together AI | `https://api.together.xyz/v1` | `Qwen/Qwen3-Coder-480B-A35B-Instruct` |
| Ollama / lokal | `http://10.0.2.2:11434/v1` | `qwen2.5-coder:7b` |
| OpenAI-compatible | URL sendiri | nama model sendiri |

- Key disimpan di `SharedPreferences` terenkripsi; tidak pernah ditulis ke file proyek atau log.
- Ollama dari emulator memakai `10.0.2.2`; dari perangkat fisik gunakan IP komputer (`http://192.168.x.x:11434/v1`). Cleartext HTTP diizinkan untuk kasus lokal ini.
- Model reasoning OpenAI (`o3`, `o4`, `gpt-5…`) otomatis memakai `max_completion_tokens` dan tanpa `temperature`.

## Design system

Token disimpan di `DesignSystem` dan ditulis sebagai CSS variable:

```css
:root{
  --rb-primary:#7C5CFF; --rb-accent:#39D0FF; --rb-bg:#0B0B12;
  --rb-surface:#15151F; --rb-text:#F5F5FA; --rb-muted:#8C8CA1;
  --rb-radius:20px;     --rb-font:'Inter', system-ui, sans-serif;
}
```

Template menyimpan blok ini di antara `/*RB_DESIGN_START*/` dan `/*RB_DESIGN_END*/`. Saat design diubah, RetroBit menulis ulang blok tersebut **dan** menyuntikkan stylesheet langsung ke WebView, jadi preview berubah seketika tanpa reload. Halaman baru cukup memakai `var(--rb-primary)` dan seterusnya agar ikut bertema.

## Runtime preview

`PreviewInjector` menambahkan API berikut ke setiap halaman:

```html
<script>
  // Query read-only maupun write ke SQLite proyek
  const tasks = await RetroBitDB.query("SELECT * FROM tasks ORDER BY id DESC");
  await RetroBitDB.exec("INSERT INTO tasks(title,status) VALUES (?,?)", ["Belajar", "active"]);

  // Secret yang sengaja di-expose (Map nama → nilai)
  const env = RetroBitDB.env();
  console.log(env.SUPABASE_URL);

  // Toast bergaya aplikasi
  RetroBitToast("Tersimpan!");
</script>
```

- `RetroBitDB.query(sql, params)` mengembalikan `{ columns, rows, error, message, changes }`.
- `RetroBitDB.env()` sama dengan `window.__RETROBIT_ENV__`.
- Runtime hanya aktif di preview dalam aplikasi; ekspor ZIP tetap HTML biasa.

## Struktur proyek

```
app/src/main/java/dev/retrobit/agent/
├── MainActivity.kt            # edge-to-edge + setContent
├── RetroBitApp.kt             # Application + AppContainer (DI manual)
├── data/
│   ├── Models.kt              # Project, DesignSystem, ChatMessage, QueryResult, ...
│   ├── ProjectFiles.kt        # VFS proyek, snapshot, ekspor ZIP
│   ├── ProjectRepository.kt   # index projects.json + chat thread
│   ├── Templates.kt           # 7 template + penulisan blok design
│   ├── DatabaseManager.kt     # SQLite per proyek + bridge JSON
│   ├── SecretStore.kt         # AES-GCM + Android Keystore
│   └── SettingsStore.kt       # AI config, tema, bahasa, usage
├── ai/                        # AiConfig, AiClient (OpenAI/Anthropic/Gemini)
├── agent/                     # AgentEngine, tool runner, DemoAgent
├── preview/                   # PreviewView (WebView), PreviewInjector, LanPreviewServer
└── ui/
    ├── RetroBitRoot.kt        # NavHost + bottom bar
    ├── theme/, components/    # tema, markdown, komponen bersama
    └── screens/               # Projects, Templates, Cloud, Workspace + 6 pane
app/src/main/assets/templates/ # saas, tasks, commerce, fitness, chat, portfolio, blank
```

Penyimpanan di perangkat:

```
filesDir/
├── projects.json              # metadata semua proyek
└── projects/<id>/
    ├── index.html             # entry point (dilindungi)
    ├── …file lain
    └── _meta/
        ├── chat.json          # riwayat percakapan (200 pesan terakhir)
        └── snapshots/*.zip    # 10 snapshot terakhir, sebelum tiap perintah AI
```

## Keamanan & privasi

- **Secret terenkripsi** dengan kunci yang dibuat di Android Keystore (tidak dapat diekspor). Jika keystore di-reset, secret lama tidak bisa dibaca — isi ulang dari Vault.
- **Secret tidak ikut ekspor ZIP.** Hanya metadata nama secret yang dibaca agen; nilai tidak pernah dikirim ke model.
- **Ekspos ke preview bersifat opt-in** per secret.
- **LAN server bersifat read-only**, hanya menyajikan file proyek, menolak path `..`, dan tidak menyertakan secret atau database.
- **Cleartext HTTP** diaktifkan agar Ollama/LAN bisa dipakai; untuk produksi, batasi atau matikan `usesCleartextTraffic`.
- Semua data proyek berada di penyimpanan internal aplikasi.

## Batasan yang diketahui

- Belum ada streaming token: jawaban muncul setelah satu putaran selesai (dengan indikator langkah berjalan).
- Belum ada sistem plugin/ekstensi; tool agen didefinisikan di `AgentToolRunner`.
- Snapshot hanya memulihkan file sumber, bukan database atau design metadata.
- Preview LAN menyajikan file statis tanpa runtime `RetroBitDB`/`__RETROBIT_ENV__`.
- Belum ada unit test/UI test; verifikasi dilakukan lewat build Android Studio.

## Troubleshooting

| Gejala | Solusi |
| --- | --- |
| `SDK location not found` | Buat `local.properties` berisi `sdk.dir=/path/ke/Android/Sdk`, atau atur lewat SDK Manager Android Studio. |
| Gradle sync gagal mengunduh | Periksa koneksi; `gradle-wrapper.properties` menunjuk ke `services.gradle.org`. |
| Preview kosong | Pastikan proyek punya `index.html`; buka menu ⋮ → **Muat ulang preview**, lihat **Console**. |
| Preview LAN tidak bisa dibuka | Pastikan HP dan komputer di Wi-Fi yang sama, port default 8787 tidak dipakai aplikasi lain, dan firewall mengizinkan koneksi masuk. |
| 401/403 dari provider | Key salah/kedaluwarsa; buka **AI Cloud** dan uji ulang koneksi. |
| Ollama gagal | Gunakan `10.0.2.2` untuk emulator, `http://IP-LAN:11434/v1` untuk perangkat fisik, dan pastikan `OLLAMA_HOST=0.0.0.0` bila perlu. |

## Teknologi

AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.12.01 · Navigation Compose 2.8.5 · OkHttp 4.12.0 · kotlinx-serialization 1.7.3 · Coroutines 1.9.0 · Material 3 · WebView · SQLite (SQLiteOpenHelper) · Android Keystore.

---

Dibuat untuk eksperimen membangun aplikasi dari mana saja, langsung dari Android.
