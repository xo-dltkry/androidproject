package de.dbauer.expensetracker

import Constants
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import asString
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import data.*
import data.MainNavRoute.Companion.serializer
import de.dbauer.expensetracker.widget.UpcomingPaymentsWidget
import de.dbauer.expensetracker.widget.UpcomingPaymentsWidgetReceiver
import di.sharedModule
import di.androidModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import model.DatabaseBackupRestore
import model.database.UserPreferencesRepository
import model.notification.ExpenseNotificationManager
import model.notification.NotificationLoopReceiver
import model.notification.startAlarmLooper
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.android.get
import recurringexpensetracker.app.generated.resources.Res
import recurringexpensetracker.app.generated.resources.biometric_prompt_manager_title
import recurringexpensetracker.app.generated.resources.biometric_prompt_manager_unlock
import recurringexpensetracker.app.generated.resources.cancel
import recurringexpensetracker.app.generated.resources.settings_backup_created_toast
import recurringexpensetracker.app.generated.resources.settings_backup_not_created_toast
import recurringexpensetracker.app.generated.resources.settings_backup_not_restored_toast
import recurringexpensetracker.app.generated.resources.settings_backup_restored_toast
import security.BiometricPromptManager
import security.BiometricPromptManager.BiometricResult
import ui.DefaultTab
import ui.MainContent
import ui.ThemeMode
import ui.theme.ExpenseTrackerTheme
import viewmodel.MainActivityViewModel
import java.io.File
import androidx.compose.runtime.remember

class MainActivity : AppCompatActivity() {
    private val databasePath by lazy { getDatabasePath(Constants.DATABASE_NAME).path }
    private val userPreferencesRepository = get<UserPreferencesRepository>()
    private val expenseNotificationManager = get<ExpenseNotificationManager>()
    private val mainActivityViewModel: MainActivityViewModel by viewModels()
    private val authRepository: AuthRepository = get()

    private val biometricPromptManager: BiometricPromptManager by lazy { BiometricPromptManager(this) }

    private val biometricSetup =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lifecycleScope.launch {
                if (it.resultCode == FINISH_TASK_WITH_ACTIVITY) {
                    triggerAuthPrompt()
                } else if (it.resultCode == RESULT_CANCELED) {
                    userPreferencesRepository.biometricSecurity.save(false)
                }
            }
        }

    private fun canUseNotifications(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun navigateToPermissionsSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        })
    }

    private fun backupDatabase() {
        val backupPathLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(Constants.BACKUP_MIME_TYPE)) { uri ->
            if (uri == null) return@registerForActivityResult
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            applicationContext.contentResolver.takePersistableUriPermission(uri, takeFlags)

            lifecycleScope.launch {
                val backupSuccessful = DatabaseBackupRestore().exportDatabaseFile(
                    databasePath = databasePath,
                    targetUri = uri,
                    applicationContext = applicationContext,
                )
                val toastString = if (backupSuccessful) {
                    Res.string.settings_backup_created_toast
                } else {
                    Res.string.settings_backup_not_created_toast
                }.asString()
                Toast.makeText(this@MainActivity, toastString, Toast.LENGTH_LONG).show()
            }
        }
        backupPathLauncher.launch(Constants.DEFAULT_BACKUP_NAME)
    }

    private fun restoreDatabase() {
        val importPathLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                File(databasePath).parent?.let { targetPath ->
                    val backupRestored = DatabaseBackupRestore().importDatabaseFile(
                        srcZipUri = uri,
                        targetPath = targetPath,
                        applicationContext = applicationContext,
                    )
                    val toastString = if (backupRestored) {
                        Res.string.settings_backup_restored_toast
                    } else {
                        Res.string.settings_backup_not_restored_toast
                    }.asString()
                    Toast.makeText(this@MainActivity, toastString, Toast.LENGTH_LONG).show()

                    if (backupRestored) {
                        // Restart Activity after restoring backup to make sure the repository is updated
                        finish()
                        startActivity(intent)
                    }
                }
            }
        }
        importPathLauncher.launch(arrayOf(Constants.BACKUP_MIME_TYPE))
    }

    private fun updateWidget() {
        lifecycleScope.launch {
            val glanceAppWidgetManager = GlanceAppWidgetManager(this@MainActivity)
            glanceAppWidgetManager.requestPinGlanceAppWidget(
                UpcomingPaymentsWidgetReceiver::class.java,
                null
            )
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Check authentication state on startup
        lifecycleScope.launch {
            authRepository.currentUser.collect { user ->
                Log.d(TAG, "Auth state changed: user = ${user?.email}")
                mainActivityViewModel.isUnlocked = user != null
            }
        }

        lifecycleScope.launch {
            biometricPromptManager.promptResult.collectLatest {
                when (it) {
                    is BiometricResult.AuthenticationError -> {
                        Log.e(TAG, it.error)
                    }
                    BiometricResult.AuthenticationFailed -> {
                        Log.e(TAG, "Authentication failed")
                    }
                    BiometricResult.AuthenticationNotSet -> {
                        // open directly the setup settings for biometrics
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            biometricSetup.launch(
                                Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                    putExtra(
                                        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                        biometricPromptManager.authenticators,
                                    )
                                },
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // open old setup settings dialog
                            @Suppress("DEPRECATION")
                            biometricSetup.launch(Intent(Settings.ACTION_FINGERPRINT_ENROLL))
                        
                            // open security settings
                            try {
                                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                            } finally {
                                launch {
                                    userPreferencesRepository.biometricSecurity.save(false)
                                }
                            }
                        }
                    }
                    BiometricResult.AuthenticationSuccess -> {
                        Log.i(TAG, "Authentication Success")
                        mainActivityViewModel.isUnlocked = true
                    }
                    BiometricResult.FeatureUnavailable -> {
                        Log.i(TAG, "Authentication unavailable")
                    }
                    BiometricResult.HardwareUnavailable -> {
                        Log.i(TAG, "Hardware not available")
                    }
                }
            }
        }

        val canUseBiometric = biometricPromptManager.canUseAuthenticator()
        val startRoute = runBlocking {
            val user = authRepository.currentUser.first()
            Log.d(TAG, "Initial auth check: user = ${user?.email}")
            if (user != null) HomePane else LoginPane
        }

        val invalidExpenseId = -1
        val expenseId = intent.getIntExtra(EXTRA_EXPENSE_ID, invalidExpenseId)
        if (expenseId != invalidExpenseId) {
            lifecycleScope.launch {
                expenseNotificationManager.markNotificationAsShown(expenseId)
            }
        }

        // Register on change for upcoming payment notification and reschedule alarm looper
        lifecycleScope.launch {
            userPreferencesRepository.upcomingPaymentNotification.get().collect {
                startAlarmLooper(NotificationLoopReceiver::class.java)
            }
        }

        setContent {
            val isGridMode by userPreferencesRepository.gridMode.collectAsState()
            val biometricSecurity by userPreferencesRepository.biometricSecurity.collectAsState()

            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainContent(
                        isGridMode = isGridMode,
                        biometricSecurity = biometricSecurity,
                        canUseBiometric = canUseBiometric,
                        canUseNotifications = canUseNotifications(),
                        hasNotificationPermission = hasNotificationPermission(),
                        toggleGridMode = {
                            lifecycleScope.launch {
                                userPreferencesRepository.gridMode.save(!isGridMode)
                            }
                        },
                        onBiometricSecurityChange = { enabled ->
                            lifecycleScope.launch {
                                userPreferencesRepository.biometricSecurity.save(enabled)
                                if (enabled) {
                                    triggerAuthPrompt()
                                }
                            }
                        },
                        requestNotificationPermission = {
                            requestNotificationPermission()
                        },
                        navigateToPermissionsSettings = {
                            navigateToPermissionsSettings()
                        },
                        onClickBackup = {
                            backupDatabase()
                        },
                        onClickRestore = {
                            restoreDatabase()
                        },
                        updateWidget = {
                            updateWidget()
                        },
                        startRoute = startRoute,
                    )
                }
            }
        }
    }

    private suspend fun triggerAuthPrompt() {
        val title = Res.string.biometric_prompt_manager_title.asString()
        val cancel = Res.string.cancel.asString()
        
        biometricPromptManager.showBiometricPrompt(
            title = title,
            cancel = cancel
        )
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_START_ROUTE = "extra_start_route"
        private const val EXTRA_EXPENSE_ID = "extra_expense_id"
        private const val FINISH_TASK_WITH_ACTIVITY = 1
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100

        fun createIntent(
            context: Context,
            startRoute: MainNavRoute? = null,
            expenseId: Int? = null,
        ): Intent {
            return Intent(context, MainActivity::class.java).apply {
                startRoute?.let {
                    putExtra(
                        EXTRA_START_ROUTE,
                        Json.encodeToString(serializer(), it),
                    )
                }
                expenseId?.let { putExtra(EXTRA_EXPENSE_ID, it) }
            }
        }
    }
}
