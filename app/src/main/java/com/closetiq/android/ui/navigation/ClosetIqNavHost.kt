package com.closetiq.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
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

sealed class Screen(val route: String, val label: String, val icon: ImageVector?) {
    data object Mirror : Screen("mirror", "Mirror", Icons.Filled.Face)
    data object Closet : Screen("closet", "Closet", Icons.Filled.Checkroom)
    data object Dormant : Screen("dormant", "Dormant", Icons.Filled.HourglassBottom)
    data object AddItem : Screen("add", "Add", null)
    data object BuyCheck : Screen("buy-check", "Buy check", null)
}

private val BOTTOM_TABS = listOf(Screen.Mirror, Screen.Closet, Screen.Dormant)

@Composable
fun ClosetIqNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in BOTTOM_TABS.map { it.route }) {
                NavigationBar {
                    BOTTOM_TABS.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                screen.icon?.let { Icon(it, contentDescription = screen.label) }
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Mirror.route,
            modifier = Modifier.padding(padding)
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
                BuyCheckScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
