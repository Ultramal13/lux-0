# AGENTS.md — LUX-0

Instrucciones para cualquier asistente de IA que trabaje en este proyecto.

## Qué es
LUX-0 es una app Android nativa que usa la cámara del celular como medidor de luz.
- Mide la temperatura de color (K) y el tinte (Duv).
- Traduce el resultado a los ajustes de balance de blancos de cámaras externas (Canon R5, Insta360 Luna Ultra y otras).

Antes de cambiar algo, lee:
- `docs/2026-10-19-lux0-diseno.md`: diseño aprobado. Las secciones 8b–8d tienen decisiones y cambios posteriores.
- `docs/superpowers/plans/2026-10-19-lux0-etapa1.md`: plan de la etapa 1.
- `COMO-FUNCIONA.txt`: resumen técnico.

## Reglas del proyecto (obligatorias)
- **Todo lo visible en español**: textos de la app, mensajes de commit y respuestas. En la app, los textos van en minúscula con estilo de aparato ("01 color", "hold", "● guardar").
- **Seguridad del teléfono (Samsung S26 Ultra, SM-S948B, Android 17).** Por ADB solo se permite:
  - `adb install -r`
  - `adb uninstall cl.lux0`
  - `adb shell am start` / `am force-stop cl.lux0`
  - logcat
  - `screencap`
  - lecturas de `dumpsys` (thermalservice, battery, media.camera)

  Nada de root, flasheo, bootloader, `settings put` ni cambios de sistema. Si hay riesgo, entrega el APK como archivo.
- **No instales ni pruebes en el teléfono sin que el titular lo pida.** El emulador sí se puede usar libremente.
- **Calor:** la v0.1, que capturaba RAW a resolución completa cada segundo, calentó el teléfono. No vuelvas a capturas RAW o de resolución completa periódicas. Mantén el flujo YUV 640x480, los fps bajos, la protección térmica, la pausa automática y que hold apague la cámara.
- **Diseño propio:** no copies el aspecto, nombres ni marcas de otras apps o medidores.
- **Datos de cámaras:** cada modelo de `app/src/main/assets/cameras.json` debe venir del manual o de una fuente oficial. Si no se pudo verificar, va con `"verified": false` (se muestra con `*`). No inventes rangos.
- **Captura sin escribir:** ISO, fps, obturación, f/ y ND se eligen de listas (`CaptureOptions.kt`), sin texto libre.

## Entorno (Mac Apple Silicon)
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
ADB=$ANDROID_HOME/platform-tools/adb
```
- SDK: compileSdk 37.2, targetSdk 36, minSdk 31. AGP 9.4.1, Kotlin integrado (+ plugin compose 2.4.21), Gradle 9.8.1 (wrapper incluido).
- Emulador: AVD `lux0_test` (Android 17, arm64). Arrancarlo con:
  ```bash
  $ANDROID_HOME/emulator/emulator -avd lux0_test -no-snapshot-save -no-boot-anim -gpu host
  ```
  En el emulador la app entra sola en **modo demo** (lecturas simuladas, marcadas "● demo"). En un teléfono real nunca.

## Comandos
```bash
./gradlew :app:testDebugUnitTest      # pruebas (JUnit, 19 hoy)
./gradlew :app:assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=emulator-5554 $ADB install -r app/build/outputs/apk/debug/app-debug.apk
```
Corre las pruebas y compila antes de cada commit. Haz commits pequeños, con mensaje en español.

## Mapa del código (`app/src/main/java/cl/lux0/`)
- `color/ColorMath.kt`: xy → CCT/Duv (Krystek + búsqueda en mired) y neutral de cámara → xy (método DNG).
- `color/CameraProfile.kt`: perfiles de cámara, recomendación por cámara y tinte Canon (≈0.0025 Duv por paso).
- `color/Calibration.kt`: calibración de un punto, en mired.
- `camera/MeterCamera.kt`: Camera2 con un solo hilo propio. Usa preview + YUV con AWB apagado, matriz identidad y curva lineal.
- `camera/YuvRoi.kt`: promedio RGB del círculo (`ROI_FRACTION`, el mismo radio que dibuja el visor).
- `data/`: modelos, `Store` (JSON en filesDir), `Prefs`, CSV y `CaptureOptions`.
- `ui/`: tema y paletas (claro/oscuro/noche rojo), teclas, pantalla de medición, sesiones, ajustes, calibración y `MeterViewModel` (estado, protección térmica, pausa y demo).

## Estado (2026-10-19)
- **Etapa 1 lista:** color, cámaras 1/2 con captura, sesiones (miniatura, comentario, CSV), temas, calibración, protección térmica y modo demo.
- **Pendiente:**
  1. Prueba de calor en el teléfono comparada con la app Cámara de Samsung.
  2. Validar que el HAL de Samsung respeta la matriz identidad y la curva lineal (calibrando con RAW de la R5).
  3. ISO máximo y rango de Kelvin de la Luna Ultra (sin verificar).
- **Etapa 2:** exposición, lux, parpadeo, comparar → gel, mapa de luces mezcladas y objetivo. Las perillas (`ui/Controls.kt`, `Knob`) vuelven en Exposición.
