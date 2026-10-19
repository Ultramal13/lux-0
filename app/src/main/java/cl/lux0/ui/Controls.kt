package cl.lux0.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Perilla que se gira arrastrando el dedo; cada 24 px es un "clic". */
@Composable
fun Knob(index: Int, label: String, enabled: Boolean, onStep: (Int) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    var angle by remember { mutableFloatStateOf(0f) }
    var acc by remember { mutableFloatStateOf(0f) }
    fun step(d: Int) {
        angle += d * 24f
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onStep(d)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.3f)) {
        Box(
            Modifier.size(52.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(p.knobs[index])
                .then(if (!enabled) Modifier else Modifier
                    .clickable { step(1) }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { acc = 0f }) { _, d ->
                            acc += d.x - d.y
                            while (acc > 24) { acc -= 24; step(1) }
                            while (acc < -24) { acc += 24; step(-1) }
                        }
                    }),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(52.dp).rotate(angle)) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).width(3.dp).height(14.dp)
                    .clip(RoundedCornerShape(2.dp)).background(p.knobMark))
            }
            if (p.red) Text("${index + 1}", style = mono(14, p.screenText, true))
        }
        Text(label, style = mono(10, p.text), modifier = Modifier.padding(top = 5.dp))
    }
}

data class KeyDef(val n: Int, val glyph: String, val label: String, val enabled: Boolean)

val KEYS = listOf(
    KeyDef(1, "K", "color", true), KeyDef(2, "EV", "expo", false),
    KeyDef(3, "lx", "lux", false), KeyDef(4, "Hz", "parpadeo", false),
    KeyDef(5, "A|B", "comparar", false), KeyDef(6, "▦", "mapa", false),
    KeyDef(7, "≡", "sesiones", true), KeyDef(8, "⚙", "ajustes", true),
)

@Composable
fun KeyGrid(mode: Int, onKey: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        KEYS.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { k ->
                    val on = k.n == mode || (k.n == 8 && mode == 9)
                    Column(
                        Modifier.weight(1f).aspectRatio(1.25f).shadow(2.dp, RoundedCornerShape(9.dp))
                            .clip(RoundedCornerShape(9.dp)).background(if (on) p.keyOn else p.key)
                            .alpha(if (k.enabled) 1f else 0.35f)
                            .clickable(enabled = k.enabled) { onKey(k.n) }
                            .padding(6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val c = if (on) p.keyOnText else p.keyText
                        Text("${k.n}", style = mono(9, c))
                        Text(k.glyph, style = mono(15, c, true))
                        Text(k.label, style = mono(9, c))
                    }
                }
            }
        }
    }
}

@Composable
fun WideKey(text: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.height(44.dp).shadow(2.dp, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp))
            .background(if (accent) p.accent else p.key).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = mono(13, if (accent) (if (p.red) p.body else androidx.compose.ui.graphics.Color.White) else p.keyText, true))
    }
}

@Composable
fun Spacer4() = Box(Modifier.fillMaxWidth().height(4.dp))
