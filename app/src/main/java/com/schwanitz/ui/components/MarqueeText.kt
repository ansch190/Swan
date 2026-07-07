package com.schwanitz.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color.Unspecified,
) {
    var textLineWidth by remember { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableIntStateOf(0) }

    val didOverflow = textLineWidth > containerWidth && containerWidth > 0
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(didOverflow, textLineWidth, containerWidth, text) {
        if (didOverflow) {
            val target = -(textLineWidth - containerWidth).toFloat()
            animatable.snapTo(0f)
            if (target < 0f) {
                while (isActive) {
                    kotlinx.coroutines.delay(2000)
                    animatable.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = 5000,
                            easing = LinearEasing
                        )
                    )
                    kotlinx.coroutines.delay(2000)
                    animatable.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 5000,
                            easing = LinearEasing
                        )
                    )
                }
            }
        } else {
            animatable.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width }
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = if (didOverflow) TextAlign.Start else textAlign,
            onTextLayout = { result ->
                if (result.lineCount == 1) {
                    textLineWidth = result.getLineRight(0) - result.getLineLeft(0)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (didOverflow) Modifier.offset { IntOffset(animatable.value.roundToInt(), 0) }
                    else Modifier
                )
        )
    }
}
