package io.composecast.renderer

import org.junit.runner.JUnitCore
import kotlin.system.exitProcess

/** Robolectric qualifiers of the phone frame (Pixel 7: 411×914 dp, xxhdpi). */
const val PHONE = "w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** Starts [RenderServer] in a Robolectric sandbox; returns only if it fails to start. */
fun main() {
    System.setProperty(RenderServer.SERVE, "true")
    val result = JUnitCore().run(RenderServer::class.java)
    result.failures.forEach { System.err.println(it.trace) }
    exitProcess(1)
}
