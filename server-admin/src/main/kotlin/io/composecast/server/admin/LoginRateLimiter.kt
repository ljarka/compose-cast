package io.composecast.server.admin

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * In-memory per-IP sliding-window rate limiter for `POST /admin/login` (T-051):
 * blocks brute-force attempts and prevents BCrypt CPU exhaustion.
 */
internal class LoginRateLimiter(
    private val maxFailures: Int = 10,
    private val window: Duration = Duration.ofMinutes(5),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val failures = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun isLimited(key: String): Boolean {
        val queue = failures[key] ?: return false
        prune(queue, clock.instant())
        if (queue.isEmpty()) {
            failures.remove(key)
            return false
        }
        return queue.size >= maxFailures
    }

    @Synchronized
    fun recordFailure(key: String) {
        val now = clock.instant()
        val queue = failures.getOrPut(key) { ArrayDeque() }
        prune(queue, now)
        queue.addLast(now)
    }

    @Synchronized
    fun recordSuccess(key: String) {
        failures.remove(key)
    }

    private fun prune(queue: ArrayDeque<Instant>, now: Instant) {
        val cutoff = now.minus(window)
        while (queue.isNotEmpty() && !queue.first().isAfter(cutoff)) {
            queue.removeFirst()
        }
    }
}
