package cl.lux0.data

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

/** Sesiones, mediciones y cámaras personalizadas en filesDir/lux0.json. */
class Store(private val context: Context) {
    private val file = File(context.filesDir, "lux0.json")
    val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    private var sessions = mutableListOf<Session>()
    private var measurements = mutableListOf<Measurement>()
    private var custom = mutableListOf<CameraProfile>()
    private var activeId = 0L

    init { load() }

    private fun load() {
        if (!file.exists()) return
        try {
            parse(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Archivo dañado: se respalda y se parte de cero en vez de caerse en cada arranque.
            android.util.Log.w("lux0", "lux0.json dañado", e)
            file.copyTo(File(file.path + ".bak"), overwrite = true)
            sessions.clear(); measurements.clear(); custom.clear(); activeId = 0L
        }
    }

    private fun parse(o: JSONObject) {
        o.optJSONArray("sessions")?.let { a -> for (i in 0 until a.length()) sessions += Session.fromJson(a.getJSONObject(i)) }
        o.optJSONArray("measurements")?.let { a -> for (i in 0 until a.length()) measurements += Measurement.fromJson(a.getJSONObject(i)) }
        o.optJSONArray("custom")?.let { a -> for (i in 0 until a.length()) custom += cameraProfileFromJson(a.getJSONObject(i)) }
        activeId = o.optLong("active", 0L)
    }

    private fun persist() {
        val o = JSONObject()
            .put("sessions", JSONArray(sessions.map { it.toJson() }))
            .put("measurements", JSONArray(measurements.map { it.toJson() }))
            .put("custom", JSONArray(custom.map { it.toJson() }))
            .put("active", activeId)
        val tmp = File(file.path + ".tmp")
        java.io.FileOutputStream(tmp).use { out ->
            out.write(o.toString().toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) { file.writeText(o.toString()); tmp.delete() }
    }

    fun sessions(): List<Session> = sessions.sortedByDescending { it.created }

    fun activeSession(): Session {
        sessions.firstOrNull { it.id == activeId }?.let { return it }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val existing = sessions.firstOrNull { it.name == "sesión $day" }
        val s = existing ?: newSessionInternal("sesión $day")
        activeId = s.id
        persist()
        return s
    }

    private fun newSessionInternal(name: String): Session {
        val s = Session(System.currentTimeMillis(), name, System.currentTimeMillis())
        sessions += s
        return s
    }

    fun newSession(name: String): Session {
        val s = newSessionInternal(name.ifBlank { "sesión" })
        activeId = s.id
        persist()
        return s
    }

    fun setActive(id: Long) { activeId = id; persist() }

    fun measurements(sessionId: Long?): List<Measurement> =
        measurements.filter { sessionId == null || it.sessionId == sessionId }.sortedByDescending { it.time }

    fun save(m: Measurement) { measurements += m; persist() }

    fun updateComment(id: Long, text: String) {
        val i = measurements.indexOfFirst { it.id == id }
        if (i >= 0) { measurements[i] = measurements[i].copy(comment = text); persist() }
    }

    fun delete(id: Long) {
        measurements.firstOrNull { it.id == id }?.thumbPath?.let { File(it).delete() }
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
    var cap1: CaptureSettings
        get() = CaptureSettings.fromJson(sp.getString("cap1", null)?.let { JSONObject(it) })
        set(v) = sp.edit().putString("cap1", v.toJson().toString()).apply()
    var cap2: CaptureSettings
        get() = CaptureSettings.fromJson(sp.getString("cap2", null)?.let { JSONObject(it) } ?: JSONObject().put("fps", 60.0).put("shutter", "auto"))
        set(v) = sp.edit().putString("cap2", v.toJson().toString()).apply()
}
