package com.theartist.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.theartist.music.platform.AndroidAudioPlayerController
import com.theartist.music.platform.AndroidLocalMusicScanner
import com.theartist.music.state.MusicPlayerStateHolder

class MainActivity : ComponentActivity() {

    private lateinit var stateHolder: MusicPlayerStateHolder

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        stateHolder.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            audioPermission
        ) == PackageManager.PERMISSION_GRANTED

        stateHolder = MusicPlayerStateHolder(
            scanner = AndroidLocalMusicScanner(this),
            audioController = AndroidAudioPlayerController(this),
            initialPermissionGranted = hasPermission
        )

        if (!hasPermission) {
            permissionLauncher.launch(audioPermission)
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
            TheArtistMusicApp(
                stateHolder = stateHolder,
                onRequestAudioPermission = { permissionLauncher.launch(audioPermission) }
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
