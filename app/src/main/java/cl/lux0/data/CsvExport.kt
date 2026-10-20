package cl.lux0.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val CSV_HEADER =
    "fecha;sesion;fuente;kelvin;duv;cam1;rec1;cam2;rec2;iso1;fps1;obt1;f1;nd1;ev1;iso2;fps2;obt2;f2;nd2;ev2;ev100;lux;comentario"
const val CSV_HEADER_EN =
    "date;session;source;kelvin;duv;cam1;rec1;cam2;rec2;iso1;fps1;shutter1;f1;nd1;ev1;iso2;fps2;shutter2;f2;nd2;ev2;ev100;lux;comment"

/**
 * Celda segura: entre comillas si hace falta, y con ' delante si es texto que empieza con = + - @
 * (para que Excel o Sheets no lo ejecuten como fórmula). Los números negativos quedan igual.
 */
private fun esc(raw: String): String {
    val s = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r" && raw.toDoubleOrNull() == null) "'$raw" else raw
    return if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

fun toCsv(list: List<Measurement>, sessions: Map<Long, String>): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val sb = StringBuilder(if (cl.lux0.I18n.en) CSV_HEADER_EN else CSV_HEADER).append('\n')
    for (m in list) {
        val cols = listOf(
            fmt.format(Date(m.time)), sessions[m.sessionId] ?: "", m.source,
            Math.round(m.cct).toString(), String.format(Locale.US, "%.4f", m.duv),
            m.cam1, m.rec1, m.cam2, m.rec2,
            m.cap1.iso.toString(), fpsLabel(m.cap1.fps), m.cap1.shutter, m.cap1.fStop?.toString() ?: "", m.cap1.nd, CaptureOptions.evLabel(m.cap1.ev),
            m.cap2.iso.toString(), fpsLabel(m.cap2.fps), m.cap2.shutter, m.cap2.fStop?.toString() ?: "", m.cap2.nd, CaptureOptions.evLabel(m.cap2.ev),
            m.ev100?.let { String.format(Locale.US, "%.1f", it) } ?: "",
            m.lux?.let { Math.round(it).toString() } ?: "",
            m.comment,
        )
        sb.append(cols.joinToString(";") { esc(it) }).append('\n')
    }
    return sb.toString()
}
