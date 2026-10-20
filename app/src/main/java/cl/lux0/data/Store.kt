package cl.lux0.data

import cl.lux0.tr
import android.content.Context
import cl.lux0.color.CameraProfile
import cl.lux0.color.cameraProfileFromJson
import cl.lux0.color.parseProfiles
import cl.lux0.color.toJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sesiones, mediciones y cámaras personalizadas en filesDir/lux0.json (privado de la app).
 * La escritura va a un hilo aparte; la lectura descarta solo los registros dañados.
 */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "lux0.json")
    val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var sessions = mutableListOf<Session>()
    private var measurements = mutableListOf<Measurement>()
    private var custom = mutableListOf<CameraProfile>()
    private var activeId = 0L

    init { load() }

    private fun load() {
        if (!file.exists()) return
        val o = try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            // Archivo ilegible: se guarda una copia con fecha y se parte de cero, sin caerse en cada arranque.
            android.util.Log.w("lux0", "lux0.json dañado", e)
            try { file.copyTo(File(file.path + ".bak-" + System.currentTimeMillis()), overwrite = false) } catch (_: Exception) {}
            return
        }
        // Registro por registro: uno malo no borra los demás.
        o.optJSONArray("sessions")?.let { arr -> for (i in 0 until arr.length()) runCatching { sessions += Session.fromJson(arr.getJSONObject(i)) } }
        o.optJSONArray("measurements")?.let { arr -> for (i in 0 until arr.length()) runCatching { measurements += Measurement.fromJson(arr.getJSONObject(i)) } }
        o.optJSONArray("custom")?.let { arr -> for (i in 0 until arr.length()) runCatching { custom += cameraProfileFromJson(arr.getJSONObject(i)) } }
        activeId = o.optLong("active", 0L)
        cleanOrphanThumbs()
    }

    /** Borra miniaturas que ya no pertenecen a ninguna medición. */
    private fun cleanOrphanThumbs() {
        val used = measurements.mapNotNull { it.thumbPath?.let { p -> File(p).name } }.toSet()
        thumbDir.listFiles()?.forEach { if (it.name !in used) it.delete() }
    }

    /**
     * Miniatura de una medición, siempre dentro de thumbs/ (también para rutas antiguas guardadas
     * completas). Nunca devuelve un archivo fuera de esa carpeta.
     */
    fun thumbFile(m: Measurement): File? {
        val name = m.thumbPath?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: return null
        val f = File(thumbDir, name)
        return f.takeIf { it.canonicalPath.startsWith(thumbDir.canonicalPath + File.separator) }
    }

    /** Arma el JSON en el hilo actual y lo escribe en segundo plano (atómico, con fsync). */
    private fun persist() {
        val text = try {
            JSONObject()
                .put("sessions", JSONArray(sessions.map { it.toJson() }))
                .put("measurements", JSONArray(measurements.map { it.toJson() }))
                .put("custom", JSONArray(custom.map { it.toJson() }))
                .put("active", activeId)
                .toString()
        } catch (e: Exception) {
            android.util.Log.w("lux0", "no se pudo preparar el guardado", e); return
        }
        writer.execute {
            try {
                val tmp = File(file.path + ".tmp")
                java.io.FileOutputStream(tmp).use { out -> out.write(text.toByteArray()); out.fd.sync() }
                if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
            } catch (e: Exception) {
                android.util.Log.w("lux0", "no se pudo guardar", e)
            }
        }
    }

    fun sessions(): List<Session> = sessions.sortedByDescending { it.created }

    fun activeSession(): Session {
        sessions.firstOrNull { it.id == activeId }?.let { return it }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val auto = tr("sesión", "session") + " $day"
        val existing = sessions.firstOrNull { it.name == auto }
        val s = existing ?: newSessionInternal(auto)
        activeId = s.id
        persist()
        return s
    }

    private fun newSessionInternal(name: String): Session {
        val s = Session(System.currentTimeMillis(), name.take(80), System.currentTimeMillis())
        sessions += s
        return s
    }

    fun newSession(name: String): Session {
        val s = newSessionInternal(name.ifBlank { tr("sesión", "session") })
        activeId = s.id
        persist()
        return s
    }

    fun setActive(id: Long) { activeId = id; persist() }

    /** Borra una sesión con todas sus mediciones y miniaturas. */
    fun deleteSession(id: Long) {
        measurements.filter { it.sessionId == id }.forEach { m -> thumbFile(m)?.delete() }
        measurements.removeAll { it.sessionId == id }
        sessions.removeAll { it.id == id }
        if (activeId == id) activeId = 0L
        persist()
    }

    fun measurements(sessionId: Long?): List<Measurement> =
        measurements.filter { sessionId == null || it.sessionId == sessionId }.sortedByDescending { it.time }

    /** Agrega una medición solo si todos sus números son válidos (un NaN rompería el guardado). */
    fun save(m: Measurement): Boolean {
        val nums = listOfNotNull(m.cct, m.duv, m.ev100, m.lux, m.cap1.fps, m.cap2.fps, m.cap1.ev, m.cap2.ev, m.cap1.fStop, m.cap2.fStop)
        if (nums.any { !it.isFinite() }) return false
        measurements += m
        persist()
        return true
    }

    fun updateComment(id: Long, text: String) {
        val i = measurements.indexOfFirst { it.id == id }
        if (i >= 0) { measurements[i] = measurements[i].copy(comment = text.take(500)); persist() }
    }

    fun delete(id: Long) {
        measurements.firstOrNull { it.id == id }?.let { thumbFile(it)?.delete() }
        measurements.removeAll { it.id == id }
        persist()
    }

    fun bundledCameras(): List<CameraProfile> =
        parseProfiles(context.assets.open("cameras.json").bufferedReader().readText())

    fun customCameras(): List<CameraProfile> = custom.toList()

    fun allCameras(): List<CameraProfile> = bundledCameras() + custom

    fun saveCustomCamera(p: CameraProfile) {
        custom.removeAll { it.id == p.id }
        custom += p
        persist()
    }
}

/** Ajustes simples en SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lux0", Context.MODE_PRIVATE)

    var cam1Id: String
        get() = sp.getString("cam1", "canon-r5")!!
        set(v) = sp.edit().putString("cam1", v).apply()
    var cam2Id: String
        get() = sp.getString("cam2", "insta360-luna-ultra")!!
        set(v) = sp.edit().putString("cam2", v).apply()
    var tintFormat: String
        get() = sp.getString("tintFormat", "camara")!!
        set(v) = sp.edit().putString("tintFormat", v).apply()
    var reflectance: Int
        get() = sp.getInt("reflectance", 18)
        set(v) = sp.edit().putInt("reflectance", v).apply()
    var intervalMs: Long
        get() = sp.getLong("intervalMs", 1000L)
        set(v) = sp.edit().putLong("intervalMs", v).apply()
    var frontCamera: Boolean
        get() = sp.getBoolean("front", false)
        set(v) = sp.edit().putBoolean("front", v).apply()
    var mainsHz: Int
        get() = sp.getInt("mainsHz", 50)
        set(v) = sp.edit().putInt("mainsHz", v).apply()
    var theme: String
        get() = sp.getString("theme", "auto")!!
        set(v) = sp.edit().putString("theme", v).apply()
    var calOffsetMired: Double
        get() = sp.getFloat("calOffset", 0f).toDouble()
        set(v) = sp.edit().putFloat("calOffset", v.toFloat()).apply()
    /** Temperatura objetivo en K (0 = sin objetivo). */
    var targetK: Int
        get() = sp.getInt("targetK", 0)
        set(v) = sp.edit().putInt("targetK", v).apply()
    /** Ajuste fino de la exposición medida, en pasos (calibración). */
    var evCal: Double
        get() = sp.getFloat("evCal", 0f).toDouble()
        set(v) = sp.edit().putFloat("evCal", v.toFloat()).apply()
    /** Factor de calibración del sensor de luz. */
    var luxFactor: Double
        get() = sp.getFloat("luxFactor", 1f).toDouble()
        set(v) = sp.edit().putFloat("luxFactor", v.toFloat()).apply()
    /** Versión del aviso de responsabilidad que el usuario aceptó (0 = ninguna). */
    var termsAccepted: Int
        get() = sp.getInt("termsAccepted", 0)
        set(v) = sp.edit().putInt("termsAccepted", v).apply()
    var cap1: CaptureSettings
        get() = CaptureSettings.fromJson(sp.getString("cap1", null)?.let { JSONObject(it) })
        set(v) = sp.edit().putString("cap1", v.toJson().toString()).apply()
    var cap2: CaptureSettings
        get() = CaptureSettings.fromJson(sp.getString("cap2", null)?.let { JSONObject(it) } ?: JSONObject().put("fps", 60.0).put("shutter", "auto"))
        set(v) = sp.edit().putString("cap2", v.toJson().toString()).apply()
}
