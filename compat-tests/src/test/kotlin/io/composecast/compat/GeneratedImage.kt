package io.composecast.compat

import io.composecast.server.sdk.ImageLoader
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Deterministic stand-in for remote images: a gradient in the demo brand colors. */
internal object GeneratedImage : ImageLoader {
    override fun load(url: String): ByteArray {
        val image = BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB)
        with(image.createGraphics()) {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paint = GradientPaint(0f, 0f, Color(0x62, 0x00, 0xEE), 800f, 400f, Color(0x03, 0xDA, 0xC6))
            fillRect(0, 0, 800, 400)
            color = Color(255, 255, 255, 70)
            fillOval(520, 60, 220, 220)
            fillOval(80, 230, 140, 140)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
