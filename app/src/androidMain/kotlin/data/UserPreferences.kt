package data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val CURRENT_USER = stringPreferencesKey("current_user")
    }

    val currentUser: Flow<User?> = dataStore.data.map { preferences ->
        try {
            preferences[CURRENT_USER]?.let { Json.decodeFromString<User>(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUser(user: User?) {
        dataStore.edit { preferences ->
            if (user != null) {
                preferences[CURRENT_USER] = Json.encodeToString(user)
            } else {
                preferences.remove(CURRENT_USER)
            }
        }
    }
} 