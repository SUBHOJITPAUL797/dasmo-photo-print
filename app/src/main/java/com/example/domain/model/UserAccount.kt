package com.example.domain.model

data class UserAccount(
    val email: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val role: String = "user", // "admin" or "user"
    val status: String = "pending", // "pending", "approved", "rejected"
    val currentSessionToken: String = "", // For single-session enforcement
    val expiryTimestamp: Long = 0L // 0L means unlimited/lifetime access, otherwise expiration timestamp in ms
)