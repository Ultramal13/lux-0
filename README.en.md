<p align="center">
  <img src="docs/banner.en.svg" alt="LUX-0 — light meter for photo and video" width="100%">
</p>

<p align="center"><a href="README.md">Español</a> · <b>English</b></p>

# LUX-0

**A light meter for photo and video, for Android.** LUX-0 uses the phone's camera as a sensor. It measures the **color temperature (Kelvin)** and the **green/magenta tint** of the light, then tells you what to dial into the manual white balance of **your cameras** (for example a Canon R5 or an Insta360 Luna Ultra).

It does not take pictures. It is a measuring instrument.

> **Status: in development. Stages 1 and 2 are implemented and being tested.**
> It measures and recommends values. Accuracy in real shoots, and battery use and temperature during long sessions, still need to be validated. There is no published release yet.
>
> The app is in **English and Spanish**: it follows the phone's language.

---

## Screenshots

| Measuring (real phone) | Measuring (emulator, demo mode) | Capture settings | Built-in help |
|---|---|---|---|
| <img src="docs/capturas/medir.png" width="200"> | <img src="docs/capturas/emu-demo.png" width="200"> | <img src="docs/capturas/emu-captura.png" width="200"> | <img src="docs/capturas/emu-ayuda.png" width="200"> |

---

## What it does (stage 1)

- **Live Kelvin and tint.** Point the circle at a white sheet or gray card, or measure the light through a diffuser.
- **Recommendation for each camera (Camera 1 and Camera 2).** For example, `4400–5000 · 4700 · ≈g2` gives:
  - a reasonable range
  - the value rounded to that camera's steps
  - the tint correction in that camera's own units (Canon's G/M WB Shift)
- **Camera database:**
  - Canon R5 and Insta360 Luna Ultra, with verified data.
  - Custom cameras: Kelvin range, step, tint, and max ISO.
- **Capture settings per camera,** picked from lists: ISO, fps, shutter, f-stop, ND, and EV compensation.
- **Sessions per shoot.** Each reading stores the time, a thumbnail, a comment, and the settings. Sessions export to **CSV** for the colorist.
- **Calibration** against a reference: a RAW photo of a white sheet taken with your own camera.
- **Themes:** light, dark, and **red night mode** for dark sets.
- **Phone care:**
  - thermal protection
  - automatic pause after 90 s without use
  - a small analysis image at a low frame rate
- **Built-in help** that explains every setting.

## Stage 2 (implemented, being tested)

- **Exposure:** scene EV, plus the suggested aperture (or how many stops over or under) for each camera, based on its ISO, shutter, ND, and EV.
- **Lux:** from the phone's front light sensor.
- **Flicker:** detects 100/120 Hz from rolling-shutter bands at 1/2000 s, and tells you which shutter is safe for each camera's fps.
- **Two-light comparison:** the difference in mired and tint, plus the suggested gel (CTO, CTB, ≈ Plus/Minus Green) or a panel value.
- **Mixed-light map:** a grid over the viewfinder, with warmer areas in orange and cooler areas in blue.
- **Target temperature:** with the gel needed to reach it.

---

## How it measures

1. Takes a small (640×480) image from the camera's viewfinder stream at a low frame rate.
2. **Turns off the phone's color processing**: auto white balance off, an identity color matrix, and a linear tone curve. This way the values are the sensor's own.
3. Averages the color of the center circle, and warns if the area is clipped, too dark, or not neutral.
4. Converts the sensor RGB to **CIE 1931 xy**, using the sensor's own factory color calibration matrices (the same method as the DNG format).
5. Computes the **CCT**, the closest point on the Planckian locus (Krystek 1985 approximation, in CIE 1960 uv), and the **Duv**.
6. Translates the result for each camera, based on its range, its steps, and its tint system.

Everything relies on public colorimetry standards. More detail in [`HOW-IT-WORKS.txt`](HOW-IT-WORKS.txt).

**Expected accuracy** (estimated, still being validated):

| Mode | Accuracy |
|---|---|
| White sheet or diffuser, calibrated | ±150–300 K |
| Scene mode | ±500 K or more |

## Technology

- Native Android · **Kotlin** · **Jetpack Compose** · **Camera2**
- Gradle 9 + Android Gradle Plugin 9 · JDK 21 · compileSdk 37 · minSdk 31 (Android 12)
- No third-party libraries or external services. Data stays on the phone.
- JUnit unit tests for the colorimetry, the image reading, the CSV export, and the capture lists.

## Build

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug       # APK in app/build/outputs/apk/debug/
```

Create a `local.properties` file with `sdk.dir=/path/to/android-sdk`.

On the Android emulator, the app switches to **demo mode**. The readings are simulated and labeled as such, because the virtual camera cannot measure color.

## Project documents (in Spanish)

- [`docs/2026-10-19-lux0-diseno.md`](docs/2026-10-19-lux0-diseno.md): design and decisions.
- [`docs/superpowers/plans/2026-10-19-lux0-etapa1.md`](docs/superpowers/plans/2026-10-19-lux0-etapa1.md): stage 1 plan.
- [`AGENTS.md`](AGENTS.md): guide for picking the project up with an AI coding assistant.

---

<sub>Original "handheld device" design. The icon is the digit **0** in Morse code (−−−−−) drawn as a ring, with a point of light in the center.</sub>
