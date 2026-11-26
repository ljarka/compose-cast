package io.composecast.server.store

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Admin accounts and their login sessions (T-007 A2). Stores what it is given: password hashes
 * (bcrypt, made by `server-admin`), roles as text, session ids (hashes of cookie tokens).
 */
class UserStore(private val database: StoreDatabase, private val clock: Clock = Clock.systemUTC()) {
    data class User(
        val id: Int,
        val email: String,
        val hash: String,
        val role: String,
        val lastLoginAt: Instant? = null,
    )

    /** Thrown when an update or delete would leave the server with no account in the protected role. */
    class LastAdminException(message: String) : IllegalStateException(message)

    fun isEmpty(): Boolean = database.tx { Users.selectAll().empty() }

    fun list(): List<User> = database.tx { Users.selectAll().orderBy(Users.id to SortOrder.ASC).map(::user) }

    fun findById(id: Int): User? = database.tx {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.let(::user)
    }

    /** Emails are compared exactly; callers normalize them (trim, lowercase) before storing and looking up. */
    fun findByEmail(email: String): User? =
        database.tx { Users.selectAll().where { Users.email eq email }.singleOrNull()?.let(::user) }

    /** Creates an account; fails when [email] exists. */
    fun create(email: String, hash: String, role: String): User = database.tx { insertUser(email, hash, role) }

    /** Creates the account only when there is no account at all (first-admin bootstrap); atomic. */
    fun createIfNone(email: String, hash: String, role: String): User? =
        database.tx { if (Users.selectAll().empty()) insertUser(email, hash, role) else null }

    /** Changes [userId]'s role; refuses to demote the last account with [protectedRole]. Returns false if missing. */
    fun updateRole(userId: Int, role: String, protectedRole: String = "admin"): Boolean = database.tx {
        val current = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@tx false
        if (current[Users.role] == protectedRole && role != protectedRole && countRole(protectedRole) <= 1) {
            throw LastAdminException("Cannot change the role of the last admin.")
        }
        Users.update({ Users.id eq userId }) { it[Users.role] = role } > 0
    }

    /** Updates [userId]'s password hash and revokes their other sessions. Returns false if missing. */
    fun updatePassword(userId: Int, hash: String, keepSessionId: String? = null): Boolean = database.tx {
        val updated = Users.update({ Users.id eq userId }) { it[Users.hash] = hash } > 0
        if (updated) {
            if (keepSessionId != null) {
                Sessions.deleteWhere { (Sessions.userId eq userId) and (Sessions.id neq keepSessionId) }
            } else {
                Sessions.deleteWhere { Sessions.userId eq userId }
            }
        }
        updated
    }

    /** Deletes [userId] and their sessions; refuses to delete the last account with [protectedRole]. */
    fun delete(userId: Int, protectedRole: String = "admin"): Boolean = database.tx {
        val current = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@tx false
        if (current[Users.role] == protectedRole && countRole(protectedRole) <= 1) {
            throw LastAdminException("Cannot remove the last admin.")
        }
        Sessions.deleteWhere { Sessions.userId eq userId }
        Users.deleteWhere { Users.id eq userId } > 0
    }

    fun createSession(id: String, userId: Int, expiresAt: Instant) {
        val now = clock.instant().truncatedTo(ChronoUnit.SECONDS).toString()
        database.tx {
            Sessions.insert {
                it[Sessions.id] = id
                it[Sessions.userId] = userId
                it[Sessions.expiresAt] = expiresAt.truncatedTo(ChronoUnit.SECONDS).toString()
            }
            Users.update({ Users.id eq userId }) { it[Users.lastLoginAt] = now }
        }
    }

    /** The user of an unexpired session; an expired one is deleted. */
    fun sessionUser(id: String): User? = database.tx {
        val session = Sessions.selectAll().where { Sessions.id eq id }.singleOrNull() ?: return@tx null
        if (Instant.parse(session[Sessions.expiresAt]) <= clock.instant()) {
            Sessions.deleteWhere { Sessions.id eq id }
            return@tx null
        }
        Users.selectAll().where { Users.id eq session[Sessions.userId] }.singleOrNull()?.let(::user)
    }

    fun deleteSession(id: String) {
        database.tx { Sessions.deleteWhere { Sessions.id eq id } }
    }

    /** Removes expired sessions. Expiries are stored in whole seconds, so the strings sort chronologically. */
    fun deleteExpiredSessions(): Int {
        val now = clock.instant().truncatedTo(ChronoUnit.SECONDS).toString()
        return database.tx { Sessions.deleteWhere { Sessions.expiresAt lessEq now } }
    }

    private fun JdbcTransaction.countRole(role: String): Long = Users.selectAll().where { Users.role eq role }.count()

    private fun JdbcTransaction.insertUser(email: String, hash: String, role: String): User {
        val id = Users.insert {
            it[Users.email] = email
            it[Users.hash] = hash
            it[Users.role] = role
        } get Users.id
        return User(id, email, hash, role)
    }

    private fun user(row: ResultRow) = User(
        id = row[Users.id],
        email = row[Users.email],
        hash = row[Users.hash],
        role = row[Users.role],
        lastLoginAt = row[Users.lastLoginAt]?.let(Instant::parse),
    )
}
