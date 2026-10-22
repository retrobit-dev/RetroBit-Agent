package dev.retrobit.agent.agent

import dev.retrobit.agent.local.OfflineToolProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import dev.retrobit.agent.ai.AiToolDef
import dev.retrobit.agent.ai.ToolCall
import dev.retrobit.agent.ai.parseObject
import dev.retrobit.agent.data.AgentColumn
import dev.retrobit.agent.data.DatabaseManager
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.data.ProjectFiles
import dev.retrobit.agent.data.SecretStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

data class ToolOutcome(
    val success: Boolean,
    val title: String,
    val detail: String,
    val modelOutput: String,
    val filesChanged: List<String> = emptyList(),
)

class AgentToolRunner(
    private val files: ProjectFiles,
    private val db: DatabaseManager,
    private val secrets: SecretStore,
    private val currentDesign: () -> DesignSystem,
    private val onDesignChange: suspend (DesignSystem) -> Unit,
    val onProjectFileChanged: () -> Unit,
) {

    suspend fun execute(call: ToolCall): ToolOutcome {
        return try {
            val definition = DEFINITIONS.singleOrNull { it.name == call.name }
                ?: throw IllegalArgumentException("Tool tidak tersedia: ${call.name}")
            val args = Json.parseToJsonElement(call.argumentsJson) as? JsonObject
                ?: throw IllegalArgumentException("Argumen harus object JSON")
            OfflineToolProtocol.validate(args, definition.parameters)
            when (call.name) {
                "list_files" -> listFiles()
                "read_file" -> readFile(args)
                "write_file" -> writeFile(args)
                "edit_file" -> editFile(args)
                "delete_file" -> deleteFile(args)
                "rename_file" -> renameFile(args)
                "append_file" -> appendFile(args)
                "search_files" -> searchFiles(args)
                "project_info" -> projectInfo()
                "validate_project" -> validateProject()
                "update_design" -> updateDesign(args)
                "create_table" -> createTable(args)
                "run_sql" -> runSql(args)
                "inspect_database" -> inspectDatabase()
                "request_secret" -> requestSecret(args)
                else -> ToolOutcome(false, call.name, "Tool tidak dikenal", "ERROR: tool ${call.name} tidak tersedia")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            ToolOutcome(false, call.name, e.message ?: "Argumen tidak valid", "ERROR: ${e.message}")
        } catch (e: Exception) {
            ToolOutcome(false, call.name, "Gagal: ${e.message}", "ERROR: ${e.message}")
        }
    }

    private fun listFiles(): ToolOutcome {
        val tree = files.treeText().take(6000)
        return ToolOutcome(
            true, "Membaca struktur proyek", "${files.fileCount()} file",
            "STRUKTUR PROYEK:\n$tree", emptyList()
        )
    }

    private fun readFile(args: JsonObject): ToolOutcome {
        val path = args.string("path") ?: throw IllegalArgumentException("path wajib diisi")
        require(files.resolve(path).length() <= 400_000) { "File terlalu besar untuk dibaca tool (maks 400KB)." }
        val content = files.read(path) ?: return ToolOutcome(
            false, "Membaca $path", "File tidak ditemukan", "ERROR: file $path tidak ditemukan. Gunakan list_files."
        )
        return ToolOutcome(
            true, "Membaca $path", "${content.length} karakter",
            content.take(16000) + if (content.length > 16000) "\n… (dipotong)" else "",
        )
    }

    private fun writeFile(args: JsonObject): ToolOutcome {
        val path = args.string("path") ?: throw IllegalArgumentException("path wajib diisi")
        val content = args.string("content") ?: throw IllegalArgumentException("content wajib diisi")
        if (content.toByteArray().size > 400_000) throw IllegalArgumentException("File terlalu besar (maks 400KB)")
        files.write(path, content)
        onProjectFileChanged()
        return ToolOutcome(
            true, "Menulis $path", formatBytes(content.toByteArray().size),
            "OK: $path ditulis (${content.length} karakter).", listOf(path)
        )
    }

    private fun editFile(args: JsonObject): ToolOutcome {
        val path = args.string("path") ?: throw IllegalArgumentException("path wajib diisi")
        val find = args.string("find") ?: throw IllegalArgumentException("find wajib diisi")
        val replace = args.string("replace") ?: throw IllegalArgumentException("replace wajib diisi")
        require(find.isNotEmpty()) { "find tidak boleh kosong" }
        require(files.resolve(path).length() + replace.toByteArray().size <= 400_000) { "Hasil edit terlalu besar." }
        val ok = files.edit(path, find, replace, args.bool("replace_all") ?: false)
        if (!ok) {
            return ToolOutcome(
                false, "Mengedit $path", "Teks tidak ditemukan",
                "ERROR: teks yang dicari tidak ada di $path. Baca ulang file dengan read_file."
            )
        }
        onProjectFileChanged()
        return ToolOutcome(true, "Mengedit $path", "1 bagian diperbarui", "OK: $path diperbarui.", listOf(path))
    }

    private fun deleteFile(args: JsonObject): ToolOutcome {
        val path = args.string("path") ?: throw IllegalArgumentException("path wajib diisi")
        if (files.resolve(path).canonicalFile == files.resolve(ProjectFiles.ENTRY_POINT).canonicalFile) throw IllegalArgumentException("index.html tidak boleh dihapus")
        val ok = files.delete(path)
        if (ok) onProjectFileChanged()
        return ToolOutcome(ok, "Menghapus $path", if (ok) "Berhasil" else "Tidak ditemukan",
            if (ok) "OK: $path dihapus." else "ERROR: $path tidak ditemukan.", if (ok) listOf(path) else emptyList())
    }

    private fun renameFile(args: JsonObject): ToolOutcome {
        val path = args.string("path") ?: throw IllegalArgumentException("path wajib diisi")
        val newPath = args.string("new_path") ?: throw IllegalArgumentException("new_path wajib diisi")
        if (files.resolve(path).canonicalFile == files.resolve(ProjectFiles.ENTRY_POINT).canonicalFile) throw IllegalArgumentException("index.html tidak boleh dipindah")
        val ok = files.rename(path, newPath)
        if (ok) onProjectFileChanged()
        return ToolOutcome(ok, "Memindahkan file", if (ok) "$path → $newPath" else "Gagal",
            if (ok) "OK: $path dipindah ke $newPath." else "ERROR: gagal memindahkan $path.", if (ok) listOf(newPath) else emptyList())
    }

    private suspend fun updateDesign(args: JsonObject): ToolOutcome {
        var design = currentDesign()
        args.string("preset")?.let { preset ->
            DesignSystem.PRESETS.firstOrNull { it.id == preset }?.let { design = it.design }
        }
        design = design.copy(
            primary = args.string("primary") ?: design.primary,
            accent = args.string("accent") ?: design.accent,
            background = args.string("background") ?: design.background,
            surface = args.string("surface") ?: design.surface,
            textColor = args.string("text") ?: design.textColor,
            muted = args.string("muted") ?: design.muted,
            radius = args.int("radius") ?: design.radius,
            font = args.string("font") ?: design.font,
            mode = args.string("mode") ?: design.mode,
        )
        design = design.copy(radius = design.radius.coerceIn(0, 40), font = "Pixel Operator")
        if (!design.isGrayscale()) design = design.grayscaleFallback()
        onDesignChange(design)
        return ToolOutcome(
            true, "Memperbarui design system",
            "Primer ${design.primary} · Radius ${design.radius}px",
            "OK: design diperbarui. Variabel CSS --rb-primary=${design.primary}, --rb-radius=${design.radius}px. Semua file HTML sudah disesuaikan.",
        )
    }

    private fun createTable(args: JsonObject): ToolOutcome {
        val name = args.string("name") ?: throw IllegalArgumentException("name wajib diisi")
        val columnsJson = args["columns"]?.jsonArray ?: throw IllegalArgumentException("columns wajib diisi")
        val columns = columnsJson.mapNotNull { el ->
            val obj = el.jsonObject
            val colName = obj.string("name") ?: return@mapNotNull null
            AgentColumn(
                name = colName,
                type = obj.string("type") ?: "TEXT",
                primaryKey = obj.bool("primary_key") ?: false,
                notNull = obj.bool("not_null") ?: false,
                default = obj.string("default"),
            )
        }
        if (columns.isEmpty()) throw IllegalArgumentException("Minimal satu kolom valid")
        require(columns.size <= 32) { "Maksimal 32 kolom per tool." }
        columns.forEach { column -> require(column.default == null || AgentSqlPolicy.defaultLiteral(column.default)) { "Default kolom harus literal SQL sederhana." } }
        require((args.int("seed_rows") ?: 0) in 0..20) { "seed_rows harus 0–20." }
        val result = db.createTable(name, columns, args.int("seed_rows") ?: 0)
        if (result.error != null) {
            return ToolOutcome(false, "Membuat tabel $name", result.error, "ERROR: ${result.error}")
        }
        return ToolOutcome(
            true, "Membuat tabel $name",
            "${columns.size} kolom" + if ((args.int("seed_rows") ?: 0) > 0) " · data contoh" else "",
            "OK: tabel $name dibuat.\n${db.statsText()}",
        )
    }

    private fun runSql(args: JsonObject): ToolOutcome {
        val sql = args.string("sql") ?: throw IllegalArgumentException("sql wajib diisi")
        AgentSqlPolicy.validate(sql)
        val started = System.currentTimeMillis()
        val output = db.runForAgent(sql)
        val elapsed = System.currentTimeMillis() - started
        val failed = output.startsWith("ERROR")
        return ToolOutcome(!failed, "Menjalankan SQL", if (failed) "Gagal" else "${elapsed}ms", output)
    }

    private fun inspectDatabase(): ToolOutcome {
        val tables = db.tables()
        if (tables.isEmpty()) {
            return ToolOutcome(true, "Memeriksa database", "Belum ada tabel",
                "DATABASE: kosong (belum ada tabel). Buat dengan create_table.")
        }
        val sb = StringBuilder("DATABASE:\n")
        tables.forEach { table ->
            sb.appendLine("Tabel ${table.name} (${table.rows} baris)")
            db.columns(table.name).forEach { col ->
                sb.appendLine("  - ${col.name} ${col.type}${if (col.primaryKey) " PK" else ""}${if (col.notNull) " NOT NULL" else ""}")
            }
        }
        return ToolOutcome(true, "Memeriksa database", "${tables.size} tabel", sb.toString())
    }

    private fun requestSecret(args: JsonObject): ToolOutcome {
        val key = args.string("key") ?: throw IllegalArgumentException("key wajib diisi")
        val description = args.string("description") ?: "Dibutuhkan aplikasi"
        val normalized = key.uppercase().replace(" ", "_")
        require(normalized.matches(Regex("[A-Z_][A-Z0-9_]{0,63}"))) { "Nama secret hanya huruf, angka, underscore (maks 64)." }
        secrets.request(normalized, description.take(400))
        return ToolOutcome(
            true, "Meminta secret $key", "Menunggu diisi pengguna",
            "OK: permintaan secret $key dicatat. Kode bisa memakai placeholder {{SECRET:$key}} atau window.__RETROBIT_ENV__.Jangan pernah menulis nilai rahasia di file.",
        )
    }

    private fun appendFile(args: JsonObject): ToolOutcome {
        val path = args.string("path")!!
        val content = args.string("content")!!
        require(files.resolve(path).length() + content.toByteArray().size <= 400_000) { "File terlalu besar (maks 400KB)." }
        files.append(path, content)
        onProjectFileChanged()
        return ToolOutcome(true, "Menambah isi $path", "${content.length} karakter", "OK: isi ditambahkan ke $path.", listOf(path))
    }

    private fun searchFiles(args: JsonObject): ToolOutcome {
        val query = args.string("query")!!
        require(query.length in 1..200) { "query harus 1–200 karakter, pencarian literal (bukan regex)." }
        val results = mutableListOf<String>()
        var scanned = 0
        for (file in files.allFiles().take(200)) {
            val path = file.relativeTo(files.root).path.replace('\\', '/')
            if (!files.isTextFile(path) || file.length() > 400_000) continue
            scanned++
            file.useLines { lines ->
                for ((index, line) in lines.withIndex()) {
                    if (line.contains(query, ignoreCase = true)) results += "$path:${index + 1}: ${line.take(180)}"
                    if (results.size >= 30) break
                }
            }
            if (results.size >= 30) break
        }
        return ToolOutcome(true, "Mencari teks proyek", "${results.size} hasil · $scanned file dipindai", results.joinToString("\n").ifEmpty { "Tidak ditemukan. Maksimal 200 file, 30 hasil; file >400KB dilewati." })
    }

    private fun projectInfo(): ToolOutcome = ToolOutcome(true, "Informasi proyek", "${files.fileCount()} file",
        "${files.fileCount()} file · ${files.totalBytes()} byte. Entry: index.html. Preview HTML/CSS/JS, ekspor ZIP source, bukan APK. Snapshot file: ${files.latestSnapshot() != null}.\n${db.statsText()}")

    private fun validateProject(): ToolOutcome {
        val issues = mutableListOf<String>()
        if (!files.exists(ProjectFiles.ENTRY_POINT)) issues += "index.html belum ada"
        for (file in files.allFiles().take(200)) {
            if (file.extension != "json" || file.length() > 400_000) continue
            if (runCatching { Json.parseToJsonElement(file.readText()) }.isFailure) issues += "JSON tidak valid: ${file.relativeTo(files.root).path}"
        }
        return ToolOutcome(issues.isEmpty(), "Memeriksa struktur proyek", if (issues.isEmpty()) "Lulus cek struktur" else "${issues.size} masalah",
            if (issues.isEmpty()) "OK: entry point ada dan JSON valid (maks 200 file). Ini bukan tes JavaScript, keamanan, kompilasi Kotlin, atau build APK. Buka tab Preview untuk menguji tampilan." else issues.take(20).joinToString("\n"))
    }

    private fun formatBytes(bytes: Int): String =
        if (bytes < 1024) "$bytes B" else "%.1f KB".format(bytes / 1024.0)

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    companion object {
        private fun schema(properties: JsonObject, required: List<String>): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", properties)
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }

        private fun prop(type: String, description: String, extra: JsonObject = buildJsonObject { }): JsonObject =
            buildJsonObject {
                put("type", type)
                put("description", description)
                extra.forEach { (k, v) -> put(k, v) }
            }

        val DEFINITIONS: List<AiToolDef> = listOf(
            AiToolDef("project_info", "Informasi workspace, snapshot file, database dan format ekspor.", schema(buildJsonObject {}, emptyList())),
            AiToolDef("search_files", "Cari teks literal di maksimal 200 file proyek; maksimal 30 hasil dengan nomor baris.",
                schema(buildJsonObject { put("query", prop("string", "Teks literal 1–200 karakter")) }, listOf("query"))),
            AiToolDef("append_file", "Tambahkan teks di akhir file (atau buat file baru). Berguna untuk model kecil yang menulis bertahap.",
                schema(buildJsonObject { put("path", prop("string", "Path relatif")); put("content", prop("string", "Teks tambahan")) }, listOf("path", "content"))),
            AiToolDef("validate_project", "Cek entry point dan JSON; bukan compiler/tes runtime atau build APK.", schema(buildJsonObject {}, emptyList())),
            AiToolDef(
                "list_files",
                "Tampilkan semua file dan folder di proyek. Panggil ini dulu untuk memahami struktur sebelum mengubah apa pun.",
                schema(buildJsonObject { }, emptyList()),
            ),
            AiToolDef(
                "read_file",
                "Baca isi sebuah file teks. Path relatif, contoh: index.html atau assets/app.js.",
                schema(buildJsonObject { put("path", prop("string", "Path relatif file")) }, listOf("path")),
            ),
            AiToolDef(
                "write_file",
                "Buat atau timpa file dengan isi lengkap. Untuk perubahan kecil pada file besar, pakai edit_file agar lebih hemat.",
                schema(
                    buildJsonObject {
                        put("path", prop("string", "Path relatif file, mis. index.html"))
                        put("content", prop("string", "Isi file secara lengkap"))
                    },
                    listOf("path", "content")
                ),
            ),
            AiToolDef(
                "edit_file",
                "Ganti satu bagian teks di dalam file yang sudah ada. Gagal jika teks tidak ditemukan persis.",
                schema(
                    buildJsonObject {
                        put("path", prop("string", "Path relatif file"))
                        put("find", prop("string", "Teks persis yang dicari"))
                        put("replace", prop("string", "Teks pengganti"))
                        put("replace_all", prop("boolean", "Ganti semua kemunculan (default false)"))
                    },
                    listOf("path", "find", "replace")
                ),
            ),
            AiToolDef(
                "delete_file",
                "Hapus file atau folder di proyek. index.html tidak bisa dihapus.",
                schema(buildJsonObject { put("path", prop("string", "Path yang dihapus")) }, listOf("path")),
            ),
            AiToolDef(
                "rename_file",
                "Pindahkan atau ganti nama file.",
                schema(
                    buildJsonObject {
                        put("path", prop("string", "Path lama"))
                        put("new_path", prop("string", "Path baru"))
                    },
                    listOf("path", "new_path")
                ),
            ),
            AiToolDef(
                "update_design",
                "Ubah design system proyek. Variabel otomatis diterapkan ke CSS --rb-* di semua file HTML. Bisa memakai preset atau warna eksplisit.",
                schema(
                    buildJsonObject {
                        put("preset", prop("string", "Preset: midnight, aurora, sunset, candy, mono, terminal"))
                        put("primary", prop("string", "Warna utama hex grayscale, mis. #DEDED5"))
                        put("accent", prop("string", "Warna aksen hex"))
                        put("background", prop("string", "Warna latar hex"))
                        put("surface", prop("string", "Warna permukaan/kartu hex"))
                        put("text", prop("string", "Warna teks hex"))
                        put("muted", prop("string", "Warna teks sekunder hex"))
                        put("radius", prop("integer", "Radius sudut dalam px, 0-40"))
                        put("font", prop("string", "Nama font, pakai Pixel Operator (default) atau Mono"))
                        put("mode", prop("string", "dark atau light"))
                    },
                    emptyList()
                ),
            ),
            AiToolDef(
                "create_table",
                "Buat tabel SQLite baru di database proyek. Bisa langsung mengisi data contoh.",
                schema(
                    buildJsonObject {
                        put("name", prop("string", "Nama tabel (huruf/angka/underscore)"))
                        put(
                            "columns", prop(
                                "array", "Daftar kolom",
                                buildJsonObject {
                                    put("items", buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("name", prop("string", "Nama kolom"))
                                            put("type", prop("string", "TEXT/INTEGER/REAL/BLOB/NUMERIC"))
                                            put("primary_key", prop("boolean", "Jadikan primary key"))
                                            put("not_null", prop("boolean", "Wajib diisi"))
                                            put("default", prop("string", "Nilai default SQL"))
                                        })
                                        put("required", JsonArray(listOf(JsonPrimitive("name"))))
                                    })
                                }
                            )
                        )
                        put("seed_rows", prop("integer", "Jumlah baris data contoh (0-20)"))
                    },
                    listOf("name", "columns")
                ),
            ),
            AiToolDef(
                "run_sql",
                "Jalankan SQL apa pun pada database proyek (SELECT, INSERT, UPDATE, CREATE, ALTER, DELETE). Hasil SELECT dikembalikan sebagai tabel teks.",
                schema(buildJsonObject { put("sql", prop("string", "Satu perintah SQL")) }, listOf("sql")),
            ),
            AiToolDef(
                "inspect_database",
                "Lihat daftar tabel, kolom, dan jumlah baris. Panggil sebelum menulis query yang bergantung pada skema.",
                schema(buildJsonObject { }, emptyList()),
            ),
            AiToolDef(
                "request_secret",
                "Minta pengguna mengisi secret/API key yang dibutuhkan aplikasi (mis. URL Supabase). Jangan pernah mengarang nilai rahasia.",
                schema(
                    buildJsonObject {
                        put("key", prop("string", "Nama secret, mis. API_KEY"))
                        put("description", prop("string", "Untuk apa secret ini"))
                    },
                    listOf("key", "description")
                ),
            ),
        )

        fun titleFor(tool: String, args: JsonObject): String {
            val path = (args["path"] as? JsonPrimitive)?.content
            return when (tool) {
                "project_info" -> "Informasi proyek"
                "search_files" -> "Mencari teks proyek"
                "append_file" -> "Menambah isi ${path ?: "file"}"
                "validate_project" -> "Memeriksa struktur proyek"
                "list_files" -> "Membaca struktur proyek"
                "read_file" -> "Membaca ${path ?: "file"}"
                "write_file" -> "Menulis ${path ?: "file"}"
                "edit_file" -> "Mengedit ${path ?: "file"}"
                "delete_file" -> "Menghapus ${path ?: "file"}"
                "rename_file" -> "Memindahkan file"
                "update_design" -> "Memperbarui desain"
                "create_table" -> "Membuat tabel ${(args["name"] as? JsonPrimitive)?.content.orEmpty()}"
                "run_sql" -> "Menjalankan query"
                "inspect_database" -> "Memeriksa database"
                "request_secret" -> "Meminta secret"
                else -> tool
            }
        }
    }
}
