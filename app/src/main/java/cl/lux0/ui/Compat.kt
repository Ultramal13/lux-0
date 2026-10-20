package cl.lux0.ui

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import cl.lux0.tr

/**
 * Qué tan capaz es el teléfono, revisado al abrir la app:
 * - FULL: todo funciona.
 * - LITE: poca memoria o cámara limitada → mide cada 2 s y se apagan parpadeo y mapa (lo más pesado).
 * - NONE: la cámara no permite controlar el color → solo lux, sesiones y ajustes.
 */
enum class Compat { FULL, LITE, NONE }

fun detectCompat(context: Context): Compat {
    if (IS_EMULATOR) return Compat.FULL
    return try {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return Compat.NONE
        val c = cm.getCameraCharacteristics(id)
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING !in caps ||
            c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1) == null
        ) return Compat.NONE
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val limitedCamera = level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
            level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED ||
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in caps
        val lowMemory = am.isLowRamDevice || mem.totalMem < 4L * 1024 * 1024 * 1024
        if (limitedCamera || lowMemory) Compat.LITE else Compat.FULL
    } catch (e: Exception) {
        Compat.LITE   // ante la duda, el modo más liviano
    }
}

fun compatLabel(c: Compat): String = when (c) {
    Compat.FULL -> tr("completa: todas las herramientas", "full: all tools")
    Compat.LITE -> tr("modo liviano: mide cada 2 s; parpadeo y mapa desactivados para no exigir el teléfono",
        "lite mode: measures every 2 s; flicker and map turned off so the phone is not pushed")
    Compat.NONE -> tr("sin cámara compatible: este teléfono no permite controlar el color; solo funciona lux",
        "no compatible camera: this phone does not allow color control; only lux works")
}

/** Teclas disponibles según la compatibilidad. */
fun Compat.allows(key: Int): Boolean = when (this) {
    Compat.FULL -> true
    Compat.LITE -> key != 4 && key != 6
    Compat.NONE -> key in listOf(3, 7, 8)
}

/** Nombre de la versión instalada (el del APK, ej. "0.3.1-beta"). */
fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) { "?" }

/** Versión del aviso: si cambia, se vuelve a pedir aceptación. */
const val TERMS_VERSION = 1

fun termsText(): List<String> = listOf(
    tr("lux-0 es una beta gratuita y se entrega \"tal cual\", sin garantías.", "lux-0 is a free beta provided \"as is\", without warranty."),
    tr("las mediciones son orientativas: verifica con tu cámara antes de un trabajo importante.",
        "measurements are for guidance only: check with your camera before important work."),
    tr("no se garantiza que funcione en todos los teléfonos. en equipos básicos puede funcionar limitada o no funcionar.",
        "it is not guaranteed to work on every phone. on basic devices it may work in a limited way or not at all."),
    tr("la usas bajo tu propia responsabilidad: bexia no se hace responsable por fallas o calentamiento del teléfono, pérdida de datos ni resultados de trabajos.",
        "you use it at your own risk: bexia is not liable for phone malfunction or heating, data loss, or affected work."),
    tr("no usa internet ni recopila datos; lo que guardas queda en tu teléfono y respaldarlo es tu responsabilidad.",
        "it does not use the internet or collect data; what you save stays on your phone and backing it up is your responsibility."),
    tr("las marcas de cámaras mencionadas pertenecen a sus dueños y no están afiliadas a lux-0.",
        "camera brands mentioned belong to their owners and are not affiliated with lux-0."),
)
