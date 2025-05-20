package de.dbauer.expensetracker

import android.app.Application
import di.sharedModule
import di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext

class ExpenseTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@ExpenseTrackerApplication)
                modules(sharedModule, androidModule)
            }
        }
    }
}
