package com.gymwp.free

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymwp.free.data.WorkoutDayEntity
import com.gymwp.free.data.WorkoutExerciseEntity
import com.gymwp.free.data.WorkoutPlanEntity
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AppUpdateDialogIfNeeded
import com.gymwp.free.ui.screens.*
import com.gymwp.free.ui.theme.GymCardBg
import com.gymwp.free.ui.theme.GymOrange
import com.gymwp.free.ui.theme.GymWpTheme

private data class ActiveLiveSession(
    val plan: WorkoutPlanEntity?,
    val day: WorkoutDayEntity,
    val exercises: List<WorkoutExerciseEntity>
)

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
            GymWpTheme {
                val vm: GymWpViewModel = viewModel()
                val context = LocalContext.current
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

                // Diálogo automático de actualización si existe una nueva versión en GitHub
                AppUpdateDialogIfNeeded(viewModel = vm)

                var currentTab by remember { mutableStateOf(BottomNavTab.HOME) }
                var liveSession by remember { mutableStateOf<ActiveLiveSession?>(null) }

                val currentLive = liveSession
                if (currentLive != null) {
                    LiveWorkoutAssistantScreen(
                        viewModel = vm,
                        plan = currentLive.plan,
                        day = currentLive.day,
                        exercises = currentLive.exercises,
                        onExitSession = { liveSession = null }
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = GymCardBg,
                                contentColor = Color.White
                            ) {
                                BottomNavTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
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
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
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
                                    onStartLiveWorkout = { plan, day, exs ->
                                        liveSession = ActiveLiveSession(plan, day, exs)
                                    },
                                    onNavigateToExplore = { currentTab = BottomNavTab.EXPLORE }
                                )
                                BottomNavTab.EXPLORE -> ExplorePrebuiltPlansScreen(
                                    viewModel = vm,
                                    onPlanImported = { currentTab = BottomNavTab.HOME }
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
