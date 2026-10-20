package cl.lux0.color

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cómo corrige el tinte (verde/magenta) cada cámara:
 * - GM: eje G/M con números (Canon, Nikon, Sony).
 * - GM_DIR: tiene eje G/M pero el fabricante no publica la escala (Panasonic): solo se indica hacia dónde.
 * - RB: Fujifilm corrige con ejes R y B (R y B iguales ≈ magenta/verde).
 * - NUMERIC: un control "tint" numérico (Blackmagic, Canon cine).
 * - CANON_GM9: valor antiguo, igual a GM −9..+9 (se mantiene para cámaras guardadas).
 */
enum class TintSystem { NONE, CANON_GM9, GM, GM_DIR, RB, NUMERIC }

/** Cómo cambia el Kelvin en la cámara. */
enum class KelvinMode { STEP, MIRED, LIST }

data class CameraProfile(
    val id: String,
    val brand: String,
    val model: String,
    val kMin: Int,
    val kMax: Int,
    val kStep: Int,
    val tint: TintSystem,
    val verified: Boolean,
    val source: String,
    /** ISO máximo útil; null = desconocido (se muestra la lista completa). */
    val isoMax: Int? = null,
    val kMode: KelvinMode = KelvinMode.STEP,
    /** Temperaturas fijas (solo en modo LIST, ej. GoPro). */
    val kValues: List<Int> = emptyList(),
    val tintMin: Double = -9.0,
    val tintMax: Double = 9.0,
    val tintStep: Double = 1.0,
    /** Duv aproximado que corrige una unidad del control de tinte (no hay equivalencias oficiales). */
    val duvPerUnit: Double = CANON_DUV_PER_STEP,
    /** Factor de recorte del sensor (1.0 full frame, 1.5/1.6 APS-C, 2.0 micro 4/3); null = lente fijo o desconocido. */
    val crop: Double? = null,
) {
    val label: String get() = "$brand $model".lowercase()
}

data class Recommendation(val kSet: Int, val kLow: Int, val kHigh: Int, val tintLabel: String?)

/** Duv por paso del WB Shift G/M de Canon (aproximado, no hay equivalencia oficial). */
const val CANON_DUV_PER_STEP = 0.0025

private fun CameraProfile.snap(k: Double): Int = when (kMode) {
    KelvinMode.LIST -> kValues.minByOrNull { abs(ColorMath.mired(it.toDouble()) - ColorMath.mired(k)) } ?: k.roundToInt()
    // Nikon cambia de a 1 mired: se redondea en mired y se muestra el K resultante (a la decena).
    KelvinMode.MIRED -> {
        val m = ColorMath.mired(k.coerceIn(kMin.toDouble(), kMax.toDouble())).roundToInt().coerceAtLeast(1)
        ((1e6 / m / 10).roundToInt() * 10).coerceIn(kMin, kMax)
    }
    KelvinMode.STEP -> ((k / kStep).roundToInt() * kStep).coerceIn(kMin, kMax)
}

private fun num(v: Double): String =
    if (abs(v - v.roundToInt()) < 1e-9) "${v.roundToInt()}" else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

/** Lleva un valor al paso y rango del control de tinte. */
private fun CameraProfile.tintUnits(raw: Double): Double =
    ((raw / tintStep).roundToInt() * tintStep).coerceIn(tintMin, tintMax)

fun CameraProfile.recommend(cct: Double, duv: Double, uncertaintyK: Double): Recommendation {
    // Siempre se compensa la luz: luz magenta (duv < 0) → corregir hacia verde, y al revés.
    val tint = when (tint) {
        TintSystem.NONE -> null
        TintSystem.CANON_GM9, TintSystem.GM -> {
            val u = if (tint == TintSystem.CANON_GM9) (-duv / CANON_DUV_PER_STEP).roundToInt().coerceIn(-9, 9).toDouble()
            else tintUnits(-duv / duvPerUnit)
            when {
                u > 0 -> "g${num(u)}"
                u < 0 -> "m${num(-u)}"
                else -> "0"
            }
        }
        TintSystem.GM_DIR -> when {
            abs(duv) < 0.002 -> "0"
            duv < 0 -> "→g"
            else -> "→m"
        }
        TintSystem.RB -> {
            // Fujifilm: subir R y B por igual tiñe hacia magenta; bajarlos, hacia verde.
            val n = tintUnits(duv / duvPerUnit)
            if (n == 0.0) "0" else "r${if (n > 0) "+" else ""}${num(n)} b${if (n > 0) "+" else ""}${num(n)}"
        }
        TintSystem.NUMERIC -> {
            // Tint positivo = magenta (Blackmagic): luz magenta → valor negativo.
            val n = tintUnits(duv / duvPerUnit)
            "tint ${if (n > 0) "+" else ""}${num(n)}"
        }
    }
    return Recommendation(snap(cct), snap(cct - uncertaintyK), snap(cct + uncertaintyK), tint)
}

fun CameraProfile.toJson(): JSONObject = JSONObject()
    .put("id", id).put("brand", brand).put("model", model)
    .put("kMin", kMin).put("kMax", kMax).put("kStep", kStep)
    .put("tint", tint.name).put("verified", verified).put("source", source)
    .put("isoMax", isoMax ?: JSONObject.NULL)
    .put("kMode", kMode.name).put("kValues", JSONArray(kValues))
    .put("tintMin", tintMin).put("tintMax", tintMax).put("tintStep", tintStep).put("duvPerUnit", duvPerUnit)
    .put("crop", crop ?: JSONObject.NULL)

fun cameraProfileFromJson(o: JSONObject): CameraProfile {
    val values = o.optJSONArray("kValues")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList()
    return CameraProfile(
        id = o.getString("id"),
        brand = o.getString("brand"),
        model = o.getString("model"),
        kMin = o.optInt("kMin", values.minOrNull() ?: 2500),
        kMax = o.optInt("kMax", values.maxOrNull() ?: 10000),
        kStep = o.optInt("kStep", 100).coerceAtLeast(1),
        tint = runCatching { TintSystem.valueOf(o.getString("tint")) }.getOrDefault(TintSystem.NONE),
        verified = o.optBoolean("verified", false),
        source = o.optString("source", ""),
        isoMax = if (o.isNull("isoMax")) null else o.optInt("isoMax"),
        kMode = runCatching { KelvinMode.valueOf(o.optString("kMode", "STEP")) }.getOrDefault(KelvinMode.STEP)
            .let { if (it == KelvinMode.LIST && values.isEmpty()) KelvinMode.STEP else it },
        kValues = values,
        tintMin = o.optDouble("tintMin", -9.0),
        tintMax = o.optDouble("tintMax", 9.0),
        tintStep = o.optDouble("tintStep", 1.0).takeIf { it > 0 } ?: 1.0,
        duvPerUnit = o.optDouble("duvPerUnit", CANON_DUV_PER_STEP).takeIf { it > 0 } ?: CANON_DUV_PER_STEP,
        crop = if (o.isNull("crop")) null else o.optDouble("crop").takeIf { it > 0 },
    )
}

fun parseProfiles(json: String): List<CameraProfile> {
    val arr = JSONArray(json)
    return List(arr.length()) { cameraProfileFromJson(arr.getJSONObject(it)) }
}
