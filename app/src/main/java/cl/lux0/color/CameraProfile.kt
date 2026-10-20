package cl.lux0.color

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class TintSystem { NONE, CANON_GM9 }

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
) {
    val label: String get() = "$brand $model".lowercase()
}

data class Recommendation(val kSet: Int, val kLow: Int, val kHigh: Int, val tintLabel: String?)

/** Duv por paso del WB Shift G/M de Canon (aproximado, no hay equivalencia oficial). */
const val CANON_DUV_PER_STEP = 0.0025

private fun CameraProfile.snap(k: Double): Int =
    ((k / kStep).roundToInt() * kStep).coerceIn(kMin, kMax)

fun CameraProfile.recommend(cct: Double, duv: Double, uncertaintyK: Double): Recommendation {
    val tint = when (tint) {
        TintSystem.NONE -> null
        TintSystem.CANON_GM9 -> {
            // Compensa la luz: luz magenta (duv < 0) → corrimiento hacia G.
            val steps = (-duv / CANON_DUV_PER_STEP).roundToInt().coerceIn(-9, 9)
            when {
                steps > 0 -> "g$steps"
                steps < 0 -> "m${-steps}"
                else -> "0"
            }
        }
    }
    return Recommendation(snap(cct), snap(cct - uncertaintyK), snap(cct + uncertaintyK), tint)
}

fun CameraProfile.toJson(): JSONObject = JSONObject()
    .put("id", id).put("brand", brand).put("model", model)
    .put("kMin", kMin).put("kMax", kMax).put("kStep", kStep)
    .put("tint", tint.name).put("verified", verified).put("source", source)
    .put("isoMax", isoMax ?: JSONObject.NULL)

fun cameraProfileFromJson(o: JSONObject) = CameraProfile(
    id = o.getString("id"),
    brand = o.getString("brand"),
    model = o.getString("model"),
    kMin = o.getInt("kMin"),
    kMax = o.getInt("kMax"),
    kStep = o.getInt("kStep"),
    tint = runCatching { TintSystem.valueOf(o.getString("tint")) }.getOrDefault(TintSystem.NONE),
    verified = o.optBoolean("verified", false),
    source = o.optString("source", ""),
    isoMax = if (o.isNull("isoMax")) null else o.optInt("isoMax"),
)

fun parseProfiles(json: String): List<CameraProfile> {
    val arr = JSONArray(json)
    return List(arr.length()) { cameraProfileFromJson(arr.getJSONObject(it)) }
}
