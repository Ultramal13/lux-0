package cl.lux0.ui

import cl.lux0.tr
import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.PowerManager
import android.os.SystemClock
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

    private val camera = MeterCamera(app, ::onSample) { msg -> _state.update { it.copy(error = msg) } }
    @Volatile var lastTouch = SystemClock.elapsedRealtime()
        private set

    private val power = app.getSystemService(PowerManager::class.java)
    private val thermal = PowerManager.OnThermalStatusChangedListener { status -> onThermal(status) }

    init {
        power.addThermalStatusListener(app.mainExecutor, thermal)
        onThermal(power.currentThermalStatus)
        if (IS_EMULATOR) startDemo()
    }

    /** Modo demo: una luz que varía lento entre ~4100 y ~4900 K con leve magenta. */
    private fun startDemo() = viewModelScope.launch {
        var t = 0.0
        while (true) {
            delay(prefs.intervalMs)
            val st = _state.value
            if (st.hold || st.idle || st.mode !in listOf(1, 2, 3, 9)) continue
            t += 0.15
            val cct = Calibration.apply(4500 + 400 * sin(t) + 60 * sin(t * 3.1), prefs.calOffsetMired)
            val duv = -0.004 + 0.003 * sin(t * 0.7)
            val ev = 10.0 + 0.8 * sin(t * 0.5)
            val lux = (420 + 60 * sin(t * 0.9)) * prefs.luxFactor
            _state.update {
                it.copy(reading = Reading(cct, duv, 0.0, 0.0, false), warning = null, error = null, fresh = true, ev100 = ev, lux = lux)
            }
        }
    }

    /** Ligero: mide cada 3 s. Moderado o más: apaga la cámara hasta que se enfríe. */
    private fun onThermal(status: Int) {
        camera.intervalMs = if (status >= PowerManager.THERMAL_STATUS_LIGHT) maxOf(prefs.intervalMs, 3000L) else prefs.intervalMs
        _state.update { it.copy(hot = status >= PowerManager.THERMAL_STATUS_MODERATE) }
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
            st.copy(reading = s.reading ?: st.reading, warning = s.warning, error = null, fresh = s.reading != null, ev100 = s.ev100 ?: st.ev100)
        }
    }

    // --- cámara ---
    fun previewAspect(): Float {
        val sz = camera.choosePreviewSize(prefs.frontCamera) ?: return 4f / 3f
        return sz.width.toFloat() / sz.height
    }

    fun startCamera(texture: SurfaceTexture) {
        if (IS_EMULATOR) return
        onThermal(power.currentThermalStatus)
        camera.calOffsetMired = prefs.calOffsetMired
        camera.start(texture, prefs.frontCamera)
    }

    fun stopCamera() = camera.stop()

    override fun onCleared() {
        power.removeThermalStatusListener(thermal)
        stopLux()
        camera.release()
    }

    // --- acciones ---
    fun setMode(m: Int) = _state.update { it.copy(mode = m) }
    /** Hold congela la lectura y apaga la cámara (el visor la vuelve a encender al soltar). */
    fun toggleHold() = _state.update { it.copy(hold = !it.hold) }
    fun stepSource(d: Int) = _state.update { it.copy(source = (it.source + d).mod(SOURCES.size)) }
    fun setSource(i: Int) = _state.update { it.copy(source = i) }

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
        if (st.mode == 3 && st.lux == null) return
        val id = System.currentTimeMillis()
        val path = thumb?.let {
            val f = File(store.thumbDir, "$id.jpg")
            FileOutputStream(f).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            f.path
        }
        val c1 = cam(1); val c2 = cam(2)
        store.save(Measurement(
            id, store.activeSession().id, id,
            (if (st.demo) tr("demo (simulado) · ", "demo (simulated) · ") else "") + when (st.mode) {
                2 -> tr("exposición", "exposure"); 3 -> "lux"; else -> SOURCES[st.source]
            },
            r?.cct ?: 0.0, r?.duv ?: 0.0,
            c1.label, slotText(1, r), c2.label, slotText(2, r), prefs.cap1, prefs.cap2, path, "",
            ev100 = st.ev100?.let { sceneEv(it) }, lux = if (st.mode == 3) st.lux else null,
        ))
        bump()
    }

    // Sin bump(): refrescar recrearía los campos y se perdería el foco al escribir.
    /** Texto de la fila de cada cámara según el modo actual. */
    fun slotText(slot: Int, r: Reading?): String = when (_state.value.mode) {
        2 -> expoText(slot)
        3 -> ""
        else -> r?.let { recText(cam(slot), it) } ?: ""
    }

    /** EV de la escena (gris medio) desde el EV del teléfono, la reflectancia y el ajuste de calibración. */
    fun sceneEv(phoneEv100: Double): Double =
        cl.lux0.color.Exposure.sceneEv(phoneEv100, prefs.reflectance.coerceIn(5, 95), calibrationEv = prefs.evCal)

    /** Recomendación de exposición para una cámara con sus ajustes de captura. */
    fun expoText(slot: Int): String {
        val ev = _state.value.ev100 ?: return "—"
        val c = if (slot == 1) prefs.cap1 else prefs.cap2
        val t = cl.lux0.color.Exposure.shutterSeconds(c.shutter, c.fps)
            ?: return tr("auto · usa comp. ev", "auto · use ev comp.")
        val nd = cl.lux0.data.CaptureOptions.ND.firstOrNull { it.first == c.nd }?.second ?: 0
        val scene = sceneEv(ev)
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
