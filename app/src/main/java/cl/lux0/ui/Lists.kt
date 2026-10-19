package cl.lux0.ui

import cl.lux0.tr
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cl.lux0.color.CameraProfile
import cl.lux0.color.TintSystem
import cl.lux0.data.CaptureOptions
import cl.lux0.data.CaptureSettings
import cl.lux0.data.Measurement
import cl.lux0.data.toCsv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun Field(value: String, onChange: (String) -> Unit, width: Int, number: Boolean = false, hint: String = "") {
    val p = LocalPalette.current
    Box(Modifier.width(width.dp).clip(RoundedCornerShape(7.dp)).background(p.key).padding(8.dp)) {
        if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = mono(12, p.keyText.copy(alpha = 0.4f)))
        BasicTextField(
            value, onChange, singleLine = true, textStyle = mono(12, p.keyText), cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        )
    }
}

@Composable
private fun Dialog(title: String, onDismiss: () -> Unit, confirm: String, onConfirm: () -> Unit, showCancel: Boolean = true, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.body,
        title = { Text(title, style = mono(14, p.text, true)) },
        text = content,
        confirmButton = { Text(confirm, style = mono(13, p.accent, true), modifier = Modifier.clickable(onClick = onConfirm).padding(8.dp)) },
        dismissButton = if (!showCancel) null else { { Text(tr("cancelar", "cancel"), style = mono(13, p.text), modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)) } },
    )
}

@Composable
fun HistoryPanel(vm: MeterViewModel, version: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val active = remember(version) { vm.store.activeSession() }
    val sessions = remember(version) { vm.store.sessions() }
    val list = remember(version) { vm.store.measurements(active.id) }
    var newSession by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Measurement?>(null) }
    var deleting by remember { mutableStateOf<Measurement?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Seg(sessions.map { it.name }, sessions.indexOfFirst { it.id == active.id }, { vm.store.setActive(sessions[it].id); vm.bump() })
            Text(tr("+ nueva", "+ new"), style = mono(12, p.accent, true),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.key).clickable { newSession = true }.padding(horizontal = 10.dp, vertical = 7.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} " + tr("mediciones", "readings"), style = mono(11, p.text))
            Spacer(Modifier.weight(1f))
            Text(tr("exportar csv ↗", "export csv ↗"), style = mono(12, p.accent, true), modifier = Modifier.clickable {
                val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                val f = File(dir, "lux0-${active.name.replace(Regex("[^a-zA-Z0-9-]"), "_")}.csv")
                f.writeText(toCsv(vm.store.measurements(active.id), sessions.associate { it.id to it.name }))
                val uri = FileProvider.getUriForFile(ctx, "cl.lux0.files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, tr("exportar csv", "export csv")))
            }.padding(6.dp))
        }
        if (list.isEmpty()) Text(tr("sin mediciones en esta sesión. mide y toca ● guardar.", "no readings in this session. measure and tap ● save."), style = mono(11, p.text), modifier = Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.screen)
                        .combinedClickable(onClick = { editing = m }, onLongClick = { deleting = m }).padding(8.dp),
                ) {
                    val bmp = remember(m.thumbPath) { m.thumbPath?.let { BitmapFactory.decodeFile(it) } }
                    if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(6.dp)))
                    else Box(Modifier.size(58.dp).clip(RoundedCornerShape(6.dp)).background(p.screenDim))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text("${m.cct.roundToInt()} k  ${String.format(Locale.US, "%+.3f", m.duv)}  · ${fmt.format(Date(m.time))}", style = mono(13, p.screenText, true))
                        Text("① ${m.cam1}: ${m.rec1}", style = mono(10, p.screenText))
                        Text("② ${m.cam2}: ${m.rec2}", style = mono(10, p.screenText))
                        Text("${m.source} · ${m.cap1.label}", style = mono(9, p.screenDim))
                        Text(m.comment.ifBlank { tr("toca para comentar", "tap to add a comment") }, style = mono(11, if (m.comment.isBlank()) p.screenDim else p.accent))
                    }
                }
            }
        }
    }

    if (newSession) {
        var name by remember { mutableStateOf("") }
        Dialog(tr("nueva sesión", "new session"), { newSession = false }, tr("crear", "create"), { vm.store.newSession(name); newSession = false; vm.bump() }) {
            Field(name, { name = it }, 240, hint = tr("videoclip · día 1 · interior", "music video · day 1 · interior"))
        }
    }
    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.comment) }
        Dialog(tr("comentario", "comment"), { editing = null }, tr("guardar", "save"), { vm.store.updateComment(m.id, text); editing = null; vm.bump() }) {
            Field(text, { text = it }, 260)
        }
    }
    deleting?.let { m ->
        Dialog(tr("¿borrar medición de ", "delete reading of ") + "${m.cct.roundToInt()} k?", { deleting = null }, tr("borrar", "delete"), { vm.store.delete(m.id); deleting = null; vm.bump() }) {}
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = mono(11, p.text))
        Box(Modifier.padding(top = 4.dp)) { content() }
    }
}

/** Una fila "etiqueta  valor ▾" que abre una lista para elegir; no se escribe nada. */
@Composable
private fun <T> Picker(label: String, value: T, options: List<T>, show: (T) -> String, onPick: (T) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = mono(12, p.text), modifier = Modifier.width(100.dp))
        Box {
            Text("${show(value)}  ▾", style = mono(14, p.keyText, true),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.key)
                    .clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            DropdownMenu(open, { open = false }, containerColor = p.body,
                modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(show(o), style = mono(14, if (o == value) p.accent else p.text, o == value)) },
                        onClick = { onPick(o); open = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureEditor(vm: MeterViewModel, slot: Int) {
    var c by remember(slot) { mutableStateOf(if (slot == 1) vm.prefs.cap1 else vm.prefs.cap2) }
    fun set(n: CaptureSettings) { c = n; vm.setCapture(slot, n) }
    val isoList = CaptureOptions.isoFor(vm.cam(slot).isoMax)
    Column {
        Picker("iso", c.iso, isoList, { it.toString() }) { set(c.copy(iso = it)) }
        Picker("fps", c.fps, CaptureOptions.FPS, CaptureOptions::fpsLabel) { set(c.copy(fps = it)) }
        Picker(tr("obturación", "shutter"), c.shutter, CaptureOptions.SHUTTER, { it }) { set(c.copy(shutter = it)) }
        Picker(tr("diafragma", "aperture"), c.fStop, CaptureOptions.F_STOP, CaptureOptions::fLabel) { set(c.copy(fStop = it)) }
        Picker(tr("filtro nd", "nd filter"), c.nd, CaptureOptions.ND.map { it.first }, CaptureOptions::ndLabel) { set(c.copy(nd = it)) }
        Picker(tr("comp. ev", "ev comp."), c.ev, CaptureOptions.EV, CaptureOptions::evLabel) { set(c.copy(ev = it)) }
        if (vm.cam(slot).isoMax == null) {
            Text(tr("iso máximo de esta cámara sin verificar", "max iso of this camera not verified"), style = mono(9, LocalPalette.current.text.copy(alpha = 0.6f)),
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SettingsPanel(vm: MeterViewModel, st: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    var custom by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(tr("* datos sin verificar con el fabricante", "* data not verified with the manufacturer"), style = mono(9, p.text.copy(alpha = 0.6f)))
        Text(tr("+ nueva cámara personalizada", "+ new custom camera"), style = mono(12, p.accent, true),
            modifier = Modifier.clickable { custom = true }.padding(vertical = 8.dp))
        SettingRow(tr("formato del tinte", "tint format")) {
            Seg(listOf(tr("unidades cámara", "camera units"), "duv"), if (vm.prefs.tintFormat == "duv") 1 else 0,
                { vm.prefs.tintFormat = if (it == 1) "duv" else "camara"; vm.bump() })
        }
        SettingRow(tr("reflectancia de la superficie (%)", "surface reflectance (%)")) {
            var r by remember { mutableStateOf(vm.prefs.reflectance.toString()) }
            Field(r, { r = it.filter(Char::isDigit).take(2); r.toIntOrNull()?.let { v -> vm.prefs.reflectance = v; vm.bump() } }, 70, true)
        }
        SettingRow(tr("intervalo entre mediciones", "measuring interval")) {
            Seg(listOf(tr("0,5 s", "0.5 s"), "1 s", "2 s"), INTERVALS.indexOf(vm.prefs.intervalMs), { vm.setInterval(INTERVALS[it]) })
        }
        SettingRow(tr("cámara del celular", "phone camera")) {
            Seg(listOf(tr("trasera", "back"), tr("frontal", "front")), if (vm.prefs.frontCamera) 1 else 0, { vm.prefs.frontCamera = it == 1; vm.bump() })
        }
        SettingRow(tr("red eléctrica", "mains frequency")) {
            Seg(listOf("50 hz", "60 hz"), if (vm.prefs.mainsHz == 60) 1 else 0, { vm.prefs.mainsHz = if (it == 1) 60 else 50; vm.bump() })
        }
        SettingRow(tr("tema", "theme")) {
            val t = listOf("auto", "claro", "oscuro", "rojo")
            Seg(t.map(::themeLabel), t.indexOf(st.theme), { vm.setTheme(t[it]) })
        }
        SettingRow(tr("calibración", "calibration") + " (${if (st.calOffset == 0.0) tr("sin calibrar", "uncalibrated") else tr("calibrado", "calibrated")})") {
            Seg(listOf(tr("calibrar →", "calibrate →")), -1, { vm.setMode(9) })
        }
        SettingRow(tr("ayuda", "help")) {
            Seg(listOf(tr("qué significa cada cosa →", "what each setting means →")), -1, { vm.setMode(10) })
        }
        Spacer(Modifier.height(12.dp))
    }

    if (custom) CustomCameraDialog(vm) { custom = false }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = mono(9, LocalPalette.current.text.copy(alpha = 0.65f)), modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
}

/** Cámara personalizada: solo el nombre se escribe; el resto se elige de listas con su explicación. */
@Composable
fun CustomCameraDialog(vm: MeterViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kMin by remember { mutableStateOf(2500) }
    var kMax by remember { mutableStateOf(10000) }
    var step by remember { mutableStateOf(100) }
    var tint by remember { mutableStateOf(false) }
    var isoMax by remember { mutableStateOf<Int?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    Dialog(tr("cámara personalizada", "custom camera"), onDone, tr("guardar", "save"), {
        err = when {
            name.isBlank() -> tr("escribe marca y modelo", "type brand and model")
            kMin >= kMax -> tr("el k mínimo debe ser menor que el máximo", "min k must be lower than max k")
            else -> null
        }
        if (err == null) {
            val parts = name.trim().split(" ", limit = 2)
            vm.addCustomCamera(CameraProfile(
                "custom-" + System.currentTimeMillis(), parts[0], parts.getOrElse(1) { "" }, kMin, kMax, step,
                if (tint) TintSystem.CANON_GM9 else TintSystem.NONE, true, "personalizada", isoMax,
            ))
            onDone()
        }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Field(name, { name = it }, 240, hint = tr("marca modelo (ej. sony fx3)", "brand model (e.g. sony fx3)"))
            Hint(tr("el nombre que verás en el menú de cámaras", "the name shown in the camera menu"))
            Picker(tr("k mínimo", "min k"), kMin, listOf(2000, 2300, 2500, 2800, 3000, 3200), { "$it k" }) { kMin = it }
            Hint(tr("la temperatura más cálida que acepta la cámara", "the warmest temperature the camera accepts"))
            Picker(tr("k máximo", "max k"), kMax, listOf(7500, 8000, 9000, 9900, 10000, 12000, 15000), { "$it k" }) { kMax = it }
            Hint(tr("la temperatura más fría que acepta la cámara", "the coolest temperature the camera accepts"))
            Picker(tr("paso", "step"), step, listOf(10, 50, 100, 200), { "$it k" }) { step = it }
            Hint(tr("de cuánto en cuánto cambia el kelvin en la cámara; la recomendación se redondea a este paso", "how much the kelvin changes per click on the camera; the recommendation is rounded to this step"))
            Picker(tr("tinte", "tint"), tint, listOf(false, true), { if (it) "g/m ±9" else tr("no tiene", "none") }) { tint = it }
            Hint(tr("si la cámara permite corregir verde/magenta (como el wb shift de canon); si no, solo se muestra ⚠", "whether the camera can correct green/magenta (like canon's wb shift); if not, only ⚠ is shown"))
            Picker(tr("iso máximo", "max iso"), isoMax, listOf<Int?>(null) + CaptureOptions.ISO.filter { it >= 3200 }, { it?.toString() ?: tr("no sé", "don't know") }) { isoMax = it }
            Hint(tr("limita la lista de iso de esta cámara", "limits this camera's iso list"))
            err?.let { Text(it, style = mono(11, LocalPalette.current.accent)) }
        }
    }
}

/** Tarjetas Cámara 1 y 2 de la pantalla de medición: modelo (menú) y ajustes de captura. */
@Composable
fun CameraCards(vm: MeterViewModel, version: Int) {
    val p = LocalPalette.current
    var custom by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2).forEach { slot ->
            val cam = vm.cam(slot)
            var open by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.key).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(p.knobs[slot - 1]),
                        contentAlignment = Alignment.Center) {
                        Text("$slot", style = mono(10, if (p.red) p.screenText else androidx.compose.ui.graphics.Color.Black, true))
                    }
                    Box {
                        Text("  " + tr("cámara", "camera") + " $slot: ${cam.label}${if (!cam.verified) " *" else ""}  ▾", style = mono(13, p.keyText, true),
                            modifier = Modifier.clickable { open = true }.padding(vertical = 4.dp))
                        DropdownMenu(open, { open = false }, containerColor = p.body) {
                            vm.cameras.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.label + if (!c.verified) " *" else "", style = mono(13, p.text, c.id == cam.id)) },
                                    onClick = { vm.selectCam(slot, c.id); open = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(tr("+ cámara personalizada…", "+ custom camera…"), style = mono(13, p.accent, true)) },
                                onClick = { open = false; custom = true },
                            )
                        }
                    }
                }
                // Resumen de captura; al tocarlo se edita en una ventana con espacio para el teclado.
                val cap = if (slot == 1) vm.prefs.cap1 else vm.prefs.cap2
                Text(
                    "${cap.iso} iso · ${CaptureOptions.fpsLabel(cap.fps)}p · ${cap.shutter} · ${CaptureOptions.fLabel(cap.fStop)} · ${cap.nd.ifBlank { tr("sin nd", "no nd") }}${if (cap.ev != 0.0) " · ev " + CaptureOptions.evLabel(cap.ev) else ""}  ✎",
                    style = mono(12, p.keyText),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { editing = slot }.padding(vertical = 4.dp),
                )
            }
        }
        if (!vm.cam(1).verified || !vm.cam(2).verified) {
            Text(tr("* datos sin verificar con el fabricante", "* data not verified with the manufacturer"), style = mono(9, p.text.copy(alpha = 0.6f)))
        }
    }
    if (custom) CustomCameraDialog(vm) { custom = false }
    if (editing != 0) {
        val slot = editing
        Dialog(tr("captura cámara", "capture camera") + " $slot · ${vm.cam(slot).label}", { editing = 0; vm.bump() }, tr("listo", "done"), { editing = 0; vm.bump() }, showCancel = false) {
            CaptureEditor(vm, slot)
        }
    }
}
