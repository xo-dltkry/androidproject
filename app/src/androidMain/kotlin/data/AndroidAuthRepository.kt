package data

import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import android.util.Log

class AndroidAuthRepository(
    private val context: Context,
    private val userPreferences: UserPreferences
) : AuthRepository {
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: Flow<User?> = _currentUser.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sharedPreferences = context.getSharedPreferences("users", Context.MODE_PRIVATE)

    init {
        scope.launch {
            try {
                Log.d("AndroidAuthRepository", "Initializing auth repository")
                val user = userPreferences.currentUser.first()
                Log.d("AndroidAuthRepository", "Initial user state: ${user?.email}")
                _currentUser.value = user
            } catch (e: Exception) {
                Log.e("AndroidAuthRepository", "Failed to initialize auth repository", e)
            }
        }
    }

    private fun saveUsers(users: Map<String, User>) {
        val json = Json.encodeToString(users)
        sharedPreferences.edit().putString("users", json).apply()
    }

    private fun loadUsers(): Map<String, User> {
        val json = sharedPreferences.getString("users", null) ?: return emptyMap()
        return try {
            Json.decodeFromString<Map<String, User>>(json)
        } catch (e: Exception) {
            Log.e("AndroidAuthRepository", "Failed to load users", e)
            emptyMap()
        }
    }

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            Log.d("AndroidAuthRepository", "Attempting to login: $email")
            val users = loadUsers()
            val user = users[email]
            
            if (user == null) {
                Log.e("AndroidAuthRepository", "User not found: $email")
                return Result.failure(Exception("User not found"))
            }

            if (user.passwordHash != password.hashCode().toString()) {
                Log.e("AndroidAuthRepository", "Invalid password for user: $email")
                return Result.failure(Exception("Invalid password"))
            }

            userPreferences.saveUser(user)
            _currentUser.value = user
            Log.d("AndroidAuthRepository", "Login successful")
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AndroidAuthRepository", "Login failed", e)
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, username: String, password: String): Result<User> {
        return try {
            Log.d("AndroidAuthRepository", "Attempting to register: $email")
            val users = loadUsers()
            
            if (users.containsKey(email)) {
                Log.e("AndroidAuthRepository", "User already exists: $email")
                return Result.failure(Exception("User already exists"))
            }

            val user = User(
                id = System.currentTimeMillis().toString(),
                email = email,
                username = username,
                passwordHash = password.hashCode().toString()
            )

            val updatedUsers = users + (email to user)
            saveUsers(updatedUsers)
            userPreferences.saveUser(user)
            _currentUser.value = user
            Log.d("AndroidAuthRepository", "Registration successful")
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AndroidAuthRepository", "Registration failed", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        try {
            Log.d("AndroidAuthRepository", "Attempting to logout")
            userPreferences.saveUser(null)
            _currentUser.value = null
            Log.d("AndroidAuthRepository", "Logout successful")
        } catch (e: Exception) {
            Log.e("AndroidAuthRepository", "Logout failed", e)
            throw e
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return _currentUser.value != null
    }
} 