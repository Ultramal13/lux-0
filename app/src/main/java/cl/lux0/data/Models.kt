package cl.lux0.data

import org.json.JSONObject

data class CaptureSettings(
    val iso: Int = 800,
    val fps: Double = 24.0,
    val shutter: String = "1/50",
    val fStop: Double? = null,
    val nd: String = "",
    /** Compensación de exposición en pasos (−4..+4). */
    val ev: Double = 0.0,
    /** Lente en mm (null = no indicado / lente fijo). */
    val focal: Int? = null,
    /** Pasos de estabilización (IBIS + lente). */
    val stab: Int = 0,
    /** Formato del sensor (factor de recorte); null = el de la cámara. */
    val crop: Double? = null,
    /** ISO automático: el asistente de exposición elige el ISO; si no, el ISO de la tarjeta queda fijo. */
    val isoAuto: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().put("iso", iso).put("fps", fps).put("shutter", shutter)
        .put("fStop", fStop ?: JSONObject.NULL).put("nd", nd).put("ev", ev)
        .put("focal", focal ?: JSONObject.NULL).put("stab", stab).put("crop", crop ?: JSONObject.NULL).put("isoAuto", isoAuto)

    val label: String
        get() = buildString {
            append(fpsLabel(fps)).append("p · ").append(shutter).append(" · iso ").append(if (isoAuto) "auto" else iso.toString())
            fStop?.let { append(" · f/").append(it) }
            if (nd.isNotBlank()) append(" · ").append(nd)
        }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) CaptureSettings() else CaptureSettings(
            iso = o.optInt("iso", 800),
            fps = o.optDouble("fps", 24.0),
            shutter = o.optString("shutter", "1/50"),
            fStop = if (o.isNull("fStop")) null else o.optDouble("fStop"),
            nd = o.optString("nd", ""),
            ev = o.optDouble("ev", 0.0),
            focal = if (o.isNull("focal")) null else o.optInt("focal").takeIf { it > 0 },
            stab = o.optInt("stab", 0).coerceIn(0, 8),
            crop = if (o.isNull("crop")) null else o.optDouble("crop").takeIf { it > 0 },
            isoAuto = o.optBoolean("isoAuto", false),
        )
    }
}

fun fpsLabel(fps: Double): String =
    if (fps == Math.floor(fps)) fps.toInt().toString() else fps.toString()

data class Session(val id: Long, val name: String, val created: Long, val note: String = "") {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("created", created).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) =
            Session(o.getLong("id"), o.getString("name"), o.getLong("created"), o.optString("note", ""))
    }
}

data class Measurement(
    val id: Long,
    val sessionId: Long,
    val time: Long,
    val source: String,
    val cct: Double,
    val duv: Double,
    val cam1: String,
    val rec1: String,
    val cam2: String,
    val rec2: String,
    val cap1: CaptureSettings,
    val cap2: CaptureSettings,
    val thumbPath: String?,
    val comment: String,
    val ev100: Double? = null,
    val lux: Double? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("sessionId", sessionId).put("time", time)
        .put("source", source).put("cct", cct).put("duv", duv)
        .put("cam1", cam1).put("rec1", rec1).put("cam2", cam2).put("rec2", rec2)
        .put("cap1", cap1.toJson()).put("cap2", cap2.toJson())
        .put("thumbPath", thumbPath ?: JSONObject.NULL).put("comment", comment)
        .put("ev100", ev100 ?: JSONObject.NULL).put("lux", lux ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = Measurement(
            id = o.getLong("id"), sessionId = o.getLong("sessionId"), time = o.getLong("time"),
            source = o.optString("source"), cct = o.getDouble("cct"), duv = o.getDouble("duv"),
            cam1 = o.optString("cam1"), rec1 = o.optString("rec1"),
            cam2 = o.optString("cam2"), rec2 = o.optString("rec2"),
            cap1 = CaptureSettings.fromJson(o.optJSONObject("cap1")),
            cap2 = CaptureSettings.fromJson(o.optJSONObject("cap2")),
            thumbPath = if (o.isNull("thumbPath")) null else o.optString("thumbPath"),
            comment = o.optString("comment", ""),
            ev100 = if (o.isNull("ev100")) null else o.optDouble("ev100"),
            lux = if (o.isNull("lux")) null else o.optDouble("lux"),
        )
    }
}
