package com.korenskoy.yandexmusic

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.korenskoy.yandexmusic.presentation.auth.AuthScreen
import com.korenskoy.yandexmusic.presentation.main.MainScreen
import com.korenskoy.yandexmusic.presentation.main.MainViewModel
import com.korenskoy.yandexmusic.ui.theme.YandexMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.korenskoy.yandexmusic.data.player.MusicService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Binding a controller keeps MusicService alive while the UI is visible; media3 then promotes it
    // to a foreground service by itself once playback starts.
    private var controllerFuture: ListenableFuture<MediaController>? = null

    @javax.inject.Inject
    lateinit var libraryTree: com.korenskoy.yandexmusic.data.player.MediaLibraryTree

    /** Incremented on each "open the player" request (notification tap); MainScreen reacts to changes. */
    private var openPlayerSignal by mutableIntStateOf(0)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission granted or denied if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true && savedInstanceState == null) openPlayerSignal++
        if (savedInstanceState == null) intent?.let { playFromSearch(it) }

        enableEdgeToEdge()
        setContent {
            YandexMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Skip the splash when the app is re-entered with a session already on screen
                    AppNavigation(skipSplash = savedInstanceState != null, openPlayerSignal = openPlayerSignal)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerSignal++
        playFromSearch(intent)
    }

    /** Assistant request "play <query> on Yandex Music Lite": same matching as voice search in the car. */
    private fun playFromSearch(intent: Intent) {
        if (intent.action != android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(android.app.SearchManager.QUERY)
        lifecycleScope.launch {
            libraryTree.startAction(mediaId = "", searchQuery = query)?.invoke()
            openPlayerSignal++
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

@Composable
fun AppNavigation(skipSplash: Boolean = false, openPlayerSignal: Int = 0) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val isAuthorized by mainViewModel.isAuthorized.collectAsState()
    // Music is playing or a session was restored: open straight to the player UI, like Apple Music.
    val startDestination = remember {
        val hasSession = mainViewModel.currentTrack.value != null
        if (isAuthorized && (skipSplash || hasSession)) "main" else "splash"
    }

    androidx.compose.runtime.LaunchedEffect(isAuthorized) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (!isAuthorized && currentRoute != "splash" && currentRoute != "auth") {
            navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("splash") {
            com.korenskoy.yandexmusic.presentation.splash.SplashScreen(
                onSplashFinished = {
                    val destination = if (isAuthorized) "main" else "auth"
                    navController.navigate(destination) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                openPlayerSignal = openPlayerSignal,
                onNavigateToAuth = {
                    navController.navigate("auth")
                }
            )
        }
    }
}