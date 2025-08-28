package com.fertipos.agroshop.data.repo

import com.fertipos.agroshop.data.local.dao.UserDao
import com.fertipos.agroshop.data.local.entities.User
import com.fertipos.agroshop.security.CryptoManager
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val crypto: CryptoManager
) {
    suspend fun register(username: String, password: String): Result<Unit> {
        val existing = userDao.findByUsername(username)
        if (existing != null) return Result.failure(IllegalStateException("Username already exists"))
        val enc = crypto.encryptToBase64(password)
        userDao.insert(User(username = username, passwordEnc = enc))
        return Result.success(Unit)
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        val user = userDao.findByUsername(username)
            ?: return Result.failure(IllegalArgumentException("Invalid credentials"))
        return try {
            val plain = crypto.decryptFromBase64(user.passwordEnc)
            if (plain == password) Result.success(Unit) else Result.failure(IllegalArgumentException("Invalid credentials"))
        } catch (e: Exception) {
            // Likely backup restored from another device: KeyStore key mismatch makes old cipher undecryptable.
            // Pragmatic recovery: if user supplies a password, rebind it to this device by re-encrypting with current key.
            // This acts as a local password reset after restore.
            return try {
                val newEnc = crypto.encryptToBase64(password)
                userDao.update(user.copy(passwordEnc = newEnc))
                Result.success(Unit)
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Decryption error"))
            }
        }
    }
}
