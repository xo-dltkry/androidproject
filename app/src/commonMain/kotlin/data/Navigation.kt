package data

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

data class BottomNavigation(
    val route: Any,
    val name: StringResource,
    val icon: ImageVector,
)

@Serializable
sealed interface MainNavRoute {
    val route: String
}

@Serializable
object HomePane : MainNavRoute {
    override val route: String = "home"
}

@Serializable
object UpcomingPane : MainNavRoute {
    override val route: String = "upcoming"
}

@Serializable
object SettingsPane : MainNavRoute {
    override val route: String = "settings"
}

@Serializable
object SettingsPaneAbout

@Serializable
object SettingsPaneLibraries

@Serializable
object SettingsPaneDefaultCurrency

@Serializable
data class EditExpensePane(
    val expenseId: Int? = null,
) : MainNavRoute {
    override val route: String = "edit_expense"
}

@Serializable
object LoginPane : MainNavRoute {
    override val route: String = "login"
}

@Serializable
object RegisterPane : MainNavRoute {
    override val route: String = "register"
}

inline fun <reified T : Any> NavDestination?.isInRoute(vararg routes: T): Boolean {
    return routes.any { route -> this?.hasRoute(route::class) == true }
}

fun isInRoute(route: String, vararg routes: MainNavRoute): Boolean {
    return routes.any { it.route == route }
}
