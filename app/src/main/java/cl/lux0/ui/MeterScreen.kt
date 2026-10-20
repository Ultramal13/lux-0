package cl.lux0.ui

import cl.lux0.tr
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cl.lux0.camera.ROI_FRACTION
import cl.lux0.color.ColorMath
import cl.lux0.color.Reading
import java.util.Locale
import kotlin.math.roundToInt

val MODE_NAMES: Map<Int, String> get() = mapOf(1 to "color", 2 to tr("exposición", "exposure"), 3 to "lux", 4 to tr("parpadeo", "flicker"), 5 to tr("comparar", "compare"), 6 to tr("mapa", "map"), 7 to tr("sesiones", "sessions"), 8 to tr("ajustes", "settings"), 9 to tr("calibrar", "calibrate"), 10 to tr("ayuda", "help"))

@Composable
fun Header(mode: Int, theme: String, onTheme: () -> Unit, onHelp: () -> Unit = {}) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("lux-0", style = mono(16, p.text, true))
        Text("?", style = mono(13, p.keyText, true),
            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(7.dp)).background(p.key)
                .clickable(onClick = onHelp).padding(horizontal = 9.dp, vertical = 4.dp))
        Spacer(Modifier.weight(1f))
        Text(String.format(Locale.US, "%02d %s", mode, MODE_NAMES[mode] ?: ""), style = mono(13, p.text))
        Text(
            "◐ ${themeLabel(theme)}", style = mono(12, p.keyText),
            modifier = Modifier.padding(start = 10.dp).clip(RoundedCornerShape(7.dp)).background(p.key)
                .clickable(onClick = onTheme).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * Visor en vivo: TextureView recortado a cuadrado, en gris (la imagen va sin corrección de color),
 * con mira circular. La cámara corre solo si la app está visible, la superficie existe y no hay
 * pausa por inactividad ni por calor.
 */
@Composable
fun Viewfinder(
    vm: MeterViewModel,
    modifier: Modifier,
    active: Boolean,
    retry: Int,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    onView: (TextureView?) -> Unit,
) {
    val p = LocalPalette.current
    val owner = LocalLifecycleOwner.current
    var texture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { resumed = true; vm.touch() }
            // Se apaga aquí mismo, sin esperar una recomposición.
            if (e == Lifecycle.Event.ON_PAUSE) { resumed = false; vm.stopCamera() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.stopCamera(); onView(null) }
    }
    val run = active && resumed && texture != null
    LaunchedEffect(run, texture, retry) {
        if (run) vm.startCamera(texture!!) else vm.stopCamera()
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(p.screenDim)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    val aspect = vm.previewAspect()
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            setTransform(Matrix().apply { setScale(1f, w * aspect / h, w / 2f, h / 2f) })
                            texture = st
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            setTransform(Matrix().apply { setScale(1f, w * aspect / h, w / 2f, h / 2f) })
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            texture = null; vm.stopCamera(); return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    onView(this)
                }
            },
            update = { tv -> tv.setRenderEffect(viewfinderEffect(p.red)) },
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            // Mismo círculo que mide la cámara: fracción del lado corto del cuadro (= ancho del visor).
            val r = this.size.width * ROI_FRACTION
            val line = if (p.red) p.screenText else androidx.compose.ui.graphics.Color.White
            drawCircle(line, r, c, style = Stroke(1.5.dp.toPx()))
            drawLine(line, Offset(c.x, c.y - r - 10), Offset(c.x, c.y + r + 10), 1.dp.toPx())
            drawLine(line, Offset(c.x - r - 10, c.y), Offset(c.x + r + 10, c.y), 1.dp.toPx())
        }
        overlay()
        if (!active) Box(Modifier.fillMaxSize().background(p.screen.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Text("■", style = mono(22, p.screenText))
        }
    }
}

/**
 * Solo para mirar: la imagen de medición es lineal y con −1 EV, así que se ve muy oscura.
 * El visor la aclara (×2 y curva gamma 2.2) y la pasa a gris (o a rojo en noche roja).
 */
private const val VIEWFINDER_AGSL = """
    uniform shader content;
    uniform float red;
    half4 main(float2 p) {
        half4 c = content.eval(p);
        float l = dot(float3(c.rgb), float3(0.299, 0.587, 0.114));
        l = pow(clamp(l * 2.0, 0.0, 1.0), 0.4545);
        return red > 0.5 ? half4(half(l * 0.85), 0.0, 0.0, 1.0) : half4(half(l), half(l), half(l), 1.0);
    }
"""

private fun viewfinderEffect(red: Boolean): RenderEffect =
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val shader = android.graphics.RuntimeShader(VIEWFINDER_AGSL)
        shader.setFloatUniform("red", if (red) 1f else 0f)
        RenderEffect.createRuntimeShaderEffect(shader, "content")
    } else {
        // Android 12: sin sombreadores; ganancia fija como aproximación.
        val g = 2.5f
        val m = if (red) floatArrayOf(0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        else floatArrayOf(0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f, 0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f, 0.3f * g, 0.59f * g, 0.11f * g, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix(m)))
    }

/** Nombre visible de un tema (la clave interna sigue en español). */
fun themeLabel(key: String): String = when (key) {
    "claro" -> tr("claro", "light"); "oscuro" -> tr("oscuro", "dark"); "rojo" -> tr("rojo", "red")
    else -> "auto"
}

private fun kFraction(k: Double): Float =
    ((ColorMath.mired(2000.0) - ColorMath.mired(k)) / (ColorMath.mired(2000.0) - ColorMath.mired(10000.0)))
        .toFloat().coerceIn(0f, 1f)

@Composable
fun DotScale(k: Double?) {
    val p = LocalPalette.current
    val dot = if (p.red) p.screenDim else androidx.compose.ui.graphics.Color(0xFFFFB46B)
    Column {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            var x = 4f
            while (x < size.width) {
                drawCircle(dot, 1.6.dp.toPx(), Offset(x, size.height / 2)); x += 8.dp.toPx()
            }
            k?.let {
                val px = kFraction(it) * size.width
                drawRoundRect(p.screenText, Offset(px - 1.5.dp.toPx(), 0f),
                    androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                    androidx.compose.ui.geometry.CornerRadius(2f))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
            listOf(2000.0, 3200.0, 5600.0, 10000.0).forEach { t ->
                val f = kFraction(t)
                val shift = if (f > 0.9f) (-30).dp else if (f < 0.05f) 0.dp else (-12).dp
                Text("${t.roundToInt()}", style = mono(9, p.screenDim), modifier = Modifier.offset(x = maxWidth * f + shift))
            }
        }
    }
}

private fun tintText(duv: Double): String {
    val s = String.format(Locale.US, "%.3f", kotlin.math.abs(duv))
    return when {
        duv < -0.0005 -> "−$s magenta"
        duv > 0.0005 -> "+$s " + tr("verde", "green")
        else -> "0.000 " + tr("neutro", "neutral")
    }
}

@Composable
fun BlackScreen(vm: MeterViewModel, st: UiState, modifier: Modifier = Modifier, fill: Boolean = false, onView: (TextureView?) -> Unit) {
    val p = LocalPalette.current
    val r: Reading? = st.reading
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.screen).padding(12.dp)) {
        Row {
            Text(
                when (st.mode) {
                    2 -> tr("exposición reflejada · ", "reflected exposure · ") + "${vm.prefs.reflectance}%"
                    4 -> tr("parpadeo · exposición 1/2000", "flicker · exposure 1/2000")
                    else -> "${SOURCES[st.source]} · ${vm.prefs.reflectance}%"
                },
                style = mono(10, p.screenDim),
            )
            Spacer(Modifier.weight(1f))
            val status = when {
                st.demo -> "● demo"
                st.hot -> tr("■ caliente", "■ hot")
                st.idle -> tr("■ pausa", "■ paused")
                st.error != null -> tr("● sin cámara", "● no camera")
                st.hold -> "■ hold"
                else -> tr("● en vivo", "● live")
            }
            Text(status, style = mono(10, p.accent))
        }
        Spacer(Modifier.height(8.dp))
        // En la pantalla de color el visor es fijo: todo el ancho y el alto libre, con el número encima.
        // En calibración queda chico (132 dp) con el número al lado, para dejar espacio al panel.
        val reading: @Composable (Int) -> Unit = { size ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r?.let { "${it.cct.roundToInt()}" } ?: "----", style = mono(size, p.screenText, true))
                    if (fill) Text(" k", style = mono(16, p.screenDim))
                }
                if (!fill) Text("k" + if (st.source == 2) tr("  ≈ estimado", "  ≈ estimated") else "", style = mono(13, p.screenDim))
                if (!fill) Spacer(Modifier.height(4.dp))
                Text(r?.let { tintText(it.duv) } ?: "", style = mono(12, if (r != null && r.duv > 0) p.green else p.magenta))
                Text(
                    (if (st.calOffset == 0.0) tr("sin calibrar", "uncalibrated") else tr("calibrado", "calibrated")) +
                        if (fill && st.source == 2) tr(" · ≈ estimado", " · ≈ estimated") else "",
                    style = mono(9, p.screenDim),
                )
            }
        }
        val expo: @Composable () -> Unit = {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(st.ev100?.let { String.format(Locale.US, "%.1f", vm.sceneEv(it)) } ?: "--", style = mono(36, p.screenText, true))
                    Text(" ev", style = mono(16, p.screenDim))
                }
                Text(tr("escena a iso 100 · ≈ reflejada", "scene at iso 100 · ≈ reflected"), style = mono(9, p.screenDim))
            }
        }
        val flick: @Composable () -> Unit = {
            Column {
                val f = st.flicker
                Text(
                    when {
                        !st.flickerChecked -> tr("midiendo…", "measuring…")
                        f == null -> tr("sin parpadeo", "no flicker")
                        else -> "${f.hz.roundToInt()} hz"
                    },
                    style = mono(if (f != null) 36 else 22, p.screenText, true),
                )
                Text(
                    if (f != null) tr("parpadeo ", "flicker ") + "${(f.depth * 100).roundToInt()} % · " + tr("≈ red ", "≈ mains ") + "${(f.hz / 2).roundToInt()} hz"
                    else tr("profundidad < 3 %", "depth < 3 %"),
                    style = mono(9, p.screenDim),
                )
            }
        }
        val active = !st.idle && !st.hot && !st.hold
        if (fill) {
            Viewfinder(vm, Modifier.fillMaxWidth().weight(1f), active, st.retry, overlay = {
                if (st.mode == 6) MapOverlay(st.map, vm.mapReference(), vm.previewAspect())
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(p.screen.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp),
                ) { when (st.mode) { 2 -> expo(); 4 -> flick(); else -> reading(36) } }
            }, onView = onView)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Viewfinder(vm, Modifier.size(132.dp), active, st.retry, onView = onView)
                Spacer(Modifier.width(12.dp)); reading(44)
            }
        }
        val alert = when {
            st.demo -> tr("modo demo · lecturas simuladas (emulador)", "demo mode · simulated readings (emulator)")
            st.hot -> tr("teléfono caliente: medición en pausa hasta que se enfríe", "phone is hot: measuring paused until it cools down")
            st.idle -> tr("en pausa para ahorrar batería · toca la pantalla", "paused to save battery · tap the screen")
            else -> st.error ?: st.warning
        }
        val hint = when (st.mode) {
            2 -> tr("apunta el círculo al sujeto o a una tarjeta gris", "aim the circle at the subject or a gray card")
            4 -> tr("apunta a una zona iluminada por la luz que quieres revisar", "aim at an area lit by the light you want to check")
            5 -> tr("mide la luz a, luego la b, sobre la misma hoja", "measure light a, then b, on the same sheet")
            6 -> tr("naranja = más cálida · azul = más fría · ≈ estimado", "orange = warmer · blue = cooler · ≈ estimated")
            else -> SOURCE_HINTS[st.source]
        }
        Text(alert ?: hint, style = mono(11, if (alert != null) p.accent else p.screenDim),
            modifier = Modifier.padding(top = 8.dp))
        if (st.mode != 2 && st.mode != 4) DotScale(r?.cct)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(p.screenDim))
        listOf(1, 2).forEach { slot ->
            val cam = vm.cam(slot)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(p.knobs[slot - 1]),
                    contentAlignment = Alignment.Center) {
                    Text("$slot", style = mono(10, if (p.red) p.screenText else androidx.compose.ui.graphics.Color.Black, true))
                }
                Text(" " + cam.label + if (!cam.verified) " *" else "", style = mono(12, p.screenText))
                Spacer(Modifier.weight(1f))
                Text(vm.slotText(slot, r).ifEmpty { "—" }, style = mono(12, p.screenText, true))
            }
        }
        if (st.mode == 1) vm.targetText(r)?.let {
            Text(it, style = mono(12, p.accent, true), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Fuente de medición: tres botones directos. */
@Composable
fun SourceRow(st: UiState, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Text(tr("fuente de medición", "measuring source"), style = mono(11, p.text))
        Seg(SOURCES, st.source, onSelect, Modifier.padding(top = 5.dp))
    }
}

@Composable
fun CalibrationPanel(vm: MeterViewModel, st: UiState) {
    val p = LocalPalette.current
    var ref by remember { mutableStateOf("") }
    var done by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp, horizontal = 4.dp)) {
        Text(tr("calibración de color", "color calibration"), style = mono(14, p.text, true))
        Text(
            tr(
                "1. pon una hoja blanca bajo la luz y fotografíala en raw con la r5.\n" +
                    "2. en dpp, lightroom o camera raw usa el cuentagotas de balance sobre la hoja y anota los k.\n" +
                    "3. mide la misma hoja aquí (fuente blanco/gris) e ingresa ese valor.\n" +
                    "ojo: papel muy brillante (blanqueadores ópticos) puede leer más frío con luz de día.",
                "1. put a white sheet under the light and shoot it in raw with your camera.\n" +
                    "2. in dpp, lightroom or camera raw use the white balance eyedropper on the sheet and note the k.\n" +
                    "3. measure the same sheet here (white/gray source) and enter that value.\n" +
                    "note: very bright paper (optical brighteners) may read cooler in daylight.",
            ),
            style = mono(11, p.text), modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("k referencia ", "reference k "), style = mono(12, p.text))
            BasicTextField(
                ref, { ref = it.filter(Char::isDigit).take(5) },
                textStyle = mono(16, p.keyText, true), cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp).clip(RoundedCornerShape(7.dp)).background(p.key).padding(8.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideKey(tr("guardar", "save"), true, Modifier.weight(1f)) {
                val k = ref.toDoubleOrNull()
                done = when {
                    k == null || k < 1500 || k > 15000 -> tr("ingresa un valor entre 1500 y 15000", "enter a value between 1500 and 15000")
                    st.reading == null -> tr("aún no hay lectura", "no reading yet")
                    vm.calibrate(k) -> tr("calibrado a ", "calibrated to ") + "${k.roundToInt()} k"
                    else -> tr("no se pudo calibrar", "could not calibrate")
                }
            }
            WideKey(tr("reiniciar", "reset"), false, Modifier.weight(1f)) { vm.resetCalibration(); done = tr("calibración borrada", "calibration cleared") }
        }
        done?.let { Text(it, style = mono(12, p.accent), modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** Selector rápido de lo que se apunta en exposición (cambia la corrección por reflectancia). */
@Composable
fun ReflectanceRow(vm: MeterViewModel, version: Int) {
    val p = LocalPalette.current
    val opts = listOf(18, 90)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Text(tr("qué apuntas", "what you aim at"), style = mono(11, p.text))
        Seg(listOf(tr("gris 18 %", "gray 18 %"), tr("blanco 90 %", "white 90 %")), opts.indexOf(vm.prefs.reflectance),
            { vm.prefs.reflectance = opts[it]; vm.bump() }, Modifier.padding(top = 5.dp))
    }
}

/** Modo lux: sensor de luz frontal; la cámara queda apagada. */
@Composable
fun LuxPanel(vm: MeterViewModel, st: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.startLux()
            if (e == Lifecycle.Event.ON_PAUSE) vm.stopLux()
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.startLux()
        onDispose { owner.lifecycle.removeObserver(obs); vm.stopLux() }
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.screen).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row {
            Text(tr("sensor de luz frontal", "front light sensor"), style = mono(10, p.screenDim))
            Spacer(Modifier.weight(1f))
            Text(if (st.demo) "● demo" else if (st.hold) "■ hold" else tr("● en vivo", "● live"), style = mono(10, p.accent))
        }
        Spacer(Modifier.weight(1f))
        if (!st.luxSensor) {
            Text(tr("este teléfono no tiene sensor de luz", "this phone has no light sensor"), style = mono(14, p.accent))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(st.lux?.let { "${it.roundToInt()}" } ?: "----", style = mono(64, p.screenText, true))
                Text(" lx", style = mono(20, p.screenDim))
            }
            Text(st.lux?.let { String.format(Locale.US, "%.1f fc", it / 10.764) } ?: "", style = mono(16, p.screenDim))
        }
        Spacer(Modifier.weight(1f))
        Text(
            tr("apunta la pantalla del teléfono hacia la luz (el sensor está al frente) · ≈ aproximado",
                "point the phone's screen at the light (the sensor is on the front) · ≈ approximate"),
            style = mono(11, if (st.demo) p.accent else p.screenDim),
        )
        if (vm.prefs.luxFactor != 1.0) Text(tr("factor de calibración ", "calibration factor ") + String.format(Locale.US, "×%.1f", vm.prefs.luxFactor),
            style = mono(9, p.screenDim))
    }
}

/** Comparar dos luces: congela A y B y sugiere el gel para igualarlas. */
@Composable
fun ComparePanel(vm: MeterViewModel, st: UiState) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("a" to st.compareA, "b" to st.compareB).forEach { (name, rd) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.key).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(tr("luz ", "light ") + name, style = mono(13, p.keyText, true), modifier = Modifier.width(70.dp))
                Text(rd?.let { "${it.cct.roundToInt()} k  " + String.format(Locale.US, "%+.3f", it.duv) } ?: "—",
                    style = mono(13, p.keyText), modifier = Modifier.weight(1f))
                Seg(listOf(tr("medir ", "measure ") + name), -1, { if (name == "a") vm.measureA() else vm.measureB() })
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.screen).padding(10.dp)) {
            vm.compareText().forEachIndexed { i, line ->
                Text(line, style = mono(if (i == 1) 13 else 11, if (i == 1) p.accent else p.screenText, i == 1))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Seg(listOf(tr("intercambiar", "swap")), -1, { vm.swapCompare() })
            Seg(listOf(tr("borrar", "clear")), -1, { vm.clearCompare() })
        }
    }
}

/** Parpadeo: recordatorio de la red eléctrica configurada. */
@Composable
fun FlickerInfoRow(vm: MeterViewModel) {
    val p = LocalPalette.current
    Text(
        tr("red ", "mains ") + "${vm.prefs.mainsHz} hz → " + tr("las luces suelen parpadear a ", "lights usually flicker at ") +
            "${vm.prefs.mainsHz * 2} hz · " + tr("cambia fps u obturación en la tarjeta", "change fps or shutter in the card"),
        style = mono(10, p.text), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** Celdas del mapa sobre el visor: naranja más cálida, azul más fría que la referencia. */
@Composable
fun MapOverlay(map: cl.lux0.color.LightMap?, refK: Double?, aspect: Float) {
    val p = LocalPalette.current
    if (map == null || refK == null) return
    Canvas(Modifier.fillMaxSize()) {
        // El contenido (vertical 3:4) llena el ancho y se recorta arriba y abajo, igual que el visor.
        val w = size.width
        val contentH = w * aspect
        val top = (size.height - contentH) / 2
        val cw = w / map.cols; val ch = contentH / map.rows
        for (row in 0 until map.rows) for (col in 0 until map.cols) {
            val k = map.at(col, row)
            val x = col * cw; val y = top + row * ch
            if (k.isNaN()) {
                drawRect(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.18f), Offset(x, y), androidx.compose.ui.geometry.Size(cw, ch))
                continue
            }
            val d = cl.lux0.color.LightMap.warmth(k, refK)
            val a = (cl.lux0.color.LightMap.strength(d) * 0.6).toFloat()
            if (p.red) {
                // Noche roja: sin colores; triángulo hacia arriba = más cálida, hacia abajo = más fría.
                if (a > 0.06f) {
                    val cx = x + cw / 2; val cy = y + ch / 2; val r = minOf(cw, ch) * 0.25f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (d > 0) { moveTo(cx, cy - r); lineTo(cx - r, cy + r); lineTo(cx + r, cy + r) }
                        else { moveTo(cx, cy + r); lineTo(cx - r, cy - r); lineTo(cx + r, cy - r) }
                        close()
                    }
                    drawPath(path, p.screenText.copy(alpha = 0.3f + a))
                }
            } else {
                val color = if (d > 0) androidx.compose.ui.graphics.Color(0xFFFF8A3D) else androidx.compose.ui.graphics.Color(0xFF4DA3FF)
                drawRect(color.copy(alpha = a), Offset(x, y), androidx.compose.ui.geometry.Size(cw, ch))
            }
            drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f), Offset(x, y), androidx.compose.ui.geometry.Size(cw, ch), style = Stroke(1f))
        }
    }
}

/** Leyenda del mapa: qué se usa como referencia. */
@Composable
fun MapLegend(vm: MeterViewModel) {
    val p = LocalPalette.current
    val ref = vm.prefs.targetK.takeIf { it > 0 }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            tr("referencia: ", "reference: ") + (ref?.let { tr("objetivo ", "target ") + "$it k" } ?: tr("la luz del círculo", "the light in the circle")),
            style = mono(12, p.text, true),
        )
        Text(
            tr("cada celda compara su luz con la referencia: naranja = más cálida, azul = más fría; gris = sin dato (oscura, quemada o de color propio). " +
                "depende del color de las superficies, así que es una guía rápida.",
                "each cell compares its light with the reference: orange = warmer, blue = cooler; gray = no data (dark, clipped or colored surface). " +
                "it depends on surface colors, so it is a quick guide."),
            style = mono(10, p.text), modifier = Modifier.padding(top = 4.dp),
        )
    }
}
