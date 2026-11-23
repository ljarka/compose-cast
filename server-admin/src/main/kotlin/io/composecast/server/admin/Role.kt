package io.composecast.server.admin

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

/** The logged-in account. */
data class AdminUser(val id: Int, val email: String, val role: Role)
