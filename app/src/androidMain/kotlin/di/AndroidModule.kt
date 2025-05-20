package di

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import data.AuthRepository
import data.AndroidAuthRepository
import data.UserPreferences
import model.database.UserPreferencesRepository
import model.database.ExpenseRepository
import model.database.RecurringExpenseDatabase
import model.database.RecurringExpenseDao
import model.notification.ExpenseNotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val androidModule = module {
    // User preferences and authentication
    single { UserPreferences(androidContext()) }
    single<AuthRepository> { AndroidAuthRepository(androidContext(), get()) }
    single { UserPreferencesRepository(androidContext().dataStore) }
    
    // Database
    single { 
        Room.databaseBuilder(
            androidContext(),
            RecurringExpenseDatabase::class.java,
            "recurring_expense_database"
        ).build()
    }
    single<RecurringExpenseDao> { get<RecurringExpenseDatabase>().recurringExpenseDao() }
    single { ExpenseRepository(get()) }
    
    // Notifications
    single { ExpenseNotificationManager(get(), get()) }
    
    // System services
    single { androidContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    
    // Include shared module
    includes(sharedModule)
} 