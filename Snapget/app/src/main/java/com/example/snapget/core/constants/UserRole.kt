package com.example.snapget.core.constants

enum class UserRole {

    USER,
    STAFF,
    ADMIN,
    GUEST,
    ;

    companion object {
        fun fromString(role: String): UserRole = when (role.lowercase()) {
            "user" -> USER
            "staff" -> STAFF
            "admin" -> ADMIN
            "guest" -> GUEST
            else -> throw IllegalArgumentException("Unknown role: $role")
        }
    }
}
