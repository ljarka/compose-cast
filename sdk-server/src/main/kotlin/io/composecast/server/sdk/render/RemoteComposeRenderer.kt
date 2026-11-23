package io.composecast.server.sdk.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ScreenDefinition
import io.composecast.server.sdk.ImageCache
import io.composecast.server.sdk.ImageLoader
import io.composecast.server.sdk.UnsupportedClientVersionException
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * Entry point of the `render` package — the only place in compose-cast that talks to the Remote
 * Compose creation API (ADR 0002, rule R1).
 *
 * As of remote-compose 1.0.0-alpha20 the whole creation API is `@RestrictTo(LIBRARY_GROUP)`
 * and may change between alphas, so it is deliberately kept behind this internal package:
 * this file (document, profiles, images), `Modifiers.kt` (box model), `Nodes.kt` (dispatch, text),
 * `Containers.kt`, `Images.kt`, `Interactive.kt` (button, expandable), `Mappings.kt`, `Style.kt`.
 */
internal class RemoteComposeRenderer(brand: BrandConfig?, private val imageLoader: ImageLoader) {
    private val background = parseColor(brand?.backgroundColor ?: DEFAULT_BACKGROUND)

    /** Images fetched so far, shared by all (concurrent) renders of one registry. */
    private val images = ImageCache<EncodedImage>()
    private val profiles = ConcurrentHashMap<Pair<Int, Int>, RcProfile>()

    private val env = RenderEnv(
        primary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY),
        surface = brand?.surfaceColor?.let(::parseColor) ?: SURFACE_COLOR,
        text = brand?.textColor?.let(::parseColor) ?: TEXT_COLOR,
        outline = brand?.outlineColor?.let(::parseColor) ?: DIVIDER_COLOR,
    ) { url ->
        images.get(url, ::fetch)
    }

    fun render(screen: ScreenDefinition, apiLevel: Int = MAX_API_LEVEL, profiles: Int = DEFAULT_PROFILES): ByteArray {
        if (apiLevel < MIN_API_LEVEL) {
            throw UnsupportedClientVersionException(
                MIN_API_LEVEL,
                "Client RC API level $apiLevel is below minimum supported $MIN_API_LEVEL",
            )
        }
        val targetLevel = minOf(apiLevel, MAX_API_LEVEL)
        val targetProfiles = (profiles and DEFAULT_PROFILES).let { if (it == 0) DEFAULT_PROFILES else it }
        val profile = this.profiles.computeIfAbsent(targetLevel to targetProfiles) { (level, mask) ->
            RcProfile(
                Profile(level, mask, PlatformServices) { info, p, tag -> RemoteComposeWriter(info, null, p, tag) },
                false,
            )
        }
        return try {
            createRcBuffer(profile, *HEADER_TAGS) {
                Column(Modifier.fillMaxSize().background(background).verticalScroll().padding(SCREEN_PADDING)) {
                    renderChildren(env, screen.components, Slot(Axis.Vertical) { m, w -> m.weight(w) })
                }
            }
        } catch (e: Exception) {
            if (targetLevel < MAX_API_LEVEL) {
                throw UnsupportedClientVersionException(
                    targetLevel + 1,
                    "Screen '${screen.id}' requires a newer RC API level than $targetLevel: ${e.message}",
                    e,
                )
            }
            throw e
        }
    }

    private fun fetch(url: String): EncodedImage? = try {
        val bytes = imageLoader.load(url)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
            ?: error("unsupported image format (supported: PNG, JPEG, GIF, BMP)")
        EncodedImage(bytes, decoded.width, decoded.height)
    } catch (e: Exception) {
        log.warning("Image '$url' not embedded, showing placeholder: ${e.message}")
        null
    }

    /** JVM platform services that understand [EncodedImage]. */
    private object PlatformServices : RcPlatformServices by JvmRcPlatformServices() {
        private val jvm = JvmRcPlatformServices()
        override fun imageToByteArray(image: Any): ByteArray? =
            (image as? EncodedImage)?.bytes ?: jvm.imageToByteArray(image)
        override fun getImageWidth(image: Any): Int = (image as? EncodedImage)?.width ?: jvm.getImageWidth(image)
        override fun getImageHeight(image: Any): Int = (image as? EncodedImage)?.height ?: jvm.getImageHeight(image)
        override fun isAlpha8Image(image: Any): Boolean = if (image is EncodedImage) false else jvm.isAlpha8Image(image)
    }

    companion object {
        private val log = Logger.getLogger(RemoteComposeRenderer::class.java.name)

        /**
         * `Header.DOC_DENSITY_BEHAVIOR` requires API level 8+ in RC alpha23
         * (`RemoteComposeWriter.filterAndValidateTags`), so 8 is the minimum supported level.
         */
        const val MIN_API_LEVEL = 8
        const val MAX_API_LEVEL = CoreDocument.DOCUMENT_API_LEVEL
        const val DEFAULT_PROFILES = RcProfiles.PROFILE_ANDROIDX or RcProfiles.PROFILE_EXPERIMENTAL

        /** All sizes in the document are dp/sp; without this the player mixes pixels and dp. */
        private val HEADER_TAGS = arrayOf(
            RemoteComposeWriter.HTag(Header.DOC_DENSITY_BEHAVIOR, CoreDocument.DENSITY_BEHAVIOR_DP),
        )

        /** Decodes a document with the Remote Compose core reader and dumps its operation tree. */
        fun describe(bytes: ByteArray): String {
            val document = CoreDocument()
            document.initFromBuffer(RemoteComposeBuffer.fromInputStream(bytes.inputStream()))
            return document.toNestedString()
        }

        /** Parses `#RRGGBB` or `#AARRGGBB` into an ARGB int. */
        internal fun parseColor(hex: String): Int {
            val digits = hex.removePrefix("#")
            val argb = when (digits.length) {
                6 -> "FF$digits"
                8 -> digits
                else -> throw IllegalArgumentException("Invalid color '$hex', expected #RRGGBB or #AARRGGBB")
            }
            return argb.toLong(16).toInt()
        }
    }
}

/**
 * What node renderers need from the renderer: the brand's colors (defaults from `Style.kt` when the
 * brand leaves them out, T-040) and the image cache.
 */
internal class RenderEnv(
    val primary: Int,
    val surface: Int,
    val text: Int,
    val outline: Int,
    private val loadImage: (String) -> EncodedImage?,
) {
    /** The fetched image, or null (failed or rejected: callers render the alt text). */
    fun image(url: String): EncodedImage? = loadImage(url)
}

/** Original encoded bytes plus size; embedded as-is, so JPEGs are not re-encoded as PNG. */
internal class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)
