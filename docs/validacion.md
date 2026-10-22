# Validación de LUX-0 · Validation log

Comparaciones reales de LUX-0 contra una referencia. Los betatesters pueden sumar las suyas con el formulario **[medición / sugerencia](https://github.com/Ultramal13/lux-0/issues/new/choose)**.

*Real-world comparisons of LUX-0 against a reference. Beta testers can add theirs with the **measurement / suggestion** form.*

---

## Teléfonos probados · Tested phones

| Fecha | Teléfono | Android | Versión | Resultado | Nota |
|---|---|---|---|---|---|
| 2026-10-20 | Samsung Galaxy S26 Ultra | 17 | v0.2–v0.3.1 | Todas las herramientas funcionan | Equipo de desarrollo |
| 2026-10-21 | Sony Xperia 1 III | 13 | v0.3.x | Abre y mide en vivo (exposición: 14,3 EV en exterior, interfaz en inglés) | El permiso de cámara no se pidió al instalar: se activó a mano en Ajustes |

## Color (Kelvin)

| Fecha | Teléfono | Luz | Referencia | LUX-0 sin calibrar | Diferencia | Nota |
|---|---|---|---|---|---|---|
| 2026-10-20 | Samsung Galaxy S26 Ultra | Hoja blanca, luz fría (~6700 K) | Canon R5, RAW, cuentagotas de Capture One: **6739 K** | **6016 K** | −723 K · **−17,8 mired** (el teléfono lee más cálido) | Se usó como calibración de un punto |

**Cómo leerlo:**
- A temperaturas altas, unos pocos mired equivalen a cientos de Kelvin. 17,8 mired es menos que un gel ⅛ CTB (≈ 12–20 mired).
- La calibración corrige en mired, así que se aplica de forma proporcional en todo el rango. Por ejemplo, 3200 K medidos pasan a ≈ 3390 K con esta corrección.
- Cada programa de revelado (Capture One, Lightroom, DPP) calcula los K con su propio perfil de cámara, así que conviene comparar siempre con el mismo.

### Pendiente
- [ ] Comprobar la calibración con una luz cálida (ampolleta o tungsteno), comparando de nuevo con la R5 + Capture One.
- [ ] Mediciones en otros teléfonos (betatesters).

## Exposición

Aún sin comparaciones registradas. Lo ideal es comparar contra un fotómetro o contra la exposición correcta de la cámara.

## Parpadeo

Funciona en el S26 Ultra. Falta registrar la frecuencia medida con luces conocidas: LED, fluorescente o dimmer.

## Calor

| Fecha | Teléfono | Uso | Piel del teléfono | Estado térmico |
|---|---|---|---|---|
| 2026-10-19 | S26 Ultra | v0.1 (RAW completo cada 1 s), 15 s | 36,4 → 40,1 °C | — (se cortó) |
| 2026-10-20 | S26 Ultra | v0.2, 60 s recorriendo todas las herramientas, cargando | 30,2 → 32,1 °C | 0 (sin aviso) |
