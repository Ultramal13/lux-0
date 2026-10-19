package cl.lux0.ui

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
private fun Dialog(title: String, onDismiss: () -> Unit, confirm: String, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = p.body,
        title = { Text(title, style = mono(14, p.text, true)) },
        text = content,
        confirmButton = { Text(confirm, style = mono(13, p.accent, true), modifier = Modifier.clickable(onClick = onConfirm).padding(8.dp)) },
        dismissButton = { Text("cancelar", style = mono(13, p.text), modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)) },
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
            Text("+ nueva", style = mono(12, p.accent, true),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.key).clickable { newSession = true }.padding(horizontal = 10.dp, vertical = 7.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} mediciones", style = mono(11, p.text))
            Spacer(Modifier.weight(1f))
            Text("exportar csv ↗", style = mono(12, p.accent, true), modifier = Modifier.clickable {
                val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                val f = File(dir, "lux0-${active.name.replace(Regex("[^a-zA-Z0-9-]"), "_")}.csv")
                f.writeText(toCsv(vm.store.measurements(active.id), sessions.associate { it.id to it.name }))
                val uri = FileProvider.getUriForFile(ctx, "cl.lux0.files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "exportar csv"))
            }.padding(6.dp))
        }
        if (list.isEmpty()) Text("sin mediciones en esta sesión. mide y toca ● guardar.", style = mono(11, p.text), modifier = Modifier.padding(top = 12.dp))
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
                        Text(m.comment.ifBlank { "toca para comentar" }, style = mono(11, if (m.comment.isBlank()) p.screenDim else p.accent))
                    }
                }
            }
        }
    }

    if (newSession) {
        var name by remember { mutableStateOf("") }
        Dialog("nueva sesión", { newSession = false }, "crear", { vm.store.newSession(name); newSession = false; vm.bump() }) {
            Field(name, { name = it }, 240, hint = "videoclip · día 1 · interior")
        }
    }
    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.comment) }
        Dialog("comentario", { editing = null }, "guardar", { vm.store.updateComment(m.id, text); editing = null; vm.bump() }) {
            Field(text, { text = it }, 260)
        }
    }
    deleting?.let { m ->
        Dialog("¿borrar medición de ${m.cct.roundToInt()} k?", { deleting = null }, "borrar", { vm.store.delete(m.id); deleting = null; vm.bump() }) {}
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
        Text(label, style = mono(12, p.text), modifier = Modifier.width(96.dp))
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
        Picker("obturación", c.shutter, CaptureOptions.SHUTTER, { it }) { set(c.copy(shutter = it)) }
        Picker("diafragma", c.fStop, CaptureOptions.F_STOP, CaptureOptions::fLabel) { set(c.copy(fStop = it)) }
        Picker("filtro nd", c.nd, CaptureOptions.ND.map { it.first }, CaptureOptions::ndLabel) { set(c.copy(nd = it)) }
        if (vm.cam(slot).isoMax == null) {
            Text("iso máximo de esta cámara sin verificar", style = mono(9, LocalPalette.current.text.copy(alpha = 0.6f)),
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun SettingsPanel(vm: MeterViewModel, st: UiState, modifier: Modifier) {
    val p = LocalPalette.current
    var custom by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("* datos sin verificar con el fabricante", style = mono(9, p.text.copy(alpha = 0.6f)))
        Text("+ nueva cámara personalizada", style = mono(12, p.accent, true),
            modifier = Modifier.clickable { custom = true }.padding(vertical = 8.dp))
        SettingRow("formato del tinte") {
            Seg(listOf("unidades cámara", "duv"), if (vm.prefs.tintFormat == "duv") 1 else 0,
                { vm.prefs.tintFormat = if (it == 1) "duv" else "camara"; vm.bump() })
        }
        SettingRow("reflectancia de la superficie (%)") {
            var r by remember { mutableStateOf(vm.prefs.reflectance.toString()) }
            Field(r, { r = it.filter(Char::isDigit).take(2); r.toIntOrNull()?.let { v -> vm.prefs.reflectance = v; vm.bump() } }, 70, true)
        }
        SettingRow("intervalo entre mediciones") {
            Seg(listOf("0,5 s", "1 s", "2 s"), INTERVALS.indexOf(vm.prefs.intervalMs), { vm.setInterval(INTERVALS[it]) })
        }
        SettingRow("cámara del celular") {
            Seg(listOf("trasera", "frontal"), if (vm.prefs.frontCamera) 1 else 0, { vm.prefs.frontCamera = it == 1; vm.bump() })
        }
        SettingRow("red eléctrica") {
            Seg(listOf("50 hz", "60 hz"), if (vm.prefs.mainsHz == 60) 1 else 0, { vm.prefs.mainsHz = if (it == 1) 60 else 50; vm.bump() })
        }
        SettingRow("tema") {
            val t = listOf("auto", "claro", "oscuro", "rojo")
            Seg(t, t.indexOf(st.theme), { vm.setTheme(t[it]) })
        }
        SettingRow("calibración (${if (st.calOffset == 0.0) "sin calibrar" else "calibrado"})") {
            Seg(listOf("calibrar →"), -1, { vm.setMode(9) })
        }
        Spacer(Modifier.height(12.dp))
    }

    if (custom) CustomCameraDialog(vm) { custom = false }
}

@Composable
fun CustomCameraDialog(vm: MeterViewModel, onDone: () -> Unit) {
        var name by remember { mutableStateOf("") }
        var kMin by remember { mutableStateOf("2500") }
        var kMax by remember { mutableStateOf("10000") }
        var step by remember { mutableStateOf("100") }
        var tint by remember { mutableStateOf(0) }
        Dialog("cámara personalizada", onDone, "guardar", {
            val lo = kMin.toIntOrNull(); val hi = kMax.toIntOrNull(); val s = step.toIntOrNull()
            if (name.isNotBlank() && lo != null && hi != null && s != null && lo < hi && s > 0) {
                val parts = name.trim().split(" ", limit = 2)
                vm.addCustomCamera(CameraProfile(
                    "custom-" + System.currentTimeMillis(), parts[0], parts.getOrElse(1) { "" }, lo, hi, s,
                    if (tint == 1) TintSystem.CANON_GM9 else TintSystem.NONE, true, "personalizada",
                ))
                onDone()
            }
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(name, { name = it }, 240, hint = "marca modelo")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Field(kMin, { kMin = it }, 70, true, "mín k"); Field(kMax, { kMax = it }, 70, true, "máx k"); Field(step, { step = it }, 60, true, "paso")
                }
                Seg(listOf("sin tinte", "tinte g/m ±9"), tint, { tint = it })
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
                        Text("  cámara $slot: ${cam.label}${if (!cam.verified) " *" else ""}  ▾", style = mono(13, p.keyText, true),
                            modifier = Modifier.clickable { open = true }.padding(vertical = 4.dp))
                        DropdownMenu(open, { open = false }, containerColor = p.body) {
                            vm.cameras.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.label + if (!c.verified) " *" else "", style = mono(13, p.text, c.id == cam.id)) },
                                    onClick = { vm.selectCam(slot, c.id); open = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ cámara personalizada…", style = mono(13, p.accent, true)) },
                                onClick = { open = false; custom = true },
                            )
                        }
                    }
                }
                // Resumen de captura; al tocarlo se edita en una ventana con espacio para el teclado.
                val cap = if (slot == 1) vm.prefs.cap1 else vm.prefs.cap2
                Text(
                    "${cap.iso} iso · ${CaptureOptions.fpsLabel(cap.fps)}p · ${cap.shutter} · ${CaptureOptions.fLabel(cap.fStop)} · ${cap.nd.ifBlank { "sin nd" }}  ✎",
                    style = mono(12, p.keyText),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { editing = slot }.padding(vertical = 4.dp),
                )
            }
        }
        Text("* datos sin verificar con el fabricante", style = mono(9, p.text.copy(alpha = 0.6f)))
    }
    if (custom) CustomCameraDialog(vm) { custom = false }
    if (editing != 0) {
        val slot = editing
        Dialog("captura cámara $slot · ${vm.cam(slot).label}", { editing = 0; vm.bump() }, "listo", { editing = 0; vm.bump() }) {
            CaptureEditor(vm, slot)
        }
    }
}
