package io.composecast.server.sdk

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RcPlatformServices
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.operations.Header
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
import androidx.compose.remote.creation.dsl.RcContentScale
import androidx.compose.remote.creation.dsl.RcFloat
import androidx.compose.remote.creation.dsl.RcSp
import androidx.compose.remote.creation.dsl.RcFontWeight
import androidx.compose.remote.creation.dsl.RcHorizontalPositioning
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.RcScope
import androidx.compose.remote.creation.dsl.RcVerticalPositioning
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.clip
import androidx.compose.remote.creation.dsl.createRcBuffer
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.fillMaxWidth
import androidx.compose.remote.creation.dsl.height
import androidx.compose.remote.creation.dsl.onClick
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.BrandConfig
import java.io.ByteArrayInputStream
import java.util.logging.Logger
import javax.imageio.ImageIO

/**
 * The only place in compose-cast that talks to the Remote Compose creation API.
 *
 * As of remote-compose 1.0.0-alpha20 the whole creation API is `@RestrictTo(LIBRARY_GROUP)`
 * and may change between alphas, so it is deliberately kept behind this one internal class.
 */
internal class RemoteComposeRenderer(
    brand: BrandConfig?,
    private val imageLoader: ImageLoader,
) {
    private val primary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY)
    private val background = parseColor(brand?.backgroundColor ?: DEFAULT_BACKGROUND)

    /** Images fetched so far, shared by all screens of one registry. `null` = failed to load. */
    private val images = mutableMapOf<String, EncodedImage?>()

    fun render(nodes: List<ScreenNode>): ByteArray =
        createRcBuffer(PROFILE, *HEADER_TAGS) {
            Column(Modifier.fillMaxSize().background(background).verticalScroll().padding(SCREEN_PADDING)) {
                nodes.forEach { node -> renderNode(node) }
            }
        }

    private fun RcScope.renderNode(node: ScreenNode) {
        when (node) {
            is ScreenNode.Text -> {
                val (size, weight) = when (node.style) {
                    TextStyle.H1 -> 32 to RcFontWeight.W700
                    TextStyle.H2 -> 24 to RcFontWeight.W700
                    TextStyle.H3 -> 20 to RcFontWeight.W500
                    TextStyle.Body -> 16 to RcFontWeight.W400
                    TextStyle.Caption -> 12 to RcFontWeight.W400
                }
                Text(
                    node.text,
                    modifier = Modifier.fillMaxWidth(),
                    color = TEXT_COLOR,
                    fontSize = sp(size),
                    fontWeight = weight,
                )
            }

            is ScreenNode.Image -> {
                val image = images.getOrPut(node.url) { fetch(node.url) }
                if (image != null) {
                    // Full width, keeping aspect ratio, but never upscaled past the natural size
                    // (pixels treated as dp). Height is a device-side expression in dp
                    // (the player multiplies heights by density in DP mode).
                    val ratio = image.height.toFloat() / image.width
                    val contentWidthDp = windowWidth() / density() - 2 * SCREEN_PADDING
                    Image(
                        remoteNamedBitmap("$IMAGE_NAME_PREFIX${node.url}", image),
                        modifier = Modifier.fillMaxWidth()
                            .height(written(min(contentWidthDp * ratio, image.height.toFloat()))),
                        contentDescription = node.alt.ifBlank { null },
                        contentScale = RcContentScale.Fit,
                    )
                } else {
                    // Keep the screen usable when an image cannot be fetched.
                    Text(node.alt.ifBlank { node.url }, color = PLACEHOLDER_COLOR, fontSize = sp(12))
                }
            }

            is ScreenNode.Button -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectShape(BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS, BUTTON_RADIUS))
                    .background(primary)
                    .onClick { hostAction(node.action) }
                    .padding(14f),
                horizontal = RcHorizontalPositioning.Center,
                vertical = RcVerticalPositioning.Center,
            ) {
                Text(node.label, color = ON_PRIMARY, fontSize = sp(16), fontWeight = RcFontWeight.W500)
            }

            is ScreenNode.Spacer -> Spacer(Modifier.height(node.height.toFloat()))
        }
    }

    /*
     * Units (alpha23). With DENSITY_BEHAVIOR_DP the player multiplies padding, borders, clip radii
     * and width/height (constants *and* expressions) by density, so sizes are written in dp.
     * Font sizes: constants are multiplied by density only (no font scale), expressions not at all
     * -> fonts use the sp() expression below to honor the user's font scale.
     */

    /**
     * Writes [value]'s expression to the document now. Workaround (still needed in alpha23): the
     * height(RcFloat) modifier reads the expression id without writing it, giving
     * `Height(EXACT, 0.0)`.
     */
    private fun written(value: RcFloat): RcFloat = value.also { it.toFloat() }

    /**
     * [value] sp as a font size. The player sets FONT_SIZE to 14sp in pixels (density and the
     * user's font scale applied), so `FONT_SIZE * value / 14` honors accessibility font scaling.
     */
    private fun RcScope.sp(value: Int): RcSp = RcSp((fontSize() * (value / 14f)).toFloat())

    private fun fetch(url: String): EncodedImage? =
        try {
            val bytes = imageLoader.load(url)
            val decoded = ImageIO.read(ByteArrayInputStream(bytes))
                ?: error("unsupported image format (supported: PNG, JPEG, GIF, BMP)")
            EncodedImage(bytes, decoded.width, decoded.height)
        } catch (e: Exception) {
            log.warning("Image '$url' not embedded, showing placeholder: ${e.message}")
            null
        }

    /** Original encoded bytes plus size; embedded as-is, so JPEGs are not re-encoded as PNG. */
    private class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)

    /** JVM platform services that understand [EncodedImage]. */
    private object PlatformServices : RcPlatformServices by JvmRcPlatformServices() {
        private val jvm = JvmRcPlatformServices()
        override fun imageToByteArray(image: Any): ByteArray? =
            (image as? EncodedImage)?.bytes ?: jvm.imageToByteArray(image)
        override fun getImageWidth(image: Any): Int =
            (image as? EncodedImage)?.width ?: jvm.getImageWidth(image)
        override fun getImageHeight(image: Any): Int =
            (image as? EncodedImage)?.height ?: jvm.getImageHeight(image)
        override fun isAlpha8Image(image: Any): Boolean =
            if (image is EncodedImage) false else jvm.isAlpha8Image(image)
    }

    companion object {
        private val log = Logger.getLogger(RemoteComposeRenderer::class.java.name)

        private const val DEFAULT_PRIMARY = "#6200EE"
        private const val DEFAULT_BACKGROUND = "#FFFFFF"
        private const val TEXT_COLOR = 0xFF1C1B1F.toInt()
        private const val PLACEHOLDER_COLOR = 0xFF79747E.toInt()
        private const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val BUTTON_RADIUS = 12f
        private const val IMAGE_NAME_PREFIX = "image:"
        private const val SCREEN_PADDING = 16f

        private val PROFILE = RcProfile(
            Profile(
                CoreDocument.DOCUMENT_API_LEVEL,
                RcProfiles.PROFILE_ANDROIDX,
                PlatformServices,
            ) { info, profile, tag -> RemoteComposeWriter(info, null, profile, tag) },
            false,
        )

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
