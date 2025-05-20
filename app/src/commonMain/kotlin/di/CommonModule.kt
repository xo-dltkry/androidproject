package di

import data.AuthRepository
import model.CurrencyProvider
import model.ExchangeRateProvider
import model.database.ExpenseRepository
import model.database.RecurringExpenseDao
import model.database.RecurringExpenseDatabase
import model.notification.ExpenseNotificationManager
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import viewmodel.AuthViewModel
import viewmodel.EditRecurringExpenseViewModel
import viewmodel.MainNavigationViewModel
import viewmodel.RecurringExpenseViewModel
import viewmodel.SettingsViewModel
import viewmodel.UpcomingPaymentsViewModel

expect val platformModule: Module

val sharedModule = module {
    // ViewModels
    viewModelOf(::RecurringExpenseViewModel)
    viewModelOf(::UpcomingPaymentsViewModel)
    viewModel { (expenseId: Int?) ->
        EditRecurringExpenseViewModel(expenseId, get(), get(), get())
    }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::MainNavigationViewModel)
    viewModel { AuthViewModel(get()) }

    // Services
    singleOf(::CurrencyProvider)
    singleOf(::ExchangeRateProvider)
    singleOf(::ExpenseNotificationManager)
}

val commonModule = module {
    // Common module dependencies
} 