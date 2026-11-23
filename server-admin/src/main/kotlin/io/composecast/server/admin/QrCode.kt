package io.composecast.server.admin

import io.nayuki.qrcodegen.QrCode as Qr

/** A QR code as inline SVG (T-045 B2): no image files, no JavaScript (ADR 0012), crisp at any size. */
internal object QrCode {
    /** [text] as an SVG of [sizePx] with a 4-module quiet zone; medium error correction. */
    fun svg(text: String, sizePx: Int = 240): String {
        val qr = Qr.encodeText(text, Qr.Ecc.MEDIUM)
        val border = 4
        val modules = qr.size + 2 * border
        val path = buildString {
            for (y in 0 until qr.size) {
                for (x in 0 until qr.size) {
                    if (qr.getModule(x, y)) append("M${x + border},${y + border}h1v1h-1z")
                }
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $modules $modules" width="$sizePx" """ +
            """height="$sizePx" shape-rendering="crispEdges" role="img" aria-label="QR code">""" +
            """<rect width="100%" height="100%" fill="#fff"/><path d="$path" fill="#000"/></svg>"""
    }
}
