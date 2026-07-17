package com.example.ui.screens.player

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Primary
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

enum class VlcAspectRatio(val displayName: String) {
    BEST_FIT("Вписать"),
    FILL("Заполнить"),
    STRETCH("Растянуть");

    fun next(): VlcAspectRatio {
        val values = entries.toTypedArray()
        val nextOrdinal = (this.ordinal + 1) % values.size
        return values[nextOrdinal]
    }
}

fun formatMillis(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun forceResizeViewAndDescendants(v: View) {
    v.forceLayout()
    v.requestLayout()
    v.invalidate()
    if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
            forceResizeViewAndDescendants(v.getChildAt(i))
        }
    }
}

fun forceFullResize(view: View) {
    view.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    forceResizeViewAndDescendants(view)
    var parent = view.parent as? ViewGroup
    while (parent != null) {
        parent.forceLayout()
        parent.requestLayout()
        parent.invalidate()
        parent = parent.parent as? ViewGroup
    }
}

@Composable
fun SimulatedPlaybackBars(modifier: Modifier = Modifier) {
    val bar1 = remember { Animatable(0.2f) }
    val bar2 = remember { Animatable(0.7f) }
    val bar3 = remember { Animatable(0.4f) }
    
    LaunchedEffect(Unit) {
        val duration = 400
        while (true) {
            bar1.animateTo(0.8f, tween(duration, easing = LinearEasing))
            bar1.animateTo(0.2f, tween(duration, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        val duration = 500
        delay(100)
        while (true) {
            bar2.animateTo(1.0f, tween(duration, easing = LinearEasing))
            bar2.animateTo(0.3f, tween(duration, easing = LinearEasing))
        }
    }
    LaunchedEffect(Unit) {
        val duration = 300
        delay(200)
        while (true) {
            bar3.animateTo(0.9f, tween(duration, easing = LinearEasing))
            bar3.animateTo(0.4f, tween(duration, easing = LinearEasing))
        }
    }

    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { anim ->
            val scale = anim.value
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .fillMaxHeight(scale * 0.4f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Primary.copy(alpha = 0.35f))
            )
        }
    }
}
