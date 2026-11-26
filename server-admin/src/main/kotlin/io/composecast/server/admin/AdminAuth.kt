package io.composecast.server.admin

import at.favre.lib.crypto.bcrypt.BCrypt
import io.composecast.server.store.UserStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.HexFormat

/**
 * Accounts and sessions of the admin panel (T-007 A2): bcrypt password hashes, random session
 * tokens. The cookie holds the token; the database only its SHA-256 (a copied database logs nobody in).
 */
class AdminAuth(
    private val users: UserStore,
    private val clock: Clock = Clock.systemUTC(),
    val sessionTtl: Duration = Duration.ofDays(7),
    private val bcryptCost: Int = 12,
) {
    /** A successful login: [token] goes into the session cookie. */
    data class Login(val token: String, val user: AdminUser)

    /** Compared against when the email is unknown, so a wrong email costs as much time as a wrong password. */
    private val dummyHash by lazy { hash("not a password of anyone") }
    private val random = SecureRandom()

    fun hasAccounts(): Boolean = !users.isEmpty()

    /**
     * Creates the first admin from `COMPOSECAST_ADMIN_EMAIL` / `COMPOSECAST_ADMIN_PASSWORD` when there is
     * no account yet (ADR 0016); returns false (and changes nothing) when accounts exist.
     */
    fun bootstrapAdmin(email: String, password: String): Boolean {
        if (!users.isEmpty()) return false
        checkPassword(password)
        return users.createIfNone(normalize(email).also(::checkEmail), hash(password), Role.ADMIN.id) != null
    }

    fun createUser(email: String, password: String, role: Role): AdminUser {
        checkPassword(password)
        val user = users.create(normalize(email).also(::checkEmail), hash(password), role.id)
        return AdminUser(user.id, user.email, role, user.lastLoginAt)
    }

    fun listUsers(): List<AdminUser> = users.list().mapNotNull(::adminUser)

    fun findUser(id: Int): AdminUser? = users.findById(id)?.let(::adminUser)

    fun findByEmail(email: String): AdminUser? = users.findByEmail(normalize(email))?.let(::adminUser)

    /** Changes [userId]'s role; throws [UserStore.LastAdminException] if demoting the last admin. */
    fun updateRole(userId: Int, role: Role): Boolean = users.updateRole(userId, role.id, Role.ADMIN.id)

    /** Resets [userId]'s password and revokes their other sessions (keeping [keepSessionToken] if given). */
    fun resetPassword(userId: Int, newPassword: String, keepSessionToken: String? = null): Boolean {
        checkPassword(newPassword)
        return users.updatePassword(userId, hash(newPassword), keepSessionToken?.let(::sessionId))
    }

    /** Deletes [userId] and their sessions; throws [UserStore.LastAdminException] if deleting the last admin. */
    fun deleteUser(userId: Int): Boolean = users.delete(userId, Role.ADMIN.id)

    /** Null for an unknown email, a wrong password or an account with an unknown role. */
    fun login(email: String, password: String): Login? {
        val stored = users.findByEmail(normalize(email))
        if (password.toByteArray().size > MAX_PASSWORD_BYTES) return null // bcrypt would throw
        val verified = BCrypt.verifyer().verify(password.toCharArray(), stored?.hash ?: dummyHash).verified
        if (stored == null || !verified) return null
        val user = adminUser(stored) ?: return null
        val token = ByteArray(32).also(random::nextBytes).let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
        users.createSession(sessionId(token), user.id, clock.instant().plus(sessionTtl))
        return Login(token, user)
    }

    /** The user of a valid session token, or null (unknown, expired, logged out). */
    fun user(token: String): AdminUser? = users.sessionUser(sessionId(token))?.let(::adminUser)

    fun logout(token: String) = users.deleteSession(sessionId(token))

    private fun adminUser(user: UserStore.User) =
        Role.of(user.role)?.let { AdminUser(user.id, user.email, it, user.lastLoginAt) }

    private fun hash(password: String) = BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray())

    private fun sessionId(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    companion object {
        const val MIN_PASSWORD_LENGTH = 12

        /** bcrypt (`$2a$`) uses at most 71 bytes; the library rejects longer passwords. */
        const val MAX_PASSWORD_BYTES = 71

        private fun normalize(email: String) = email.trim().lowercase()

        private fun checkEmail(email: String) =
            require(email.length in 3..255 && '@' in email) { "Not an email address: '$email'" }

        private fun checkPassword(password: String) {
            require(password.length >= MIN_PASSWORD_LENGTH) {
                "The password needs at least $MIN_PASSWORD_LENGTH characters"
            }
            require(password.toByteArray().size <= MAX_PASSWORD_BYTES) {
                "The password may have at most $MAX_PASSWORD_BYTES bytes (UTF-8)"
            }
        }
    }
}
