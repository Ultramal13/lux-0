package cl.lux0.camera

import cl.lux0.tr
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.TonemapCurve
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import cl.lux0.color.Calibration
import cl.lux0.color.ColorMath
import cl.lux0.color.Reading
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.roundToInt

data class Sample(
    val reading: Reading?,
    val warning: String?,
    val roi: RoiResult?,
    val ev100: Double? = null,
    /** Solo en modo parpadeo: true si se pudo analizar el cuadro. */
    val flickerChecked: Boolean = false,
    val flicker: cl.lux0.color.Flicker.Result? = null,
    /** Solo en modo mapa: temperatura por celda, orientada como la pantalla. */
    val map: cl.lux0.color.LightMap? = null,
)

/** Fracción del lado corto del cuadro que mide el círculo. La mira del visor usa la misma. */
const val ROI_FRACTION = 0.19f

/**
 * Medición liviana: vista previa chica + un flujo YUV chico en la misma petición repetida.
 * El sensor queda en su modo de visor (como la app de cámara). El procesamiento de color del
 * teléfono se neutraliza (AWB apagado, matriz identidad, ganancias 1, curva lineal), así que el
 * RGB promedio del círculo es el del sensor y se convierte a xy con las matrices del propio sensor.
 *
 * Todas las operaciones de cámara corren en un único hilo propio que vive lo mismo que el objeto,
 * así un stop() que llega mientras la cámara se abre nunca deja el dispositivo abierto.
 */
class MeterCamera(
    context: Context,
    private val onSample: (Sample) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("lux0-cam").also { it.start() }
    private val handler = Handler(thread.looper)
    private val exec = Executor { handler.post(it) }

    // Solo se tocan desde el hilo de cámara.
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var chars: CameraCharacteristics? = null
    private var lastProcessed = 0L
    @Volatile private var lastResult: android.hardware.camera2.CaptureResult? = null
    private val history = ArrayDeque<Pair<Double, Double>>()

    @Volatile private var generation = 0
    @Volatile private var released = false
    @Volatile var intervalMs: Long = 1000
    /** Modo parpadeo: exposición manual corta para ver las bandas del rolling shutter. */
    @Volatile private var flicker = false
    /** Modo mapa: además del círculo, calcula la temperatura de una grilla de 8×6 celdas. */
    @Volatile var mapMode = false
    private var aeTimeNs = 10_000_000L
    private var aeIso = 400

    private val repeatCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(cs: CameraCaptureSession, req: CaptureRequest, res: android.hardware.camera2.TotalCaptureResult) {
            lastResult = res
            if (!flicker) {
                res.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)?.let { aeTimeNs = it }
                res.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)?.let { aeIso = it }
            }
        }
    }

    /** Activa o desactiva el modo parpadeo sin reiniciar la cámara. */
    fun setFlicker(on: Boolean) {
        if (released) return
        handler.post {
            if (flicker == on) return@post
            flicker = on
            history.clear()
            val d = device ?: return@post
            val pv = previewSurface ?: return@post
            val yuv = reader?.surface ?: return@post
            try { session?.setRepeatingRequest(request(d, pv, yuv), repeatCallback, handler) } catch (e: Exception) { Log.w("lux0", "modo parpadeo", e) }
        }
    }
    @Volatile var calOffsetMired: Double = 0.0

    var previewSize: Size = Size(640, 480)
        private set

    private fun findCamera(front: Boolean): String? {
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want
        }
    }

    /** Elige el tamaño 4:3 más chico ≥ 640 px para preview y análisis. */
    fun choosePreviewSize(front: Boolean): Size {
        val sizes = findCamera(front)
            ?.let { manager.getCameraCharacteristics(it).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) }
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.takeIf { it.isNotEmpty() }
            ?: return previewSize
        previewSize = sizes.filter { it.width * 3 == it.height * 4 && it.width >= 640 }
            .minByOrNull { it.width } ?: sizes.minBy { abs(it.width - 640) }
        return previewSize
    }

    fun start(texture: SurfaceTexture, front: Boolean) {
        if (released) return
        val gen = ++generation
        handler.post { startInternal(texture, front, gen) }
    }

    fun stop() {
        generation++
        if (!released) handler.post { closeAll() }
    }

    /** Libera la cámara y termina el hilo. Llamar una sola vez (onCleared). */
    fun release() {
        generation++
        released = true
        handler.post { closeAll(); thread.quitSafely() }
    }

    private fun fail(gen: Int, msg: String) {
        if (gen != generation) return
        closeAll()
        onError(msg)
    }

    @SuppressLint("MissingPermission")
    private fun startInternal(texture: SurfaceTexture, front: Boolean, gen: Int) {
        closeAll()
        if (gen != generation) return
        try {
            val id = findCamera(front) ?: return fail(gen, tr("no se encontró la cámara", "camera not found"))
            val c = manager.getCameraCharacteristics(id)
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING !in caps) {
                return fail(gen, tr("esta cámara no permite controlar el color", "this camera does not allow color control"))
            }
            if (c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) == null) {
                return fail(gen, tr("la cámara no entrega matrices de color", "the camera does not provide color matrices"))
            }
            chars = c
            history.clear()
            val size = choosePreviewSize(front)
            reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r ->
                    val img = try { r.acquireLatestImage() } catch (e: IllegalStateException) { null }
                        ?: return@setOnImageAvailableListener
                    try { maybeProcess(img, gen) } finally { img.close() }
                }, handler)
            }
            texture.setDefaultBufferSize(size.width, size.height)
            previewSurface = Surface(texture)
            manager.openCamera(id, exec, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    if (gen != generation) { d.close(); return }
                    device = d
                    createSession(d, gen)
                }
                override fun onDisconnected(d: CameraDevice) {
                    d.close()
                    if (device == d) device = null
                    fail(gen, tr("la cámara se desconectó · toca para reintentar", "camera disconnected · tap to retry"))
                }
                override fun onError(d: CameraDevice, error: Int) {
                    d.close()
                    if (device == d) device = null
                    fail(gen, tr("error de cámara", "camera error") + " ($error) · " + tr("toca para reintentar", "tap to retry"))
                }
            })
        } catch (e: Exception) {
            Log.w("lux0", "start", e)
            fail(gen, tr("no se pudo abrir la cámara · toca para reintentar", "could not open the camera · tap to retry"))
        }
    }

    private fun createSession(d: CameraDevice, gen: Int) {
        val preview = previewSurface ?: return
        val yuv = reader?.surface ?: return
        try {
            d.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(preview), OutputConfiguration(yuv)), exec,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (gen != generation) { s.close(); return }
                        session = s
                        try {
                            s.setRepeatingRequest(request(d, preview, yuv), repeatCallback, handler)
                        } catch (e: Exception) {
                            Log.w("lux0", "repeating", e)
                            fail(gen, tr("no se pudo iniciar la medición · toca para reintentar", "could not start measuring · tap to retry"))
                        }
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) =
                        fail(gen, tr("no se pudo configurar la cámara · toca para reintentar", "could not configure the camera · tap to retry"))
                }))
        } catch (e: Exception) {
            Log.w("lux0", "createSession", e)
            fail(gen, tr("no se pudo configurar la cámara · toca para reintentar", "could not configure the camera · tap to retry"))
        }
    }

    private fun request(d: CameraDevice, preview: Surface, yuv: Surface): CaptureRequest {
        val c = chars!!
        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(preview); b.addTarget(yuv)
        // Exposición automática medida en el mismo círculo, ~−1 EV para no quemar el blanco.
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val r = (minOf(active.width(), active.height()) * ROI_FRACTION).toInt()
        val cx = active.centerX(); val cy = active.centerY()
        b.set(CaptureRequest.CONTROL_AE_REGIONS,
            arrayOf(MeteringRectangle(Rect(cx - r, cy - r, cx + r, cy + r), MeteringRectangle.METERING_WEIGHT_MAX)))
        val step = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble() ?: (1.0 / 3)
        val comp = (-1.0 / step).roundToInt()
        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
            c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)?.clamp(comp) ?: comp)
        // Cuadros por segundo bajos: menos trabajo y menos calor.
        c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 10 }
            ?.minWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
            ?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        // Color neutro: nada de balance automático ni realce del teléfono.
        b.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        b.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        b.set(CaptureRequest.COLOR_CORRECTION_GAINS, RggbChannelVector(1f, 1f, 1f, 1f))
        b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, ColorSpaceTransform(intArrayOf(
            1, 1, 0, 1, 0, 1,
            0, 1, 1, 1, 0, 1,
            0, 1, 0, 1, 1, 1,
        )))
        b.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF)
        if (flicker) {
            // 1/2000 s con el ISO subido para mantener el brillo que eligió el AE.
            val shortNs = 500_000L
            val range = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val iso = (aeIso * aeTimeNs.toDouble() / shortNs).toInt().let { range?.clamp(it) ?: it }
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, shortNs)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
        }
        val tm = c.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
        if (CaptureRequest.TONEMAP_MODE_GAMMA_VALUE in tm) {
            b.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE)
            b.set(CaptureRequest.TONEMAP_GAMMA, 1.0f)
        } else if (CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE in tm) {
            val lin = floatArrayOf(0f, 0f, 1f, 1f)
            b.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
            b.set(CaptureRequest.TONEMAP_CURVE, TonemapCurve(lin, lin, lin))
        }
        return b.build()
    }

    private fun transform(t: ColorSpaceTransform?): DoubleArray? = t?.let {
        DoubleArray(9) { i -> it.getElement(i % 3, i / 3).toDouble() }
    }

    // Iluminantes EXIF/DNG → K.
    private fun illuminantK(code: Int?): Double = when (code) {
        1 -> 5500.0; 2 -> 4150.0; 3 -> 2850.0; 4 -> 5500.0; 9 -> 5500.0; 10 -> 6500.0; 11 -> 7500.0
        12 -> 6430.0; 13 -> 5000.0; 14 -> 4150.0; 15 -> 3450.0; 17 -> 2856.0; 18 -> 4874.0
        19 -> 6774.0; 20 -> 5503.0; 21 -> 6504.0; 22 -> 7504.0; 23 -> 5003.0; 24 -> 3200.0
        else -> 5000.0
    }

    private fun maybeProcess(img: Image, gen: Int) {
        if (gen != generation) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessed < intervalMs) return
        lastProcessed = now
        val c = chars ?: return
        if (flicker) { processFlicker(img); return }
        try {
            val p = img.planes
            val roi = computeRoiYuv(
                Plane(p[0].buffer, p[0].rowStride, p[0].pixelStride),
                Plane(p[1].buffer, p[1].rowStride, p[1].pixelStride),
                Plane(p[2].buffer, p[2].rowStride, p[2].pixelStride),
                img.width, img.height, (minOf(img.width, img.height) * ROI_FRACTION).toInt().coerceAtLeast(8),
            )
            val ev = phoneEv100(c)
            val warn = roi.warning()
            if (warn != null || roi.g <= 0.001) { onSample(Sample(null, warn ?: tr("sin señal", "no signal"), roi, ev)); return }
            val cm1 = transform(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1))!!
            val cm2 = transform(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)) ?: cm1
            val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
            val cc1 = transform(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)) ?: id
            val cc2 = transform(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)) ?: cc1
            val t1 = illuminantK(c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1))
            val t2 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt()?.let { illuminantK(it) } ?: t1
            val toXy = { rr: RoiResult -> ColorMath.cameraNeutralToXy(doubleArrayOf(rr.r / rr.g, 1.0, rr.b / rr.g), cm1, cm2, cc1, cc2, t1, t2) }
            val map = if (mapMode) buildMap(p, img.width, img.height, c, toXy) else null
            val xy = toXy(roi)
            if (xy.first.isNaN() || xy.second.isNaN()) { onSample(Sample(null, tr("no se pudo calcular", "could not compute"), roi, ev, map = map)); return }
            history.addLast(xy)
            while (history.size > 3) history.removeFirst()
            val r = ColorMath.xyToReading(history.sumOf { it.first } / history.size, history.sumOf { it.second } / history.size)
            val warning = when {
                r.outOfRange -> tr("fuera de rango (1000–20000 k)", "out of range (1000–20000 k)")
                abs(r.duv) > 0.02 -> tr("no parece neutro o luz muy teñida", "not neutral, or strongly tinted light")
                else -> null
            }
            onSample(Sample(r.copy(cct = Calibration.apply(r.cct, calOffsetMired)), warning, roi, ev, map = map))
        } catch (e: Exception) {
            Log.w("lux0", "cálculo de color", e)
            onSample(Sample(null, tr("no se pudo calcular", "could not compute"), null))
        }
    }

    /** Grilla 8×6 en coordenadas del sensor → temperatura por celda (NaN si oscura, quemada o muy teñida). */
    private fun buildMap(p: Array<Image.Plane>, w: Int, h: Int, c: CameraCharacteristics, toXy: (RoiResult) -> Pair<Double, Double>): cl.lux0.color.LightMap {
        val bc = 8; val br = 6
        val yP = Plane(p[0].buffer, p[0].rowStride, p[0].pixelStride)
        val uP = Plane(p[1].buffer, p[1].rowStride, p[1].pixelStride)
        val vP = Plane(p[2].buffer, p[2].rowStride, p[2].pixelStride)
        val buf = DoubleArray(bc * br) { i ->
            val cx = i % bc; val cy = i / bc
            val cell = computeRectYuv(yP, uP, vP, cx * w / bc, cy * h / br, (cx + 1) * w / bc, (cy + 1) * h / br, step = 6)
            if (cell.warning() != null || cell.meanLevel < 0.03 || cell.g <= 0.001) Double.NaN
            else try {
                val xy = toXy(cell)
                val rd = ColorMath.xyToReading(xy.first, xy.second)
                if (abs(rd.duv) > 0.03 || rd.outOfRange) Double.NaN else Calibration.apply(rd.cct, calOffsetMired)
            } catch (e: Exception) { Double.NaN }
        }
        val orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        return cl.lux0.color.LightMap.toDisplay(buf, bc, br, orientation)
    }

    /** Perfil de luminancia por filas (en orden de lectura del sensor) → frecuencia y profundidad. */
    private fun processFlicker(img: Image) {
        try {
            val y = img.planes[0]
            val h = img.height; val w = img.width
            val rows = DoubleArray(h)
            for (r in 0 until h) {
                var sum = 0L; var n = 0
                var col = w / 8
                while (col < w - w / 8) { sum += y.buffer.get(r * y.rowStride + col * y.pixelStride).toInt() and 0xFF; n++; col += 4 }
                rows[r] = sum.toDouble() / n
            }
            val skew = lastResult?.get(android.hardware.camera2.CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)
            if (skew == null || skew <= 0) {
                onSample(Sample(null, tr("este teléfono no informa el tiempo de lectura", "this phone does not report readout time"), null)); return
            }
            if (rows.average() < 8) { onSample(Sample(null, tr("muy oscuro", "too dark"), null)); return }
            val res = cl.lux0.color.Flicker.detect(rows, skew / 1e9 / h)
            onSample(Sample(null, null, null, flickerChecked = true, flicker = res))
        } catch (e: Exception) {
            Log.w("lux0", "parpadeo", e)
        }
    }

    /** EV100 de los ajustes que eligió el AE del teléfono para el círculo (null si aún no hay datos). */
    private fun phoneEv100(c: CameraCharacteristics): Double? {
        val res = lastResult ?: return null
        val t = res.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME) ?: return null
        val iso = res.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY) ?: return null
        val n = res.get(android.hardware.camera2.CaptureResult.LENS_APERTURE)
            ?: c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: return null
        val boost = res.get(android.hardware.camera2.CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
        if (t <= 0 || iso <= 0) return null
        return cl.lux0.color.Exposure.ev100(n.toDouble(), t / 1e9, iso, boost)
    }

    /** Cierra sesión, dispositivo, lector y superficie. Solo en el hilo de cámara. */
    private fun closeAll() {
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        previewSurface?.release()
        previewSurface = null
        lastResult = null
        history.clear()
    }
}
