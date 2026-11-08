package io.composecast.server

import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.ImageLoader
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * `examples/demo` (JSON screens + brand.json, servable with `COMPOSECAST_DATA_DIR=examples/demo`) is
 * the same product as the DSL `demoScreens()`.
 */
class JsonScreensTest {
    private val fakeImages = ImageLoader { _ ->
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }
    private val dataDir = Path.of(System.getProperty("composecast.examples"), "demo")
    private val dir = dataDir.resolve("screens")

    private val fromDsl = demoScreens(fakeImages)
    private val fromJson = loadScreens(dataDir, fakeImages)

    @Test
    fun `brand json equals the DSL brand`() {
        assertEquals(demoBrand, fromJson.brand)
    }

    @Test
    fun `JSON files define the same screens as the DSL`() {
        // Dynamic DSL screens (echo) have no JSON form; compare static and bound ones.
        assertEquals(fromDsl.definitions().keys.sorted(), fromJson.ids().sorted())
        fromDsl.definitions().forEach { (id, definition) -> assertEquals(definition, fromJson.definitions()[id]) }
        assertEquals(fromDsl.definitions().keys.filter(fromDsl::isBound), fromJson.ids().filter(fromJson::isBound))
    }

    @Test
    fun `JSON files render byte-identical Remote Compose documents`() {
        fromDsl.all().values.forEach {
            assertContentEquals(it.document, fromJson.get(it.id)!!.document, "screen '${it.id}' differs")
        }
    }

    @Test
    fun `JSON files are in canonical form (encode of DSL screen == file)`() {
        // Fails when a file is edited by hand in a non-canonical way, or the DSL changed without
        // the JSON copy; the message shows the expected file content. Set
        // -Dcomposecast.writeExamples=true to rewrite the files from the DSL (then review the diff).
        val write = System.getProperty("composecast.writeExamples") == "true"
        fromDsl.definitions().forEach { (id, definition) ->
            val file = dir.resolve("$id.json")
            if (write) file.writeText(ScreenJson.encode(definition) + "\n")
            assertEquals(ScreenJson.encode(definition), file.readText().trimEnd())
        }
    }
}
