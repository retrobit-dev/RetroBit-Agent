package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class PreviewFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun policy() = PreviewFiles(ProjectFiles(temp.newFolder()))
    @Test fun `local origin is per project and not a file URL`() {
        val a = policy(); val b = policy()
        assertNotEquals(a.origin, b.origin)
        assertTrue(a.isLocal(a.origin + "/index.html"))
        listOf("file:///data/private", "https://example.com", a.origin + ".evil.test/", a.origin.replace("https:", "http:"), a.origin.replace("https://", "https://user@"), a.origin + ":444/").forEach {
            assertFalse(it, a.isLocal(it))
        }
    }
    @Test fun `root maps to entrypoint and path plus is not a space`() {
        val p = policy()
        assertEquals("index.html", p.resolve("/").name)
        assertEquals("a+b.css", p.resolve("/a+b.css").name)
        assertEquals("a b.css", p.resolve("/a%20b.css").name)
    }
    @Test fun `metadata traversal malformed encodings controls and absolute paths rejected`() {
        val p = policy()
        listOf("/_meta/chat.json", "/./_meta/chat.json", "/%5fmeta/chat.json", "/%2e%2e/private", "/../private", "//private", "/%2fprivate", "/a%5cb", "/%00", "/%0a", "/%ZZ", "/.git/config", "/.env", "/.env.production", "/release.jks", "https://evil.test/").forEach {
            assertTrue(it, runCatching { p.resolve(it) }.isFailure)
        }
    }
    @Test fun `symlinks cannot publish private siblings or metadata`() {
        val root = temp.newFolder(); val outside = temp.newFolder()
        Files.createSymbolicLink(File(root, "outside").toPath(), outside.toPath())
        val meta = File(root, "_meta").apply { mkdirs() }
        Files.createSymbolicLink(File(root, "alias").toPath(), meta.toPath())
        val p = PreviewFiles(ProjectFiles(root))
        assertTrue(runCatching { p.resolve("/outside/secret") }.isFailure)
        assertTrue(runCatching { p.resolve("/alias/chat.json") }.isFailure)
    }
    @Test fun `mime includes sibling scripts and fonts and CSP prevents frames`() {
        assertEquals("application/javascript", PreviewFiles.mime("app.JS"))
        assertEquals("font/ttf", PreviewFiles.mime("PixelOperator.ttf"))
        assertTrue(PreviewFiles.CSP.contains("frame-src 'none'"))
        assertTrue(PreviewFiles.CSP.contains("object-src 'none'"))
    }
}
