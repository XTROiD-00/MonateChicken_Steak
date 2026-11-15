package com.example.monatechickensteak.data

import com.example.monatechickensteak.model.Reward
import com.example.monatechickensteak.model.User
import com.example.monatechickensteak.util.PasswordHasher
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class ResetRequest {
    data class CodeCreated(val code: String) : ResetRequest()
    data class Error(val message: String) : ResetRequest()
}

/**
 * Accounts, passwords, Google single sign-on and loyalty points.
 * The rules live here; the database work is done by the [UserStore].
 */
object AuthRepository {

    const val PROVIDER_PASSWORD = "password"
    const val PROVIDER_GOOGLE = "google"

    private const val WRONG_LOGIN = "Incorrect email or password"
    private const val ACCOUNT_GONE = "Account not found. Please log in again."

    /** Set once at app start (see Graph) or to a fake store in unit tests. */
    lateinit var store: UserStore

    private const val RESET_CODE_LIFETIME_MS = 10 * 60 * 1000L
    private const val RESET_MAX_ATTEMPTS = 5

    private class PendingReset(val code: String, val expiresAt: Long, var attemptsLeft: Int)

    private val pendingResets = mutableMapOf<String, PendingReset>()

    /** Replaced in unit tests so expiry can be checked without waiting. */
    var clock: () -> Long = { System.currentTimeMillis() }

    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val record = store.findByEmail(email.trim())
            ?: return@withContext AuthResult.Error(WRONG_LOGIN)
        val hash = record.passwordHash
            ?: return@withContext AuthResult.Error("This account uses Google sign-in. Tap \"Continue with Google\".")
        if (PasswordHasher.verify(password, hash)) {
            AuthResult.Success(record.toUser())
        } else {
            AuthResult.Error(WRONG_LOGIN)
        }
    }

    suspend fun register(name: String, email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val cleanEmail = email.trim()
            if (store.findByEmail(cleanEmail) != null) {
                return@withContext AuthResult.Error("An account with this email already exists")
            }
            val record = store.insert(
                name = name.trim(),
                email = cleanEmail,
                passwordHash = PasswordHasher.encode(password),
                provider = PROVIDER_PASSWORD
            )
            AuthResult.Success(record.toUser())
        }

    /**
     * Single sign-on with a Google account. The first time, an account is created from the
     * Google name and email (no password is stored). After that the same account is reused.
     */
    suspend fun signInWithGoogle(email: String, name: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return@withContext AuthResult.Error("Google did not return an email address")
        val existing = store.findByEmail(cleanEmail)
        if (existing != null) return@withContext AuthResult.Success(existing.toUser())
        val displayName = name.trim().ifEmpty { cleanEmail.substringBefore('@') }
        val record = store.insert(displayName, cleanEmail, null, PROVIDER_GOOGLE)
        AuthResult.Success(record.toUser())
    }

    /**
     * Step 1 of "Forgot password": makes a 6 digit code that works for 10 minutes.
     * The app has no mail server, so the code is returned to the screen (demo) instead of
     * being emailed. With a server, send it by email here and return nothing to the screen.
     * Returns an error for unknown emails and Google accounts (they have no password).
     */
    suspend fun requestPasswordReset(email: String): ResetRequest = withContext(Dispatchers.IO) {
        val clean = email.trim().lowercase()
        val record = store.findByEmail(clean)
            ?: return@withContext ResetRequest.Error("No account found for that email")
        if (record.passwordHash == null) {
            return@withContext ResetRequest.Error(
                "This account uses Google sign-in, so it has no password to reset"
            )
        }
        val code = Random.nextInt(100000, 1000000).toString()
        synchronized(pendingResets) {
            pendingResets[clean] = PendingReset(code, clock() + RESET_CODE_LIFETIME_MS, RESET_MAX_ATTEMPTS)
        }
        ResetRequest.CodeCreated(code)
    }

    /** Step 2: checks the code and saves the new password. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            val clean = email.trim().lowercase()
            val pending = synchronized(pendingResets) { pendingResets[clean] }
                ?: return@withContext AuthResult.Error("Request a reset code first")
            if (clock() > pending.expiresAt) {
                synchronized(pendingResets) { pendingResets.remove(clean) }
                return@withContext AuthResult.Error("That code has expired. Request a new one.")
            }
            if (code.trim() != pending.code) {
                val left = synchronized(pendingResets) { --pending.attemptsLeft }
                if (left <= 0) {
                    synchronized(pendingResets) { pendingResets.remove(clean) }
                    return@withContext AuthResult.Error("Too many wrong codes. Request a new one.")
                }
                return@withContext AuthResult.Error("Incorrect code ($left tries left)")
            }
            val record = store.findByEmail(clean)
                ?: return@withContext AuthResult.Error(ACCOUNT_GONE)
            store.updatePasswordHash(record.id, PasswordHasher.encode(newPassword))
            synchronized(pendingResets) { pendingResets.remove(clean) }
            AuthResult.Success(record.toUser())
        }

    suspend fun updateName(userId: Int, newName: String): AuthResult = withContext(Dispatchers.IO) {
        val record = store.findById(userId) ?: return@withContext AuthResult.Error(ACCOUNT_GONE)
        store.updateName(userId, newName.trim())
        AuthResult.Success(record.copy(name = newName.trim()).toUser())
    }

    suspend fun changePassword(userId: Int, currentPassword: String, newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            val record = store.findById(userId) ?: return@withContext AuthResult.Error(ACCOUNT_GONE)
            val hash = record.passwordHash
                ?: return@withContext AuthResult.Error("Google accounts have no password to change")
            if (!PasswordHasher.verify(currentPassword, hash)) {
                return@withContext AuthResult.Error("Current password is incorrect")
            }
            if (currentPassword == newPassword) {
                return@withContext AuthResult.Error("New password must be different from the current one")
            }
            store.updatePasswordHash(userId, PasswordHasher.encode(newPassword))
            AuthResult.Success(record.toUser())
        }

    /** Deletes the account together with its orders and redemptions. */
    suspend fun deleteAccount(userId: Int): Boolean = withContext(Dispatchers.IO) {
        if (store.findById(userId) == null) return@withContext false
        store.delete(userId)
        true
    }

    suspend fun loyaltyPoints(userId: Int): Int = withContext(Dispatchers.IO) {
        store.findById(userId)?.loyaltyPoints ?: 0
    }

    suspend fun addLoyaltyPoints(userId: Int, points: Int) {
        if (points <= 0) return
        withContext(Dispatchers.IO) {
            val record = store.findById(userId) ?: return@withContext
            store.setPoints(userId, record.loyaltyPoints + points)
        }
    }

    /** Spends the points and returns the till code, or null when there are not enough points. */
    suspend fun redeem(userId: Int, reward: Reward): String? = withContext(Dispatchers.IO) {
        val record = store.findById(userId) ?: return@withContext null
        if (reward.cost <= 0 || record.loyaltyPoints < reward.cost) return@withContext null
        val code = "MON-" + Random.nextInt(1000, 10000)
        store.setPoints(userId, record.loyaltyPoints - reward.cost)
        store.addRedemption(userId, reward.id, code)
        code
    }
}
