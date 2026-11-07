package io.composecast.server.sdk

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Limits
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.creation.JvmRcPlatformServices
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.dsl.Modifier
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
import androidx.compose.remote.creation.dsl.rsp
import androidx.compose.remote.creation.modifiers.RoundedRectShape
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.BrandConfig

/**
 * The only place in compose-cast that talks to the Remote Compose creation API.
 *
 * As of remote-compose 1.0.0-alpha20 the whole creation API is `@RestrictTo(LIBRARY_GROUP)`
 * and may change between alphas, so it is deliberately kept behind this one internal class.
 */
internal class RemoteComposeRenderer(brand: BrandConfig?) {

    private val primary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY)
    private val background = parseColor(brand?.backgroundColor ?: DEFAULT_BACKGROUND)

    fun render(nodes: List<ScreenNode>): ByteArray =
        createRcBuffer(PROFILE) {
            Column(Modifier.fillMaxSize().background(background).padding(16f)) {
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
                    fontSize = size.rsp,
                    fontWeight = weight,
                )
            }

            // Named, so a host app can supply the bitmap itself if its player does not fetch URLs.
            is ScreenNode.Image -> Image(
                remoteNamedBitmapUrl("$IMAGE_NAME_PREFIX${node.url}", node.url),
                modifier = Modifier.fillMaxWidth(),
                contentDescription = node.alt.ifBlank { null },
            )

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
                Text(node.label, color = ON_PRIMARY, fontSize = 16.rsp, fontWeight = RcFontWeight.W500)
            }

            is ScreenNode.Spacer -> Spacer(Modifier.height(node.height.toFloat()))
        }
    }

    companion object {
        private const val DEFAULT_PRIMARY = "#6200EE"
        private const val DEFAULT_BACKGROUND = "#FFFFFF"
        private const val TEXT_COLOR = 0xFF1C1B1F.toInt()
        private const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        private const val BUTTON_RADIUS = 12f
        private const val IMAGE_NAME_PREFIX = "image:"

        private val PROFILE = RcProfile(
            Profile(
                CoreDocument.DOCUMENT_API_LEVEL,
                RcProfiles.PROFILE_ANDROIDX,
                JvmRcPlatformServices(),
            ) { info, profile, tag -> RemoteComposeWriter(info, null, profile, tag) },
            false,
        )

        /** Decodes a document with the Remote Compose core reader and dumps its operation tree. */
        fun describe(bytes: ByteArray): String {
            // URL images are disabled by default in the RC reader (also in the Android player,
            // which must opt in the same way). Safe here: the server only decodes for debugging.
            Limits.ENABLE_IMAGE_URLS = true
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
