package cl.lux0.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
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

val SOURCES = listOf("blanco/gris", "difusor→luz", "escena ≈")
val SOURCE_HINTS = listOf(
    "apunta a blanco o gris en el círculo",
    "difusor blanco sobre el lente, apunta a la luz",
    "escena sin referencia: valor estimado",
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
)

const val IDLE_MS = 90_000L
val INTERVALS = listOf(500L, 1000L, 2000L)

class MeterViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState(theme = prefs.theme, calOffset = prefs.calOffsetMired))
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
            st.copy(reading = s.reading ?: st.reading, warning = s.warning, error = null, fresh = s.reading != null)
        }
    }

    // --- cámara ---
    fun previewAspect(): Float {
        val sz = camera.choosePreviewSize(prefs.frontCamera) ?: return 4f / 3f
        return sz.width.toFloat() / sz.height
    }

    fun startCamera(texture: SurfaceTexture) {
        onThermal(power.currentThermalStatus)
        camera.calOffsetMired = prefs.calOffsetMired
        camera.start(texture, prefs.frontCamera)
    }

    fun stopCamera() = camera.stop()

    override fun onCleared() {
        power.removeThermalStatusListener(thermal)
        camera.release()
    }

    // --- acciones ---
    fun setMode(m: Int) = _state.update { it.copy(mode = m) }
    /** Hold congela la lectura y apaga la cámara (el visor la vuelve a encender al soltar). */
    fun toggleHold() = _state.update { it.copy(hold = !it.hold) }
    fun stepSource(d: Int) = _state.update { it.copy(source = (it.source + d).mod(SOURCES.size)) }

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
        else if (p.tint == TintSystem.NONE && abs(r.duv) > 0.006) sb.append(" · tinte!")
        return sb.toString()
    }

    fun save(thumb: Bitmap?) {
        val r = _state.value.reading ?: return
        if (!r.cct.isFinite() || !r.duv.isFinite()) return
        val id = System.currentTimeMillis()
        val path = thumb?.let {
            val f = File(store.thumbDir, "$id.jpg")
            FileOutputStream(f).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            f.path
        }
        val c1 = cam(1); val c2 = cam(2)
        store.save(Measurement(
            id, store.activeSession().id, id, SOURCES[_state.value.source], r.cct, r.duv,
            c1.label, recText(c1, r), c2.label, recText(c2, r), prefs.cap1, prefs.cap2, path, "",
        ))
        bump()
    }

    // Sin bump(): refrescar recrearía los campos y se perdería el foco al escribir.
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
