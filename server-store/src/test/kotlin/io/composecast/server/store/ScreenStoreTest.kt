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
        store.saveDraft("main", "v3-draft")
        val first = store.history("main").last()
        assertEquals(first, store.revision("main", first.id))
        assertNull(store.revision("other", first.id))
        store.rollback("main", first.id, by = "b")

        assertEquals("v1", store.live()["main"])
        assertEquals("v3-draft", store.get("main")!!.draftJson, "a rollback keeps unpublished work")
        assertEquals("b", store.get("main")!!.publishedBy)
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

    @Test
    fun `components live in their own tables with the same draft, publish and history (T-049)`() {
        val database = StoreDatabase.open(file)
        val components = ComponentStore(database, clock)
        components.saveDraft("tile", "{\"v\":1}")
        components.publish("tile", "{\"v\":1}", by = "a@x")
        components.publish("tile", "{\"v\":2}", by = "a@x")
        assertEquals(mapOf("tile" to "{\"v\":2}"), components.live())
        assertEquals(2, components.history("tile").size)
        assertTrue(ScreenStore(database, clock).get("tile") == null, "a component is not a screen")
        components.rollback("tile", components.history("tile").last().id, by = "a@x")
        assertEquals("{\"v\":1}", components.live()["tile"])
    }

    @Test
    fun `discardDraft clears a pending draft on a published screen only`() {
        val store = ScreenStore(StoreDatabase.open(file), clock)
        store.saveDraft("only-draft", "{\"v\":1}")
        assertEquals(false, store.discardDraft("only-draft"), "unpublished screen has no live version")
        assertEquals("{\"v\":1}", store.get("only-draft")!!.draftJson)

        store.publish("main", "{\"v\":1}", by = "a@x")
        assertEquals(false, store.discardDraft("main"), "no draft to discard")
        store.saveDraft("main", "{\"v\":2}")
        assertTrue(store.discardDraft("main"))
        val after = store.get("main")!!
        assertNull(after.draftJson)
        assertEquals("{\"v\":1}", after.liveJson)
    }
}
