package io.composecast.server.store

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Admin accounts and their login sessions (T-007 A2). Stores what it is given: password hashes
 * (bcrypt, made by `server-admin`), roles as text, session ids (hashes of cookie tokens).
 */
class UserStore(private val database: StoreDatabase, private val clock: Clock = Clock.systemUTC()) {
    data class User(val id: Int, val email: String, val hash: String, val role: String)

    fun isEmpty(): Boolean = database.tx { Users.selectAll().empty() }

    /** Emails are compared exactly; callers normalize them (trim, lowercase) before storing and looking up. */
    fun findByEmail(email: String): User? =
        database.tx { Users.selectAll().where { Users.email eq email }.singleOrNull()?.let(::user) }

    /** Creates an account; fails when [email] exists. */
    fun create(email: String, hash: String, role: String): User = database.tx { insertUser(email, hash, role) }

    /** Creates the account only when there is no account at all (first-admin bootstrap); atomic. */
    fun createIfNone(email: String, hash: String, role: String): User? =
        database.tx { if (Users.selectAll().empty()) insertUser(email, hash, role) else null }

    fun createSession(id: String, userId: Int, expiresAt: Instant) {
        database.tx {
            Sessions.insert {
                it[Sessions.id] = id
                it[Sessions.userId] = userId
                it[Sessions.expiresAt] = expiresAt.truncatedTo(ChronoUnit.SECONDS).toString()
            }
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

    private fun JdbcTransaction.insertUser(email: String, hash: String, role: String): User {
        val id = Users.insert {
            it[Users.email] = email
            it[Users.hash] = hash
            it[Users.role] = role
        } get Users.id
        return User(id, email, hash, role)
    }

    private fun user(row: ResultRow) = User(row[Users.id], row[Users.email], row[Users.hash], row[Users.role])
}
