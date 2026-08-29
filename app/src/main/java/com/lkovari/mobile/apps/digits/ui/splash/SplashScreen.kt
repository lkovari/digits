package com.lkovari.mobile.apps.digits.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lkovari.mobile.apps.digits.R
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlue
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBlueLight
import com.lkovari.mobile.apps.digits.ui.theme.NumbersBluePastel
import com.lkovari.mobile.apps.digits.ui.theme.NumbersInk
import com.lkovari.mobile.apps.digits.ui.theme.NumbersMagenta
import com.lkovari.mobile.apps.digits.ui.theme.NumbersSelected

private val SplashCardShape = RoundedCornerShape(32.dp)
private val MultiplyPastel = Color(0xFFF8BBD0)

@Composable
fun SplashScreen() {
    val pulse = rememberInfiniteTransition(label = "splashPulse")
    val nodeScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nodeScale"
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val compactSplash = maxHeight < 640.dp
        val titleSize = if (compactSplash) 32.sp else 40.sp
        val badgeSize = if (compactSplash) 13.sp else 15.sp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .shadow(
                    elevation = 10.dp,
                    shape = SplashCardShape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .clip(SplashCardShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE8F4FC),
                            Color(0xFFD6ECF8),
                            Color(0xFFC5E0F4)
                        )
                    ),
                    shape = SplashCardShape
                )
                .padding(
                    horizontal = if (compactSplash) 24.dp else 32.dp,
                    vertical = if (compactSplash) 28.dp else 36.dp
                )
        ) {
            Text(
                text = "+, −, ×, ÷",
                color = NumbersInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = badgeSize,
                modifier = Modifier
                    .background(Color(0x66FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(if (compactSplash) 16.dp else 28.dp))
            SplashOperators(scale = nodeScale, compact = compactSplash)
            Spacer(modifier = Modifier.height(if (compactSplash) 20.dp else 32.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = NumbersMagenta,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = titleSize,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = NumbersBlue,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun SplashOperators(scale: Float, compact: Boolean) {
    val node = if (compact) 56.dp else 70.dp
    val hGap = if (compact) 28.dp else 36.dp
    val vGap = if (compact) 18.dp else 24.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(vGap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
            SplashNode(
                label = "+",
                color = NumbersBlueLight,
                onColor = NumbersBlue,
                size = node,
                scale = scale
            )
            SplashNode(
                label = "−",
                color = NumbersBluePastel,
                onColor = NumbersBlue,
                size = node,
                scale = scale
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
            SplashNode(
                label = "×",
                color = MultiplyPastel,
                onColor = NumbersMagenta,
                size = node,
                scale = scale
            )
            SplashNode(
                label = "÷",
                color = NumbersSelected,
                onColor = NumbersBlue,
                size = node,
                scale = scale
            )
        }
    }
}

@Composable
private fun SplashNode(
    label: String,
    color: Color,
    onColor: Color,
    size: Dp,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .shadow(6.dp, CircleShape, ambientColor = color.copy(alpha = 0.35f), spotColor = color.copy(alpha = 0.28f))
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = onColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp
        )
    }
}
