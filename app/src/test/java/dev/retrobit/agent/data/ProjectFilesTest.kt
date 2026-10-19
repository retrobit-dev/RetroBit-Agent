package dev.retrobit.agent.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var files: ProjectFiles

    @Before
    fun setUp() {
        root = tmp.newFolder("project")
        files = ProjectFiles(root)
    }

    // --- paths ---------------------------------------------------------------

    @Test
    fun `resolve keeps paths inside the project`() {
        assertEquals(File(root, "index.html").canonicalFile, files.resolve("index.html").canonicalFile)
        assertEquals(File(root, "a/b/c.css").canonicalFile, files.resolve("a/b/c.css").canonicalFile)
    }

    @Test
    fun `resolve strips a leading slash and normalises windows separators`() {
        assertEquals(File(root, "a/b.js").canonicalFile, files.resolve("/a\\b.js").canonicalFile)
    }

    @Test
    fun `resolve refuses traversal`() {
        listOf("../secret.txt", "a/../../secret.txt", "a/../..").forEach { path ->
            val failed = runCatching { files.resolve(path) }.isFailure
            assertTrue("expected $path to be rejected", failed)
        }
    }

    @Test
    fun `resolve refuses an empty path`() {
        assertTrue(runCatching { files.resolve("") }.isFailure)
        assertTrue(runCatching { files.resolve("   ") }.isFailure)
    }

    @Test
    fun `meta is off limits`() {
        listOf("_meta", "_meta/chat.json", "_meta/snapshots/x.zip").forEach { path ->
            val failed = runCatching { files.resolve(path) }.isFailure
            assertTrue("expected $path to be rejected", failed)
        }
    }

    @Test
    fun `exists swallows invalid paths instead of throwing`() {
        assertFalse(files.exists("../outside"))
        assertFalse(files.exists("_meta/chat.json"))
        files.write("yes.txt", "a")
        assertTrue(files.exists("yes.txt"))
    }

    // --- read and write ------------------------------------------------------

    @Test
    fun `write creates parent directories`() {
        files.write("assets/css/app.css", "body{}")
        assertTrue(File(root, "assets/css/app.css").isFile)
        assertEquals("body{}", files.read("assets/css/app.css"))
    }

    @Test
    fun `read returns null for missing files`() {
        assertNull(files.read("nope.html"))
    }

    @Test
    fun `append extends the file`() {
        files.write("log.txt", "one\n")
        files.append("log.txt", "two\n")
        assertEquals("one\ntwo\n", files.read("log.txt"))
    }

    @Test
    fun `edit replaces the first match only unless all is set`() {
        files.write("index.html", "<p>a</p><p>a</p>")
        assertTrue(files.edit("index.html", "a", "b"))
        assertEquals("<p>b</p><p>a</p>", files.read("index.html"))

        files.write("index.html", "<p>a</p><p>a</p>")
        assertTrue(files.edit("index.html", "a", "b", all = true))
        assertEquals("<p>b</p><p>b</p>", files.read("index.html"))
    }

    @Test
    fun `edit reports a miss without touching the file`() {
        files.write("index.html", "hello")
        assertFalse(files.edit("index.html", "absent", "x"))
        assertEquals("hello", files.read("index.html"))
        assertFalse(files.edit("missing.html", "a", "b"))
    }

    @Test
    fun `rename moves the file and refuses unknown sources`() {
        files.write("a.html", "x")
        assertTrue(files.rename("a.html", "pages/a.html"))
        assertFalse(File(root, "a.html").exists())
        assertEquals("x", files.read("pages/a.html"))
        assertFalse(files.rename("ghost.html", "b.html"))
    }

    @Test
    fun `delete removes files and directories`() {
        files.write("a/b.txt", "x")
        assertTrue(files.delete("a"))
        assertFalse(File(root, "a").exists())
    }

    @Test
    fun `entry point is seeded once`() {
        assertTrue(files.ensureEntryPoint())
        assertTrue(files.readEntryPoint().contains("RetroBit Preview"))
        assertFalse(files.ensureEntryPoint())
    }

    // --- listing -------------------------------------------------------------

    @Test
    fun `list hides metadata and build folders`() {
        files.write("index.html", "x")
        files.write("assets/app.css", "y")
        files.write("node_modules/pkg/index.js", "z")
        files.write("dist/bundle.js", "z")
        files.metaFile("chat.json").writeText("{}")

        val paths = files.list().map { it.path }

        assertEquals(listOf("assets", "assets/app.css", "index.html"), paths)
        assertFalse(paths.any { it.startsWith("_meta") })
        assertFalse(paths.any { it.startsWith("node_modules") })
        assertFalse(paths.any { it.startsWith("dist") })
    }

    @Test
    fun `list sorts directories before files`() {
        files.write("b.txt", "x")
        files.write("a/z.txt", "y")
        // `name` is the leaf, `path` is relative to the project root.
        assertEquals(listOf("a", "z.txt", "b.txt"), files.list().map { it.name })
        assertEquals(listOf("a", "a/z.txt", "b.txt"), files.list().map { it.path })
    }

    @Test
    fun `tree text marks empty projects`() {
        assertEquals("(kosong)", files.treeText())
        files.write("index.html", "x")
        assertTrue(files.treeText().contains("index.html"))
    }

    @Test
    fun `text file detection covers code and extensionless files`() {
        assertTrue(files.isTextFile("index.html"))
        assertTrue(files.isTextFile("styles.CSS"))
        assertTrue(files.isTextFile("Dockerfile"))
        assertFalse(files.isTextFile("logo.png"))
        assertFalse(files.isTextFile("font.ttf"))
    }

    @Test
    fun `format bytes switches units`() {
        // The decimal separator follows the JVM locale, so only the unit and the
        // number are asserted.
        assertEquals("512 B", ProjectFiles.formatBytes(512))
        assertTrue(ProjectFiles.formatBytes(1024).startsWith("1"))
        assertTrue(ProjectFiles.formatBytes(1024).endsWith(" KB"))
        assertTrue(ProjectFiles.formatBytes((1.5 * 1024 * 1024).toLong()).endsWith(" MB"))
    }

    // --- snapshots -----------------------------------------------------------

    @Test
    fun `snapshot lists sources and skips metadata`() {
        files.write("index.html", "<h1>hi</h1>")
        files.write("logo.png", "not really a png")
        files.metaFile("chat.json").writeText("{\"secret\":true}")

        val snapshot = files.snapshot()

        assertTrue(snapshot.contains("--- index.html ---"))
        assertTrue(snapshot.contains("<h1>hi</h1>"))
        assertTrue(snapshot.contains("logo.png (binary"))
        assertFalse(snapshot.contains("chat.json"))
    }

    @Test
    fun `snapshot truncates huge files`() {
        files.write("big.html", "a".repeat(50))
        val snapshot = files.snapshot(maxCharsPerFile = 10)
        assertTrue(snapshot.contains("aaaaaaaaaa"))
        assertFalse(snapshot.contains("a".repeat(11)))
        assertTrue(snapshot.contains("dipotong"))
    }

    @Test
    fun `export zip leaves metadata out and can be restored`() {
        files.write("index.html", "v1")
        files.write("assets/app.css", "body{}")
        files.metaFile("chat.json").writeText("{}")
        val zip = files.exportZip(File(tmp.root, "out.zip"))

        assertTrue(zip.isFile)
        assertFalse(zipNames(zip).any { it.startsWith("_meta") })
        assertEquals(listOf("assets/app.css", "index.html"), zipNames(zip))

        files.write("index.html", "v2")
        files.write("junk.txt", "junk")
        assertTrue(files.restoreSnapshot(zip))
        assertEquals("v1", files.read("index.html"))
        assertNull(files.read("junk.txt"))
    }

    @Test
    fun `snapshot is skipped for an empty project`() {
        assertNull(files.createSnapshot())
        assertNull(files.latestSnapshot())
    }

    @Test
    fun `snapshots rotate after ten`() {
        files.write("index.html", "x")
        repeat(12) {
            files.write("index.html", "version $it")
            assertNotNull(files.createSnapshot())
            Thread.sleep(3)
        }
        val stored = File(root, "_meta/snapshots").listFiles()!!.filter { it.extension == "zip" }
        assertEquals(10, stored.size)
    }

    @Test
    fun `restore ignores zip-slip entries`() {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(evil.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("good.txt"))
            zip.write("ok".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }

        assertTrue(files.restoreSnapshot(evil))
        assertEquals("ok", files.read("good.txt"))
        assertFalse(File(root.parentFile, "escaped.txt").exists())
    }

    @Test
    fun `restore rejects a file that is not a zip`() {
        val broken = File(tmp.root, "broken.zip").apply { writeText("not a zip") }
        assertFalse(files.restoreSnapshot(broken))
    }

    // --- clearing ------------------------------------------------------------

    @Test
    fun `clear sources keeps the conversation`() {
        files.write("index.html", "x")
        files.write("assets/app.css", "y")
        files.metaFile("chat.json").writeText("{}")

        files.clearSources()

        assertFalse(File(root, "index.html").exists())
        assertTrue(File(root, "_meta/chat.json").isFile)
    }

    private fun zipNames(zip: File): List<String> =
        java.util.zip.ZipFile(zip).use { file -> file.entries().toList().map { it.name }.sorted() }
}
