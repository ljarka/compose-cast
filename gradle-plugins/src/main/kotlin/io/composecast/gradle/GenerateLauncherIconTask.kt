package io.composecast.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Overrides app-shell's default launcher icon `@mipmap/ic_launcher` with an adaptive icon made from
 * `app.icon`: the image is scaled to fit the 72dp safe zone of the 108dp foreground layer, on a
 * solid background color.
 */
abstract class GenerateLauncherIconTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val icon: RegularFileProperty

    @get:Input
    abstract val backgroundColor: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val source = ImageIO.read(icon.get().asFile)
            ?: throw GradleException("app.icon is not a readable PNG/JPG: ${icon.get().asFile}")
        // Foreground layer is 108dp, of which the central 72dp is always visible.
        mapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0,
        ).forEach { (density, scale) ->
            val canvas = (108 * scale).toInt()
            val safe = 72 * scale
            val fit = minOf(safe / source.width, safe / source.height)
            val w = (source.width * fit).toInt()
            val h = (source.height * fit).toInt()
            val image = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(source, (canvas - w) / 2, (canvas - h) / 2, w, h, null)
                dispose()
            }
            val dir = out.resolve("mipmap-$density").apply { mkdirs() }
            ImageIO.write(image, "png", dir.resolve("ic_launcher_custom_foreground.png"))
        }
        out.resolve("values").apply { mkdirs() }.resolve("ic_launcher_custom_colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="ic_launcher_custom_background">${backgroundColor.get()}</color>
            </resources>
            """.trimIndent() + "\n",
        )
        out.resolve("mipmap-anydpi").apply { mkdirs() }.resolve("ic_launcher.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@color/ic_launcher_custom_background" />
                <foreground android:drawable="@mipmap/ic_launcher_custom_foreground" />
            </adaptive-icon>
            """.trimIndent() + "\n",
        )
    }
}
