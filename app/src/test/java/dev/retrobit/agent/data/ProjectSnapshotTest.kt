package dev.retrobit.agent.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectSnapshotTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var files: ProjectFiles
    @Before fun setup() {
        files = ProjectFiles(temp.newFolder())
        files.write("index.html", "old source")
        files.metaFile("chat.json").writeText("private metadata")
    }
    private fun zip(vararg sources: Pair<String, String>): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> sources.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry()
        } }
    }
    private fun unchanged() {
        assertEquals("old source", files.read("index.html"))
        assertEquals("private metadata", files.metaFile("chat.json").readText())
    }
    @Test fun `metadata injection rejected before live source deletion`() {
        assertFalse(files.restoreSnapshot(zip("index.html" to "new", "./_meta/chat.json" to "replace")))
        unchanged()
    }
    @Test fun `canonical duplicate names rejected as whole transaction`() {
        assertFalse(files.restoreSnapshot(zip("index.html" to "new", "./index.html" to "duplicate")))
        unchanged()
    }
    @Test fun `file directory conflicts leave sources and metadata untouched`() {
        assertFalse(files.restoreSnapshot(zip("a" to "file", "a/b" to "child")))
        unchanged()
    }
    @Test fun `per file total and entry caps enforced while staging`() {
        val archive = zip("index.html" to "123456", "extra.txt" to "abcdef")
        for (limits in listOf(ProjectSnapshot.Limits(perFile = 5), ProjectSnapshot.Limits(total = 8), ProjectSnapshot.Limits(entries = 1))) {
            assertFalse(ProjectSnapshot.restore(files, archive, limits)); unchanged()
        }
    }
    @Test fun `truncated directory cannot be mistaken for complete snapshot`() {
        val archive = zip("index.html" to "new")
        archive.writeBytes(archive.readBytes().dropLast(10).toByteArray())
        assertFalse(files.restoreSnapshot(archive)); unchanged()
    }
    @Test fun `move failure after promotion rolls back all originals`() {
        files.write("keep.txt", "keep")
        var calls = 0
        val result = ProjectSnapshot.restore(files, zip("index.html" to "new", "new.txt" to "new"), move = { from, to ->
            if (++calls == 4) error("injected disk failure")
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        })
        assertFalse(result); unchanged(); assertEquals("keep", files.read("keep.txt")); assertNull(files.read("new.txt"))
    }
    @Test fun `successful restore replaces sources but not private metadata`() {
        files.write("remove.txt", "old")
        assertTrue(files.restoreSnapshot(zip("index.html" to "new", "assets/app.js" to "js")))
        assertEquals("new", files.read("index.html")); assertNull(files.read("remove.txt"))
        assertEquals("private metadata", files.metaFile("chat.json").readText())
        assertFalse(files.metaFile(".").listFiles()!!.any { it.name.startsWith("restore-") })
    }
    private fun interrupted(complete: Boolean): File {
        val dir = files.metaFile("restore-simulation").apply { mkdirs() }
        val backup = File(dir, "previous").apply { mkdirs() }
        // These raw moves model process death between the actual commit steps.
        Files.move(File(files.root, "index.html").toPath(), File(backup, "index.html").toPath())
        File(files.root, "index.html").writeText("partial new")
        File(files.root, "new.txt").writeText("new")
        File(dir, "journal.json").writeText(Json.encodeToString(ProjectSnapshot.Journal(listOf("index.html"), listOf("index.html", "new.txt"))))
        if (complete) File(dir, "complete").writeText("done")
        return dir
    }
    @Test fun `reopening after interrupted commit recovers old sources and removes new only files`() {
        val dir = interrupted(false)
        ProjectFiles(files.root)
        unchanged(); assertNull(files.read("new.txt")); assertFalse(dir.exists())
    }
    @Test fun `completed commit kept when process died before cleanup`() {
        val dir = interrupted(true)
        ProjectFiles(files.root)
        assertEquals("partial new", files.read("index.html")); assertEquals("new", files.read("new.txt")); assertFalse(dir.exists())
    }
    @Test fun `atomic replacement preserves source if destination cannot be replaced`() {
        val dir = File(files.root, "directory").apply { mkdirs() }
        File(dir, "keep").writeText("keep")
        assertTrue(runCatching { files.write("directory", "bad replacement") }.isFailure)
        unchanged(); assertEquals("keep", File(dir, "keep").readText())
        assertFalse(files.root.listFiles()!!.any { it.name.endsWith(".partial") })
    }
    @Test fun `independent project handles serialize mutations on same root`() {
        assertSame(files.sourceLock, ProjectFiles(files.root).sourceLock)
    }
}
