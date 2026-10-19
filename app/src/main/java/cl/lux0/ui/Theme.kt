package cl.lux0.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFFF5A1F)

data class Palette(
    val name: String,
    val body: Color,
    val key: Color,
    val keyText: Color,
    val keyOn: Color,
    val keyOnText: Color,
    val text: Color,
    val screen: Color,
    val screenText: Color,
    val screenDim: Color,
    val accent: Color,
    val knobs: List<Color>,
    val knobMark: Color,
    val magenta: Color,
    val green: Color,
    val red: Boolean,
)

val Claro = Palette(
    "claro", Color(0xFFE3E3DF), Color(0xFFF6F6F2), Color(0xFF1A1A1A), Color(0xFF1A1A1A), Color.White,
    Color(0xFF1A1A1A), Color(0xFF050505), Color(0xFFF0F0F0), Color(0xFF8B8B8B), Accent,
    listOf(Color(0xFF3A78D6), Color(0xFF35B36A), Color(0xFFF4F4F0), Accent), Color(0x8C000000),
    Color(0xFFE9A6F5), Color(0xFF8FDB8F), false,
)

val Oscuro = Claro.copy(
    name = "oscuro", body = Color(0xFF232325), key = Color(0xFF3A3A3D), keyText = Color(0xFFE8E8E4),
    keyOn = Color(0xFFF4F4F0), keyOnText = Color(0xFF111111), text = Color(0xFFE8E8E4),
)

private val Rojo1 = Color(0xFFB3261E)
val NocheRojo = Palette(
    "rojo", Color(0xFF0B0000), Color(0xFF1A0303), Rojo1, Rojo1, Color(0xFF0B0000),
    Rojo1, Color.Black, Rojo1, Color(0xFF6A1712), Rojo1,
    List(4) { Color(0xFF3A0905) }, Rojo1, Rojo1, Rojo1, true,
)

val LocalPalette = staticCompositionLocalOf { Claro }

val Mono = FontFamily.Monospace

fun mono(size: Int, color: Color, bold: Boolean = false) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Selector segmentado estilo aparato. */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = mono(12, if (on) p.keyOnText else p.keyText, on),
                modifier = Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (on) p.keyOn else p.key)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}
