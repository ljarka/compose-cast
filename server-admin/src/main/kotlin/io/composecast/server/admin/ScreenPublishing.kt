package io.composecast.server.admin

/**
 * Making screens live (T-007 A4). Implemented by `server-app`, which owns what apps are served
 * (`LiveScreens`); this module only calls it. An implementation must leave both the database and the
 * served screens unchanged when it throws, and must not let two publishes interleave.
 */
interface ScreenPublishing {
    /** Makes [json] (canonical, already checked with `ScreenJson.check`) the live version of [id]. */
    fun publish(id: String, json: String, by: String)

    /** Publishes revision [revisionId] of [id] again; a pending draft is kept. */
    fun rollback(id: String, revisionId: Int, by: String)
}

/** The screens would not build as a whole (e.g. a `{{secret.X}}` that is not set); nothing changed. */
class PublishException(message: String, cause: Throwable? = null) : Exception(message, cause)
