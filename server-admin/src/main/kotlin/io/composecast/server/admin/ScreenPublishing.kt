package io.composecast.server.admin

/**
 * Making screens and the brand live (T-007 A4, A5). Implemented by `server-app`, which owns what apps are served
 * (`LiveScreens`); this module only calls it. An implementation must leave both the database and the
 * served screens unchanged when it throws, and must not let two publishes interleave.
 */
interface ScreenPublishing {
    /** Makes [json] (canonical, already checked with `ScreenJson.check`) the live version of [id]. */
    fun publish(id: String, json: String, by: String)

    /** Publishes revision [revisionId] of [id] again; a pending draft is kept. */
    fun rollback(id: String, revisionId: Int, by: String)

    /** Makes [brandJson] (`BrandConfig` JSON, already checked with `problems()`) the brand of
     * `/brand` and every screen (A5). */
    fun publishBrand(brandJson: String)

    /**
     * T-045 B2, T-032: serves [json] (checked) as a temporary **live** screen for the Preview app,
     * without writing anything; returns its screen id (`__preview-…`, ADR 0027). The id stays the
     * same for one [owner] (admin user) and screen while the preview is open (ADR 0030).
     */
    fun preview(json: String, owner: String): String

    /**
     * T-032: [json] (a saved draft) replaces [owner]'s open preview of that screen, so phones
     * showing it update within a second; nothing happens without an open preview. Never throws:
     * the draft is saved either way.
     */
    fun updatePreview(json: String, owner: String)

    /** T-032: ends all previews of [owner] (logout). */
    fun stopPreviews(owner: String)

    /** Makes the already-published screen [id] the home screen (served for `main`). */
    fun setHome(id: String)

    /** Removes screen [id] (must not be the current home screen) and updates served screens. */
    fun delete(id: String)

    /** Renders [json] and returns the decoded Remote Compose document tree (nothing saved). */
    fun describe(json: String): String

    /**
     * T-048: renders [json] (checked) to the Remote Compose document an app would get (default
     * params, live brand, real data); nothing saved. Throws [PublishException] when it cannot.
     */
    fun document(json: String): ByteArray

    /**
     * T-049: makes [json] (a checked `ComponentDefinition`) the live version of custom component
     * [id], only if every live screen still builds with it.
     */
    fun publishComponent(id: String, json: String, by: String): Unit =
        throw PublishException("custom components are not supported")

    /** T-049: publishes revision [revisionId] of component [id] again; a pending draft is kept. */
    fun rollbackComponent(id: String, revisionId: Int, by: String): Unit =
        throw PublishException("custom components are not supported")

    /** T-049: removes component [id]; refused while a live screen uses it. */
    fun deleteComponent(id: String): Unit = throw PublishException("custom components are not supported")

    /** T-049: [json] (a checked draft component) alone on a screen with sample args, as an RC document. */
    fun componentDocument(json: String): ByteArray = throw PublishException("custom components are not supported")
}

/** The screens would not build as a whole (e.g. a `{{secret.X}}` that is not set); nothing changed. */
class PublishException(message: String, cause: Throwable? = null) : Exception(message, cause)
