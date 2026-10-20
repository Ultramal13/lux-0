package cl.lux0

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lux0.ui.BlackScreen
import cl.lux0.ui.CalibrationPanel
import cl.lux0.ui.CameraCards
import cl.lux0.ui.Claro
import cl.lux0.ui.Header
import cl.lux0.ui.HelpPanel
import cl.lux0.ui.HistoryPanel
import cl.lux0.ui.KeyGrid
import cl.lux0.ui.SourceRow
import cl.lux0.ui.ReflectanceRow
import cl.lux0.ui.LuxPanel
import cl.lux0.ui.ComparePanel
import cl.lux0.ui.FlickerInfoRow
import cl.lux0.ui.LocalPalette
import cl.lux0.ui.MeterViewModel
import cl.lux0.ui.NocheRojo
import cl.lux0.ui.Oscuro
import cl.lux0.ui.SettingsPanel
import cl.lux0.ui.WideKey
import cl.lux0.ui.mono

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(vm: MeterViewModel = viewModel()) {
    val st by vm.state.collectAsState()
    val dark = isSystemInDarkTheme()
    val palette = when (st.theme) {
        "claro" -> Claro; "oscuro" -> Oscuro; "rojo" -> NocheRojo
        else -> if (dark) Oscuro else Claro
    }
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); vm.checkIdle() }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        Column(Modifier.fillMaxSize().background(palette.body)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); vm.touch() } }
            }
            .safeDrawingPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Header(st.mode, st.theme, vm::cycleTheme) { vm.setMode(10) }
            val meter = st.mode in listOf(1, 2, 4, 5, 9)
            if (meter) {
                val fill = st.mode in listOf(1, 2, 4, 5)
                // En color la pantalla negra se estira (el visor llena el espacio libre); en calibración no.
                val screenMod = if (fill) Modifier.weight(1f) else Modifier
                if (granted) BlackScreen(vm, st, screenMod, fill) { textureView = it }
                else Box(screenMod.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).background(palette.screen).padding(16.dp)) {
                    Text(tr("lux-0 necesita la cámara para medir.\nvuelve a abrir la app para dar el permiso.", "lux-0 needs the camera to measure.\nreopen the app to grant permission."), style = mono(13, palette.screenText))
                }
                if (fill) {
                    when (st.mode) {
                        2 -> { ReflectanceRow(vm, st.version); CameraCards(vm, st.version) }
                        4 -> { FlickerInfoRow(vm); CameraCards(vm, st.version) }
                        5 -> ComparePanel(vm, st)
                        else -> { SourceRow(st, vm::setSource); CameraCards(vm, st.version) }
                    }
                } else Box(Modifier.weight(1f)) { CalibrationPanel(vm, st) }
            } else if (st.mode == 3) {
                LuxPanel(vm, st, Modifier.weight(1f))
            } else if (st.mode == 10) {
                HelpPanel(Modifier.weight(1f))
            } else if (st.mode == 7) {
                HistoryPanel(vm, st.version, Modifier.weight(1f))
            } else {
                SettingsPanel(vm, st, Modifier.weight(1f))
            }
            // Con el teclado abierto se ocultan las teclas para que no se encimen con lo que se escribe.
            if (!WindowInsets.isImeVisible) {
            Spacer(Modifier.height(8.dp))
            KeyGrid(st.mode) { vm.setMode(it) }
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WideKey(if (st.hold) "■ hold" else "hold", false, Modifier.weight(1f)) { vm.toggleHold() }
                WideKey(tr("● guardar", "● save"), true, Modifier.weight(1f)) {
                    vm.save(textureView?.takeIf { it.isAvailable }?.getBitmap(256, 256))
                }
            }
            }
        }
    }
}
