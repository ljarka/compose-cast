package io.composecast.server.sdk.render

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.WireBuffer
import androidx.compose.remote.core.operations.BitmapData
import androidx.compose.remote.core.operations.DataListFloat
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.types.IntegerConstant
import androidx.compose.remote.core.types.LongConstant
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcCanvas
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.background
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
import java.security.MessageDigest
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

    /**
     * The document of [screen]. With [dataUpdate] the header marks it as a data-update document
     * (`DOC_DATA_UPDATE`): a player that already shows the same structure only replaces its
     * texts, numbers, arrays and images by id (live screens, ADR 0029).
     */
    fun render(
        screen: ScreenDefinition,
        apiLevel: Int = MAX_API_LEVEL,
        profiles: Int = DEFAULT_PROFILES,
        dataUpdate: Boolean = false,
    ): ByteArray {
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
            val tags = if (dataUpdate) UPDATE_TAGS else HEADER_TAGS
            val writer = RemoteComposeWriter(profile.profile, *tags)
            writer.root {
                RcCanvas(writer) {
                    val values = ScreenValues.allocate(this, writer, screen)
                    val renderEnv = env.withRenderState(writer, values)
                    Column(Modifier.fillMaxSize().background(background).verticalScroll().padding(SCREEN_PADDING)) {
                        renderChildren(renderEnv, screen.components, Slot(Axis.Vertical) { m, w -> m.weight(w) })
                    }
                }
            }
            writer.encodeToByteArray()
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

        private val UPDATE_TAGS = HEADER_TAGS + RemoteComposeWriter.HTag(Header.DOC_DATA_UPDATE, 1)

        /**
         * A hash of everything in [bytes] that a data update cannot change: every operation's
         * encoding, except the replaceable values (`CoreDocument.applyUpdate`: texts, floats,
         * integers, longs, float arrays, bitmaps), which count only by type and id. Two documents
         * with the same structure can be turned into each other by a data-update document. Equal
         * texts share one id (docs/remote-compose.md behavior 21), so data can change structure.
         */
        fun structure(bytes: ByteArray): String {
            val document = CoreDocument()
            document.initFromBuffer(RemoteComposeBuffer.fromInputStream(bytes.inputStream()))
            val digest = MessageDigest.getInstance("SHA-256")
            fun visit(operations: List<Operation>) {
                for (op in operations) {
                    val replaceable = when (op) {
                        is TextData -> op.mTextId
                        is FloatConstant -> op.mId
                        is IntegerConstant -> op.mId
                        is LongConstant -> op.mId
                        is DataListFloat -> op.mId
                        is BitmapData -> op.mImageId
                        else -> null
                    }
                    when {
                        replaceable != null -> digest.update("${op.javaClass.simpleName}#$replaceable;".toByteArray())
                        op is Header -> Unit // carries the DOC_DATA_UPDATE flag itself
                        else -> {
                            val wire = WireBuffer()
                            op.write(wire)
                            digest.update(op.javaClass.simpleName.toByteArray())
                            digest.update(wire.buffer, 0, wire.size)
                        }
                    }
                    if (op is Container) visit(op.list)
                }
            }
            visit(document.operations)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

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
    private val activeWriter: RemoteComposeWriter? = null,
    val values: ScreenValues = ScreenValues.EMPTY,
    private val loadImage: (String) -> EncodedImage?,
) {
    /** The active RC writer for the current render pass (used by `ScreenValues` / `Slider`). */
    val writer: RemoteComposeWriter
        get() = checkNotNull(activeWriter) { "RenderEnv has no active RemoteComposeWriter" }

    /** Returns a per-render view carrying the active [writer] and allocated [values]. */
    fun withRenderState(writer: RemoteComposeWriter, values: ScreenValues): RenderEnv =
        RenderEnv(primary, surface, text, outline, writer, values, loadImage)

    /** The fetched image, or null (failed or rejected: callers render the alt text). */
    fun image(url: String): EncodedImage? = loadImage(url)
}

/** Original encoded bytes plus size; embedded as-is, so JPEGs are not re-encoded as PNG. */
internal class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)
