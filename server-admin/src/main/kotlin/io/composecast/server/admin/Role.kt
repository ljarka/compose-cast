package io.composecast.server.admin

import java.time.Instant

/** Admin roles (ADR 0016), weakest first: `viewer` reads, `editor` changes screens, `admin` everything. */
enum class Role(val id: String) {
    VIEWER("viewer"),
    EDITOR("editor"),
    ADMIN("admin"),
    ;

    /** May use mutating routes (every method but GET/HEAD/OPTIONS). */
    val canEdit: Boolean get() = this >= EDITOR

    companion object {
        fun of(id: String): Role? = entries.firstOrNull { it.id == id }
    }
}

/** An admin panel account. */
data class AdminUser(val id: Int, val email: String, val role: Role, val lastLoginAt: Instant? = null)
