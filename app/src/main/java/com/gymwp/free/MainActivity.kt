package com.gymwp.free

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AppUpdateDialogIfNeeded
import com.gymwp.free.ui.screens.*
import com.gymwp.free.ui.theme.GymCardBg
import com.gymwp.free.ui.theme.GymDarkBg
import com.gymwp.free.ui.theme.GymOrange
import com.gymwp.free.ui.theme.GymTextPrimary
import com.gymwp.free.ui.theme.GymTextSecondary
import com.gymwp.free.ui.theme.GymWpTheme

private enum class BottomNavTab(val label: String, val icon: ImageVector) {
    HOME("Entrenar", Icons.Default.FitnessCenter),
    EXPLORE("57 Planes", Icons.Default.Explore),
    FATIGUE("Fatiga 2D", Icons.Default.AccessibilityNew),
    EXERCISES("Ejercicios", Icons.Default.Insights),
    BODY("Mi Cuerpo", Icons.Default.MonitorWeight)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: GymWpViewModel = viewModel()
            val selectedTheme by vm.selectedTheme.collectAsState()

            GymWpTheme(themeOption = selectedTheme) {
                val context = LocalContext.current
                val view = LocalView.current
                val bgArgb = GymDarkBg.toArgb()
                val navBarArgb = GymCardBg.toArgb()

                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as? Activity)?.window ?: return@SideEffect
                        @Suppress("DEPRECATION")
                        window.statusBarColor = bgArgb
                        @Suppress("DEPRECATION")
                        window.navigationBarColor = navBarArgb
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = selectedTheme.isLight
                        controller.isAppearanceLightNavigationBars = selectedTheme.isLight
                    }
                }

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    vm.checkForAppUpdates(notifyIfAvailable = true)
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Diálogo automático de actualización OTA desde GitHub sin perder datos
                AppUpdateDialogIfNeeded(viewModel = vm)

                var currentTabName by rememberSaveable { mutableStateOf(BottomNavTab.HOME.name) }
                val currentTab = remember(currentTabName) {
                    BottomNavTab.entries.firstOrNull { it.name == currentTabName } ?: BottomNavTab.HOME
                }

                // Live workout session persisted across screen locks & config changes
                var liveDayId by rememberSaveable { mutableStateOf<Long?>(null) }
                var livePlanId by rememberSaveable { mutableStateOf<Long?>(null) }
                var livePlanName by rememberSaveable { mutableStateOf("Entrenamiento Libre") }
                var liveFallbackDayName by rememberSaveable { mutableStateOf("Entrenamiento") }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GymDarkBg)
                        .systemBarsPadding()
                        .imePadding(),
                    color = GymDarkBg
                ) {
                    val activeDayId = liveDayId
                    if (activeDayId != null) {
                        LiveWorkoutAssistantScreen(
                            viewModel = vm,
                            planId = livePlanId,
                            planName = livePlanName,
                            dayId = activeDayId,
                            fallbackDayName = liveFallbackDayName,
                            onExitSession = {
                                liveDayId = null
                                livePlanId = null
                            }
                        )
                    } else {
                        Scaffold(
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = GymDarkBg,
                            bottomBar = {
                                NavigationBar(
                                    containerColor = GymCardBg,
                                    contentColor = GymTextPrimary,
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                                    BottomNavTab.entries.forEach { tab ->
                                        val selected = currentTab == tab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentTabName = tab.name },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = { Text(tab.label, fontSize = 11.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = GymOrange,
                                                selectedTextColor = GymOrange,
                                                indicatorColor = GymOrange.copy(alpha = 0.16f),
                                                unselectedIconColor = GymTextSecondary,
                                                unselectedTextColor = GymTextSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (currentTab) {
                                    BottomNavTab.HOME -> HomeWorkoutsScreen(
                                        viewModel = vm,
                                        onStartLiveWorkout = { plan, day, _ ->
                                            livePlanId = plan?.id
                                            livePlanName = plan?.name ?: "Entrenamiento Libre"
                                            liveFallbackDayName = day.name
                                            liveDayId = day.id
                                        },
                                        onNavigateToExplore = { currentTabName = BottomNavTab.EXPLORE.name }
                                    )
                                    BottomNavTab.EXPLORE -> ExplorePrebuiltPlansScreen(
                                        viewModel = vm,
                                        onPlanImported = { currentTabName = BottomNavTab.HOME.name }
                                    )
                                    BottomNavTab.FATIGUE -> MuscleFatigueScreen(viewModel = vm)
                                    BottomNavTab.EXERCISES -> CatalogAndStatsScreen(viewModel = vm)
                                    BottomNavTab.BODY -> MyBodyScreen(viewModel = vm)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
