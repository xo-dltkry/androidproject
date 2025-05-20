package data

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun register(email: String, username: String, password: String): Result<User>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
} 