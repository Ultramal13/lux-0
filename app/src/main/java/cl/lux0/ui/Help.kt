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
private val HELP = listOf(
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
    "captura (iso, fps, obturación, f/, nd)" to
        "cómo está configurada tu cámara al medir. se guarda con cada medición y sale en el csv para el colorista. " +
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

@Composable
fun HelpPanel(modifier: Modifier) {
    val p = LocalPalette.current
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HELP) { (title, body) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.key).padding(12.dp)) {
                Text(title, style = mono(13, p.keyText, true))
                Text(body, style = mono(11, p.keyText), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
