package com.pomodoro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Home : Tab("home", "Home", Icons.Default.Home)
    object Todo : Tab("todo", "Todo", Icons.Default.CheckCircle)
    object Notes : Tab("notes", "Notes", Icons.Default.Notes)
    object Settings : Tab("settings", "Settings", Icons.Default.Settings)
}

private val tabs = listOf(Tab.Home, Tab.Todo, Tab.Notes, Tab.Settings)

@Composable
fun PomodoroNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: TimerViewModel = viewModel()
    val ctx = LocalContext.current

    Scaffold(
        modifier = modifier.statusBarsPadding(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.surface)
                    .navigationBarsPadding()
            ) {
                // Thin top divider like Instagram
                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
                BottomNavigation(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface,
                    elevation = 0.dp,
                    modifier = Modifier.height(52.dp)
                ) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                        BottomNavigationItem(
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = null,
                            alwaysShowLabel = false,
                            selected = selected,
                            selectedContentColor = MaterialTheme.colors.primary,
                            unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.Home.route) {
                MainScreen(viewModel = viewModel)
            }
            composable(Tab.Todo.route) {
                TodoScreen(
                    viewModel = viewModel,
                    onStartTask = {
                        navController.navigate(Tab.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Tab.Notes.route) {
                NotesScreen(viewModel = viewModel)
            }
            composable(Tab.Settings.route) {
                SettingsScreen(ctx = ctx, viewModel = viewModel)
            }
        }
    }
}
