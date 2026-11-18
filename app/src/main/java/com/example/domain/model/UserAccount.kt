package com.example.domain.model

data class UserAccount(
    val email: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val isApproved: Boolean = false,
    val isAdmin: Boolean = false,
    val role: String = "user", // "admin" or "user"
    val status: String = "pending", // "pending", "approved", "rejected"
    val currentSessionToken: String = "", // For single-session enforcement
    val expiryTimestamp: Long = 0L, // 0L means unlimited/lifetime access, otherwise expiration timestamp in ms
    val registrationTimestamp: Long = 0L,
    val lastActiveTimestamp: Long = 0L,
    val appSource: String = "DASMO Photo Print"
) {
    fun resolveLifecycleStatus(
        nowMs: Long = System.currentTimeMillis(),
        superAdminEmail: String = "subhojitpaul26042004@gmail.com"
    ): AccountLifecycleStatus {
        val cleanEmail = email.trim().lowercase()
        return when {
            cleanEmail == superAdminEmail -> AccountLifecycleStatus.SUPER_ADMIN
            isAdmin || role.equals("admin", ignoreCase = true) -> AccountLifecycleStatus.ADMIN
            status.equals("rejected", ignoreCase = true) || (!isApproved && !status.equals("pending", ignoreCase = true)) -> AccountLifecycleStatus.REVOKED
            !isApproved || status.equals("pending", ignoreCase = true) -> AccountLifecycleStatus.PENDING
            expiryTimestamp in 1..<nowMs -> AccountLifecycleStatus.EXPIRED
            else -> AccountLifecycleStatus.ACTIVE
        }
    }
}

enum class AccountLifecycleStatus {
    SUPER_ADMIN,
    ADMIN,
    PENDING,
    ACTIVE,
    EXPIRED,
    REVOKED
}