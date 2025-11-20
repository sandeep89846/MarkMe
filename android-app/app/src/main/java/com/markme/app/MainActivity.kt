package com.markme.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
// --- START FIX ---
import androidx.compose.material.icons.automirrored.filled.ExitToApp // Use AutoMirrored
// --- END FIX ---
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.markme.app.ui.AuthUiState
import com.markme.app.ui.AuthViewModel
import com.markme.app.ui.HomeScreen
import com.markme.app.ui.HistoryScreen
import com.markme.app.ui.MainViewModel
import com.markme.app.ui.theme.ProjectMarkMeTheme

// Define navigation routes
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object History : Screen("history", "History", Icons.Default.History)
}

val navItems = listOf(Screen.Home, Screen.History)

class MainActivity : AppCompatActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authViewModel.initialize(this.applicationContext)

        setContent {
            ProjectMarkMeTheme {
                val authState by authViewModel.uiState.collectAsState()

                // 1. Create Google Sign-In launcher
                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                    onResult = { result ->
                        authViewModel.onSignInResult(result)
                    }
                )

                // 2. Show Auth screen or Main App
                if (authState.isAuthenticated) {
                    MainAppScreen(
                        onSignOut = {
                            authViewModel.signOut()
                        }
                    )
                } else {
                    AuthScreen(
                        uiState = authState,
                        onSignInClick = {
                            authViewModel.startSignIn(googleSignInLauncher)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(uiState: AuthUiState, onSignInClick: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to MarkMe",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
                uiState.userMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                Button(onClick = onSignInClick, enabled = !uiState.isLoading) {
                    Text("Sign in with Google")
                }
                uiState.userMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Needed for TopAppBar
@Composable
fun MainAppScreen(onSignOut: () -> Unit) { // Add onSignOut parameter
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    // Initialize the ViewModel once
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        mainViewModel.initialize(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Mark Me") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            // --- START FIX ---
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp, // Use new icon
                            // --- END FIX ---
                            contentDescription = "Sign Out"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationRoute!!) {
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
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            mainViewModel = mainViewModel,
            modifier = Modifier.padding(innerPadding) // This padding now respects the TopAppBar
        )
    }
}

// --- FIX: Removed the duplicate MainAppScreen() function that was here ---

@Composable
fun AppNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            // Pass the singleton ViewModel to the home screen
            HomeScreen(mainViewModel = mainViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
    }
}