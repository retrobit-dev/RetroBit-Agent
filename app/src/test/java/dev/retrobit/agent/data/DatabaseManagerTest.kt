package dev.retrobit.agent.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DatabaseManager talks to a real SQLite file through a real Android Context, so
 * these run under Robolectric rather than on the JVM alone. API 30 matches the
 * emulator the smoke walk uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DatabaseManagerTest {

    private lateinit var context: Context
    private lateinit var manager: DatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = DatabaseManager(context, "test-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        manager.clearAll()
    }

    private fun insert(title: String, done: Int = 0) {
        val result = manager.run("INSERT INTO tasks (title, done) VALUES ('$title', $done)")
        assertNull(result.error)
    }

    private fun createTasks() {
        val created = manager.createTable(
            name = "tasks",
            columns = listOf(
                AgentColumn("id", "INTEGER", primaryKey = true),
                AgentColumn("title", "TEXT", notNull = true),
                AgentColumn("done", "INTEGER", default = "0"),
            ),
        )
        assertNull(created.error)
    }

    // --- schema ---------------------------------------------------------------

    @Test
    fun `createTable makes the table visible with its columns`() {
        createTasks()

        val tables = manager.tables()
        assertEquals(1, tables.size)
        assertEquals("tasks", tables.first().name)
        assertEquals(0L, tables.first().rows)
        assertEquals(3, tables.first().columns)

        val names = manager.columns("tasks").map { it.name }
        assertEquals(listOf("id", "title", "done"), names)
    }

    @Test
    fun `primary key and not null survive the round trip`() {
        createTasks()
        val columns = manager.columns("tasks").associateBy { it.name }
        assertTrue(columns.getValue("id").primaryKey)
        assertTrue(columns.getValue("title").notNull)
        assertFalse(columns.getValue("done").notNull)
        assertEquals("0", columns.getValue("done").defaultValue)
    }

    @Test
    fun `unknown types fall back to TEXT`() {
        manager.createTable("weird", listOf(AgentColumn("payload", "GEOMETRY")))
        assertEquals("TEXT", manager.columns("weird").single().type)
    }

    @Test
    fun `createTable rejects unsafe names and empty columns`() {
        assertEquals(
            "Nama tabel harus huruf/angka/underscore",
            manager.createTable("../escape", listOf(AgentColumn("id"))).error,
        )
        assertEquals(
            "Nama tabel harus huruf/angka/underscore",
            manager.createTable("tasks; DROP TABLE x", listOf(AgentColumn("id"))).error,
        )
        assertEquals("Minimal satu kolom", manager.createTable("empty", emptyList()).error)
        assertTrue(manager.tables().isEmpty())
    }

    // --- rows -----------------------------------------------------------------

    @Test
    fun `insert and select round trip`() {
        createTasks()
        insert("Rancang onboarding", done = 1)
        insert("Hubungkan pembayaran")

        val result = manager.run("SELECT title, done FROM tasks ORDER BY id")
        assertNull(result.error)
        assertTrue(result.isSelect)
        assertEquals(listOf("title", "done"), result.columns)
        assertEquals(2, result.rows.size)
        assertEquals(listOf("Rancang onboarding", "1"), result.rows[0])
        assertEquals(listOf("Hubungkan pembayaran", "0"), result.rows[1])
        assertEquals("2 baris", result.message)
    }

    @Test
    fun `tables reports row counts after inserts`() {
        createTasks()
        repeat(3) { insert("Item $it") }
        assertEquals(3L, manager.tables().single().rows)
    }

    @Test
    fun `select honours bound arguments`() {
        createTasks()
        insert("satu")
        insert("dua")
        val result = manager.run("SELECT title FROM tasks WHERE title = ?", listOf("dua"))
        assertEquals(listOf(listOf("dua")), result.rows)
    }

    @Test
    fun `update and delete go through exec`() {
        createTasks()
        insert("satu")
        assertNull(manager.run("UPDATE tasks SET done = 1 WHERE title = 'satu'").error)
        assertEquals("1", manager.run("SELECT done FROM tasks").rows.single().single())
        assertNull(manager.run("DELETE FROM tasks").error)
        assertEquals(0L, manager.tables().single().rows)
    }

    @Test
    fun `several statements run in one call`() {
        assertNull(manager.run("CREATE TABLE IF NOT EXISTS notes (body TEXT)").error)
        val result = manager.run(
            "INSERT INTO notes (body) VALUES ('a;b'); INSERT INTO notes (body) VALUES ('c')"
        )
        assertNull(result.error)
        // The semicolon inside the string literal must not split the statement.
        val rows = manager.run("SELECT body FROM notes ORDER BY rowid").rows.map { it.single() }
        assertEquals(listOf("a;b", "c"), rows)
    }

    @Test
    fun `bad SQL returns an error instead of throwing`() {
        val result = manager.run("SELECT * FROM does_not_exist")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("no such table"))
        assertFalse(result.isSelect)
    }

    @Test
    fun `blank and oversized SQL are rejected`() {
        assertEquals("Tidak ada SQL untuk dijalankan", manager.run("   ").message)
        assertEquals("SQL terlalu panjang", manager.run("SELECT 1 -- " + "x".repeat(20_001)).error)
    }

    // --- seeding --------------------------------------------------------------

    @Test
    fun `seedPreset builds a usable table`() {
        val result = manager.seedPreset("tasks")
        assertNull(result.error)
        assertEquals(4L, manager.tables().single().rows)
        assertEquals("high", manager.run("SELECT priority FROM tasks WHERE id = 1").rows.single().single())
    }

    @Test
    fun `seedPreset rejects unknown presets`() {
        assertEquals("Preset tidak dikenal", manager.seedPreset("nope").error)
    }

    @Test
    fun `seedTable fills generated rows`() {
        manager.createTable("people", listOf(AgentColumn("name"), AgentColumn("email")))
        val result = manager.seedTable("people", rows = 2)
        assertEquals("2 baris contoh ditambahkan ke people", result.message)
        val emails = manager.run("SELECT email FROM people ORDER BY rowid").rows.map { it.single() }
        assertEquals(listOf("user1@contoh.id", "user2@contoh.id"), emails)
    }

    // --- bridge ---------------------------------------------------------------

    @Test
    fun `bridge json carries columns and rows`() {
        createTasks()
        insert("lewat preview")
        val payload = Json.parseToJsonElement(
            manager.queryAsJsonForBridge("SELECT title FROM tasks", null)
        ).jsonObject

        assertEquals("title", payload.getValue("columns").jsonArray[0].jsonPrimitive.content)
        assertEquals("lewat preview", payload.getValue("rows").jsonArray[0].jsonArray[0].jsonPrimitive.content)
        assertNull(payload.getValue("error").jsonPrimitive.contentOrNull())
    }

    @Test
    fun `bridge json reports errors as json`() {
        val payload = Json.parseToJsonElement(
            manager.queryAsJsonForBridge("SELECT * FROM missing", "[]")
        ).jsonObject
        assertTrue(payload.getValue("error").jsonPrimitive.content.contains("no such table"))
        assertEquals(0, payload.getValue("rows").jsonArray.size)
    }

    @Test
    fun `bridge accepts positional arguments`() {
        createTasks()
        insert("cari aku")
        val payload = Json.parseToJsonElement(
            manager.queryAsJsonForBridge("SELECT title FROM tasks WHERE title = ?", """["cari aku"]""")
        ).jsonObject
        assertEquals(1, payload.getValue("rows").jsonArray.size)
    }

    @Test
    fun `bridge ignores malformed argument json`() {
        createTasks()
        insert("aman")
        val payload = Json.parseToJsonElement(
            manager.queryAsJsonForBridge("SELECT COUNT(*) FROM tasks", "{not json")
        ).jsonObject
        assertNull(payload.getValue("error").jsonPrimitive.contentOrNull())
    }

    // --- lifecycle ------------------------------------------------------------

    @Test
    fun `clearAll drops every table`() {
        createTasks()
        insert("x")
        manager.createTable("notes", listOf(AgentColumn("body")))
        assertEquals(2, manager.tables().size)

        manager.clearAll()

        assertTrue(manager.tables().isEmpty())
    }

    @Test
    fun `close reopens on the next call`() {
        createTasks()
        insert("tetap ada")
        manager.close()
        assertEquals(1L, manager.tables().single().rows)
        assertEquals("tetap ada", manager.run("SELECT title FROM tasks").rows.single().single())
    }

    @Test
    fun `runForAgent renders a text table for selects`() {
        createTasks()
        insert("render")
        val text = manager.runForAgent("SELECT title, done FROM tasks")
        assertTrue(text.contains("1 baris"))
        assertTrue(text.contains("title | done"))
        assertTrue(text.contains("render | 0"))
    }

    @Test
    fun `runForAgent prefixes errors`() {
        assertTrue(manager.runForAgent("SELECT * FROM nope").startsWith("ERROR: "))
    }

    @Test
    fun `statsText summarises an empty and a filled database`() {
        assertEquals("Database kosong (belum ada tabel).", manager.statsText())
        createTasks()
        insert("x")
        assertEquals("tasks — 1 baris, 3 kolom", manager.statsText())
    }

    // --- platform-free helpers ------------------------------------------------

    @Test
    fun `identifier guard rejects injection shapes`() {
        assertTrue(DatabaseManager.isSafeIdentifier("tasks"))
        assertTrue(DatabaseManager.isSafeIdentifier("_private2"))
        assertFalse(DatabaseManager.isSafeIdentifier("2tasks"))
        assertFalse(DatabaseManager.isSafeIdentifier("tasks drop"))
        assertFalse(DatabaseManager.isSafeIdentifier("tasks;"))
        assertFalse(DatabaseManager.isSafeIdentifier(""))
    }

    @Test
    fun `splitStatements keeps quoted semicolons intact`() {
        val parts = DatabaseManager.splitStatements(
            "INSERT INTO t VALUES ('a;b'); INSERT INTO t VALUES (\"c;d\"); SELECT 1"
        )
        assertEquals(3, parts.size)
        assertTrue(parts[0].contains("'a;b'"))
        assertTrue(parts[1].contains("\"c;d\""))
    }

    @Test
    fun `splitStatements drops empty chunks`() {
        assertEquals(1, DatabaseManager.splitStatements(";;  ;SELECT 1;;").size)
    }
}
