package cl.lux0.ui

import cl.lux0.tr
import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import cl.lux0.camera.MeterCamera
import cl.lux0.camera.Sample
import cl.lux0.color.Calibration
import cl.lux0.color.CameraProfile
import cl.lux0.color.ColorMath
import cl.lux0.color.Reading
import cl.lux0.color.TintSystem
import cl.lux0.color.recommend
import cl.lux0.data.CaptureSettings
import cl.lux0.data.Measurement
import cl.lux0.data.Prefs
import cl.lux0.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs

val SOURCES: List<String> get() = listOf(tr("blanco/gris", "white/gray"), tr("difusor→luz", "diffuser→light"), tr("escena ≈", "scene ≈"))
val SOURCE_HINTS: List<String> get() = listOf(
    tr("apunta a blanco o gris en el círculo", "aim at white or gray inside the circle"),
    tr("difusor blanco sobre el lente, apunta a la luz", "white diffuser over the lens, aim at the light"),
    tr("escena sin referencia: valor estimado", "scene without reference: estimated value"),
)

data class UiState(
    val mode: Int = 1,
    val reading: Reading? = null,
    val warning: String? = null,
    val hold: Boolean = false,
    val source: Int = 0,
    val theme: String = "auto",
    val calOffset: Double = 0.0,
    val error: String? = null,
    val version: Int = 0,
    /** Cámara apagada por inactividad (se reanuda al tocar la pantalla). */
    val idle: Boolean = false,
    /** Cámara apagada porque el teléfono está caliente. */
    val hot: Boolean = false,
    /** La última muestra trajo lectura válida (para calibrar con datos actuales). */
    val fresh: Boolean = false,
    /** Sube al tocar tras un error de cámara: fuerza un reintento. */
    val retry: Int = 0,
    /** Emulador: lecturas simuladas, nunca en un teléfono real. */
    val demo: Boolean = false,
    /** EV100 de la escena según el teléfono (ajustes del AE), para exposición. */
    val ev100: Double? = null,
    /** Lux del sensor de luz frontal (ya con el factor de calibración). */
    val lux: Double? = null,
    /** false si el teléfono no tiene sensor de luz. */
    val luxSensor: Boolean = true,
    /** Parpadeo: ya se analizó al menos un cuadro, y lo detectado (null = sin parpadeo visible). */
    val flickerChecked: Boolean = false,
    val flicker: cl.lux0.color.Flicker.Result? = null,
    /** Comparar: lecturas congeladas de la luz A y la B. */
    val compareA: Reading? = null,
    val compareB: Reading? = null,
    /** Mapa de luces mezcladas (modo 6). */
    val map: cl.lux0.color.LightMap? = null,
)

/** Detecta el emulador de Android (la cámara virtual no permite medir color). */
val IS_EMULATOR: Boolean = android.os.Build.HARDWARE.contains("ranchu") ||
    android.os.Build.HARDWARE.contains("goldfish") || android.os.Build.FINGERPRINT.contains("generic")

const val IDLE_MS = 90_000L
val INTERVALS = listOf(500L, 1000L, 2000L)

class MeterViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState(theme = prefs.theme, calOffset = prefs.calOffsetMired, demo = IS_EMULATOR))
    val state: StateFlow<UiState> = _state

    var cameras: List<CameraProfile> = store.allCameras()
        private set

    /** Compatibilidad del teléfono: se revisa al abrir y otra vez cuando se concede el permiso de cámara. */
    var compat: Compat by androidx.compose.runtime.mutableStateOf(detectCompat(app))
        private set

    fun refreshCompat() {
        compat = detectCompat(getApplication())
        if (_state.value.mode in 1..8 && !compat.allows(_state.value.mode)) _state.update { it.copy(mode = 3) }
    }

    private val camera = MeterCamera(app, ::onSample) { msg -> _state.update { it.copy(error = msg) } }
    @Volatile var lastTouch = SystemClock.elapsedRealtime()
        private set

    private val power = app.getSystemService(PowerManager::class.java)
    private val thermal = PowerManager.OnThermalStatusChangedListener { status -> onThermal(status) }

    init {
        power.addThermalStatusListener(app.mainExecutor, thermal)
        onThermal(power.currentThermalStatus)
        if (IS_EMULATOR) startDemo()
        if (compat == Compat.NONE) _state.update { it.copy(mode = 3) }
    }

    /** Modo demo: una luz que varía lento entre ~4100 y ~4900 K con leve magenta. */
    private fun startDemo() = viewModelScope.launch {
        var t = 0.0
        while (true) {
            delay(prefs.intervalMs)
            val st = _state.value
            if (st.hold || st.idle || st.mode !in listOf(1, 2, 3, 4, 5, 6, 9)) continue
            t += 0.15
            val cct = Calibration.apply(4500 + 400 * sin(t) + 60 * sin(t * 3.1), prefs.calOffsetMired)
            val duv = -0.004 + 0.003 * sin(t * 0.7)
            val ev = 10.0 + 0.8 * sin(t * 0.5)
            val lux = (420 + 60 * sin(t * 0.9)) * prefs.luxFactor
            _state.update {
                it.copy(reading = Reading(cct, duv, 0.0, 0.0, false), warning = null, error = null, fresh = true, ev100 = ev, lux = lux,
                    flickerChecked = true, flicker = cl.lux0.color.Flicker.Result(prefs.mainsHz * 2.0, 0.18),
                    map = demoMap(t))
            }
        }
    }

    /** Ligero: mide cada 3 s. Moderado o más: apaga la cámara hasta que se enfríe. */
    private var coolJob: kotlinx.coroutines.Job? = null

    /**
     * Ligero: mide cada 3 s. Moderado o más: apaga la cámara. Para no encender y apagar en ciclos, solo
     * se reanuda cuando el estado baja a "sin calor", o queda en "ligero" al menos 60 s seguidos.
     */
    private fun onThermal(status: Int) {
        val base = if (compat == Compat.LITE) maxOf(prefs.intervalMs, 2000L) else prefs.intervalMs
        camera.intervalMs = if (status >= PowerManager.THERMAL_STATUS_LIGHT) maxOf(base, 3000L) else base
        coolJob?.cancel()
        when {
            status >= PowerManager.THERMAL_STATUS_MODERATE -> _state.update { it.copy(hot = true) }
            !_state.value.hot -> Unit
            status == PowerManager.THERMAL_STATUS_NONE -> _state.update { it.copy(hot = false) }
            else -> coolJob = viewModelScope.launch {
                delay(60_000)
                if (power.currentThermalStatus < PowerManager.THERMAL_STATUS_MODERATE) _state.update { it.copy(hot = false) }
            }
        }
    }

    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
        val st = _state.value
        if (st.idle || st.error != null) _state.update { it.copy(idle = false, error = null, retry = it.retry + 1) }
    }

    fun checkIdle() {
        if (!_state.value.idle && SystemClock.elapsedRealtime() - lastTouch > IDLE_MS) {
            _state.update { it.copy(idle = true) }
        }
    }

    private fun onSample(s: Sample) {
        if (_state.value.hold) return
        _state.update { st ->
            if (s.flickerChecked || (st.mode == 4 && s.reading == null)) {
                st.copy(warning = s.warning, error = null, flickerChecked = s.flickerChecked || st.flickerChecked,
                    flicker = if (s.flickerChecked) s.flicker else st.flicker)
            } else {
                st.copy(reading = s.reading ?: st.reading, warning = s.warning, error = null, fresh = s.reading != null, ev100 = s.ev100 ?: st.ev100,
                    map = if (st.mode == 6) s.map ?: st.map else st.map)
            }
        }
    }

    // --- cámara ---
    private val aspectCache = HashMap<Boolean, Float>()

    /** Proporción del cuadro; se consulta a la cámara una sola vez por lado (trasera/frontal). */
    fun previewAspect(): Float = aspectCache.getOrPut(prefs.frontCamera) {
        try { camera.aspectFor(prefs.frontCamera) } catch (e: Exception) { 4f / 3f }
    }

    fun startCamera(texture: SurfaceTexture) {
        if (IS_EMULATOR) return
        onThermal(power.currentThermalStatus)
        camera.calOffsetMired = prefs.calOffsetMired
        camera.sceneSource = _state.value.source == 2
        camera.start(texture, prefs.frontCamera)
    }

    fun stopCamera() = camera.stop()

    override fun onCleared() {
        power.removeThermalStatusListener(thermal)
        stopLux()
        camera.release()
    }

    // --- acciones ---
    fun setMode(m: Int) {
        if (m in 1..8 && !compat.allows(m)) return
        // El modo parpadeo cambia la exposición de la cámara; al salir vuelve a la normal.
        camera.setFlicker(m == 4)
        camera.mapMode = m == 6
        _state.update { it.copy(mode = m, flickerChecked = if (m == 4) false else it.flickerChecked, flicker = if (m == 4) null else it.flicker) }
    }

    /** Mapa simulado: ventana fría a la izquierda, lámpara cálida a la derecha, algunas celdas oscuras. */
    private fun demoMap(t: Double): cl.lux0.color.LightMap {
        val cols = 6; val rows = 8
        return cl.lux0.color.LightMap(cols, rows, DoubleArray(cols * rows) { i ->
            val x = i % cols; val y = i / cols
            if (y == rows - 1 && x % 2 == 0) Double.NaN else 6800 - x * 650 + 150 * sin(t + y) - y * 40
        })
    }

    /** Referencia del mapa: el objetivo si existe; si no, la lectura del círculo. */
    fun mapReference(): Double? = prefs.targetK.takeIf { it > 0 }?.toDouble() ?: _state.value.reading?.cct

    // --- comparar ---
    fun measureA() = _state.value.reading?.let { r -> _state.update { it.copy(compareA = r) } }
    fun measureB() = _state.value.reading?.let { r -> _state.update { it.copy(compareB = r) } }
    fun swapCompare() = _state.update { it.copy(compareA = it.compareB, compareB = it.compareA) }
    fun clearCompare() = _state.update { it.copy(compareA = null, compareB = null) }

    /** Resultado de comparar: diferencia y gel para que B iguale a A. */
    fun compareText(): List<String> {
        val a = _state.value.compareA ?: return listOf(tr("mide la luz A", "measure light A"))
        val b = _state.value.compareB ?: return listOf(tr("ahora mide la luz B", "now measure light B"))
        val dMired = ColorMath.mired(a.cct) - ColorMath.mired(b.cct)
        val g = cl.lux0.color.Gels.suggest(b.cct, b.duv, a.cct, a.duv)
        return listOf(
            String.format(Locale.US, "Δ %+.0f mired · Δduv %+.3f", dMired, a.duv - b.duv),
            tr("para que b iguale a a: ", "to make b match a: ") + g.label,
            tr("o pon el panel b en ", "or set panel b to ") + "${Math.round(a.cct / 100.0) * 100} k",
        )
    }

    // --- parpadeo ---
    /** Recomendación de obturación según el parpadeo detectado y los fps de cada cámara. */
    fun flickerText(slot: Int): String {
        val st = _state.value
        if (!st.flickerChecked) return tr("midiendo…", "measuring…")
        val f = st.flicker ?: return tr("✓ sin parpadeo visible", "✓ no visible flicker")
        val c = if (slot == 1) prefs.cap1 else prefs.cap2
        val safe = cl.lux0.color.Flicker.safeShutters(c.fps, f.hz)
        val t = cl.lux0.color.Exposure.shutterSeconds(c.shutter, c.fps)
        return when {
            t != null && cl.lux0.color.Flicker.isSafe(t, f.hz) -> "✓ ${c.shutter} " + tr("seguro", "safe")
            safe.isEmpty() -> tr("riesgo: a ", "risk: at ") + cl.lux0.data.CaptureOptions.fpsLabel(c.fps) + tr("p no hay obturación segura", "p no safe shutter")
            else -> (if (t == null) "auto · " else tr("riesgo · ", "risk · ")) + tr("usa ", "use ") +
                safe.take(2).joinToString(tr(" o ", " or ")) { "${it.first} (${it.second})" }
        }
    }
    /** Hold congela la lectura y apaga la cámara (el visor la vuelve a encender al soltar). */
    fun toggleHold() = _state.update { it.copy(hold = !it.hold) }
    fun stepSource(d: Int) = setSource((_state.value.source + d).mod(SOURCES.size))
    fun setSource(i: Int) {
        _state.update { it.copy(source = i) }
        camera.sceneSource = i == 2
    }

    fun cycleTheme() {
        val order = listOf("auto", "claro", "oscuro", "rojo")
        setTheme(order[(order.indexOf(_state.value.theme) + 1) % order.size])
    }

    fun stepInterval(d: Int) {
        val i = INTERVALS.indexOf(prefs.intervalMs).coerceAtLeast(0)
        setInterval(INTERVALS[(i + d).mod(INTERVALS.size)])
    }

    fun setInterval(ms: Long) {
        prefs.intervalMs = ms
        onThermal(power.currentThermalStatus)
        bump()
    }

    fun toggleTintFormat() {
        prefs.tintFormat = if (prefs.tintFormat == "duv") "camara" else "duv"
        bump()
    }

    fun selectCam(slot: Int, id: String) {
        if (slot == 1) prefs.cam1Id = id else prefs.cam2Id = id
        bump()
    }

    fun setTheme(t: String) { prefs.theme = t; _state.update { it.copy(theme = t) } }

    fun bump() = _state.update { it.copy(version = it.version + 1) }

    fun cam(slot: Int): CameraProfile {
        val id = if (slot == 1) prefs.cam1Id else prefs.cam2Id
        return cameras.firstOrNull { it.id == id } ?: cameras.first()
    }

    fun cycleCam(slot: Int, d: Int) {
        val i = cameras.indexOf(cam(slot))
        val next = cameras[(i + d).mod(cameras.size)].id
        if (slot == 1) prefs.cam1Id = next else prefs.cam2Id = next
        bump()
    }

    fun addCustomCamera(p: CameraProfile) {
        store.saveCustomCamera(p)
        cameras = store.allCameras()
        bump()
    }

    fun uncertainty(): Double = when {
        _state.value.source == 2 -> 600.0
        _state.value.calOffset != 0.0 -> 150.0
        else -> 300.0
    }

    fun recText(p: CameraProfile, r: Reading): String {
        val rec = p.recommend(r.cct, r.duv, uncertainty())
        val sb = StringBuilder("${rec.kLow}–${rec.kHigh} · ${rec.kSet}")
        if (prefs.tintFormat == "duv") sb.append(" · ").append(String.format(Locale.US, "%+.3f", r.duv))
        else if (rec.tintLabel != null) sb.append(" · ≈").append(rec.tintLabel)
        else if (p.tint == TintSystem.NONE && abs(r.duv) > 0.006) sb.append(" · ⚠")
        return sb.toString()
    }

    fun save(thumb: Bitmap?) {
        val st = _state.value
        val r = if (st.mode == 3) null else st.reading
        if (r == null && st.mode != 3) return
        if (r != null && (!r.cct.isFinite() || !r.duv.isFinite())) return
        val evOk = st.ev100?.let { sceneEv(it) }?.takeIf { it.isFinite() }
        val luxOk = st.lux?.takeIf { it.isFinite() && it >= 0 }
        if (st.mode == 3 && st.lux == null) return
        val id = System.currentTimeMillis()
        // Solo se guarda el nombre del archivo; la ruta se resuelve siempre dentro de thumbs/.
        val thumbName = thumb?.let {
            try {
                FileOutputStream(File(store.thumbDir, "$id.jpg")).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                "$id.jpg"
            } catch (e: Exception) { null }
        }
        val c1 = cam(1); val c2 = cam(2)
        val ok = store.save(Measurement(
            id, store.activeSession().id, id,
            (if (st.demo) tr("demo (simulado) · ", "demo (simulated) · ") else "") + when (st.mode) {
                2 -> tr("exposición", "exposure"); 3 -> "lux"; 4 -> tr("parpadeo", "flicker")
                5 -> tr("comparar", "compare"); 6 -> tr("mapa", "map"); else -> SOURCES[st.source]
            },
            r?.cct ?: 0.0, r?.duv ?: 0.0,
            c1.label, slotText(1, r), c2.label, slotText(2, r), prefs.cap1, prefs.cap2, thumbName, "",
            ev100 = evOk, lux = if (st.mode == 3) luxOk else null,
        ))
        if (!ok) thumbName?.let { File(store.thumbDir, it).delete() }
        bump()
    }

    // Sin bump(): refrescar recrearía los campos y se perdería el foco al escribir.
    /** Texto de la fila de cada cámara según el modo actual. */
    fun slotText(slot: Int, r: Reading?): String = when (_state.value.mode) {
        2 -> assistText(slot)
        3 -> ""
        4 -> flickerText(slot)
        else -> r?.let { recText(cam(slot), it) } ?: ""
    }

    /** EV de la escena (gris medio) desde el EV del teléfono, la reflectancia y el ajuste de calibración. */
    fun sceneEv(phoneEv100: Double): Double =
        cl.lux0.color.Exposure.sceneEv(phoneEv100, prefs.reflectance.coerceIn(5, 95), calibrationEv = prefs.evCal)

    /** Resultado del asistente para una cámara (null si falta el diafragma o la medición). */
    fun assist(slot: Int): cl.lux0.color.ExposureAssistant.Result? {
        val ev = _state.value.ev100 ?: return null
        val c = if (slot == 1) prefs.cap1 else prefs.cap2
        val p = cam(slot)
        val subject = runCatching { cl.lux0.color.ExposureAssistant.Subject.valueOf(prefs.assistSubject) }
            .getOrDefault(cl.lux0.color.ExposureAssistant.Subject.PERSON)
        return cl.lux0.color.ExposureAssistant.suggest(cl.lux0.color.ExposureAssistant.Input(
            sceneEv100 = sceneEv(ev), fStop = c.fStop,
            ndStops = cl.lux0.data.CaptureOptions.ND.firstOrNull { it.first == c.nd }?.second ?: 0,
            isoMax = p.isoMax ?: 51200, intentEv = prefs.assistIntent + c.ev, video = prefs.assistVideo, fps = c.fps,
            cameraShutterSec = cl.lux0.color.Exposure.shutterSeconds(c.shutter, c.fps), subject = subject,
            tripod = prefs.assistTripod, focalMm = c.focal, crop = c.crop ?: p.crop, stabStops = c.stab,
            iso = if (c.isoAuto) null else c.iso,
        ))
    }

    /** Fila de la cámara en exposición: la combinación del asistente o, sin diafragma, el cálculo anterior. */
    fun assistText(slot: Int): String {
        val r = assist(slot) ?: return expoText(slot)
        val base = cl.lux0.color.ExposureAssistant.shutterLabel(r.shutterSec) + " · " +
            cl.lux0.data.CaptureOptions.fLabel(r.fStop) + " · iso ${r.iso}"
        return if (kotlin.math.abs(r.stopsOff) >= 0.17) "$base · ⚠" else base
    }

    /** Explicación del aviso ⚠ de una cámara (o null si calza). */
    fun assistNote(slot: Int): String? {
        val r = assist(slot) ?: return null
        val n = cl.lux0.data.CaptureOptions.evLabel(Math.round(kotlin.math.abs(r.stopsOff) * 3) / 3.0).removePrefix("+").removePrefix("−")
        return when {
            r.stopsOff >= 0.17 -> tr("falta luz (", "not enough light (") + n + tr(" pasos): ", " stops): ") +
                (r.fixAperture?.let { tr("abre a ", "open to ") + cl.lux0.data.CaptureOptions.fLabel(it) + tr(", ", ", ") } ?: "") +
                (r.fixIso?.let { tr("sube a iso ", "raise to iso ") + it + ", " } ?: "") +
                tr("agrega luz o usa trípode", "add light or use a tripod")
            r.stopsOff <= -0.17 -> tr("sobra luz (", "too much light (") + n + tr(" pasos): ", " stops): ") +
                (r.fixIso?.let { tr("baja a iso ", "lower to iso ") + it + ", " } ?: "") +
                (r.fixNdStops?.let { s -> cl.lux0.data.CaptureOptions.ND.firstOrNull { it.second >= s }?.first?.let { tr("usa ", "use ") + it } }
                    ?: tr("cierra el diafragma", "stop down"))
            else -> null
        }
    }

    /** Recomendación de exposición para una cámara con sus ajustes de captura. */
    fun expoText(slot: Int): String {
        val ev = _state.value.ev100 ?: return "—"
        val c = if (slot == 1) prefs.cap1 else prefs.cap2
        val t = cl.lux0.color.Exposure.shutterSeconds(c.shutter, c.fps)
            ?: return tr("auto · usa comp. ev", "auto · use ev comp.")
        val nd = cl.lux0.data.CaptureOptions.ND.firstOrNull { it.first == c.nd }?.second ?: 0
        val scene = sceneEv(ev)
        // Con iso auto y sin diafragma hay dos incógnitas: se pide el diafragma.
        if (c.isoAuto) return tr("iso auto · elige f/ en la tarjeta", "auto iso · pick f/ in the card")
        val exact = cl.lux0.color.Exposure.apertureFor(scene, c.iso, t, nd, c.ev)
        val f = c.fStop
        val stops = { x: Double -> cl.lux0.data.CaptureOptions.evLabel(Math.round(kotlin.math.abs(x) * 3) / 3.0).removePrefix("+").removePrefix("−") }
        if (f == null) {
            return when {
                exact < 1.2 * 0.94 -> tr("falta luz: f/1.2 y aún ", "not enough light: f/1.2 and still ") +
                    stops(2 * kotlin.math.ln(1.2 / exact) / kotlin.math.ln(2.0)) + tr(" pasos", " stops")
                exact > 22 * 1.06 -> tr("sobra luz: f/22 y aún ", "too much light: f/22 and still ") +
                    stops(2 * kotlin.math.ln(exact / 22) / kotlin.math.ln(2.0)) + tr(" pasos (más nd)", " stops (more nd)")
                else -> "→ " + cl.lux0.data.CaptureOptions.fLabel(cl.lux0.color.Exposure.nearestStop(exact))
            }
        }
        val off = cl.lux0.color.Exposure.stopsOff(scene, c.iso, t, nd, c.ev, f)
        return when {
            kotlin.math.abs(off) < 0.17 -> "✓ " + cl.lux0.data.CaptureOptions.fLabel(f) + tr(" correcto", " correct")
            off > 0 -> tr("sobran ", "over by ") + stops(off) + tr(" · cierra a ", " · stop down to ") +
                cl.lux0.data.CaptureOptions.fLabel(cl.lux0.color.Exposure.nearestStop(exact.coerceAtMost(22.0)))
            else -> tr("faltan ", "under by ") + stops(off) + tr(" · abre a ", " · open to ") +
                cl.lux0.data.CaptureOptions.fLabel(cl.lux0.color.Exposure.nearestStop(exact.coerceAtLeast(1.2)))
        }
    }

    /** "◎ objetivo 5600 → ¼ ctb" cuando hay temperatura objetivo. */
    fun targetText(r: Reading?): String? {
        val target = prefs.targetK.takeIf { it > 0 } ?: return null
        val g = r?.let { cl.lux0.color.Gels.suggest(it.cct, it.duv, target.toDouble()) }
        return tr("◎ objetivo ", "◎ target ") + "$target" + (g?.let { " → " + it.label } ?: "")
    }

    // --- sensor de luz (modo lux) ---
    private val sensors = app.getSystemService(android.hardware.SensorManager::class.java)
    private val luxListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            if (_state.value.hold) return
            _state.update { it.copy(lux = e.values[0] * prefs.luxFactor) }
        }
        override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
    }

    fun startLux() {
        if (IS_EMULATOR) return
        val sensor = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
        if (sensor == null) { _state.update { it.copy(luxSensor = false) }; return }
        sensors.registerListener(luxListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopLux() = sensors.unregisterListener(luxListener)

    fun setCapture(slot: Int, c: CaptureSettings) {
        if (slot == 1) prefs.cap1 = c else prefs.cap2 = c
    }

    /** Calibra con el K de referencia usando la lectura actual (sin la calibración previa). */
    fun calibrate(referenceK: Double): Boolean {
        if (!_state.value.fresh) return false
        val r = _state.value.reading ?: return false
        val old = prefs.calOffsetMired
        val rawCct = 1e6 / (ColorMath.mired(r.cct) - old)
        val off = Calibration.offsetFor(rawCct, referenceK)
        prefs.calOffsetMired = off
        camera.calOffsetMired = off
        _state.update { it.copy(calOffset = off, reading = r.copy(cct = referenceK)) }
        return true
    }

    fun resetCalibration() {
        val old = prefs.calOffsetMired
        prefs.calOffsetMired = 0.0
        camera.calOffsetMired = 0.0
        _state.update { st ->
            st.copy(calOffset = 0.0, reading = st.reading?.let { it.copy(cct = 1e6 / (ColorMath.mired(it.cct) - old)) })
        }
    }
}
