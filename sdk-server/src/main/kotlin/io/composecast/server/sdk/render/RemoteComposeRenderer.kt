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
import androidx.compose.remote.creation.dsl.RcColor
import androidx.compose.remote.creation.dsl.RcProfile
import androidx.compose.remote.creation.dsl.background
import androidx.compose.remote.creation.dsl.fillMaxSize
import androidx.compose.remote.creation.dsl.padding
import androidx.compose.remote.creation.dsl.verticalScroll
import androidx.compose.remote.creation.json.RcJsonBridge
import androidx.compose.remote.creation.json.RemoteComposeJsonParser
import androidx.compose.remote.creation.profile.Profile
import io.composecast.protocol.BrandConfig
import io.composecast.protocol.ComponentDefinition
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
internal class RemoteComposeRenderer(
    brand: BrandConfig?,
    private val imageLoader: ImageLoader,
    /** Custom components by id (ADR 0033), checked by the registry ([CustomEntry.broken]). */
    private val components: Map<String, CustomEntry> = emptyMap(),
) {
    private val background = parseColor(brand?.backgroundColor ?: DEFAULT_BACKGROUND)
    private val darkBackground = brand?.darkBackgroundColor?.let(::parseColor) ?: DARK_BACKGROUND_COLOR
    private val darkPrimary = brand?.darkPrimaryColor?.let(::parseColor)
        ?: brand?.primaryColor?.let(::parseColor)
        ?: DARK_PRIMARY_COLOR
    private val darkSecondary = brand?.darkSecondaryColor?.let(::parseColor)
        ?: brand?.secondaryColor?.let(::parseColor)
        ?: darkPrimary
    private val darkSurface = brand?.darkSurfaceColor?.let(::parseColor) ?: DARK_SURFACE_COLOR
    private val darkText = brand?.darkTextColor?.let(::parseColor) ?: DARK_TEXT_COLOR
    private val darkOutline = brand?.darkOutlineColor?.let(::parseColor) ?: DARK_DIVIDER_COLOR

    /** Images fetched so far, shared by all (concurrent) renders of one registry. */
    private val images = ImageCache<EncodedImage>()
    private val profiles = ConcurrentHashMap<Pair<Int, Int>, RcProfile>()

    private val env = RenderEnv(
        primary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY),
        surface = brand?.surfaceColor?.let(::parseColor) ?: SURFACE_COLOR,
        text = brand?.textColor?.let(::parseColor) ?: TEXT_COLOR,
        outline = brand?.outlineColor?.let(::parseColor) ?: DIVIDER_COLOR,
        palette = ThemePalette(
            lightBackground = background,
            darkBackground = darkBackground,
            lightPrimary = parseColor(brand?.primaryColor ?: DEFAULT_PRIMARY),
            darkPrimary = darkPrimary,
            lightOnPrimary = ON_PRIMARY,
            darkOnPrimary = if (isLightColor(darkPrimary)) DARK_ON_LIGHT_PRIMARY else ON_PRIMARY,
            lightSurface = brand?.surfaceColor?.let(::parseColor) ?: SURFACE_COLOR,
            darkSurface = darkSurface,
            lightText = brand?.textColor?.let(::parseColor) ?: TEXT_COLOR,
            darkText = darkText,
            lightOutline = brand?.outlineColor?.let(::parseColor) ?: DIVIDER_COLOR,
            darkOutline = darkOutline,
        ),
    ) { url ->
        images.get(url, ::fetch)
    }

    /** `{{brand.<field>}}` in custom component bodies: the brand's colors with the renderer's defaults. */
    private val brandColors: Map<String, String> = mapOf(
        "primaryColor" to hex(env.primary),
        "secondaryColor" to (brand?.secondaryColor ?: hex(env.primary)),
        "backgroundColor" to hex(background),
        "surfaceColor" to hex(env.surface),
        "textColor" to hex(env.text),
        "outlineColor" to hex(env.outline),
    )

    /** Dark-mode counterparts of [brandColors] for `addThemedColor` in custom components. */
    private val darkBrandColors: Map<String, String> = mapOf(
        "primaryColor" to hex(darkPrimary),
        "secondaryColor" to hex(darkSecondary),
        "backgroundColor" to hex(darkBackground),
        "surfaceColor" to hex(darkSurface),
        "textColor" to hex(darkText),
        "outlineColor" to hex(darkOutline),
    )

    private fun profile(level: Int, mask: Int): RcProfile = this.profiles.computeIfAbsent(level to mask) { (l, m) ->
        RcProfile(
            Profile(l, m, PlatformServices) { info, p, tag -> RemoteComposeWriter(info, null, p, tag) },
            false,
        )
    }

    /** A throw-away writer for checking a custom component body before it touches a real document. */
    private fun scratchWriter(): RemoteComposeWriter =
        RemoteComposeWriter(profile(MAX_API_LEVEL, DEFAULT_PROFILES).profile, *HEADER_TAGS)

    /**
     * Every problem of [definition] (ADR 0033): its own [ComponentDefinition.problems], the whitelist
     * (before and after substituting sample arguments) and a trial parse. Empty = usable.
     */
    fun componentProblems(definition: ComponentDefinition): List<String> {
        val own = definition.problems()
        if (own.isNotEmpty()) return own
        val sampleArgs = RcJsonComponents.sampleArgs(definition)
        val sample = RcJsonComponents.substitute(definition, sampleArgs, brandColors, darkBrandColors)
        val whitelist = (
            RcJsonComponents.bodyProblems(definition.body, substituted = false) +
                RcJsonComponents.bodyProblems(sample, substituted = true)
            ).distinct()
        if (whitelist.isNotEmpty()) return whitelist
        return listOfNotNull(
            RcJsonComponents.parseProblem(definition, sampleArgs, brandColors, darkBrandColors, ::scratchWriter)
                ?.let { "body: $it" },
        )
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
        val profile = profile(targetLevel, targetProfiles)
        return try {
            val tags = if (dataUpdate) UPDATE_TAGS else HEADER_TAGS
            val writer = RemoteComposeWriter(profile.profile, *tags)
            writer.root {
                RcCanvas(writer) {
                    val theme = ThemeColors.allocate(writer, env.palette)
                    val values = ScreenValues.allocate(this, writer, screen)
                    val visibility = VisibilityStates.allocate(this, screen)
                    val custom = CustomContext(components, brandColors, darkBrandColors, targetLevel, ::scratchWriter)
                    val renderEnv = env.withRenderState(writer, theme, values, visibility, custom)
                    Column(
                        Modifier.fillMaxSize().background(theme.background).verticalScroll().padding(SCREEN_PADDING),
                    ) {
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

        private fun hex(argb: Int) = "#%08X".format(argb)

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

        private fun isLightColor(argb: Int): Boolean {
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            return (0.299 * r + 0.587 * g + 0.114 * b) > 160.0
        }
    }
}

/** Resolved light + dark ARGB pairs for one brand, emitted once per document as [ThemeColors]. */
internal class ThemePalette(
    val lightBackground: Int,
    val darkBackground: Int,
    val lightPrimary: Int,
    val darkPrimary: Int,
    val lightOnPrimary: Int,
    val darkOnPrimary: Int,
    val lightSurface: Int,
    val darkSurface: Int,
    val lightText: Int,
    val darkText: Int,
    val lightOutline: Int,
    val darkOutline: Int,
)

/** Per-document RC themed color IDs (`remoteThemedColor`), switching locally with `player.setTheme`. */
internal class ThemeColors(
    val background: RcColor,
    val primary: RcColor,
    val onPrimary: RcColor,
    val surface: RcColor,
    val text: RcColor,
    val mutedText: RcColor,
    val outline: RcColor,
    val placeholder: RcColor,
) {
    companion object {
        private const val RGB_MASK = 0x00FFFFFF
        private const val MUTED_ALPHA = 0xA6000000.toInt()

        fun allocate(writer: RemoteComposeWriter, p: ThemePalette): ThemeColors {
            fun themed(name: String, light: Int, dark: Int): RcColor =
                RcColor(writer.addThemedColor(name, 0.toShort(), 0.toShort(), light, dark).toInt())
            return ThemeColors(
                background = themed("brand.bg", p.lightBackground, p.darkBackground),
                primary = themed("brand.primary", p.lightPrimary, p.darkPrimary),
                onPrimary = themed("brand.onPrimary", p.lightOnPrimary, p.darkOnPrimary),
                surface = themed("brand.surface", p.lightSurface, p.darkSurface),
                text = themed("brand.text", p.lightText, p.darkText),
                mutedText = themed(
                    "brand.muted",
                    (p.lightText and RGB_MASK) or MUTED_ALPHA,
                    (p.darkText and RGB_MASK) or MUTED_ALPHA,
                ),
                outline = themed("brand.outline", p.lightOutline, p.darkOutline),
                placeholder = themed("brand.placeholder", PLACEHOLDER_COLOR, DARK_PLACEHOLDER_COLOR),
            )
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
    val palette: ThemePalette,
    private val activeTheme: ThemeColors? = null,
    private val activeWriter: RemoteComposeWriter? = null,
    val values: ScreenValues = ScreenValues.EMPTY,
    val visibility: VisibilityStates = VisibilityStates.EMPTY,
    val custom: CustomContext = CustomContext.EMPTY,
    private val loadImage: (String) -> EncodedImage?,
) {
    /** One RC JSON parser per document (custom components, ADR 0033), on the active [writer]. */
    private val parser by lazy { RcJsonBridge.parser(writer) }
    private val customColors by lazy { ThemedColorRegistry(writer, parser) }

    fun jsonParser(): RemoteComposeJsonParser = parser

    fun themedColors(): ThemedColorRegistry = customColors

    /** The active themed RC colors for the current render pass. */
    val theme: ThemeColors
        get() = checkNotNull(activeTheme) { "RenderEnv has no active ThemeColors" }

    /** The active RC writer for the current render pass (used by `ScreenValues` / `Slider`). */
    val writer: RemoteComposeWriter
        get() = checkNotNull(activeWriter) { "RenderEnv has no active RemoteComposeWriter" }

    /** Returns a per-render view carrying the active [writer], [theme], [values], and [visibility]. */
    fun withRenderState(
        writer: RemoteComposeWriter,
        theme: ThemeColors,
        values: ScreenValues,
        visibility: VisibilityStates = VisibilityStates.EMPTY,
        custom: CustomContext = CustomContext.EMPTY,
    ): RenderEnv = RenderEnv(
        primary,
        surface,
        text,
        outline,
        palette,
        theme,
        writer,
        values,
        visibility,
        custom,
        loadImage,
    )

    /** The fetched image, or null (failed or rejected: callers render the alt text). */
    fun image(url: String): EncodedImage? = loadImage(url)
}

/** Original encoded bytes plus size; embedded as-is, so JPEGs are not re-encoded as PNG. */
internal class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)
