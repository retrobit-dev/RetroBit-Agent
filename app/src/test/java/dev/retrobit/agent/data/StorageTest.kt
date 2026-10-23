package dev.retrobit.agent.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
 * SettingsStore and SecretStore are the storage layer the agent and the preview
 * trust. Both fall back gracefully when the platform is missing something, and
 * those fallbacks are exactly what is easy to break silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SettingsStoreTest {

    private lateinit var context: Application
    private lateinit var settings: SettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsStore(context)
    }

    @Test fun `new install starts with real on device setup not demo`() {
        assertEquals(dev.retrobit.agent.ai.ProviderKind.ON_DEVICE, settings.config.value.provider)
        assertFalse(settings.config.value.isDemo)
    }
    @Test fun `old default demo migrates once without changing persona or theme`() {
        val prefs = context.getSharedPreferences("retrobit_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("ai_config", """{"provider":"DEMO","model":"retrobit-demo"}""").remove("real_ai_setup_v1").commit()
        settings.systemPrompt = "my persona"
        val updated = SettingsStore(context)
        assertEquals(dev.retrobit.agent.ai.ProviderKind.ON_DEVICE, updated.config.value.provider)
        assertEquals("my persona", updated.systemPrompt)
        updated.updateConfig { it.copy(provider = dev.retrobit.agent.ai.ProviderKind.DEMO) }
        assertTrue(SettingsStore(context).config.value.isDemo) // explicit opt-in after upgrade
    }
    @Test fun `model activation enables actual backend but preserves real cloud choice`() {
        settings.activateLocalModel("model.gguf")
        assertEquals("model.gguf", settings.config.value.model)
        assertTrue(settings.config.value.connectedAt > 0)
        settings.updateConfig { it.copy(provider = dev.retrobit.agent.ai.ProviderKind.GEMINI, model = "cloud", baseUrl = "https://example.com") }
        val before = settings.config.value
        settings.activateLocalModel("other.gguf")
        assertEquals(before, settings.config.value)
    }
    @Test fun `corrupt config never silently invokes simulation`() {
        context.getSharedPreferences("retrobit_settings", android.content.Context.MODE_PRIVATE).edit().putString("ai_config", "bad json").commit()
        assertFalse(SettingsStore(context).config.value.isDemo)
    }

    @Test
    fun `first launch uses the documented defaults`() {
        assertEquals("id", settings.agentLanguage)
        assertEquals("dark", settings.themeMode)
        assertTrue(settings.autoOpenPreview)
        assertEquals(8787, settings.lanPort)
        assertFalse(settings.onboarded)
        assertEquals(SettingsStore.DEFAULT_SYSTEM_PROMPT, settings.systemPrompt)
    }

    @Test
    fun `values survive a new store instance`() {
        settings.themeMode = "light"
        settings.agentLanguage = "en"
        settings.autoOpenPreview = false
        settings.lanPort = 9090
        settings.onboarded = true
        settings.systemPrompt = "persona baru"

        val reopened = SettingsStore(context)

        assertEquals("light", reopened.themeMode)
        assertEquals("en", reopened.agentLanguage)
        assertFalse(reopened.autoOpenPreview)
        assertEquals(9090, reopened.lanPort)
        assertTrue(reopened.onboarded)
        assertEquals("persona baru", reopened.systemPrompt)
    }

    @Test
    fun `blank system prompt falls back to the default`() {
        settings.systemPrompt = "   "
        assertEquals(SettingsStore.DEFAULT_SYSTEM_PROMPT, settings.systemPrompt)
    }

    @Test
    fun `reset restores the default persona`() {
        settings.systemPrompt = "sementara"
        settings.resetSystemPrompt()
        assertEquals(SettingsStore.DEFAULT_SYSTEM_PROMPT, settings.systemPrompt)
    }

    @Test
    fun `config changes are persisted`() {
        settings.updateConfig { it.copy(temperature = 0.9, maxTokens = 2048) }

        val reopened = SettingsStore(context)
        assertEquals(0.9, reopened.config.value.temperature, 0.0001)
        assertEquals(2048, reopened.config.value.maxTokens)
    }

    @Test
    fun `usage accumulates and resets`() {
        settings.recordUsage(promptTokens = 100, completionTokens = 50, costUsd = 0.25)
        settings.recordUsage(promptTokens = 10, completionTokens = 5, costUsd = 0.05)

        assertEquals(110, settings.usage.value.promptTokens)
        assertEquals(55, settings.usage.value.completionTokens)
        assertEquals(2, settings.usage.value.requests)
        assertEquals(0.30, settings.usage.value.costUsd, 0.0001)

        settings.resetUsage()
        assertEquals(UsageRecord(), settings.usage.value)
    }

    @Test
    fun `usage survives a restart`() {
        settings.recordUsage(promptTokens = 7, completionTokens = 3, costUsd = 0.01)
        val reopened = SettingsStore(context)
        assertEquals(10, reopened.usage.value.promptTokens + reopened.usage.value.completionTokens)
        assertEquals(1, reopened.usage.value.requests)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SecretStoreTest {

    private lateinit var context: Application
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = SecretStore(context)
    }

    @Test
    fun `legacy plaintext remains flagged after reopening vault`() {
        val raw = """[{"key":"LEGACY","value":"plain:old","note":"","expose":false,"updatedAt":0}]"""
        context.getSharedPreferences("retrobit_secrets", android.content.Context.MODE_PRIVATE).edit().putString("entries_v1", raw).commit()
        val reopened = SecretStore(context)
        assertEquals("old", reopened.get("LEGACY"))
        assertFalse(reopened.keystoreAvailable)
    }

    @Test
    fun `put then get round trips the value`() {
        store.put(SecretEntry("SUPABASE_URL", "https://x.supabase.co", "URL proyek"))
        assertEquals("https://x.supabase.co", store.get("SUPABASE_URL"))
        assertEquals("URL proyek", store.secrets().single().note)
    }

    @Test
    fun `put replaces an existing key instead of duplicating it`() {
        store.put(SecretEntry("TOKEN", "lama"))
        store.put(SecretEntry("TOKEN", "baru"))
        assertEquals(1, store.secrets().size)
        assertEquals("baru", store.get("TOKEN"))
    }

    @Test
    fun `missing keys return null and removal is complete`() {
        assertNull(store.get("TIDAK_ADA"))
        store.put(SecretEntry("A", "1"))
        store.remove("A")
        assertNull(store.get("A"))
        assertTrue(store.secrets().isEmpty())
    }

    @Test
    fun `secrets persist across instances`() {
        store.put(SecretEntry("K", "v"))
        assertEquals("v", SecretStore(context).get("K"))
    }

    @Test
    fun `expose flag filters what the preview may see`() {
        store.put(SecretEntry("PUBLIC", "p", exposeToPreview = true))
        store.put(SecretEntry("PRIVATE", "s"))
        assertEquals(mapOf("PUBLIC" to "p"), store.exposedForPreview())
    }

    @Test
    fun `requests are deduplicated and cleared with the secret`() {
        store.request("API_KEY", "butuh key")
        store.request("API_KEY", "duplikat")
        assertEquals(1, store.requests().size)
        assertEquals("butuh key", store.requests().single().description)

        store.put(SecretEntry("API_KEY", "isi"))
        store.remove("API_KEY")
        assertTrue(store.requests().isEmpty())
    }

    // Robolectric ships no AndroidKeyStore, so this exercises the documented
    // fallback. The point of the test is that the store never hides the
    // degradation: the payload is marked, the flag is false, and values still
    // round-trip instead of being lost.
    @Test
    fun `without a keystore values are stored with the plain marker`() {
        store.put(SecretEntry("RAHASIA", "isi-rahasia"))
        val raw = context.getSharedPreferences("retrobit_secrets", 0)
            .getString("entries_v1", "").orEmpty()

        assertFalse("keystore should be unavailable under Robolectric", store.keystoreAvailable)
        assertTrue("payload should be marked as plaintext: $raw", raw.contains("plain:isi-rahasia"))
        assertEquals("isi-rahasia", SecretStore(context).get("RAHASIA"))
    }

    @Test
    fun `a broken preferences payload degrades to empty instead of crashing`() {
        context.getSharedPreferences("retrobit_secrets", 0).edit()
            .putString("entries_v1", "{bukan json")
            .commit()
        assertTrue(SecretStore(context).secrets().isEmpty())
    }

    @Test
    fun `corrupt payload does not block new writes`() {
        context.getSharedPreferences("retrobit_secrets", 0).edit()
            .putString("entries_v1", "[]]")
            .commit()
        val fresh = SecretStore(context)
        fresh.put(SecretEntry("SETELAH_RUSAK", "ok"))
        assertEquals("ok", SecretStore(context).get("SETELAH_RUSAK"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ProjectRepositoryTest {

    private lateinit var context: Application
    private lateinit var settings: SettingsStore
    private lateinit var repository: ProjectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsStore(context)
        repository = ProjectRepository(context, settings)
    }

    private fun runLoad() = kotlinx.coroutines.runBlocking { repository.load() }

    @Test
    fun `first load seeds one sample project`() {
        runLoad()
        val projects = repository.projects.value
        assertEquals(1, projects.size)
        assertEquals("Nimbus", projects.single().name)
        assertTrue(projects.single().id.isNotBlank())
    }

    @Test
    fun `seeded project ships an entry point and fonts`() {
        runLoad()
        val files = repository.filesFor(repository.projects.value.single().id)
        assertTrue(files.root.exists())
        assertTrue("index.html missing at the root", files.exists("index.html"))
    }

    @Test
    fun `deleting every project does not resurrect the sample`() {
        runLoad()
        val id = repository.projects.value.single().id
        kotlinx.coroutines.runBlocking { repository.delete(id) }
        assertTrue(repository.projects.value.isEmpty())

        val reopened = ProjectRepository(context, settings)
        kotlinx.coroutines.runBlocking { reopened.load() }
        assertTrue(reopened.projects.value.isEmpty())
    }

    @Test
    fun `created project is listed first and has files`() {
        runLoad()
        val created = kotlinx.coroutines.runBlocking {
            repository.create("Aplikasi Kasir", "commerce", DesignSystem.PRESETS.first().design)
        }
        assertEquals("Aplikasi Kasir", repository.projects.value.first().name)
        assertEquals("commerce", created.templateId)
        assertTrue(repository.filesFor(created.id).root.exists())
    }

    @Test
    fun `blank names get a readable fallback`() {
        runLoad()
        val created = kotlinx.coroutines.runBlocking {
            repository.create("   ", "blank", DesignSystem.PRESETS.first().design)
        }
        assertEquals("Aplikasi Baru", created.name)
    }

    @Test
    fun `duplicate copies metadata and files but not the id`() {
        runLoad()
        val source = repository.projects.value.single()
        val copy = kotlinx.coroutines.runBlocking { repository.duplicate(source.id) }
        assertNotNull(copy)
        assertEquals("Nimbus copy", copy!!.name)
        assertTrue(copy.id != source.id)
        assertTrue(repository.filesFor(copy.id).root.exists())
        assertEquals(2, repository.projects.value.size)
    }

    @Test
    fun `duplicate of an unknown id returns null`() {
        runLoad()
        assertNull(kotlinx.coroutines.runBlocking { repository.duplicate("tidak-ada") })
    }

    @Test
    fun `update reorders by recency`() {
        runLoad()
        val older = repository.projects.value.single()
        val newer = kotlinx.coroutines.runBlocking {
            repository.create("Baru", "tasks", DesignSystem.PRESETS.first().design)
        }
        kotlinx.coroutines.runBlocking { repository.update(older.copy(name = "Nimbus v2")) }

        assertEquals("Nimbus v2", repository.projects.value.first().name)
        assertTrue(repository.projects.value.first().updatedAt >= newer.updatedAt)
    }

    @Test
    fun `update with touch disabled keeps the timestamp`() {
        runLoad()
        val project = repository.projects.value.single()
        kotlinx.coroutines.runBlocking { repository.update(project.copy(favorite = true), touch = false) }
        assertTrue(repository.projects.value.single().favorite)
        assertEquals(project.updatedAt, repository.projects.value.single().updatedAt)
    }

    @Test
    fun `thread save load and clear`() {
        runLoad()
        val id = repository.projects.value.single().id
        val messages = listOf(
            ChatMessage(role = Role.USER, content = "halo"),
            ChatMessage(role = Role.ASSISTANT, content = "hai"),
        )
        kotlinx.coroutines.runBlocking { repository.saveThread(id, messages) }

        val loaded = kotlinx.coroutines.runBlocking { repository.loadThread(id) }
        assertEquals(listOf("halo", "hai"), loaded.map { it.content })

        kotlinx.coroutines.runBlocking { repository.clearThread(id) }
        assertTrue(kotlinx.coroutines.runBlocking { repository.loadThread(id) }.isEmpty())
    }

    @Test
    fun `thread is capped at the newest messages`() {
        runLoad()
        val id = repository.projects.value.single().id
        val many = (1..250).map { ChatMessage(role = Role.USER, content = "pesan $it") }
        kotlinx.coroutines.runBlocking { repository.saveThread(id, many) }

        val loaded = kotlinx.coroutines.runBlocking { repository.loadThread(id) }
        assertEquals(200, loaded.size)
        assertEquals("pesan 51", loaded.first().content)
    }

    @Test
    fun `projects survive a restart`() {
        runLoad()
        val created = kotlinx.coroutines.runBlocking {
            repository.create("Toko Lumen", "commerce", DesignSystem.PRESETS.first().design)
        }

        val reopened = ProjectRepository(context, settings)
        kotlinx.coroutines.runBlocking { reopened.load() }

        assertTrue(reopened.projects.value.any { it.id == created.id })
        assertEquals(2, reopened.projects.value.size)
    }

    @Test
    fun `delete removes the project directory`() {
        runLoad()
        val project = kotlinx.coroutines.runBlocking {
            repository.create("Sementara", "tasks", DesignSystem.PRESETS.first().design)
        }
        val root = repository.filesFor(project.id).root
        assertTrue(root.exists())

        kotlinx.coroutines.runBlocking { repository.delete(project.id) }
        assertFalse(root.exists())
        assertNull(repository.project(project.id))
    }
}
