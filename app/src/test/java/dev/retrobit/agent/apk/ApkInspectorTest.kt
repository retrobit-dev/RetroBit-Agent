package dev.retrobit.agent.apk

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ApkInspectorTest {
    private lateinit var app: Application
    private lateinit var info: PackageInfo
    private lateinit var service: ApkInspector
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        info = PackageInfo().apply {
            packageName = "test.sample"; versionName = "1.2"; longVersionCode = 42
            applicationInfo = ApplicationInfo().apply { minSdkVersion = 26; targetSdkVersion = 35 }
            requestedPermissions = arrayOf("android.permission.INTERNET")
            activities = arrayOf(ActivityInfo().apply { name = "test.sample.Main"; exported = true; enabled = true })
        }
        service = ApkInspector(app) { info }
    }
    private fun bytes(): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> zip.putNextEntry(ZipEntry("AndroidManifest.xml")); zip.write(byteArrayOf(3, 0, 8, 0, 8, 0, 0, 0)); zip.closeEntry() }
    }.toByteArray()
    private fun assertClean() { assertTrue(File(app.noBackupFilesDir, "apk-inspector").listFiles()!!.isEmpty()) }
    @Test fun `report uses package metadata and hash of copied bytes then removes APK`() = runBlocking {
        val source = bytes()
        val report = service.analyze(source.inputStream(), "sample.apk") {}
        assertEquals("test.sample", report.packageName); assertEquals(42L, report.versionCode)
        assertEquals(26, report.minSdk); assertEquals(1, report.permissionCount); assertTrue(report.components.single().exported)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it.toInt() and 255) }, report.sha256)
        val json = Json.parseToJsonElement(report.json()).jsonObject
        assertEquals("test.sample", json.getValue("packageName").jsonPrimitive.content)
        assertTrue(report.summary().contains("bukan bukti keamanan")); assertClean()
    }
    @Test fun `bad ZIP never reaches Android parser and leaves no file`() = runBlocking {
        var parsed = false
        val parser = ApkInspector(app) { parsed = true; info }
        assertTrue(runCatching { parser.analyze("bad zip".byteInputStream(), "bad.apk") {} }.isFailure)
        assertFalse(parsed); assertClean()
    }
    @Test fun `Android rejection produces no misleading partial report`() = runBlocking {
        val parser = ApkInspector(app) { null }
        assertTrue(runCatching { parser.analyze(bytes().inputStream(), "bad.apk") {} }.isFailure)
        assertClean()
    }
    @Test fun `cancellation in progress callback deletes temporary APK`() = runBlocking {
        val error = runCatching { service.analyze(bytes().inputStream(), "sample.apk") { throw CancellationException("stop") } }.exceptionOrNull()
        assertTrue(error is CancellationException); assertClean()
    }
    @Test fun `permission and component previews are capped with totals retained`() = runBlocking {
        info.requestedPermissions = Array(600) { "permission.$it" }
        info.activities = Array(250) { ActivityInfo().apply { name = "activity.$it" } }
        val report = service.analyze(bytes().inputStream(), "sample.apk") {}
        assertEquals(600, report.permissionCount); assertEquals(500, report.permissions.size)
        assertEquals(250, report.componentCount); assertEquals(200, report.components.size); assertClean()
    }
    @Test fun `stale APK cleaned when inspector is reopened`() {
        val stale = File(app.noBackupFilesDir, "apk-inspector/stale.partial.apk").apply { writeText("partial") }
        ApkInspector(app) { info }
        assertFalse(stale.exists())
    }
}
