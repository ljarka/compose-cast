package io.composecast.server

import io.composecast.protocol.ScreenJson
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.composeCast
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** The JSON copy of the demo screens (src/test/resources/screens) is the same product as the DSL. */
class JsonScreensTest {
    private val fakeImages = ImageLoader { _ ->
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }
    private val dir = Path.of(requireNotNull(javaClass.getResource("/screens")).toURI())

    private val fromDsl = demoScreens(fakeImages)
    private val fromJson = composeCast(fakeImages) {
        brand(demoBrand)
        screensFrom(dir)
    }

    @Test
    fun `JSON files define the same screens as the DSL`() {
        assertEquals(fromDsl.ids().sorted(), fromJson.ids().sorted())
        fromDsl.all().values.forEach { assertEquals(it.definition, fromJson.get(it.id)!!.definition) }
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
        // the JSON copy; the message shows the expected file content.
        fromDsl.all().values.forEach {
            assertEquals(ScreenJson.encode(it.definition), dir.resolve("${it.id}.json").readText().trimEnd())
        }
    }
}
