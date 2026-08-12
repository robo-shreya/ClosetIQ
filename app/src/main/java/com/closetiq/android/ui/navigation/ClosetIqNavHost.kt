package com.closetiq.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.closetiq.android.AppContainer
import com.closetiq.android.ui.additem.AddItemScreen
import com.closetiq.android.ui.buycheck.BuyCheckScreen
import com.closetiq.android.ui.closet.ClosetScreen
import com.closetiq.android.ui.dormant.DormantQueueScreen
import com.closetiq.android.ui.mirror.MirrorScreen
import com.closetiq.android.ui.theme.Nocturne
import kotlinx.coroutines.flow.map

/**
 * @param label the bottom-bar caption; unused by screens that are not tabs
 * @param kicker the uppercase eyebrow above the header title
 */
sealed class Screen(
    val route: String,
    val label: String,
    val kicker: String,
    val title: String
) {
    data object Mirror : Screen("mirror", "Mirror", "Start destination", "Mirror")
    data object Closet : Screen("closet", "Closet", "Everything you own", "Closet")
    data object Dormant : Screen("dormant", "Dormant", "The forgotten ones", "Dormant")
    data object AddItem : Screen("add", "Add", "Closet", "Add item")
    data object BuyCheck : Screen("buy-check", "Worth it?", "Before you buy", "Worth it?")

    companion object {
        val bottomTabs = listOf(Mirror, Closet, Dormant, BuyCheck)

        private val all = listOf(Mirror, Closet, Dormant, AddItem, BuyCheck)

        fun fromRoute(route: String?): Screen = all.firstOrNull { it.route == route } ?: Mirror
    }
}

/**
 * The header and the tab bar live outside the NavHost, so only the content area swaps.
 * That holds the header still between tabs instead of re-animating it on every switch.
 */
@Composable
fun ClosetIqNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Screen.fromRoute(backStackEntry?.destination?.route)

    val garmentCountFlow = remember(container) {
        container.wardrobeRepository.observeAllGarments().map { it.size }
    }
    val garmentCount by garmentCountFlow.collectAsStateWithLifecycle(initialValue = 0)

    // popBackStack() returns Boolean, so the type has to be stated for the lambda to
    // coerce to the () -> Unit the header expects.
    val onBack: (() -> Unit)? = if (current == Screen.AddItem) {
        { navController.popBackStack() }
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.Bg)
    ) {
        ScreenHeader(
            kicker = current.kicker,
            title = current.title,
            meta = when (current) {
                Screen.Closet -> "$garmentCount items"
                Screen.Dormant -> "ranked"
                else -> null
            },
            onBack = onBack
        )

        Column(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Mirror.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Mirror.route) {
                    MirrorScreen(container = container)
                }
                composable(Screen.Closet.route) {
                    ClosetScreen(
                        container = container,
                        onAddItem = { navController.navigate(Screen.AddItem.route) }
                    )
                }
                composable(Screen.Dormant.route) {
                    DormantQueueScreen(container = container)
                }
                composable(Screen.AddItem.route) {
                    AddItemScreen(
                        container = container,
                        onDone = { navController.popBackStack() }
                    )
                }
                composable(Screen.BuyCheck.route) {
                    BuyCheckScreen(container = container)
                }
            }
        }

        if (current in Screen.bottomTabs) {
            BottomTabs(
                tabs = Screen.bottomTabs,
                currentRoute = current.route,
                onSelect = navController::switchTab
            )
        }
    }
}

/** Tab switches reuse the existing entry rather than stacking duplicates. */
private fun NavHostController.switchTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
