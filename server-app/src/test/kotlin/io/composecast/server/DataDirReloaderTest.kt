package io.composecast.server

import io.composecast.server.sdk.ImageLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-042: edits of the data directory are served without a restart; a broken edit is not. */
class DataDirReloaderTest {
    private val noImages = ImageLoader { error("no images") }
    private val dir: Path = Files.createTempDirectory("composecast-reload")
    private val screensDir = dir.resolve("screens").createDirectories()

    private fun writeScreen(id: String, title: String) = screensDir.resolve("$id.json").writeText(
        """{"id": "$id", "title": "$title", "components": [{"type": "text", "text": "x"}]}""",
    )

    private fun load(path: Path) = loadScreens(path, imageLoader = noImages)

    @Test
    fun `changed, added and removed files are reloaded, a broken file keeps the old screens`() {
        writeScreen("main", "One")
        val screens = LiveScreens(load(dir))
        val reloader = DataDirReloader(dir, screens, ::load, debounceMs = 0)
        assertFalse(reloader.reloadIfChanged(), "nothing changed yet")

        writeScreen("main", "Second title")
        assertTrue(reloader.reloadIfChanged())
        assertEquals("Second title", screens.current.get("main")!!.title)

        writeScreen("other", "Other")
        assertTrue(reloader.reloadIfChanged())
        assertEquals(listOf("main", "other"), screens.current.ids().sorted())

        screensDir.resolve("other.json").deleteIfExists()
        assertTrue(reloader.reloadIfChanged())
        assertNull(screens.current.get("other"))

        screensDir.resolve("main.json").writeText("""{"id": "main", "components": [{"type": "nope"}]}""")
        assertFalse(reloader.reloadIfChanged(), "invalid screen must not be served")
        assertEquals("Second title", screens.current.get("main")!!.title)
        assertFalse(reloader.reloadIfChanged(), "a failed version is not retried every poll")

        writeScreen("main", "Fixed")
        assertTrue(reloader.reloadIfChanged())
        assertEquals("Fixed", screens.current.get("main")!!.title)
    }
}
