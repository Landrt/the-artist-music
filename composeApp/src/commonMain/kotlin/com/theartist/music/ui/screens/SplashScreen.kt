package com.theartist.music.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theartist.music.platform.brandDisplayFontFamily
import com.theartist.music.ui.components.OrbitLoadingIndicator
import com.theartist.music.ui.theme.ArtistCrimson
import com.theartist.music.ui.theme.OledBlack
import com.theartist.music.ui.theme.PrimaryTextDark
import com.theartist.music.ui.theme.SecondaryTextDark

@Composable
fun SplashScreen() {
    val displayFont = remember { brandDisplayFontFamily() }
    val pulseTransition = rememberInfiniteTransition(label = "brandPulse")
    val textScale by pulseTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1.1f))

            // Centerpiece brand title — no music logo, centered & bold
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(textScale)
            ) {
                Text(
                    text = "The @rtist😎💰 Music",
                    fontFamily = displayFont,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ArtistCrimson)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Votre moment, votre musique",
                    fontSize = 14.5.sp,
                    color = SecondaryTextDark,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1.1f))

            // Bottom loading ring
            OrbitLoadingIndicator(size = 44.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Chargement en cours",
                fontSize = 13.sp,
                color = PrimaryTextDark.copy(alpha = 0.80f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Lecteur audio 100% local",
                fontSize = 12.sp,
                color = Color(0xFF5E5E5E),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
