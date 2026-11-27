// ADR 0033: the only code in the parser's own package. Kept in render/ (rule R1).
@file:Suppress("ktlint:standard:package-name", "PackageDirectoryMismatch")

package androidx.compose.remote.creation.json

import androidx.compose.remote.creation.RemoteComposeWriter
import org.json.JSONObject

/**
 * Bridge to `RemoteComposeJsonParser.parseComponent`, which is package-private in RC alpha23. The
 * public `parse()` takes only a whole document and wraps it in `writer.root {}`. Through this
 * bridge, one RC JSON component is written into **our** writer at the current position (custom
 * components, ADR 0033; verified in docs/remote-compose.md → RC JSON). Re-check on every RC upgrade.
 */
internal object RcJsonBridge {
    fun parser(writer: RemoteComposeWriter): RemoteComposeJsonParser = RemoteComposeJsonParser(writer)

    /** Registers a writer color ID (e.g. from `addThemedColor`) so RC JSON can reference it via `$colors.<name>`. */
    fun registerColor(parser: RemoteComposeJsonParser, name: String, colorId: Int) {
        parser.mColors[name] = colorId
    }

    /** Writes [component] into the parser's writer; throws `org.json.JSONException` with a context path. */
    fun parseComponent(parser: RemoteComposeJsonParser, component: String) {
        parser.parseComponent(JSONObject(component))
    }
}
