package io.composecast.server.store

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenStoreTest {
    private val file = Files.createTempDirectory("composecast-store").resolve("data/compose-cast.db")
    private val clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC)
    private val store = ScreenStore(StoreDatabase.open(file), clock)

    @Test
    fun `drafts do not change what is live, publish does and is recorded`() {
        assertTrue(store.isEmpty())
        store.publish("main", "v1", by = "import")
        store.saveDraft("main", "v2-draft")
        store.saveDraft("new", "only a draft")

        assertEquals(mapOf("main" to "v1"), store.live(), "drafts and unpublished screens are not served")
        assertEquals(listOf("main", "new"), store.list().map { it.id })
        assertEquals("v2-draft", store.get("main")!!.draftJson)
        assertNull(store.get("new")!!.liveJson)

        store.publish("main", "v2", by = "admin@example.com")
        val main = store.get("main")!!
        assertEquals("v2", main.liveJson)
        assertNull(main.draftJson, "publish clears the draft")
        assertEquals("admin@example.com", main.publishedBy)
        assertEquals(Instant.parse("2026-11-23T10:00:00Z"), main.publishedAt)
        assertEquals(listOf("v2", "v1"), store.history("main").map { it.json })
    }

    @Test
    fun `publishAll is one transaction`() {
        store.publishAll(mapOf("a" to "1", "b" to "2"), by = "import")
        assertEquals(mapOf("a" to "1", "b" to "2"), store.live())
        assertFailsWith<Exception> {
            store.publishAll(mapOf("c" to "3", "x".repeat(200) to "too long id"), by = "import")
        }
        assertNull(store.get("c"), "a failed import leaves nothing behind")
    }

    @Test
    fun `rollback publishes an old revision as a new history row`() {
        store.publish("main", "v1", by = "a")
        store.publish("main", "v2", by = "a")
        val first = store.history("main").last()
        store.rollback("main", first.id, by = "b")

        assertEquals("v1", store.live()["main"])
        assertEquals(listOf("v1", "v2", "v1"), store.history("main").map { it.json }, "history is append-only")
        assertFailsWith<IllegalArgumentException> { store.rollback("main", 999, by = "b") }
        assertFailsWith<IllegalArgumentException> { store.rollback("other", first.id, by = "b") }
    }

    @Test
    fun `data survives reopening the file, settings are key-value`() {
        store.publish("main", "v1", by = "a")
        SettingsStore(StoreDatabase.open(file))[SettingsStore.BRAND] = """{"name":"X"}"""

        val reopened = StoreDatabase.open(file)
        assertEquals(mapOf("main" to "v1"), ScreenStore(reopened).live())
        val settings = SettingsStore(reopened)
        assertEquals("""{"name":"X"}""", settings[SettingsStore.BRAND])
        settings[SettingsStore.BRAND] = "changed"
        assertEquals("changed", settings[SettingsStore.BRAND])
        assertNull(settings["missing"])
    }
}
