package cl.lux0.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Ayuda: qué significa cada parámetro y en qué influye. */
private val HELP_ES = listOf(
    "kelvin (k)" to
        "temperatura de color de la luz. número bajo = luz cálida/naranja (vela ~1900 k, ampolleta ~2700–3200 k). " +
        "número alto = luz fría/azul (sol ~5600 k, nublado ~6500 k, sombra 7500 k o más). " +
        "es el valor que pones en el balance de blancos manual de tu cámara.",
    "tinte (duv)" to
        "cuánto se aleja la luz del blanco 'natural' hacia verde (+) o magenta (−). " +
        "fluorescentes y led baratos suelen tener tinte. entre −0.003 y +0.003 casi no se nota. " +
        "sobre 0.02 la app avisa \"luz muy teñida\".",
    "cómo leer la recomendación" to
        "ejemplo: \"4400–5000 · 4700 · ≈g2\". 4400–5000 = rango razonable según la precisión de la medición. " +
        "4700 = valor sugerido, ya redondeado a los pasos de esa cámara. " +
        "≈g2 = en canon, wb shift 2 pasos hacia verde para compensar una luz magenta (≈ porque la equivalencia es aproximada). " +
        "⚠ = la luz tiene tinte y esa cámara no lo puede corregir.",
    "visor" to
        "es solo para apuntar: la medición usa su propia imagen y promedia lo que está dentro del círculo. " +
        "ocupa todo el espacio libre de la pantalla para apuntar cómodo. está en gris a propósito: " +
        "la imagen va sin corrección de color para medir bien y en color se vería verdosa.",
    "fuente de medición" to
        "blanco/gris: apunta el círculo a una hoja blanca o tarjeta gris bajo la luz (lo más preciso). " +
        "difusor→luz: pon un papel o difusor blanco sobre el lente y apunta hacia la luz desde donde está el sujeto. " +
        "escena ≈: sin referencia; solo estimado (±500 k o más) porque depende del color de lo que mires. si hay una lámpara o ventana quemada en el círculo, se ignora y se mide con el resto.",
    "avisos" to
        "zona quemada: el círculo está demasiado brillante, aléjate o cambia el ángulo. " +
        "muy oscuro: falta luz en el círculo. no parece neutro: lo que apuntas tiene color propio o la luz está muy teñida.",
    "asistente de exposición (tecla 2)" to
        "propone la combinación completa obturación · diafragma · iso para cada cámara. usa la luz medida, el diafragma, el nd, el lente y la estabilización de la tarjeta, " +
        "más lo que elijas: qué apuntas, foto o video, sujeto (fijo, persona, niños/mascotas, deporte, vehículos/aves), a mano o trípode, e intención (clave baja, proteger altas, normal, abrir sombras, clave alta). " +
        "en foto, la obturación más lenta es la más exigente entre el sujeto y el pulso: 1/(focal × formato del sensor), con los pasos de estabilización a favor. " +
        "la focal se elige tal cual dice el lente: la app aplica sola el formato (full frame, aps-c, micro 4/3) de esa cámara. " +
"en video manda la obturación de la tarjeta (180°) y el asistente resuelve el iso o el nd. si no alcanza, te dice cuántos pasos faltan o sobran y qué hacer. " +
        "iso: si eliges un valor en la tarjeta queda fijo y el asistente ajusta el resto (y te dice a qué iso subir o bajar si no alcanza); con iso auto lo elige el asistente.",
    "exposición (tecla 2)" to
        "el teléfono mide el círculo y la app calcula el ev de la escena (a iso 100). con los ajustes de cada tarjeta de cámara " +
        "(iso, obturación, nd, ev) te dice qué diafragma usar, o si ya fijaste uno, cuántos pasos sobran o faltan. " +
        "elige \"qué apuntas\": gris 18 % o blanco 90 %. con obturación auto (como la luna) la cámara decide: ajusta con comp. ev. " +
        "es medición reflejada: ≈ ±⅓–½ paso tras calibrar.",
    "lux (tecla 3)" to
        "usa el sensor de luz del frente del teléfono (el del brillo automático): apunta la pantalla hacia la luz. " +
        "muestra lux y pie-candela. es aproximado y sirve para comparar luces; la cámara queda apagada.",
    "parpadeo (tecla 4)" to
        "detecta luces que pulsan (led, fluorescentes, dimmers): la cámara usa 1/2000 s y lee las bandas del rolling shutter. " +
        "muestra la frecuencia (100 hz con red de 50 hz, 120 hz con 60 hz) y para cada cámara dice si su obturación es segura " +
        "o cuál usar (como velocidad y ángulo) según sus fps. al salir de esta pantalla la cámara vuelve a la exposición normal.",
    "comparar (tecla 5)" to
        "mide la luz a (botón medir a), luego la b sobre la misma hoja. te da la diferencia en mired y tinte, " +
        "el gel para que b iguale a a (cto/ctb y ≈ plus/minus green) o el valor en k para un panel regulable.",
    "mapa (tecla 6)" to
        "divide la imagen en celdas y colorea cada una según su luz comparada con la referencia (el objetivo si lo fijaste; si no, la luz del círculo): " +
        "naranja = más cálida, azul = más fría, gris = sin dato. sirve para ver de un vistazo luces mezcladas (ventana fría, lámpara cálida). " +
        "depende del color de las superficies: es una guía ≈.",
    "temperatura objetivo" to
        "en ajustes eliges la temperatura que quieres (ej. 5600 k). en color aparece \"◎ objetivo\" con el gel para llevar la luz medida a ese valor.",
    "ajustes de calibración de exposición y lux" to
        "si comparas con tu cámara o un fotómetro y la app se desvía siempre igual, corrige con \"ajuste de exposición\" (en pasos) " +
        "y \"factor del sensor de luz\".",
    "reflectancia (%)" to
        "cuánta luz devuelve la superficie que mides: tarjeta gris 18 %, papel blanco ~90 %. " +
        "no cambia el kelvin; sí cambia la exposición sugerida.",
    "intervalo" to
        "cada cuánto se calcula una lectura (0,5 / 1 / 2 s). más largo = menos trabajo y menos calor. " +
        "si el teléfono se entibia, la app sube sola a 3 s.",
    "cámara 1 y cámara 2" to
        "las dos cámaras para las que quieres la recomendación. se eligen en el menú ▾ de cada tarjeta. " +
        "* = datos de esa cámara sin verificar con el fabricante (revisa en su menú).",
    "datos de una cámara" to
        "k mínimo / k máximo: rango de temperatura que acepta. paso: de cuánto en cuánto cambia (canon r5: 100 k); " +
        "la recomendación se redondea a ese paso. tinte: si tiene corrección verde/magenta. " +
        "iso máximo: limita la lista de iso de esa cámara. el tinte depende de la marca: canon, nikon y sony usan eje g/m (≈g2, ≈m0.5); " +
        "fujifilm usa r y b (≈r-2 b-2 = hacia verde); blackmagic usa un número de tint; panasonic solo indica la dirección (→g / →m). " +
        "dji no aparece porque sus manuales no publican el rango de kelvin.",
    "captura (iso, fps, obturación, f/, nd, ev)" to
        "cómo está configurada tu cámara al medir. se guarda con cada medición y sale en el csv para el colorista. " +
        "comp. ev = compensación de exposición: no cambia el kelvin, pero en cámaras de diafragma fijo con exposición " +
        "automática (como la luna ultra) es el ajuste que controlas para aclarar u oscurecer. " +
        "en la etapa 2 se usará para exposición (qué diafragma o nd usar) y para avisar parpadeo según fps y obturación.",
    "calibración" to
        "corrige la diferencia propia del sensor del celular. foto raw de la hoja blanca con la r5, lees los k con el cuentagotas " +
        "de balance en capture one, lightroom o dpp (canon), mides la misma hoja aquí e ingresas ese valor. usa siempre el mismo programa. sin calibrar el margen es mayor (±300 k).",
    "hold y guardar" to
        "hold congela el número (y apaga la cámara) para copiarlo con calma. guardar lo agrega a la sesión activa con " +
        "hora, miniatura, recomendación y ajustes de captura; tócalo en sesiones para escribir un comentario.",
    "sesiones" to
        "agrupa mediciones por rodaje o locación (+ nueva). exportar csv envía la planilla de la sesión. " +
        "mantén presionada una medición para borrarla.",
    "pausa y calor" to
        "tras 90 s sin tocar la pantalla la cámara se apaga para ahorrar batería; toca para seguir. " +
        "si el teléfono se calienta, la app mide más lento o se detiene hasta que se enfríe.",
    "temas" to
        "◐ cambia entre auto, claro, oscuro y noche rojo. noche rojo sirve en sets a oscuras: no encandila ni ensucia la escena.",
    "aviso y compatibilidad" to
        "lux-0 es una beta gratuita entregada tal cual: las mediciones son orientativas y la usas bajo tu responsabilidad. " +
        "al abrir, la app revisa tu teléfono: completa, liviana (mide cada 2 s y apaga parpadeo y mapa para no exigir equipos básicos) " +
        "o sin cámara compatible (solo lux). el estado se ve en ajustes.",
    "modo demo" to
        "solo en el emulador del computador: lecturas simuladas para revisar la interfaz. en el teléfono nunca se activa.",
)

private val HELP_EN = listOf(
    "kelvin (k)" to
        "the color temperature of the light. low number = warm/orange light (candle ~1900 k, bulb ~2700–3200 k). " +
        "high number = cool/blue light (sun ~5600 k, overcast ~6500 k, shade 7500 k or more). " +
        "it is the value you set in your camera's manual white balance.",
    "tint (duv)" to
        "how far the light drifts from 'natural' white toward green (+) or magenta (−). " +
        "fluorescent tubes and cheap leds often have tint. between −0.003 and +0.003 it is barely visible. " +
        "above 0.02 the app warns \"strongly tinted light\".",
    "reading the recommendation" to
        "example: \"4400–5000 · 4700 · ≈g2\". 4400–5000 = reasonable range given the measurement accuracy. " +
        "4700 = suggested value, already rounded to that camera's steps. " +
        "≈g2 = on canon, wb shift 2 steps toward green to compensate magenta light (≈ because the equivalence is approximate). " +
        "⚠ = the light has a tint this camera cannot correct.",
    "viewfinder" to
        "it is only for aiming: the measurement uses its own image and averages what is inside the circle. " +
        "it fills all the free space on screen so aiming is easy. it is gray on purpose: " +
        "the image has no color correction so it can measure correctly, and in color it would look greenish.",
    "measuring source" to
        "white/gray: aim the circle at a white sheet or gray card under the light (most accurate). " +
        "diffuser→light: put white paper or a diffuser over the lens and point it at the light from the subject's position. " +
        "scene ≈: no reference; only an estimate (±500 k or more) because it depends on the color of what you aim at. a clipped lamp or window in the circle is ignored and the rest is measured.",
    "warnings" to
        "clipped area: the circle is too bright, step back or change the angle. too dark: not enough light in the circle. " +
        "not neutral: what you aim at has its own color, or the light is strongly tinted.",
    "exposure assistant (key 2)" to
        "suggests the full shutter · aperture · iso combination for each camera. it uses the measured light, plus the aperture, nd, lens and stabilization from the card, " +
        "and what you pick: what you aim at, photo or video, subject (still, person, kids/pets, sport, vehicles/birds), handheld or tripod, and intent (low key, protect highlights, normal, open shadows, high key). " +
        "for photos, the slowest shutter is the stricter of the subject and the handheld limit: 1/(focal × sensor format), with stabilization stops in your favor. " +
        "pick the focal length printed on the lens: the app applies that camera's format (full frame, aps-c, micro 4/3) by itself. " +
"for video the card's shutter rules (180°) and the assistant solves iso or nd. if it can't fit, it tells you how many stops you are short or over and what to do. " +
        "iso: a value set in the card stays fixed and the assistant adjusts the rest (and tells you which iso to raise or lower to if it can't fit); with auto iso the assistant picks it.",
    "exposure (key 2)" to
        "the phone meters the circle and the app computes the scene ev (at iso 100). using each camera card's settings " +
        "(iso, shutter, nd, ev) it tells you which aperture to use, or, if you already set one, how many stops you are over or under. " +
        "pick \"what you aim at\": gray 18 % or white 90 %. with auto shutter (like the luna) the camera decides: adjust with ev comp. " +
        "it is reflected metering: ≈ ±⅓–½ stop after calibration.",
    "lux (key 3)" to
        "uses the phone's front light sensor (the auto-brightness one): point the screen at the light. " +
        "shows lux and foot-candles. it is approximate and useful to compare lights; the camera stays off.",
    "flicker (key 4)" to
        "detects pulsing lights (leds, fluorescents, dimmers): the camera uses 1/2000 s and reads the rolling-shutter bands. " +
        "it shows the frequency (100 hz on 50 hz mains, 120 hz on 60 hz) and tells each camera whether its shutter is safe " +
        "or which one to use (as speed and angle) for its fps. when you leave this screen the camera returns to normal exposure.",
    "compare (key 5)" to
        "measure light a (measure a button), then b on the same sheet. it gives the difference in mired and tint, " +
        "the gel to make b match a (cto/ctb and ≈ plus/minus green) or the k value for a dimmable panel.",
    "map (key 6)" to
        "splits the image into cells and colors each one by its light compared with the reference (the target if set; otherwise the light in the circle): " +
        "orange = warmer, blue = cooler, gray = no data. useful to spot mixed lighting at a glance (cool window, warm lamp). " +
        "it depends on surface colors: it is a ≈ guide.",
    "target temperature" to
        "in settings you pick the temperature you want (e.g. 5600 k). in color mode \"◎ target\" shows the gel that brings the measured light to it.",
    "exposure and lux calibration" to
        "if you compare with your camera or a light meter and the app is always off by the same amount, correct it with \"exposure adjustment\" (in stops) " +
        "and \"light sensor factor\".",
    "reflectance (%)" to
        "how much light the measured surface returns: gray card 18 %, white paper ~90 %. " +
        "it does not change kelvin; it does change the suggested exposure.",
    "interval" to
        "how often a reading is computed (0.5 / 1 / 2 s). longer = less work and less heat. " +
        "if the phone warms up, the app switches to 3 s by itself.",
    "camera 1 and camera 2" to
        "the two cameras you want recommendations for. pick them in each card's ▾ menu. " +
        "* = that camera's data is not verified with the manufacturer (check its menu).",
    "camera data" to
        "min k / max k: the temperature range it accepts. step: how much it changes per click (canon r5: 100 k); " +
        "the recommendation is rounded to that step. tint: whether it has green/magenta correction. " +
        "max iso: limits that camera's iso list. tint depends on the brand: canon, nikon and sony use a g/m axis (≈g2, ≈m0.5); " +
        "fujifilm uses r and b (≈r-2 b-2 = toward green); blackmagic uses a tint number; panasonic shows only the direction (→g / →m). " +
        "dji is not listed because its manuals do not publish the kelvin range.",
    "capture (iso, fps, shutter, f/, nd, ev)" to
        "how your camera is set while measuring. it is saved with each reading and goes into the csv for the colorist. " +
        "ev comp. = exposure compensation: it does not change kelvin, but on fixed-aperture cameras with auto exposure " +
        "(like the luna ultra) it is the setting you control to brighten or darken. " +
        "in stage 2 it will be used for exposure (which aperture or nd to use) and for flicker warnings based on fps and shutter.",
    "calibration" to
        "corrects the phone sensor's own bias. shoot a white sheet in raw with your camera, read the k with the white balance " +
        "eyedropper in capture one, lightroom or dpp (canon), measure the same sheet here and enter that value. always use the same program. uncalibrated, the margin is wider (±300 k).",
    "hold and save" to
        "hold freezes the number (and turns the camera off) so you can copy it calmly. save adds it to the active session with " +
        "time, thumbnail, recommendation and capture settings; tap it in sessions to write a comment.",
    "sessions" to
        "group readings by shoot or location (+ new). export csv sends the session spreadsheet. " +
        "long-press a reading to delete it.",
    "pause and heat" to
        "after 90 s without touching the screen the camera turns off to save battery; tap to continue. " +
        "if the phone gets warm, the app measures more slowly or stops until it cools down.",
    "themes" to
        "◐ switches between auto, light, dark and red night. red night is for dark sets: it does not dazzle or spill light on the scene.",
    "disclaimer and compatibility" to
        "lux-0 is a free beta provided as is: measurements are for guidance and you use it at your own risk. " +
        "on launch the app checks your phone: full, lite (measures every 2 s and turns off flicker and map so basic devices are not pushed) " +
        "or no compatible camera (lux only). the status is shown in settings.",
    "demo mode" to
        "only on the computer's emulator: simulated readings to review the interface. it never turns on on a phone.",
)

private val HELP_JA = listOf(
    "ケルビン（k）" to
        "光の色温度。数値が低いほど暖かいオレンジの光（ろうそく 約1900 k、電球 約2700–3200 k）。" +
        "数値が高いほど冷たい青い光（太陽 約5600 k、曇り 約6500 k、日陰 7500 k 以上）。" +
        "カメラのマニュアルホワイトバランスに設定する値です。",
    "ティント（duv）" to
        "光が「自然な」白からグリーン（+）またはマゼンタ（−）へどれだけずれているか。" +
        "蛍光灯や安価な led はティントがあることが多いです。−0.003〜+0.003 ならほとんど目立ちません。" +
        "0.02 を超えると「色かぶりの強い光」と表示されます。",
    "推奨値の読み方" to
        "例：「4400–5000 · 4700 · ≈g2」。4400–5000 = 測定精度から見た妥当な範囲。" +
        "4700 = 推奨値（そのカメラのステップに丸め済み）。" +
        "≈g2 = canon の場合、マゼンタの光を補正するため wb シフトをグリーン側へ 2 ステップ（換算が概算なので ≈）。" +
        "⚠ = 光にティントがあり、このカメラでは補正できません。",
    "ファインダー" to
        "狙いを定めるためだけのものです。測定は独自の画像を使い、円の中を平均します。" +
        "狙いやすいよう画面の空きスペースいっぱいに表示されます。グレーなのは意図的です：" +
        "正しく測定するため色補正をかけていないので、カラーだと緑がかって見えます。",
    "測定方法" to
        "白/グレー：光の下にある白い紙かグレーカードに円を向けます（最も正確）。" +
        "ディフューザー→光：レンズに白い紙かディフューザーを当て、被写体の位置から光に向けます。" +
        "シーン ≈：基準なし。向けたものの色に左右されるため推定値のみ（±500 k 以上）。円の中の白飛びしたランプや窓は除外し、残りを測定します。",
    "警告" to
        "白飛びあり：円の中が明るすぎます。離れるか角度を変えてください。暗すぎます：円の中の光が足りません。" +
        "無彩色ではない：向けたものに色があるか、光の色かぶりが強いです。",
    "露出アシスタント（キー 2）" to
        "各カメラにシャッター · 絞り · iso の組み合わせを提案します。測定した光と、カードの絞り・nd・レンズ・手ぶれ補正、" +
        "そして選んだ内容（測る対象、写真か動画、被写体〈静止・人物・子ども/ペット・スポーツ・乗り物/鳥〉、手持ちか三脚、意図〈ローキー・ハイライト保護・標準・シャドウを明るく・ハイキー〉）を使います。" +
        "写真では、最も遅いシャッターは被写体の条件と手持ちの限界 1/(焦点距離 × センサーフォーマット) の厳しい方で、手ぶれ補正の段数ぶん有利になります。" +
        "焦点距離はレンズに書かれた数値を選んでください。フォーマット（フルサイズ、aps-c、マイクロフォーサーズ）はアプリが自動で反映します。" +
        "動画ではカードのシャッター（180°）を優先し、アシスタントが iso か nd を決めます。合わない場合は何段足りないか・多いかと対処法を表示します。" +
        "iso：カードで値を選ぶと固定され、アシスタントは残りを調整します（合わない場合は上げる・下げる iso を表示）。iso オートではアシスタントが選びます。",
    "露出（キー 2）" to
        "端末が円の中を測光し、アプリがシーンの ev（iso 100）を計算します。各カメラカードの設定" +
        "（iso、シャッター、nd、ev）から使う絞りを、または絞りを設定済みなら何段オーバー・アンダーかを表示します。" +
        "「測る対象」を選んでください：グレー 18 % か白 90 %。シャッターがオートのカメラ（luna など）はカメラが決めるので、露出補正で調整します。" +
        "反射式の測光です：キャリブレーション後で約 ±⅓–½ 段。",
    "lux（キー 3）" to
        "端末前面の照度センサー（明るさ自動調整用）を使います。画面を光に向けてください。" +
        "lux とフートキャンドルを表示します。概算値ですが光の比較に便利です。カメラはオフのままです。",
    "フリッカー（キー 4）" to
        "点滅する光（led、蛍光灯、調光器）を検出します。カメラは 1/2000 秒でローリングシャッターの縞を読み取ります。" +
        "周波数（50 hz 地域は 100 hz、60 hz 地域は 120 hz）を表示し、各カメラのシャッターが安全か、" +
        "fps に合った安全なシャッター（速度と角度）を示します。この画面を離れるとカメラは通常の露出に戻ります。",
    "比較（キー 5）" to
        "光 a を測定（測定 a ボタン）し、次に同じ紙で光 b を測定します。mired とティントの差、" +
        "b を a に合わせるジェル（cto/ctb と ≈ プラス/マイナスグリーン）、または調光パネルの k 値を表示します。",
    "マップ（キー 6）" to
        "画像をセルに分け、基準（目標があれば目標、なければ円の中の光）と比べた光で色分けします：" +
        "オレンジ = 暖かい、青 = 冷たい、グレー = データなし。混在した光（冷たい窓、暖かいランプ）がひと目でわかります。" +
        "表面の色に左右されるので ≈ の目安です。",
    "目標色温度" to
        "設定で目標の色温度（例：5600 k）を選びます。色モードの「◎ 目標」に、測定した光を目標に近づけるジェルが表示されます。",
    "露出と lux のキャリブレーション" to
        "カメラや露出計と比べて常に同じだけずれる場合は、「露出の補正」（段）と" +
        "「照度センサーの係数」で補正してください。",
    "反射率（%）" to
        "測る表面が返す光の割合：グレーカード 18 %、白い紙 約90 %。" +
        "ケルビンは変わりませんが、推奨露出は変わります。",
    "測定間隔" to
        "測定値を計算する頻度（0.5 / 1 / 2 秒）。長いほど負荷と発熱が少なくなります。" +
        "端末が温かくなると、アプリが自動で 3 秒に切り替えます。",
    "カメラ 1 とカメラ 2" to
        "推奨値を出したい 2 台のカメラ。各カードの ▾ メニューで選びます。" +
        "* = そのカメラのデータはメーカー未確認です（カメラのメニューで確認してください）。",
    "カメラのデータ" to
        "最小 k / 最大 k：設定できる色温度の範囲。ステップ：1 クリックで変わる量（canon r5：100 k）。" +
        "推奨値はこのステップに丸められます。ティント：グリーン/マゼンタ補正の有無。" +
        "最大 iso：そのカメラの iso リストを制限します。ティントはブランドで異なります：canon・nikon・sony は g/m 軸（≈g2、≈m0.5）、" +
        "fujifilm は r と b（≈r-2 b-2 = グリーン側）、blackmagic はティントの数値、panasonic は方向のみ（→g / →m）。" +
        "dji はマニュアルにケルビンの範囲が載っていないため掲載していません。",
    "撮影設定（iso、fps、シャッター、f/、nd、ev）" to
        "測定時のカメラの設定です。各測定と一緒に保存され、カラリスト向けの csv にも入ります。" +
        "露出補正：ケルビンは変わりませんが、絞り固定で自動露出のカメラ（luna ultra など）では" +
        "明るさを調整する設定になります。",
    "キャリブレーション" to
        "端末センサーのクセを補正します。白い紙をカメラで raw 撮影し、capture one・lightroom・dpp（canon）の" +
        "ホワイトバランス用スポイトで k を読み取り、同じ紙をここで測定してその値を入力します。毎回同じソフトを使ってください。未キャリブレーションでは誤差が大きくなります（±300 k）。",
    "ホールドと保存" to
        "ホールドは数値を固定し（カメラもオフ）、落ち着いて書き写せます。保存すると、アクティブなセッションに" +
        "時刻・サムネイル・推奨値・撮影設定とともに追加されます。セッション画面でタップするとコメントを書けます。",
    "セッション" to
        "測定を撮影や場所ごとにまとめます（+ 新規）。csv を書き出す でセッションの表を送れます。" +
        "測定を長押しすると削除できます。",
    "一時停止と発熱" to
        "画面に 90 秒触れないと、バッテリー節約のためカメラがオフになります。タップで再開します。" +
        "端末が温かくなると、冷えるまで測定を遅くするか停止します。",
    "テーマ" to
        "◐ でオート・ライト・ダーク・レッドナイトを切り替えます。レッドナイトは暗い現場用で、まぶしくなく、シーンに光を漏らしません。",
    "注意事項と対応状況" to
        "lux-0 は現状のまま提供される無料のベータ版です。測定値は目安で、自己責任でご利用ください。" +
        "起動時に端末を確認します：フル、ライト（2 秒ごとに測定し、ベーシックな端末に負荷をかけないようフリッカーとマップをオフ）、" +
        "または対応カメラなし（lux のみ）。状態は設定で確認できます。",
    "デモモード" to
        "パソコンのエミュレーターでのみ動作：画面確認用のシミュレーション値です。端末では作動しません。",
)

@Composable
fun HelpPanel(modifier: Modifier) {
    val p = LocalPalette.current
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(when (cl.lux0.I18n.code) { "es" -> HELP_ES; "ja" -> HELP_JA; else -> HELP_EN }) { (title, body) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.key).padding(12.dp)) {
                Text(title, style = mono(13, p.keyText, true))
                Text(body, style = mono(11, p.keyText), modifier = Modifier.padding(top = 4.dp))
            }
        }
        // Versión al final, para copiarla al reportar un problema.
        item {
            val v = appVersion(androidx.compose.ui.platform.LocalContext.current)
            Text("lux-0 · v$v · " + cl.lux0.tr("inclúyela al reportar un problema", "include it when reporting a problem"),
                style = mono(11, p.text.copy(alpha = 0.7f)), modifier = Modifier.padding(4.dp))
        }
    }
}
