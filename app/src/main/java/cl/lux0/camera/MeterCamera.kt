package cl.lux0.camera

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
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import cl.lux0.color.Calibration
import cl.lux0.color.ColorMath
import cl.lux0.color.Reading
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.roundToInt

data class Sample(val reading: Reading?, val warning: String?, val roi: RoiResult)

/**
 * Preview a una SurfaceTexture + captura RAW periódica. El color sale del RAW y de las
 * matrices del sensor; el balance automático del teléfono no interviene.
 */
class MeterCamera(
    context: Context,
    private val onSample: (Sample) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var rawReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var chars: CameraCharacteristics? = null
    @Volatile private var lastResult: CaptureResult? = null
    private val history = ArrayDeque<Pair<Double, Double>>()

    @Volatile var intervalMs: Long = 1000
    @Volatile var calOffsetMired: Double = 0.0
    @Volatile var paused: Boolean = false

    /** Tamaño del buffer de preview elegido (ancho > alto, orientación del sensor). */
    var previewSize: Size = Size(1280, 960)
        private set

    private val poll = object : Runnable {
        override fun run() {
            if (!paused) captureRaw()
            handler?.postDelayed(this, intervalMs)
        }
    }

    fun choosePreviewSize(front: Boolean): Size? {
        val id = findCamera(front) ?: return null
        val map = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return null
        previewSize = sizes.filter { it.width * 3 == it.height * 4 && it.width <= 1920 }
            .maxByOrNull { it.width } ?: sizes.minBy { abs(it.width - 1280) }
        return previewSize
    }

    private fun findCamera(front: Boolean): String? {
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want
        }
    }

    @SuppressLint("MissingPermission")
    fun start(texture: SurfaceTexture, front: Boolean) {
        stop()
        val id = findCamera(front) ?: return onError("no se encontró la cámara")
        val c = manager.getCameraCharacteristics(id)
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW !in caps) {
            return onError("esta cámara no entrega raw")
        }
        if (c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) == null) {
            return onError("la cámara no entrega matrices de color")
        }
        chars = c
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val rawSize = map.getOutputSizes(ImageFormat.RAW_SENSOR).minBy { it.width.toLong() * it.height }
        val t = HandlerThread("lux0-cam").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        handler = h
        rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
            setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { processRaw(it) } }, h)
        }
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(texture)
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(d: CameraDevice) { device = d; createSession(d) }
            override fun onDisconnected(d: CameraDevice) { d.close(); device = null }
            override fun onError(d: CameraDevice, error: Int) {
                d.close(); device = null
                onError("error de cámara ($error)")
            }
        }, h)
    }

    private fun createSession(d: CameraDevice) {
        val h = handler ?: return
        val exec = Executor { h.post(it) }
        val outs = listOf(OutputConfiguration(previewSurface!!), OutputConfiguration(rawReader!!.surface))
        d.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outs, exec,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    s.setRepeatingRequest(baseRequest(d, false).build(), null, h)
                    h.postDelayed(poll, 600)
                }
                override fun onConfigureFailed(s: CameraCaptureSession) = onError("no se pudo configurar la cámara")
            }))
    }

    private fun baseRequest(d: CameraDevice, withRaw: Boolean): CaptureRequest.Builder {
        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(previewSurface!!)
        if (withRaw) b.addTarget(rawReader!!.surface)
        val c = chars!!
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
        val r = active.width() / 10
        val cx = active.centerX(); val cy = active.centerY()
        b.set(CaptureRequest.CONTROL_AE_REGIONS,
            arrayOf(MeteringRectangle(Rect(cx - r, cy - r, cx + r, cy + r), MeteringRectangle.METERING_WEIGHT_MAX)))
        // ~−1 EV para que una hoja blanca no se queme en el RAW.
        val step = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toDouble() ?: 1.0 / 3
        val range = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val comp = (-1.0 / step).roundToInt()
        b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, range?.clamp(comp) ?: comp)
        return b
    }

    private fun captureRaw() {
        val d = device ?: return
        val s = session ?: return
        try {
            s.capture(baseRequest(d, true).build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(cs: CameraCaptureSession, req: CaptureRequest, res: TotalCaptureResult) {
                    lastResult = res
                }
            }, handler)
        } catch (e: Exception) {
            Log.w("lux0", "captura raw", e)
        }
    }

    private fun transform(t: ColorSpaceTransform?): DoubleArray? = t?.let {
        DoubleArray(9) { i -> it.getElement(i % 3, i / 3).toDouble() }
    }

    private fun illuminantK(code: Int?): Double = when (code) {
        17 -> 2856.0; 21 -> 6504.0; 23 -> 5003.0; 20 -> 5503.0; 22 -> 7504.0
        3 -> 2850.0; 24 -> 3200.0; 1 -> 5500.0; else -> 5000.0
    }

    private fun processRaw(img: android.media.Image) {
        val c = chars ?: return
        val plane = img.planes[0]
        val stridePx = plane.rowStride / 2
        val w = img.width; val h = img.height
        // Recorte cuadrado centrado (origen par para conservar la fase del mosaico).
        val radius = (minOf(w, h) * 0.08).toInt().coerceAtLeast(8)
        val side = radius * 2
        val x0 = (w / 2 - radius) and 1.inv()
        val y0 = (h / 2 - radius) and 1.inv()
        val crop = ShortArray(side * side)
        val sb = plane.buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (row in 0 until side) {
            sb.position((y0 + row) * stridePx + x0)
            sb.get(crop, row * side, side)
        }
        val res = lastResult
        val dynBlack = res?.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
        val black = dynBlack ?: c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)!!.let { p ->
            FloatArray(4) { i -> p.getOffsetForIndex(i % 2, i / 2).toFloat() }
        }
        val white = res?.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)
            ?: c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)!!
        val cfa = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) ?: 0
        val roi = computeRoi(crop, side, side, side, cfa, black, white, radius, radius, radius)
        val warn = roi.warning()
        if (warn != null || roi.g <= 0) {
            onSample(Sample(null, warn ?: "sin señal", roi)); return
        }
        val cm1 = transform(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1))!!
        val cm2 = transform(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)) ?: cm1
        val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val cc1 = transform(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)) ?: id
        val cc2 = transform(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)) ?: cc1
        val t1 = illuminantK(c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1))
        val t2 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt()?.let { illuminantK(it) } ?: t1
        try {
            val xy = ColorMath.cameraNeutralToXy(
                doubleArrayOf(roi.r / roi.g, 1.0, roi.b / roi.g), cm1, cm2, cc1, cc2, t1, t2)
            history.addLast(xy)
            while (history.size > 3) history.removeFirst()
            val ax = history.sumOf { it.first } / history.size
            val ay = history.sumOf { it.second } / history.size
            val r = ColorMath.xyToReading(ax, ay)
            val cct = Calibration.apply(r.cct, calOffsetMired)
            val tinted = if (abs(r.duv) > 0.02) "no parece neutro o luz muy teñida" else null
            onSample(Sample(r.copy(cct = cct), tinted, roi))
        } catch (e: Exception) {
            Log.w("lux0", "cálculo de color", e)
            onSample(Sample(null, "no se pudo calcular", roi))
        }
    }

    fun stop() {
        handler?.removeCallbacks(poll)
        try { session?.close() } catch (_: Exception) {}
        session = null
        device?.close(); device = null
        rawReader?.close(); rawReader = null
        previewSurface?.release(); previewSurface = null
        thread?.quitSafely(); thread = null; handler = null
        history.clear()
    }
}
