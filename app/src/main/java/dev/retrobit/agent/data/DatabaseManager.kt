package dev.retrobit.agent.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small SQLite console scoped to one project. Powers the Data tab as well as the
 * AI's `create_table` / `run_sql` tools, and the window.RetroBitDB bridge in preview.
 */
class DatabaseManager(context: Context, private val projectId: String) {

    private val dbFile: File = context.getDatabasePath("rb_$projectId.db")
    private var db: SQLiteDatabase? = null
    private val json = Json { prettyPrint = false }

    @Synchronized
    fun connection(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        dbFile.parentFile?.mkdirs()
        val opened = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // Some storage (and Robolectric's SQLite) rejects WAL; journaling is an
        // optimisation, not a requirement.
        runCatching { opened.enableWriteAheadLogging() }
        db = opened
        return opened
    }

    @Synchronized
    fun close() {
        runCatching { db?.close() }
        db = null
    }

    fun tables(): List<TableInfo> {
        val database = connection()
        val result = mutableListOf<TableInfo>()
        runCatching {
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val rows = runCatching {
                        database.rawQuery("SELECT COUNT(*) FROM \"${name.replace("\"", "\"\"")}\"", null).use {
                            if (it.moveToFirst()) it.getLong(0) else 0L
                        }
                    }.getOrDefault(0L)
                    val cols = columns(name).size
                    result.add(TableInfo(name, rows, cols))
                }
            }
        }
        return result
    }

    fun columns(table: String): List<ColumnInfo> {
        if (!isSafeIdentifier(table)) return emptyList()
        val database = connection()
        val result = mutableListOf<ColumnInfo>()
        runCatching {
            database.rawQuery("PRAGMA table_info(\"$table\")", null).use { c ->
                while (c.moveToNext()) {
                    result.add(
                        ColumnInfo(
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            type = c.getString(c.getColumnIndexOrThrow("type")).ifBlank { "TEXT" },
                            notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                            primaryKey = c.getInt(c.getColumnIndexOrThrow("pk")) == 1,
                            defaultValue = c.getString(c.getColumnIndexOrThrow("dflt_value")),
                        )
                    )
                }
            }
        }
        return result
    }

    fun rowPreview(table: String, limit: Int = 50): QueryResult {
        if (!isSafeIdentifier(table)) return QueryResult(error = "Nama tabel tidak valid")
        return run("SELECT * FROM \"$table\" LIMIT $limit")
    }

    fun run(sql: String, args: List<String?> = emptyList()): QueryResult {
        val started = System.currentTimeMillis()
        val trimmed = sql.trim().trimEnd(';')
        if (trimmed.isBlank()) return QueryResult(message = "Tidak ada SQL untuk dijalankan")
        if (trimmed.length > 20000) return QueryResult(error = "SQL terlalu panjang")
        val database = try {
            connection()
        } catch (e: Exception) {
            return QueryResult(error = "Gagal membuka database: ${e.message}")
        }
        val isSelect = trimmed.looksLikeQuery()
        return try {
            if (isSelect) {
                database.rawQuery(trimmed, args.filterNotNull().toTypedArray()).use { cursor ->
                    val columns = cursor.columnNames.toList()
                    val rows = mutableListOf<List<String?>>()
                    while (cursor.moveToNext() && rows.size < 500) {
                        rows.add(columns.indices.map { i ->
                            runCatching {
                                when (cursor.getType(i)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> null
                                    android.database.Cursor.FIELD_TYPE_BLOB -> "[blob ${cursor.getBlob(i)?.size ?: 0} B]"
                                    else -> cursor.getString(i)
                                }
                            }.getOrNull()
                        })
                    }
                    QueryResult(
                        columns = columns,
                        rows = rows,
                        message = "${rows.size} baris",
                        elapsedMs = System.currentTimeMillis() - started,
                        isSelect = true,
                    )
                }
            } else {
                splitStatements(trimmed).forEach { statement ->
                    if (statement.isBlank()) return@forEach
                    if (args.isEmpty()) database.execSQL(statement)
                    else database.execSQL(statement, args.filterNotNull().toTypedArray())
                }
                QueryResult(
                    message = "Perintah berhasil dijalankan",
                    rowsAffected = 1,
                    elapsedMs = System.currentTimeMillis() - started,
                )
            }
        } catch (e: SQLiteException) {
            QueryResult(error = e.message ?: "SQLite error", elapsedMs = System.currentTimeMillis() - started)
        } catch (e: Exception) {
            QueryResult(error = e.message ?: "Kesalahan tidak diketahui", elapsedMs = System.currentTimeMillis() - started)
        }
    }

    /** Executes SQL and returns a compact text summary that fits in an LLM context. */
    fun runForAgent(sql: String): String {
        val result = run(sql)
        return if (result.error != null) {
            "ERROR: ${result.error}"
        } else if (result.isSelect) {
            buildString {
                appendLine(result.message)
                appendLine(result.columns.joinToString(" | "))
                result.rows.take(60).forEach { row ->
                    appendLine(row.joinToString(" | ") { it ?: "null" })
                }
                if (result.rows.size > 60) appendLine("… (${result.rows.size - 60} baris lagi)")
            }
        } else {
            result.message
        }
    }

    fun createTable(name: String, columns: List<AgentColumn>, seedRows: Int = 0): QueryResult {
        if (!isSafeIdentifier(name)) return QueryResult(error = "Nama tabel harus huruf/angka/underscore")
        if (columns.isEmpty()) return QueryResult(error = "Minimal satu kolom")
        val columnDefs = columns.map { col ->
            if (!isSafeIdentifier(col.name)) throw IllegalArgumentException("Kolom tidak valid: ${col.name}")
            val type = col.type.uppercase().takeIf { it in ALLOWED_TYPES } ?: "TEXT"
            buildString {
                append("\"").append(col.name).append("\" ").append(type)
                if (col.primaryKey) append(" PRIMARY KEY")
                if (col.notNull && !col.primaryKey) append(" NOT NULL")
                col.default?.let { append(" DEFAULT ").append(it) }
            }
        }
        val sql = "CREATE TABLE IF NOT EXISTS \"$name\" (${columnDefs.joinToString(", ")})"
        val created = run(sql)
        if (created.error != null) return created
        if (seedRows > 0) seedTable(name, seedRows)
        return created.copy(message = "Tabel $name siap")
    }

    fun seedTable(table: String, rows: Int = 5): QueryResult {
        if (!isSafeIdentifier(table)) return QueryResult(error = "Nama tabel tidak valid")
        val cols = columns(table).filterNot { it.primaryKey && it.type.contains("INT") }
        val db = connection()
        var inserted = 0
        runCatching {
            db.beginTransaction()
            repeat(rows) { i ->
                val values = ContentValues()
                cols.forEach { col ->
                    val value: Any = when {
                        col.type.contains("INT") -> (i + 1) * 3
                        col.type.contains("REAL") -> 4.5 + i
                        else -> sampleText(col.name, i)
                    }
                    values.put(col.name, value as? String ?: value.toString())
                }
                if (db.insert(table, null, values) != -1L) inserted++
            }
            db.setTransactionSuccessful()
            db.endTransaction()
        }
        return QueryResult(message = "$inserted baris contoh ditambahkan ke $table")
    }

    fun seedPreset(preset: String): QueryResult {
        return when (preset) {
            "tasks" -> run(
                """
                CREATE TABLE IF NOT EXISTS tasks (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  title TEXT NOT NULL,
                  done INTEGER NOT NULL DEFAULT 0,
                  priority TEXT DEFAULT 'medium',
                  created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
                INSERT INTO tasks (title, done, priority) VALUES
                  ('Rancang halaman onboarding', 1, 'high'),
                  ('Hubungkan pembayaran', 0, 'high'),
                  ('Tulis dokumentasi API', 0, 'low'),
                  ('Uji coba di perangkat', 0, 'medium');
                """.trimIndent()
            )
            "users" -> run(
                """
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  email TEXT UNIQUE,
                  role TEXT DEFAULT 'member',
                  created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
                INSERT INTO users (name, email, role) VALUES
                  ('Ayu Lestari', 'ayu@contoh.id', 'admin'),
                  ('Bima Pratama', 'bima@contoh.id', 'member'),
                  ('Citra Dewi', 'citra@contoh.id', 'member');
                """.trimIndent()
            )
            "products" -> run(
                """
                CREATE TABLE IF NOT EXISTS products (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  price INTEGER NOT NULL,
                  stock INTEGER DEFAULT 0,
                  category TEXT
                );
                INSERT INTO products (name, price, stock, category) VALUES
                  ('Kopi Gayo 200g', 85000, 24, 'kopi'),
                  ('Teh Melati', 42000, 40, 'teh'),
                  ('Madu Hutan 500ml', 120000, 12, 'madu');
                """.trimIndent()
            )
            else -> QueryResult(error = "Preset tidak dikenal")
        }
    }

    fun clearAll() {
        val db = connection()
        tables().forEach { t ->
            runCatching { db.execSQL("DROP TABLE IF EXISTS \"${t.name}\"") }
        }
        close()
        runCatching { dbFile.delete() }
        runCatching { File(dbFile.path + "-wal").delete() }
        runCatching { File(dbFile.path + "-shm").delete() }
    }

    fun statsText(): String {
        val list = tables()
        if (list.isEmpty()) return "Database kosong (belum ada tabel)."
        return list.joinToString("\n") { "${it.name} — ${it.rows} baris, ${it.columns} kolom" }
    }

    private fun sampleText(column: String, index: Int): String = when {
        "email" in column.lowercase() -> "user${index + 1}@contoh.id"
        "name" in column.lowercase() -> listOf("Ayu", "Bima", "Citra", "Damar", "Eka", "Fajar").let { it[index % it.size] }
        "status" in column.lowercase() -> listOf("active", "pending", "done")[index % 3]
        "title" in column.lowercase() -> "Contoh item ${index + 1}"
        else -> "contoh-${index + 1}"
    }

    companion object {
        private val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val ALLOWED_TYPES = setOf("TEXT", "INTEGER", "INT", "REAL", "NUMERIC", "BLOB", "BOOLEAN", "DATE", "DATETIME")

        fun isSafeIdentifier(value: String): Boolean = IDENTIFIER.matches(value)

        private fun String.looksLikeQuery(): Boolean {
            val first = trimStart().substringBefore(' ').uppercase()
            return first in setOf("SELECT", "PRAGMA", "EXPLAIN", "WITH", "VALUES")
        }

        /** Splits SQL on semicolons while respecting quoted strings. */
        fun splitStatements(sql: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var i = 0
            while (i < sql.length) {
                val ch = sql[i]
                when {
                    quote != null -> {
                        current.append(ch)
                        if (ch == quote) quote = null
                    }
                    ch == '\'' || ch == '"' -> {
                        quote = ch
                        current.append(ch)
                    }
                    ch == ';' -> {
                        if (current.isNotBlank()) out.add(current.toString().trim())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
                i++
            }
            if (current.isNotBlank()) out.add(current.toString().trim())
            return out
        }
    }

    fun queryAsJsonForBridge(sql: String, argsJson: String?): String {
        val args = parseArgs(argsJson)
        // Generated JavaScript must not bypass the agent's project SQL boundary
        // through the WebView bridge (ATTACH, PRAGMA, extension loading, etc.).
        val result = try {
            ProjectSqlPolicy.validate(sql)
            run(sql, args)
        } catch (e: IllegalArgumentException) {
            QueryResult(error = e.message ?: "SQL preview tidak diizinkan")
        }
        return json.encodeToString(BridgeResult.serializer(), BridgeResult.from(result))
    }

    private fun parseArgs(argsJson: String?): List<String?> {
        if (argsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.json.JsonPrimitive.serializer()), argsJson)
                .map { if (it.content == "null") null else it.content }
        }.getOrElse { emptyList() }
    }

    @kotlinx.serialization.Serializable
    data class BridgeResult(
        val columns: List<String>,
        val rows: List<List<String?>>,
        val error: String?,
        val message: String,
        val changes: Int,
    ) {
        companion object {
            fun from(result: QueryResult) = BridgeResult(
                result.columns, result.rows, result.error, result.message, result.rowsAffected
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class AgentColumn(
    val name: String,
    val type: String = "TEXT",
    val primaryKey: Boolean = false,
    val notNull: Boolean = false,
    val default: String? = null,
)
