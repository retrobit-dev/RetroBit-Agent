package dev.retrobit.agent.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same-directory replacement: a failed write must not truncate the previous document. */
internal object AtomicFiles {
    fun writeText(target: File, text: String) {
        val parent = target.absoluteFile.parentFile!!
        check(parent.mkdirs() || parent.isDirectory) { "Direktori tidak dapat dibuat" }
        val temp = Files.createTempFile(parent.toPath(), ".retrobit-", ".partial").toFile()
        try {
            FileOutputStream(temp).use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}
