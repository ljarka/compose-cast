package io.composecast.server.admin

import io.composecast.server.store.UserStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.freemarker.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Admin-only user management (`/admin/users`): list accounts with roles and last login, add new
 * accounts with a temporary password, change roles, reset passwords, and delete accounts (the last
 * admin cannot be demoted or removed).
 */
internal fun Route.usersPages(auth: AdminAuth) {
    route("/users") {
        get {
            if (call.adminUser.role != Role.ADMIN) return@get call.respond(HttpStatusCode.Forbidden)
            val notice = NOTICES.entries.firstOrNull { it.key in call.request.queryParameters }?.value.orEmpty()
            call.usersPage(auth, notice = notice)
        }
        post {
            if (call.adminUser.role != Role.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
            val form = call.receiveParameters()
            val email = form["email"].orEmpty().trim()
            val roleId = form["role"].orEmpty().trim()
            val password = form["password"].orEmpty()
            val role = Role.of(roleId)
            val error = when {
                role == null -> "Choose a valid role (viewer, editor, or admin)."
                auth.findByEmail(email) != null -> "An account for '$email' already exists."
                else -> try {
                    withContext(Dispatchers.Default) { auth.createUser(email, password, role) }
                    null
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Invalid account details."
                }
            }
            if (error != null) {
                call.response.status(HttpStatusCode.UnprocessableEntity)
                return@post call.usersPage(
                    auth,
                    error = error,
                    newEmail = email,
                    newRole = roleId.ifEmpty {
                        Role.EDITOR.id
                    },
                )
            }
            call.seeOther("/admin/users?created")
        }
        route("/{id}") {
            post("/role") {
                if (call.adminUser.role != Role.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                val role = Role.of(call.receiveParameters()["role"].orEmpty().trim())
                if (role == null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.usersPage(auth, error = "Choose a valid role (viewer, editor, or admin).")
                }
                val updated = try {
                    auth.updateRole(id, role)
                } catch (e: UserStore.LastAdminException) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.usersPage(auth, error = e.message ?: "Cannot change the role of the last admin.")
                }
                if (!updated) return@post call.respond(HttpStatusCode.NotFound)
                call.seeOther("/admin/users?roleUpdated")
            }
            post("/password") {
                if (call.adminUser.role != Role.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                if (auth.findUser(id) == null) return@post call.respond(HttpStatusCode.NotFound)
                val password = call.receiveParameters()["password"].orEmpty()
                val keepToken = if (id == call.adminUser.id) call.sessions.get<AdminSession>()?.token else null
                val error = try {
                    withContext(Dispatchers.Default) { auth.resetPassword(id, password, keepSessionToken = keepToken) }
                    null
                } catch (e: IllegalArgumentException) {
                    e.message ?: "Invalid password."
                }
                if (error != null) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.usersPage(auth, error = error)
                }
                call.seeOther("/admin/users?passwordReset")
            }
            post("/delete") {
                if (call.adminUser.role != Role.ADMIN) return@post call.respond(HttpStatusCode.Forbidden)
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.NotFound)
                val deleted = try {
                    auth.deleteUser(id)
                } catch (e: UserStore.LastAdminException) {
                    call.response.status(HttpStatusCode.UnprocessableEntity)
                    return@post call.usersPage(auth, error = e.message ?: "Cannot remove the last admin.")
                }
                if (!deleted) return@post call.respond(HttpStatusCode.NotFound)
                call.seeOther("/admin/users?deleted")
            }
        }
    }
}

private val NOTICES = mapOf(
    "created" to "Account created. Share the temporary password with the user.",
    "roleUpdated" to "Role updated.",
    "passwordReset" to "Password updated. Other active sessions for this user were signed out.",
    "deleted" to "Account removed.",
)

private suspend fun ApplicationCall.usersPage(
    auth: AdminAuth,
    notice: String = "",
    error: String = "",
    newEmail: String = "",
    newRole: String = Role.EDITOR.id,
) {
    val accounts = auth.listUsers()
    val adminCount = accounts.count { it.role == Role.ADMIN }
    respondTemplate(
        "admin/users.ftl",
        page("Users") + mapOf(
            "users" to accounts.map { u ->
                mapOf(
                    "id" to u.id,
                    "email" to u.email,
                    "role" to u.role.id,
                    "isSelf" to (u.id == adminUser.id),
                    "isLastAdmin" to (u.role == Role.ADMIN && adminCount <= 1),
                    *timeFields("lastLoginAt", u.lastLoginAt).toTypedArray(),
                )
            },
            "roles" to Role.entries.map { it.id },
            "minPasswordLength" to AdminAuth.MIN_PASSWORD_LENGTH,
            "notice" to notice,
            "error" to error,
            "newEmail" to newEmail,
            "newRole" to newRole,
        ),
    )
}
