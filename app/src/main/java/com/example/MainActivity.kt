package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.BuildingDatabase
import com.example.data.BuildingRepository
import com.example.model.UserRole
import com.example.ui.BuildingViewModel
import com.example.ui.BuildingViewModelFactory
import com.example.ui.components.TopBuildingBar
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalAppLanguage
import com.example.ui.localization.Strings
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

const val ROUTE_LOGIN = "login"
const val ROUTE_HOME = "home"
const val ROUTE_FINANCE = "finance"
const val ROUTE_MAINTENANCE = "maintenance"
const val ROUTE_ELEVATOR = "elevator"
const val ROUTE_VOTING = "voting"
const val ROUTE_MORE = "more"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = BuildingDatabase.getDatabase(applicationContext)
        val repository = BuildingRepository(database.buildingDao())
        val viewModelFactory = BuildingViewModelFactory(repository)

        setContent {
            val viewModel: BuildingViewModel = viewModel(factory = viewModelFactory)

            val appLanguage by viewModel.appLanguage.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val currentUser by viewModel.currentUser.collectAsState()
            val isSyndicMode by viewModel.isSyndicMode.collectAsState()
            val isOffline by viewModel.isOffline.collectAsState()
            val lastSyncTime by viewModel.lastSyncTime.collectAsState()
            val snackMessage by viewModel.snackMessage.collectAsState()

            // Localization and Direction support
            val layoutDirection = if (appLanguage == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(
                LocalAppLanguage provides appLanguage,
                LocalLayoutDirection provides layoutDirection
            ) {
                MyApplicationTheme(themeMode = themeMode) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val snackbarHostState = remember { SnackbarHostState() }
                    val scope = rememberCoroutineScope()

                    // Trigger snackbar on message
                    LaunchedEffect(snackMessage) {
                        snackMessage?.let { msg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(msg)
                                viewModel.dismissSnack()
                            }
                        }
                    }

                    // Auto navigate based on auth state
                    LaunchedEffect(currentUser) {
                        if (currentUser == null) {
                            navController.navigate(ROUTE_LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        } else if (currentRoute == ROUTE_LOGIN || currentRoute == null) {
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(ROUTE_LOGIN) { inclusive = true }
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (currentUser != null && currentRoute != ROUTE_LOGIN && currentRoute != ROUTE_ELEVATOR && currentRoute != ROUTE_VOTING) {
                                TopBuildingBar(
                                    currentUser = currentUser,
                                    isSyndicMode = isSyndicMode,
                                    isOffline = isOffline,
                                    lastSyncTime = lastSyncTime,
                                    appLanguage = appLanguage,
                                    onToggleSyndicMode = { viewModel.toggleSyndicMode() },
                                    onToggleOffline = { viewModel.toggleOffline() },
                                    onToggleLanguage = {
                                        viewModel.setLanguage(if (appLanguage == AppLanguage.ARABIC) AppLanguage.FRENCH else AppLanguage.ARABIC)
                                    },
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        },
                        bottomBar = {
                            if (currentUser != null && currentRoute != ROUTE_LOGIN && currentRoute != ROUTE_ELEVATOR && currentRoute != ROUTE_VOTING) {
                                NavigationBar(
                                    modifier = Modifier.testTag("main_bottom_nav"),
                                    tonalElevation = 8.dp
                                ) {
                                    val isHomeSelected = currentRoute == ROUTE_HOME
                                    NavigationBarItem(
                                        selected = isHomeSelected,
                                        onClick = {
                                            if (!isHomeSelected) navController.navigate(ROUTE_HOME) {
                                                popUpTo(ROUTE_HOME) { inclusive = true }
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Default.Home,
                                                contentDescription = Strings.tabHome(appLanguage)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isSyndicMode) Strings.tabSyndicManage(appLanguage) else Strings.tabHome(appLanguage),
                                                fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_item_home")
                                    )

                                    val isFinanceSelected = currentRoute == ROUTE_FINANCE
                                    NavigationBarItem(
                                        selected = isFinanceSelected,
                                        onClick = {
                                            if (!isFinanceSelected) navController.navigate(ROUTE_FINANCE)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isFinanceSelected) Icons.Filled.AccountBalance else Icons.Default.AccountBalance,
                                                contentDescription = Strings.tabFinance(appLanguage)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = Strings.tabFinance(appLanguage),
                                                fontWeight = if (isFinanceSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_item_finance")
                                    )

                                    val isMaintenanceSelected = currentRoute == ROUTE_MAINTENANCE
                                    NavigationBarItem(
                                        selected = isMaintenanceSelected,
                                        onClick = {
                                            if (!isMaintenanceSelected) navController.navigate(ROUTE_MAINTENANCE)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isMaintenanceSelected) Icons.Filled.Build else Icons.Default.Build,
                                                contentDescription = Strings.tabMaintenance(appLanguage)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = Strings.tabMaintenance(appLanguage),
                                                fontWeight = if (isMaintenanceSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_item_maintenance")
                                    )

                                    val isMoreSelected = currentRoute == ROUTE_MORE
                                    NavigationBarItem(
                                        selected = isMoreSelected,
                                        onClick = {
                                            if (!isMoreSelected) navController.navigate(ROUTE_MORE)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isMoreSelected) Icons.Filled.MoreHoriz else Icons.Default.MoreHoriz,
                                                contentDescription = Strings.tabMore(appLanguage)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = Strings.tabMore(appLanguage),
                                                fontWeight = if (isMoreSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_item_more")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = if (currentUser == null) ROUTE_LOGIN else ROUTE_HOME,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(ROUTE_LOGIN) {
                                LoginScreen(
                                    appLanguage = appLanguage,
                                    onLoginSubmit = { u, p -> viewModel.login(u, p) },
                                    onToggleLanguage = {
                                        viewModel.setLanguage(if (appLanguage == AppLanguage.ARABIC) AppLanguage.FRENCH else AppLanguage.ARABIC)
                                    }
                                )
                            }

                            composable(ROUTE_HOME) {
                                val user = currentUser ?: return@composable
                                val projects by viewModel.projects.collectAsState()
                                val transparencyList by viewModel.transparencyList.collectAsState()
                                val announcements by viewModel.announcements.collectAsState()
                                val maintenanceReports by viewModel.maintenanceReports.collectAsState()
                                val votingSessions by viewModel.votingSessions.collectAsState()
                                val lockedLedger by viewModel.lockedLedger.collectAsState()

                                if (isSyndicMode && user.role == UserRole.SYNDIC) {
                                    // Dedicated Single-Syndic Management Screen
                                    SyndicManagementScreen(
                                        user = user,
                                        appLanguage = appLanguage,
                                        projects = projects,
                                        lockedLedger = lockedLedger,
                                        onCreateProject = { t, d, c -> viewModel.createProject(t, d, c) },
                                        onRecordPayment = { pId, pTitle, apt, oId, oName, amt, meth ->
                                            viewModel.recordOwnerPayment(pId, pTitle, apt, oId, oName, amt, meth)
                                        },
                                        onCreateExpense = { pId, pT, cat, desc, amt, sup, inv, meth ->
                                            viewModel.createExpense(pId, pT, cat, desc, amt, sup, inv, meth)
                                        },
                                        onRecordCorrection = { orig, delta, isDebit, r ->
                                            viewModel.recordFinancialCorrection(orig, delta, isDebit, r)
                                        },
                                        onPublishAnnouncement = { t, c, cat -> viewModel.publishAnnouncement(t, c, cat) },
                                        onScheduleMeeting = { t, d, l, desc, ag -> viewModel.createMeeting(t, d, l, desc, ag) }
                                    )
                                } else {
                                    // Regular Owner Screen
                                    OwnerHomeScreen(
                                        user = user,
                                        appLanguage = appLanguage,
                                        transparencyList = transparencyList,
                                        projects = projects,
                                        announcements = announcements,
                                        maintenanceReports = maintenanceReports,
                                        votingSessions = votingSessions,
                                        onNavigateToFinance = { navController.navigate(ROUTE_FINANCE) },
                                        onNavigateToMaintenance = { navController.navigate(ROUTE_MAINTENANCE) },
                                        onNavigateToVoting = { navController.navigate(ROUTE_VOTING) }
                                    )
                                }
                            }

                            composable(ROUTE_FINANCE) {
                                val projects by viewModel.projects.collectAsState()
                                val transparencyList by viewModel.transparencyList.collectAsState()
                                val lockedLedger by viewModel.lockedLedger.collectAsState()
                                val selectedProjectId by viewModel.selectedProjectIdForTransparency.collectAsState()

                                FinanceScreen(
                                    appLanguage = appLanguage,
                                    projects = projects,
                                    transparencyList = transparencyList,
                                    lockedLedger = lockedLedger,
                                    selectedProjectId = selectedProjectId,
                                    onSelectProjectFilter = { viewModel.selectProjectForTransparency(it) }
                                )
                            }

                            composable(ROUTE_MAINTENANCE) {
                                val user = currentUser ?: return@composable
                                val maintenanceReports by viewModel.maintenanceReports.collectAsState()

                                MaintenanceScreen(
                                    user = user,
                                    appLanguage = appLanguage,
                                    reports = maintenanceReports,
                                    onSubmitReport = { cat, desc, uri -> viewModel.reportProblem(cat, desc, uri) },
                                    onNavigateToElevator = { navController.navigate(ROUTE_ELEVATOR) }
                                )
                            }

                            composable(ROUTE_ELEVATOR) {
                                val elevatorRecords by viewModel.elevatorRecords.collectAsState()

                                ElevatorScreen(
                                    appLanguage = appLanguage,
                                    elevatorRecords = elevatorRecords,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(ROUTE_VOTING) {
                                val user = currentUser ?: return@composable
                                val votingSessions by viewModel.votingSessions.collectAsState()

                                VotingCenterScreen(
                                    user = user,
                                    appLanguage = appLanguage,
                                    votingSessions = votingSessions,
                                    getVotesForSession = { sessionId -> viewModel.getVotesForSession(sessionId) },
                                    onCastVote = { sId, choice -> viewModel.castVote(sId, choice) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(ROUTE_MORE) {
                                val user = currentUser ?: return@composable
                                val announcements by viewModel.announcements.collectAsState()
                                val meetings by viewModel.meetings.collectAsState()
                                val documents by viewModel.documents.collectAsState()
                                val auditLogs by viewModel.auditLogs.collectAsState()

                                MoreHubScreen(
                                    user = user,
                                    appLanguage = appLanguage,
                                    themeMode = themeMode,
                                    announcements = announcements,
                                    meetings = meetings,
                                    documents = documents,
                                    auditLogs = auditLogs,
                                    onNavigateToElevator = { navController.navigate(ROUTE_ELEVATOR) },
                                    onNavigateToVoting = { navController.navigate(ROUTE_VOTING) },
                                    onSetLanguage = { viewModel.setLanguage(it) },
                                    onSetThemeMode = { viewModel.setThemeMode(it) },
                                    onLogout = { viewModel.logout() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
