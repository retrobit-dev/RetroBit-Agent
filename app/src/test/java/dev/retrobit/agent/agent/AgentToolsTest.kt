package dev.retrobit.agent.agent

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.retrobit.agent.ai.ToolCall
import dev.retrobit.agent.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AgentToolsTest {
    private lateinit var files: ProjectFiles
    private lateinit var db: DatabaseManager
    private lateinit var secrets: SecretStore
    private lateinit var runner: AgentToolRunner
    private var design = DesignSystem()
    private suspend fun call(name: String, args: JsonObject = buildJsonObject {}): ToolOutcome = runner.execute(ToolCall("test", name, args.toString()))
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        files = ProjectFiles(File(app.filesDir, "tool-project"))
        files.write("index.html", "<h1>Todo</h1>")
        db = DatabaseManager(app, "tool_test")
        secrets = SecretStore(app)
        runner = AgentToolRunner(files, db, secrets, { design }, { design = it }, {})
    }
    @After fun cleanup() { db.close() }
    @Test fun `write edit append read search and validate use actual filesystem`() = runBlocking {
        assertTrue(call("write_file", buildJsonObject { put("path", "app.js"); put("content", "let n=0;\n") }).success)
        assertTrue(call("append_file", buildJsonObject { put("path", "app.js"); put("content", "n++;\n") }).success)
        assertTrue(call("edit_file", buildJsonObject { put("path", "app.js"); put("find", "n=0"); put("replace", "n=1") }).success)
        assertTrue(call("read_file", buildJsonObject { put("path", "app.js") }).modelOutput.contains("n=1"))
        assertTrue(call("search_files", buildJsonObject { put("query", "n++") }).modelOutput.contains("app.js:2"))
        assertTrue(call("validate_project").success)
    }
    @Test fun `unknown malformed and schema-invalid calls fail without writes`() = runBlocking {
        assertFalse(call("shell").success)
        assertFalse(runner.execute(ToolCall("id", "list_files", "not json")).success)
        assertFalse(call("write_file", buildJsonObject { put("path", "bad.txt"); put("content", 1) }).success)
        assertFalse(files.exists("bad.txt"))
    }
    @Test fun `entry point aliases and project root cannot be deleted or renamed`() = runBlocking {
        for (path in listOf("index.html", "./index.html", ".", "./_meta")) {
            assertFalse(call("delete_file", buildJsonObject { put("path", path) }).success)
            assertFalse(call("rename_file", buildJsonObject { put("path", path); put("new_path", "gone") }).success)
        }
        assertTrue(files.exists("index.html"))
    }
    @Test fun `database tools persist tables and rows`() = runBlocking {
        assertTrue(call("create_table", buildJsonObject { put("name", "todos"); put("columns", buildJsonArray { add(buildJsonObject { put("name", "title"); put("type", "TEXT") }) }) }).success)
        assertTrue(call("run_sql", buildJsonObject { put("sql", "INSERT INTO todos(title) VALUES ('belajar')") }).success)
        assertTrue(call("inspect_database").modelOutput.contains("todos"))
        assertTrue(call("run_sql", buildJsonObject { put("sql", "SELECT title FROM todos") }).modelOutput.contains("belajar"))
    }
    @Test fun `unsafe SQL and injected column default are blocked`() = runBlocking {
        assertFalse(call("run_sql", buildJsonObject { put("sql", "PRAGMA\twritable_schema=1") }).success)
        assertFalse(call("create_table", buildJsonObject { put("name", "evil"); put("columns", buildJsonArray { add(buildJsonObject { put("name", "id"); put("default", "1); ATTACH 'x' AS y;--") }) }).success)
        assertFalse(db.tables().any { it.name == "evil" })
    }
    @Test fun `secret requests never return stored values`() = runBlocking {
        secrets.put(SecretEntry("PROJECT_KEY", "private-value", "test"))
        val result = call("request_secret", buildJsonObject { put("key", "PROJECT_KEY"); put("description", "API") })
        assertTrue(result.success); assertFalse(result.modelOutput.contains("private-value"))
        assertFalse(call("request_secret", buildJsonObject { put("key", "../x"); put("description", "x") }).success)
    }
    @Test fun `design tool stays grayscale pixel operator and bounded radius`() = runBlocking {
        assertTrue(call("update_design", buildJsonObject { put("primary", "#ff0000"); put("radius", 999); put("font", "Fancy") }).success)
        assertTrue(design.isGrayscale()); assertEquals("Pixel Operator", design.font); assertEquals(40, design.radius)
    }
    @Test fun `validation reports broken JSON without claiming compilation`() = runBlocking {
        files.write("bad.json", "{")
        val result = call("validate_project")
        assertFalse(result.success); assertTrue(result.modelOutput.contains("bad.json"))
        assertTrue(call("project_info").modelOutput.contains("bukan APK"))
    }
}
