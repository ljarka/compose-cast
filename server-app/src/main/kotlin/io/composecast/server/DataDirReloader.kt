package io.composecast.server

import io.composecast.server.sdk.ScreenRegistry
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile

/**
 * Serves edits of the data directory without a restart (T-042): every [intervalMs] it compares
 * the directory's fingerprint (files, mtimes, sizes) and, on a change, waits [debounceMs] for the
 * editor to finish writing, loads the directory again with [load] and swaps it into [screens].
 * A directory that fails to load is logged once and the previous screens stay served.
 *
 * Polling, not file events: inotify is unreliable on Docker bind mounts (macOS/Windows hosts).
 * The admin panel (T-007) publishes through the same [LiveScreens.replace].
 */
class DataDirReloader(
    private val dataDir: Path,
    private val screens: LiveScreens,
    private val load: (Path) -> ScreenRegistry,
    private val intervalMs: Long = 2_000,
    private val debounceMs: Long = 500,
) {
    private val log = LoggerFactory.getLogger(DataDirReloader::class.java)

    /** The fingerprint of what is served, or of the last version that failed to load. */
    @Volatile
    private var seen: List<String> = fingerprint()

    /**
     * One poll: reloads when the directory changed since the last poll.
     * @return true when new screens are served
     */
    fun reloadIfChanged(): Boolean {
        if (fingerprint() == seen) return false
        if (debounceMs > 0) Thread.sleep(debounceMs)
        val current = fingerprint()
        seen = current
        return try {
            val registry = load(dataDir)
            screens.replace(registry)
            log.info("Reloaded {}: {} screens", dataDir, registry.ids().size)
            true
        } catch (e: Exception) {
            log.error("Reload of {} failed, still serving the previous screens: {}", dataDir, e.message)
            false
        }
    }

    /** Polls on a daemon thread until the JVM exits. */
    fun start() {
        val executor = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "data-dir-reload").apply {
                isDaemon =
                    true
            }
        }
        executor.scheduleWithFixedDelay(
            {
                try {
                    reloadIfChanged()
                } catch (e: Exception) {
                    log.error("Data directory poll failed: {}", e.message) // keep polling
                }
            },
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
        log.info("Watching {} for changes every {} ms", dataDir, intervalMs)
    }

    private fun fingerprint(): List<String> = Files.walk(dataDir).use { files ->
        files.filter { it.isRegularFile() }
            .map { "${dataDir.relativize(it)}|${it.getLastModifiedTime().toMillis()}|${it.fileSize()}" }
            .sorted()
            .toList()
    }
}
