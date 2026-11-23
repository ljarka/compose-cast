package io.composecast.server.store.db

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class UserRecord(val id: Int, val email: String, val role: String)

object Users {
    fun createUser(email: String, password: String, role: String = "editor"): UserRecord {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val id = transaction {
            UsersTable.insert {
                it[UsersTable.email] = email
                it[UsersTable.hash] = hash
                it[UsersTable.role] = role
            } get UsersTable.id
        }
        return UserRecord(id, email, role)
    }

    fun findByEmail(email: String): UserRecord? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .singleOrNull()
            ?.let { UserRecord(it[UsersTable.id], it[UsersTable.email], it[UsersTable.role]) }
    }

    fun verifyPassword(email: String, password: String): UserRecord? {
        val row = transaction {
            UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
        } ?: return null
        val result = BCrypt.verifyer().verify(password.toCharArray(), row[UsersTable.hash])
        return if (result.verified) UserRecord(row[UsersTable.id], row[UsersTable.email], row[UsersTable.role]) else null
    }

    fun bootstrapAdmin(email: String, password: String) {
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        transaction {
            if (UsersTable.selectAll().count() == 0L) {
                UsersTable.insert {
                    it[UsersTable.email] = email
                    it[UsersTable.hash] = hash
                    it[UsersTable.role] = "admin"
                }
            }
        }
    }
}
