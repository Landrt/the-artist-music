package com.theartist.music

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.theartist.music.platform.AndroidAudioPlayerController
import com.theartist.music.platform.AndroidLocalMusicScanner
import com.theartist.music.platform.AndroidPreferencesStorage
import com.theartist.music.state.MusicPlayerStateHolder

class MainActivity : ComponentActivity() {

    private lateinit var stateHolder: MusicPlayerStateHolder

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[audioPermission] == true ||
            ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED
        stateHolder.onPermissionResult(audioGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep status bar & navigation bar icons clearly visible (white icons on dark OLED bars)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        val scanner = AndroidLocalMusicScanner(applicationContext)
        stateHolder = MusicPlayerStateHolder(
            scanner = scanner,
            audioController = AndroidAudioPlayerController(applicationContext, scanner),
            storage = AndroidPreferencesStorage(applicationContext),
            initialPermissionGranted = hasAudioPermission
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = stateHolder.uiState.value
                when {
                    state.isQueueSheetOpen -> stateHolder.openQueueSheet(false)
                    state.isNowPlayingOpen -> stateHolder.openNowPlaying(false)
                    state.activeCollection != null -> stateHolder.openCollection(null)
                    state.isSearchActive -> stateHolder.toggleSearch(false)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        setContent {
            val uiState by stateHolder.uiState.collectAsState()

            LaunchedEffect(uiState.isDarkTheme, uiState.isNowPlayingOpen) {
                val useDarkBars = uiState.isDarkTheme || uiState.isNowPlayingOpen
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !useDarkBars
                controller.isAppearanceLightNavigationBars = !useDarkBars
            }

            TheArtistMusicApp(
                stateHolder = stateHolder,
                onRequestAudioPermission = { permissionsLauncher.launch(requiredPermissions) }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::stateHolder.isInitialized) {
            stateHolder.dispose()
        }
    }
}
