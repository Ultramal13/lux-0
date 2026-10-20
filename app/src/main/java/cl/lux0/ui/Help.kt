package cl.lux0.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Ayuda: qué significa cada parámetro y en qué influye. */
private val HELP_ES = listOf(
    "kelvin (k)" to
        "temperatura de color de la luz. número bajo = luz cálida/naranja (vela ~1900 k, ampolleta ~2700–3200 k). " +
        "número alto = luz fría/azul (sol ~5600 k, nublado ~6500 k, sombra 7500 k o más). " +
        "es el valor que pones en el balance de blancos manual de tu cámara.",
    "tinte (duv)" to
        "cuánto se aleja la luz del blanco 'natural' hacia verde (+) o magenta (−). " +
        "fluorescentes y led baratos suelen tener tinte. entre −0.003 y +0.003 casi no se nota. " +
        "sobre 0.02 la app avisa \"luz muy teñida\".",
    "cómo leer la recomendación" to
        "ejemplo: \"4400–5000 · 4700 · ≈g2\". 4400–5000 = rango razonable según la precisión de la medición. " +
        "4700 = valor sugerido, ya redondeado a los pasos de esa cámara. " +
        "≈g2 = en canon, wb shift 2 pasos hacia verde para compensar una luz magenta (≈ porque la equivalencia es aproximada). " +
        "⚠ = la luz tiene tinte y esa cámara no lo puede corregir.",
    "visor" to
        "es solo para apuntar: la medición usa su propia imagen y promedia lo que está dentro del círculo. " +
        "toca el visor para ampliarlo a media pantalla y otra vez para achicarlo. está en gris a propósito: " +
        "la imagen va sin corrección de color para medir bien y en color se vería verdosa.",
    "fuente de medición" to
        "blanco/gris: apunta el círculo a una hoja blanca o tarjeta gris bajo la luz (lo más preciso). " +
        "difusor→luz: pon un papel o difusor blanco sobre el lente y apunta hacia la luz desde donde está el sujeto. " +
        "escena ≈: sin referencia; solo estimado (±500 k o más) porque depende del color de lo que mires.",
    "avisos" to
        "zona quemada: el círculo está demasiado brillante, aléjate o cambia el ángulo. " +
        "muy oscuro: falta luz en el círculo. no parece neutro: lo que apuntas tiene color propio o la luz está muy teñida.",
    "reflectancia (%)" to
        "cuánta luz devuelve la superficie que mides: tarjeta gris 18 %, papel blanco ~90 %. " +
        "no cambia el kelvin; se usará en exposición (etapa 2).",
    "intervalo" to
        "cada cuánto se calcula una lectura (0,5 / 1 / 2 s). más largo = menos trabajo y menos calor. " +
        "si el teléfono se entibia, la app sube sola a 3 s.",
    "cámara 1 y cámara 2" to
        "las dos cámaras para las que quieres la recomendación. se eligen en el menú ▾ de cada tarjeta. " +
        "* = datos de esa cámara sin verificar con el fabricante (revisa en su menú).",
    "datos de una cámara" to
        "k mínimo / k máximo: rango de temperatura que acepta. paso: de cuánto en cuánto cambia (canon r5: 100 k); " +
        "la recomendación se redondea a ese paso. tinte: si tiene corrección verde/magenta. " +
        "iso máximo: limita la lista de iso de esa cámara.",
    "captura (iso, fps, obturación, f/, nd, ev)" to
        "cómo está configurada tu cámara al medir. se guarda con cada medición y sale en el csv para el colorista. " +
        "comp. ev = compensación de exposición: no cambia el kelvin, pero en cámaras de diafragma fijo con exposición " +
        "automática (como la luna ultra) es el ajuste que controlas para aclarar u oscurecer. " +
        "en la etapa 2 se usará para exposición (qué diafragma o nd usar) y para avisar parpadeo según fps y obturación.",
    "calibración" to
        "corrige la diferencia propia del sensor del celular. foto raw de la hoja blanca con la r5, lees los k con el cuentagotas " +
        "de balance en dpp o lightroom, mides la misma hoja aquí e ingresas ese valor. sin calibrar el margen es mayor (±300 k).",
    "hold y guardar" to
        "hold congela el número (y apaga la cámara) para copiarlo con calma. guardar lo agrega a la sesión activa con " +
        "hora, miniatura, recomendación y ajustes de captura; tócalo en sesiones para escribir un comentario.",
    "sesiones" to
        "agrupa mediciones por rodaje o locación (+ nueva). exportar csv envía la planilla de la sesión. " +
        "mantén presionada una medición para borrarla.",
    "pausa y calor" to
        "tras 90 s sin tocar la pantalla la cámara se apaga para ahorrar batería; toca para seguir. " +
        "si el teléfono se calienta, la app mide más lento o se detiene hasta que se enfríe.",
    "temas" to
        "◐ cambia entre auto, claro, oscuro y noche rojo. noche rojo sirve en sets a oscuras: no encandila ni ensucia la escena.",
    "modo demo" to
        "solo en el emulador del computador: lecturas simuladas para revisar la interfaz. en el teléfono nunca se activa.",
)

private val HELP_EN = listOf(
    "kelvin (k)" to
        "the color temperature of the light. low number = warm/orange light (candle ~1900 k, bulb ~2700–3200 k). " +
        "high number = cool/blue light (sun ~5600 k, overcast ~6500 k, shade 7500 k or more). " +
        "it is the value you set in your camera's manual white balance.",
    "tint (duv)" to
        "how far the light drifts from 'natural' white toward green (+) or magenta (−). " +
        "fluorescent tubes and cheap leds often have tint. between −0.003 and +0.003 it is barely visible. " +
        "above 0.02 the app warns \"strongly tinted light\".",
    "reading the recommendation" to
        "example: \"4400–5000 · 4700 · ≈g2\". 4400–5000 = reasonable range given the measurement accuracy. " +
        "4700 = suggested value, already rounded to that camera's steps. " +
        "≈g2 = on canon, wb shift 2 steps toward green to compensate magenta light (≈ because the equivalence is approximate). " +
        "⚠ = the light has a tint this camera cannot correct.",
    "viewfinder" to
        "it is only for aiming: the measurement uses its own image and averages what is inside the circle. " +
        "tap the viewfinder to enlarge it to half the screen, and again to shrink it. it is gray on purpose: " +
        "the image has no color correction so it can measure correctly, and in color it would look greenish.",
    "measuring source" to
        "white/gray: aim the circle at a white sheet or gray card under the light (most accurate). " +
        "diffuser→light: put white paper or a diffuser over the lens and point it at the light from the subject's position. " +
        "scene ≈: no reference; only an estimate (±500 k or more) because it depends on the color of what you aim at.",
    "warnings" to
        "clipped area: the circle is too bright, step back or change the angle. too dark: not enough light in the circle. " +
        "not neutral: what you aim at has its own color, or the light is strongly tinted.",
    "reflectance (%)" to
        "how much light the measured surface returns: gray card 18 %, white paper ~90 %. " +
        "it does not change kelvin; it will be used for exposure (stage 2).",
    "interval" to
        "how often a reading is computed (0.5 / 1 / 2 s). longer = less work and less heat. " +
        "if the phone warms up, the app switches to 3 s by itself.",
    "camera 1 and camera 2" to
        "the two cameras you want recommendations for. pick them in each card's ▾ menu. " +
        "* = that camera's data is not verified with the manufacturer (check its menu).",
    "camera data" to
        "min k / max k: the temperature range it accepts. step: how much it changes per click (canon r5: 100 k); " +
        "the recommendation is rounded to that step. tint: whether it has green/magenta correction. " +
        "max iso: limits that camera's iso list.",
    "capture (iso, fps, shutter, f/, nd, ev)" to
        "how your camera is set while measuring. it is saved with each reading and goes into the csv for the colorist. " +
        "ev comp. = exposure compensation: it does not change kelvin, but on fixed-aperture cameras with auto exposure " +
        "(like the luna ultra) it is the setting you control to brighten or darken. " +
        "in stage 2 it will be used for exposure (which aperture or nd to use) and for flicker warnings based on fps and shutter.",
    "calibration" to
        "corrects the phone sensor's own bias. shoot a white sheet in raw with your camera, read the k with the white balance " +
        "eyedropper in dpp or lightroom, measure the same sheet here and enter that value. uncalibrated, the margin is wider (±300 k).",
    "hold and save" to
        "hold freezes the number (and turns the camera off) so you can copy it calmly. save adds it to the active session with " +
        "time, thumbnail, recommendation and capture settings; tap it in sessions to write a comment.",
    "sessions" to
        "group readings by shoot or location (+ new). export csv sends the session spreadsheet. " +
        "long-press a reading to delete it.",
    "pause and heat" to
        "after 90 s without touching the screen the camera turns off to save battery; tap to continue. " +
        "if the phone gets warm, the app measures more slowly or stops until it cools down.",
    "themes" to
        "◐ switches between auto, light, dark and red night. red night is for dark sets: it does not dazzle or spill light on the scene.",
    "demo mode" to
        "only on the computer's emulator: simulated readings to review the interface. it never turns on on a phone.",
)

@Composable
fun HelpPanel(modifier: Modifier) {
    val p = LocalPalette.current
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(if (cl.lux0.I18n.en) HELP_EN else HELP_ES) { (title, body) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.key).padding(12.dp)) {
                Text(title, style = mono(13, p.keyText, true))
                Text(body, style = mono(11, p.keyText), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
