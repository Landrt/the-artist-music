package com.theartist.music

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.theartist.music.platform.DesktopAudioPlayerController
import com.theartist.music.platform.DesktopLocalMusicScanner
import com.theartist.music.platform.DesktopPreferencesStorage
import com.theartist.music.state.MusicPlayerStateHolder

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(412.dp, 860.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "The @rtist😎💰 Music — Preview",
        state = windowState
    ) {
        val stateHolder = remember {
            MusicPlayerStateHolder(
                scanner = DesktopLocalMusicScanner(),
                audioController = DesktopAudioPlayerController(),
                storage = DesktopPreferencesStorage(),
                initialPermissionGranted = true
            )
        }

        DisposableEffect(Unit) {
            onDispose { stateHolder.dispose() }
        }

        TheArtistMusicApp(stateHolder = stateHolder)
    }
}
