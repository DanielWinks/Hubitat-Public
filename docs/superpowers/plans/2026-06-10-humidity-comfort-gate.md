# Humidity Comfort Gate + Forecast Bias Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the Solar Shade & Window Advisor from recommending "open windows" into muggy outdoor air, and stop the forecast from lagging observed outdoor temperature on rising mornings.

**Architecture:** A dew-point comfort gate (pure function) vetoes the "open" recommendation when outdoor dew point exceeds `max(comfort threshold, indoor dew point)` and the indoor−outdoor temperature advantage doesn't cover the excess. The gate layers on top of the existing thermal decision in `evaluate()` — the forecast trajectory and learned thermal model never see humidity. Separately, the windows-open forecast bias-corrects the Open-Meteo hourly curve with the observed-minus-forecast offset, decaying to zero over 90 minutes. Spec: `docs/superpowers/specs/2026-06-10-humidity-comfort-design.md`.

**Tech Stack:** Hubitat Groovy 2.4 (standalone app, no `#include`), Spock tests via `HubitatScriptHarness`/`ScriptLoader` under `tests/`.

**Environment constraint:** This machine has NO local JVM/Gradle — `gradle test`/`gradle lint` CANNOT run locally. They run in CI (`.github/workflows/lint-and-test.yml`) on push. Therefore each task commits the test AND its implementation together (so no commit is ever red in CI), and every "verify" step is a careful manual review against the lint rules in `tests/src/main/groovy/dwinks/hubitat/lint/rules/`. The final task pushes and watches CI.

**Code style reminders (lint-enforced):** parentheses on ALL method calls, braces on ALL control structures, concrete types (no `def` for new code), `@CompileStatic` only on methods that touch nothing dynamic (no `settings`/`state`/`location`), `@Field` import already present in the app.

**Files touched (all modifications, no new files):**

| File | Responsibility in this change |
|---|---|
| `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` | All new logic: constants, preferences, weather params, sensor plumbing, pure math, veto wiring, bias correction |
| `Drivers/Component/SolarShadeAdvisorStatus.groovy` | 3 new display attributes |
| `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy` | All new tests |
| `PackageManifests/SolarShadeWindowAdvisor/packageManifest.json` | Version bump 1.0.0 → 1.1.0 |

Work directly on `main` (matches this repo's existing practice for this app; CI runs on push).

---

### Task 1: Pure psychrometric math (`fToC`, `cToF`, `dewPointC`)

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (pure `@CompileStatic` section, after `clampDouble` at ~line 877)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the tests**

Add to the spec, after the `"effective solar load applies occlusion"` test:

```groovy
  // --- Humidity comfort gate --------------------------------------------------

  def "fToC and cToF convert exactly at reference points and invert each other"() {
    expect:
    app.fToC(32.0d) == 0.0d
    app.fToC(212.0d) == 100.0d
    app.cToF(0.0d) == 32.0d
    Math.abs(app.cToF(app.fToC(73.4d)) - 73.4d) < 1e-9
  }

  def "dewPointC matches known psychrometric values"() {
    expect:
    // 25C at 60% RH -> dew point ~16.7C (standard Magnus check value)
    Math.abs(app.dewPointC(25.0d, 60.0d) - 16.7d) < 0.3d
    // saturated air: dew point equals air temperature
    Math.abs(app.dewPointC(20.0d, 100.0d) - 20.0d) < 0.05d
    // dry air: dew point falls far below air temperature
    app.dewPointC(30.0d, 20.0d) < 10.0d
    // RH is clamped, so a bogus 0% does not produce -Infinity
    app.dewPointC(20.0d, 0.0d) > -100.0d
  }
```

- [ ] **Step 2: Write the implementation**

In the app, immediately after the `clampDouble` method (the first method in the "Inlined solar-load / occlusion / decision math" section), add:

```groovy
@CompileStatic
double fToC(double f) { return (f - 32.0d) / 1.8d }

@CompileStatic
double cToF(double c) { return (c * 1.8d) + 32.0d }

// Magnus-formula dew point (Celsius in, Celsius out; valid roughly -45..60C).
// RH is clamped to [1,100] so sensor glitches cannot produce -Infinity.
@CompileStatic
double dewPointC(double tempC, double rhPct) {
  double rh = clampDouble(rhPct, 1.0d, 100.0d)
  double g = Math.log(rh / 100.0d) + ((17.62d * tempC) / (243.12d + tempC))
  return (243.12d * g) / (17.62d - g)
}
```

- [ ] **Step 3: Self-check against lint rules**

Verify: parentheses on every call, braces on every `if`, `@CompileStatic` methods reference only primitives/`Math`/`clampDouble` (all statically resolvable), no `settings`/`state` access.

- [ ] **Step 4: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Add pure psychrometric helpers (Magnus dew point, F/C conversion) with tests"
```

---

### Task 2: Pure humidity veto function (`humidityVetoActive`)

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (constants block ~line 60; pure section after `shouldCloseWindows` ~line 979)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the tests**

Add to the spec after the Task 1 tests:

```groovy
  def "humidity veto blocks a muggy open, allows a cool-humid open, and ignores dry air"() {
    expect:
    // signature: (indoorTemp, outdoorTemp, outdoorDp, threshold, slope, clearMargin)
    // The reported failure: 72F in, 73F out, dew point ~70F (very muggy) -> VETO
    app.humidityVetoActive(72.0d, 73.0d, 70.0d, 60.0d, 1.0d, 0.0d)
    // Cool + humid: 73F in, 63F out at saturation (dew point 63F) -> 10F advantage
    // covers the 3F dew point excess -> open is allowed
    !app.humidityVetoActive(73.0d, 63.0d, 63.0d, 60.0d, 1.0d, 0.0d)
    // Dry air NEVER vetoes - even when outdoor is warmer than indoor, the thermal
    // forecast remains the only authority (preserves existing behavior)
    !app.humidityVetoActive(72.0d, 78.0d, 55.0d, 60.0d, 1.0d, 0.0d)
    // Exactly at the threshold (excess == 0) the gate stays disengaged
    !app.humidityVetoActive(72.0d, 78.0d, 60.0d, 60.0d, 1.0d, 0.0d)
  }

  def "humidity veto hysteresis: a marginal case clears only without the active-veto margin"() {
    expect:
    // excess 5 -> required advantage 5; actual advantage 5.5
    !app.humidityVetoActive(75.5d, 70.0d, 65.0d, 60.0d, 1.0d, 0.0d)   // inactive veto: clears
    app.humidityVetoActive(75.5d, 70.0d, 65.0d, 60.0d, 1.0d, 1.0d)    // active veto: needs 6.0, stays on
  }
```

- [ ] **Step 2: Write the implementation**

Add the constant after `@Field static final Integer WINDOW_FORECAST_STEP_MIN = 10` (~line 60):

```groovy
// Humidity comfort gate
@Field static final Double HUMIDITY_HYST = 1.0d      // extra hub-degrees of advantage required to CLEAR an active veto
```

Add the function in the pure section, directly after `shouldCloseWindows`:

```groovy
// Humidity comfort gate: when outdoor dew point exceeds the comfort threshold,
// opening is vetoed unless the indoor-outdoor temperature advantage covers the
// excess (slope degrees of advantage per degree of excess). Dry air (excess <= 0)
// NEVER vetoes - the thermal forecast stays the only authority there. clearMargin
// is HUMIDITY_HYST while the veto is already active, making it harder to clear
// (same hysteresis pattern as the window reopen margin).
@CompileStatic
boolean humidityVetoActive(double indoorTemp, double outdoorTemp, double outdoorDp,
                           double threshold, double slope, double clearMargin) {
  double excess = outdoorDp - threshold
  if (excess <= 0.0d) { return false }
  double required = slope * excess
  return (indoorTemp - outdoorTemp) < (required + clearMargin)
}
```

- [ ] **Step 3: Self-check against lint rules** (same checklist as Task 1; `HUMIDITY_HYST` is NOT referenced inside the `@CompileStatic` function — the caller passes it)

- [ ] **Step 4: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Add pure humidity-veto decision function with hysteresis"
```

---

### Task 3: Pure forecast bias decay (`decayedBias`)

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (constants block; pure section before `forecastWindowsOpenTrajectory`)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the tests**

```groovy
  def "decayedBias is full now, zero at and after the decay window, linear in between"() {
    expect:
    app.decayedBias(2.0d, 0.0d, 90.0d) == 2.0d
    Math.abs(app.decayedBias(2.0d, 45.0d, 90.0d) - 1.0d) < 1e-9
    app.decayedBias(2.0d, 90.0d, 90.0d) == 0.0d
    app.decayedBias(2.0d, 240.0d, 90.0d) == 0.0d
    // negative bias (forecast running warm) decays the same way
    Math.abs(app.decayedBias(-3.0d, 30.0d, 90.0d) + 2.0d) < 1e-9
    // degenerate decay window -> no correction at all
    app.decayedBias(2.0d, 10.0d, 0.0d) == 0.0d
  }
```

- [ ] **Step 2: Write the implementation**

Add the constant after `HUMIDITY_HYST`:

```groovy
// Forecast bias correction: the observed-minus-hourly-forecast outdoor temperature
// offset is applied to the projection, decaying linearly to zero over this window.
@Field static final Double BIAS_DECAY_MIN = 90.0d
```

Add the function in the pure section, directly before `forecastWindowsOpenTrajectory`:

```groovy
// Linear decay of the observed-vs-forecast outdoor temperature bias: full at
// minutesAhead=0, zero at/after decayMinutes.
@CompileStatic
double decayedBias(double bias, double minutesAhead, double decayMinutes) {
  if (decayMinutes <= 0.0d || minutesAhead >= decayMinutes) { return 0.0d }
  if (minutesAhead <= 0.0d) { return bias }
  return bias * (1.0d - (minutesAhead / decayMinutes))
}
```

- [ ] **Step 3: Self-check against lint rules** (same checklist)

- [ ] **Step 4: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Add pure linear bias-decay helper for forecast correction"
```

---

### Task 4: Humidity-aware advisory phrasing (`buildAdvisory`)

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy:608-623` (replace `buildAdvisory`)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the tests**

```groovy
  def "buildAdvisory phrases the humidity veto, keeps the key format, and is unchanged without the veto"() {
    expect:
    // default-args overload: existing 2-arg calls behave exactly as before
    !app.buildAdvisory(true, false).message.contains('humid')
    app.buildAdvisory(true, false, true, '70.0 degF').key == 'win:closed|shade:open'
    app.buildAdvisory(true, false, true, '70.0 degF').message.contains('too humid')
    app.buildAdvisory(true, false, true, '70.0 degF').message.contains('70.0 degF')
    // dew point text is optional
    app.buildAdvisory(true, true, true, null).message.contains('too humid')
    // veto + shades both mentioned
    app.buildAdvisory(true, true, true, null).message.contains('shades')
    // the veto flag is irrelevant when windows are recommended open
    app.buildAdvisory(false, false, false, null).message.startsWith('Open the windows')
  }
```

- [ ] **Step 2: Write the implementation**

Replace the entire `buildAdvisory` method with (the comment above it stays):

```groovy
Map buildAdvisory(boolean windowsShouldClose, boolean shadesShouldDraw, boolean humidityVeto = false, String dewPointText = null) {
  String winRec = windowsShouldClose ? 'closed' : 'open'
  String shadeRec = shadesShouldDraw ? 'draw' : 'open'
  String key = "win:${winRec}|shade:${shadeRec}"
  String msg
  if (windowsShouldClose && humidityVeto) {
    String dp = dewPointText ? " (dew point ${dewPointText})" : ''
    msg = "Keep the windows closed - outdoor air is too humid${dp} for the small temperature benefit."
    if (shadesShouldDraw) { msg = "${msg} Draw shades on the sun-exposed side(s)." }
  } else if (windowsShouldClose && shadesShouldDraw) {
    msg = 'Close the windows and draw shades on the sun-exposed sides - opening up would overheat the house soon; let HVAC handle it.'
  } else if (windowsShouldClose) {
    msg = 'Close the windows so HVAC runs efficiently - opening up would overheat the house soon. Shades can stay open (low solar load).'
  } else if (shadesShouldDraw) {
    msg = 'Open the windows for fresh air, but draw shades on the sun-exposed side(s) to limit heat gain.'
  } else {
    msg = 'Open the windows and shades - it will stay comfortable with fresh air and natural light.'
  }
  return [key: key, windowRec: winRec, shadeRec: shadeRec, message: msg]
}
```

(Groovy default parameters generate the 2-, 3- and 4-arg signatures, so all existing call sites and tests keep working.)

- [ ] **Step 3: Self-check against lint rules** (same checklist; note this method is intentionally NOT `@CompileStatic` — it never was)

- [ ] **Step 4: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Phrase the advisory message for humidity-vetoed window closes"
```

---

### Task 5: Weather + sensor plumbing (preferences, Open-Meteo params, dew point sources)

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (preferences ~line 101, `buildWeatherUri` ~line 324, `weatherCallback` ~line 356, after `averageTemp` ~line 500)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy` (also extend `setupSpec`)

- [ ] **Step 1: Extend `setupSpec` to expose the app file for fresh instances**

Integration tests must not pollute the `@Shared app` used by the pure-function tests. Replace the spec's field + `setupSpec` with:

```groovy
  @Shared HubitatScriptHarness app
  @Shared File appFile

  def setupSpec() {
    appFile = new File('../Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy')
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    app = ScriptLoader.load(appFile)
  }
```

- [ ] **Step 2: Write the failing test**

```groovy
  def "weather fetch requests and stores current humidity and dew point"() {
    given:
    HubitatScriptHarness fresh = ScriptLoader.load(appFile)
    fresh.settings.latitude = '40.0'
    fresh.settings.longitude = '-83.0'

    expect: 'the current= parameter list asks Open-Meteo for humidity + dew point'
    fresh.buildWeatherUri().contains('current=temperature_2m,relative_humidity_2m,dew_point_2m,cloud_cover')

    when: 'a successful response arrives'
    def response = new Expando(
      status: 200,
      hasError: { -> false },
      getJson: { -> [current: [temperature_2m: 73.0, relative_humidity_2m: 93.0, dew_point_2m: 70.8, cloud_cover: 80],
                     hourly: [time: ['2026-06-10T07:00'], temperature_2m: [71.0], shortwave_radiation: [120.0],
                              direct_normal_irradiance: [300.0], diffuse_radiation: [80.0], cloud_cover: [80]]] }
    )
    fresh.weatherCallback(response, null)

    then:
    fresh.state.wxCurrentTemp == 73.0d
    fresh.state.wxCurrentRh == 93.0d
    fresh.state.wxCurrentDewPoint == 70.8d
  }

  def "dew point sources: local sensors win, Open-Meteo fills in, absence disables"() {
    given:
    HubitatScriptHarness fresh = ScriptLoader.load(appFile)

    expect: 'nothing configured, no weather yet -> null (gate disengages)'
    fresh.outdoorDewPoint() == null
    fresh.indoorDewPoint() == null

    when: 'Open-Meteo current data only'
    fresh.state.wxCurrentTemp = 73.0
    fresh.state.wxCurrentDewPoint = 70.8

    then:
    fresh.outdoorDewPoint() == 70.8d

    when: 'a local outdoor RH sensor takes precedence (73F at 100% RH -> dew point 73F)'
    fresh.settings.outdoorHumiditySensors = [new MockDevice(currentValues: [humidity: 100.0])]

    then:
    Math.abs(fresh.outdoorDewPoint() - 73.0d) < 0.2d

    when: 'a local outdoor temperature sensor overrides Open-Meteo current temp'
    fresh.settings.outdoorTempSensors = [new MockDevice(currentValues: [temperature: 63.0])]

    then:
    Math.abs(fresh.outdoorDewPoint() - 63.0d) < 0.2d
    fresh.currentOutdoorTemp() == 63.0d

    when: 'indoor humidity + indoor temperature sensors -> indoor dew point (72F at 50% RH -> ~52.5F)'
    fresh.settings.tempSensors = [new MockDevice(currentValues: [temperature: 72.0])]
    fresh.settings.indoorHumiditySensors = [new MockDevice(currentValues: [humidity: 50.0])]

    then:
    Math.abs(fresh.indoorDewPoint() - 52.5d) < 1.0d
  }
```

Also add the import at the top of the spec, alongside the existing stubs imports:

```groovy
import dwinks.hubitat.stubs.MockDevice
```

- [ ] **Step 3: Write the implementation**

**(a)** New preferences section in `mainPage()`, inserted between the `'<h2>Climate Control</h2>'` section and the `'<h2>Windows &amp; Shades</h2>'` section:

```groovy
    section('<h2>Humidity &amp; Comfort</h2>') {
      input 'outdoorHumiditySensors', 'capability.relativeHumidityMeasurement', title: 'Outdoor humidity sensor(s) (optional; Open-Meteo is used when unset)', required: false, multiple: true
      input 'outdoorTempSensors', 'capability.temperatureMeasurement', title: 'Outdoor temperature sensor(s) (optional; Open-Meteo is used when unset)', required: false, multiple: true
      input 'indoorHumiditySensors', 'capability.relativeHumidityMeasurement', title: 'Indoor humidity sensor(s) (optional; a muggy interior relaxes the humidity gate)', required: false, multiple: true
      input 'dewPointComfort', 'decimal', title: "Outdoor dew point (&deg;${getTempScale()}) at/above which air starts feeling humid", required: true, defaultValue: (getTempScale() == 'C' ? 15.5 : 60.0), range: '0..100'
      input 'dewPointSlope', 'decimal', title: 'Degrees of indoor-outdoor temperature advantage required per degree of dew point excess', required: true, defaultValue: 1.0, range: '0.1..5'
      paragraph 'Windows are not recommended open when outdoor air is muggy unless it is also enough cooler outside to be worth it. With no sensors selected, Open-Meteo current conditions are used automatically.'
    }
```

**(b)** In `buildWeatherUri()`, replace the return line so `current=` includes humidity and dew point:

```groovy
  return "${OPEN_METEO_URL}?latitude=${lat}&longitude=${lng}&current=temperature_2m,relative_humidity_2m,dew_point_2m,cloud_cover&hourly=${hourly}&forecast_days=2&timezone=auto&temperature_unit=${tempUnit}"
```

**(c)** In `weatherCallback()`, directly after `state.wxCloudNow = asDouble(current?.cloud_cover)`:

```groovy
  // Current humidity/dew point (hub-scale - Open-Meteo returns dew point in the
  // requested temperature_unit). Null when absent so the comfort gate disengages.
  state.wxCurrentRh = asDoubleOrNull(current?.relative_humidity_2m)
  state.wxCurrentDewPoint = asDoubleOrNull(current?.dew_point_2m)
```

**(d)** Directly after the `averageTemp` method, add:

```groovy
Double averageHumidity(devices) {
  if (!devices) { return null }
  List vals = devices.collect { d -> asDoubleOrNull(d.currentValue('humidity')) }.findAll { it != null }
  if (vals.isEmpty()) { return null }
  return (vals.sum() as double) / vals.size()
}

// Best current outdoor temperature: local sensors when configured, else Open-Meteo.
Double currentOutdoorTemp() {
  Double sensor = averageTemp(settings.outdoorTempSensors)
  if (sensor != null) { return sensor }
  return asDoubleOrNull(state.wxCurrentTemp)
}

// Dew point in hub-scale degrees from a hub-scale temperature and RH percent.
Double dewPointHubScale(Double temp, Double rh) {
  if (temp == null || rh == null) { return null }
  if (getTempScale() == 'F') { return cToF(dewPointC(fToC(temp as double), rh as double)) }
  return dewPointC(temp as double, rh as double)
}

// Outdoor dew point (hub scale): local RH sensors (with the best available outdoor
// temperature) win; else Open-Meteo's current dew point; else null - and the
// humidity comfort gate disengages entirely.
Double outdoorDewPoint() {
  Double rh = averageHumidity(settings.outdoorHumiditySensors)
  if (rh != null) {
    Double t = currentOutdoorTemp()
    if (t != null) { return dewPointHubScale(t, rh) }
  }
  return asDoubleOrNull(state.wxCurrentDewPoint)
}

// Indoor dew point (hub scale) from the indoor humidity + temperature sensors, or
// null when either is unavailable.
Double indoorDewPoint() {
  Double rh = averageHumidity(settings.indoorHumiditySensors)
  if (rh == null) { return null }
  Double t = averageTemp(settings.tempSensors)
  if (t == null) { return null }
  return dewPointHubScale(t, rh)
}
```

- [ ] **Step 4: Self-check against lint rules**

These methods read `settings`/`state`, so they are correctly NOT `@CompileStatic`. Verify the new `input` names don't collide with existing settings keys (`outdoorHumiditySensors`, `outdoorTempSensors`, `indoorHumiditySensors`, `dewPointComfort`, `dewPointSlope` are all new).

- [ ] **Step 5: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Add humidity sensor inputs and Open-Meteo current humidity/dew point"
```

---

### Task 6: Forecast bias correction wiring

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (`forecastWindowsOpen` ~line 567, `pushThermalSample` ~line 515)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the test**

```groovy
  def "forecastWindowsOpen bias-corrects the lagging hourly curve toward observed reality"() {
    given: 'the hourly forecast says 71F but the observed current temperature is 73F (rising morning)'
    HubitatScriptHarness fresh = ScriptLoader.load(appFile)
    long t = System.currentTimeMillis()
    fresh.state.wxHourEpochs = [t - 1800000L]
    fresh.state.wxTemp = [71.0]
    fresh.state.wxCurrentTemp = 73.0
    fresh.settings.windowOpenTauMin = 45
    fresh.settings.wallCount = 0

    when: 'forecasting with the +2F bias, then again with the observation matching the curve'
    double[] withBias = fresh.forecastWindowsOpen(72.0d, 3, 1)
    fresh.state.wxCurrentTemp = 71.0
    double[] noBias = fresh.forecastWindowsOpen(72.0d, 3, 1)

    then: 'the bias-corrected projection runs warmer near-term and no cooler at the end'
    withBias[3] > noBias[3]
    withBias[2] >= noBias[2]
  }
```

- [ ] **Step 2: Write the implementation**

**(a)** In `forecastWindowsOpen()`, replace this loop:

```groovy
  double[] tOutSeq = new double[steps]
  double[] solarSeq = new double[steps]
  for (Integer i = 0; i < steps; i++) {
    Long epoch = now() + ((i + 1) * stepMin * 60000L)
    Integer wx = hourlyIndexFor(epoch)
    tOutSeq[i] = forecastOutdoorTemp(wx)
    solarSeq[i] = maxWallLoadAt(epoch, wx)
  }
```

with:

```groovy
  double[] tOutSeq = new double[steps]
  double[] solarSeq = new double[steps]
  // Bias-correct the hourly curve toward observed reality: on a rising morning the
  // hourly forecast lags the actual outdoor temperature, which made the projection
  // start too cool and recommend opening while it was already warmer outside. The
  // observed-minus-forecast offset decays to zero over BIAS_DECAY_MIN minutes.
  double bias = 0.0d
  Double obsOut = currentOutdoorTemp()
  if (obsOut != null) { bias = (obsOut as double) - forecastOutdoorTemp(hourlyIndexFor(now())) }
  for (Integer i = 0; i < steps; i++) {
    Long epoch = now() + ((i + 1) * stepMin * 60000L)
    Integer wx = hourlyIndexFor(epoch)
    tOutSeq[i] = forecastOutdoorTemp(wx) + decayedBias(bias, ((i + 1) * stepMin) as double, BIAS_DECAY_MIN as double)
    solarSeq[i] = maxWallLoadAt(epoch, wx)
  }
```

**(b)** In `pushThermalSample()`, replace:

```groovy
  Double tOut = state.wxCurrentTemp != null ? asDouble(state.wxCurrentTemp) : null
```

with:

```groovy
  Double tOut = currentOutdoorTemp()
```

so the learned model trains on the same outdoor reality the decision uses.

- [ ] **Step 3: Self-check against lint rules** (dynamic method, no `@CompileStatic` concerns; verify `decayedBias` and `currentOutdoorTemp` names match Tasks 3 and 5 exactly)

- [ ] **Step 4: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Bias-correct the windows-open forecast toward observed outdoor temperature"
```

---

### Task 7: Wire the veto into evaluate() + status outputs + driver attributes

**Files:**
- Modify: `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy` (`evaluate()` ~line 625, `updateStatusDevice` ~line 727)
- Modify: `Drivers/Component/SolarShadeAdvisorStatus.groovy` (metadata + `installed()`)
- Test: `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

- [ ] **Step 1: Write the tests**

```groovy
  // --- evaluate() end-to-end: the two real-world humidity scenarios ----------

  private HubitatScriptHarness freshEvaluateApp(double indoorTemp, double outdoorTemp, double outdoorDewPoint) {
    HubitatScriptHarness fresh = ScriptLoader.load(appFile)
    fresh.settings.thermostat = new MockDevice(currentValues: [coolingSetpoint: 74.0, heatingSetpoint: 66.0,
                                                               thermostatOperatingState: 'idle', thermostatMode: 'cool'])
    fresh.settings.tempSensors = [new MockDevice(currentValues: [temperature: indoorTemp])]
    fresh.settings.maxDevHot = 3.0
    fresh.settings.maxDevCold = 10.0
    fresh.settings.windowOpenTauMin = 45
    fresh.settings.forecastHorizonHours = 3
    fresh.settings.closeLeadHours = 1
    fresh.settings.wallCount = 0
    fresh.settings.latitude = '40.0'
    fresh.settings.longitude = '-83.0'
    fresh.settings.debugLogEnable = false
    fresh.state.wxHourEpochs = [System.currentTimeMillis() - 600000L]
    fresh.state.wxTemp = [outdoorTemp]
    fresh.state.wxCurrentTemp = outdoorTemp
    fresh.state.wxCurrentDewPoint = outdoorDewPoint
    return fresh
  }

  def "evaluate vetoes opening when muggy outside with no real temperature advantage"() {
    given: 'the reported failure: 72F inside, 73F outside, dew point 70F'
    HubitatScriptHarness fresh = freshEvaluateApp(72.0d, 73.0d, 70.0d)

    when:
    fresh.evaluate()

    then:
    fresh.state.lastHumidityVeto == true
    fresh.state.lastWindowRec == 'closed'
  }

  def "evaluate recommends open when much cooler outside even though humid"() {
    given: '73F inside, 63F outside at saturation (dew point 63F)'
    HubitatScriptHarness fresh = freshEvaluateApp(73.0d, 63.0d, 63.0d)

    when:
    fresh.evaluate()

    then:
    fresh.state.lastHumidityVeto == false
    fresh.state.lastWindowRec == 'open'
  }

  def "a muggy interior relaxes the gate: outdoor air no damper than indoors is allowed in"() {
    given: '73F inside at 75% RH (indoor dew point ~64.6F); 71F outside, dew point 64F'
    // With the fixed 60F threshold this WOULD veto (excess 4 > advantage 2), but the
    // indoor dew point relaxes the threshold above 64F, so the gate disengages.
    HubitatScriptHarness fresh = freshEvaluateApp(73.0d, 71.0d, 64.0d)
    fresh.settings.indoorHumiditySensors = [new MockDevice(currentValues: [humidity: 75.0])]

    when:
    fresh.evaluate()

    then:
    fresh.state.lastHumidityVeto == false
    fresh.state.lastWindowRec == 'open'
  }

  def "evaluate leaves behavior unchanged when no humidity data exists"() {
    given: 'mild dry conditions, no dew point data at all'
    HubitatScriptHarness fresh = freshEvaluateApp(72.0d, 70.0d, 0.0d)
    fresh.state.remove('wxCurrentDewPoint')

    when:
    fresh.evaluate()

    then:
    fresh.state.lastHumidityVeto == false
    fresh.state.lastWindowRec == 'open'
  }
```

(Note: `freshEvaluateApp(72.0d, 70.0d, 0.0d)` passes a dummy dew point that the next line removes — the helper always sets it.)

- [ ] **Step 2: Implement the evaluate() wiring**

In `evaluate()`, replace this block:

```groovy
  // Asymmetric, two-horizon decision: close for heat only when it ends hot AND is
  // already hot in the near term (so we stay open through a warm spell forecast to
  // cool, and close at the last sensible moment as it actually warms). Hysteresis
  // makes it slightly harder to RE-open once we've said close.
  double reopenMargin = (state.lastWindowRec == 'closed') ? WINDOW_HYST : 0.0d
  boolean windowsShouldClose = shouldCloseWindows(projNear, projEnd,
      coolSp as double, heatSp as double, maxDevHot, maxDevCold, reopenMargin)
```

with:

```groovy
  // Asymmetric, two-horizon decision: close for heat only when it ends hot AND is
  // already hot in the near term (so we stay open through a warm spell forecast to
  // cool, and close at the last sensible moment as it actually warms). Hysteresis
  // makes it slightly harder to RE-open once we've said close.
  double reopenMargin = (state.lastWindowRec == 'closed') ? WINDOW_HYST : 0.0d
  boolean thermalClose = shouldCloseWindows(projNear, projEnd,
      coolSp as double, heatSp as double, maxDevHot, maxDevCold, reopenMargin)

  // Humidity comfort gate: when outdoor air is muggier than the comfort threshold,
  // opening is vetoed unless it is enough cooler outside to be worth it. Evaluates
  // CURRENT conditions only - the thermal forecast knows nothing about humidity.
  Double dpOut = outdoorDewPoint()
  Double dpIn = indoorDewPoint()
  Double outTempNow = currentOutdoorTemp()
  boolean humidityVeto = false
  if (dpOut != null && outTempNow != null) {
    double threshold = (settings.dewPointComfort ?: (getTempScale() == 'C' ? 15.5 : 60.0)) as double
    if (dpIn != null && (dpIn as double) > threshold) { threshold = dpIn as double }  // muggy house relaxes the gate
    double slope = (settings.dewPointSlope ?: 1.0) as double
    double clearMargin = (state.lastHumidityVeto == true) ? (HUMIDITY_HYST as double) : 0.0d
    humidityVeto = humidityVetoActive(indoorNow as double, outTempNow as double, dpOut as double,
        threshold, slope, clearMargin)
  }
  state.lastHumidityVeto = humidityVeto

  boolean windowsShouldClose = thermalClose || humidityVeto
```

Then replace the advisory construction line:

```groovy
  Map advisory = buildAdvisory(windowsShouldClose, shadesShouldDraw)
```

with:

```groovy
  String dpText = dpOut != null ? "${round1(dpOut)} deg${getTempScale()}" : null
  Map advisory = buildAdvisory(windowsShouldClose, shadesShouldDraw, humidityVeto, dpText)
```

Then replace the `logInfo` advisory line:

```groovy
  logInfo("Advisory [${advisory.key}] | windows-open proj: now ${fmt(indoorNow)} -> +${leadHours}h ${fmt(projNear)} -> +${hours}h ${fmt(projEnd)} (min ${fmt(projMin)}/max ${fmt(projMax)}) deg${getTempScale()}, hotCeiling ${fmt(hotCeiling)}; shouldClose=${windowsShouldClose}, windowsOpen=${windowsOpen}, action=${windowAction}")
```

with:

```groovy
  logInfo("Advisory [${advisory.key}] | windows-open proj: now ${fmt(indoorNow)} -> +${leadHours}h ${fmt(projNear)} -> +${hours}h ${fmt(projEnd)} (min ${fmt(projMin)}/max ${fmt(projMax)}) deg${getTempScale()}, hotCeiling ${fmt(hotCeiling)}; dewPoint ${fmt(dpOut)}, humidityVeto=${humidityVeto}, shouldClose=${windowsShouldClose}, windowsOpen=${windowsOpen}, action=${windowAction}")
```

And replace the status-device update call:

```groovy
  updateStatusDevice(advisory, projMax, projMin, wallRecs)
```

with:

```groovy
  updateStatusDevice(advisory, projMax, projMin, wallRecs, dpOut, dpIn, humidityVeto)
```

- [ ] **Step 3: Implement the status-device output**

Change the `updateStatusDevice` signature from:

```groovy
void updateStatusDevice(Map advisory, Double projHigh, Double projLow, Map wallRecs) {
```

to:

```groovy
void updateStatusDevice(Map advisory, Double projHigh, Double projLow, Map wallRecs,
                        Double outdoorDp, Double indoorDp, boolean humidityVeto) {
```

and add, directly after the `predictedIndoorLow` sendEvent:

```groovy
  dev.sendEvent(name: 'humidityVeto', value: humidityVeto ? 'active' : 'inactive')
  if (outdoorDp != null) { dev.sendEvent(name: 'outdoorDewPoint', value: round1(outdoorDp), unit: getTempScale()) }
  if (indoorDp != null) { dev.sendEvent(name: 'indoorDewPoint', value: round1(indoorDp), unit: getTempScale()) }
```

- [ ] **Step 4: Add the driver attributes**

In `Drivers/Component/SolarShadeAdvisorStatus.groovy`, after `attribute 'predictedIndoorLow', 'number'`:

```groovy
    attribute 'outdoorDewPoint', 'number'
    attribute 'indoorDewPoint', 'number'
    attribute 'humidityVeto', 'string'            // active | inactive
```

and in `installed()`, add (non-null initialization per repo convention):

```groovy
  sendEvent(name: 'humidityVeto', value: 'inactive')
```

- [ ] **Step 5: Self-check against lint rules**

Verify: `humidityVetoActive` call passes 6 doubles matching the Task 2 signature; `buildAdvisory` 4-arg call matches Task 4; `updateStatusDevice` call site and definition agree (7 params); driver attribute names match the app's `sendEvent` names exactly (`outdoorDewPoint`, `indoorDewPoint`, `humidityVeto`).

- [ ] **Step 6: Commit**

```bash
git add Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy Drivers/Component/SolarShadeAdvisorStatus.groovy tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy
git commit -m "Wire humidity comfort gate into evaluate() with status attributes"
```

---

### Task 8: Version bump + CI verification

**Files:**
- Modify: `PackageManifests/SolarShadeWindowAdvisor/packageManifest.json:4-7`

- [ ] **Step 1: Bump the manifest**

Replace lines 4-7 (`version`, `releaseNotes`, `minimumHEVersion`, `dateReleased`) with:

```json
  "version": "1.1.0",
  "releaseNotes": "1.1.0 Humidity comfort gate: window-open advisories now respect outdoor dew point (sensors or Open-Meteo) with indoor-humidity-adaptive thresholds, plus a bias-corrected outdoor temperature forecast.\n1.0.0 Initial release: per-side solar load with occlusion modeling, Open-Meteo weather, thermostat-aware four-state shade/window advisories, and an adaptive thermal model that learns from indoor sensors.",
  "minimumHEVersion": "2.3.4",
  "dateReleased": "2026-06-10",
```

- [ ] **Step 2: Commit**

```bash
git add PackageManifests/SolarShadeWindowAdvisor/packageManifest.json
git commit -m "Bump Solar Shade & Window Advisor to 1.1.0 (humidity comfort gate)"
```

- [ ] **Step 3: Push and watch CI (this is the test run)**

```bash
git push origin main
gh run watch $(gh run list --workflow=lint-and-test.yml --limit 1 --json databaseId --jq '.[0].databaseId')
```

Expected: lint + all Spock tests PASS (existing 11 tests + ~10 new ones). If CI fails, fix forward: read the failing test/lint output, correct, commit, push again.

- [ ] **Step 4: Hub smoke test (manual, user-driven)**

Paste the updated app + status driver code onto the hub, open the app, verify the new "Humidity & Comfort" section renders, tap "Refresh & Evaluate Now", and check the logs for the new `dewPoint ... humidityVeto=...` fields and the status child device for the three new attributes.

---

## Verification checklist (spec → task)

| Spec requirement | Task |
|---|---|
| New optional sensor inputs + comfort settings | 5 |
| Open-Meteo current humidity/dew point | 5 |
| Magnus dew point math (pure) | 1 |
| Veto rule with excess>0 engagement + hysteresis | 2 (pure), 7 (wiring) |
| Indoor dew point relaxes threshold | 5 (source), 7 (max() wiring + covered by evaluate tests) |
| Veto symmetric (blocks open, flips to close) | 7 (`windowsShouldClose = thermalClose \|\| humidityVeto`) |
| Forecast untouched by humidity / learned model clean | 6+7 (humidity only enters after `forecastWindowsOpen`) |
| Bias correction with 90-min decay | 3 (pure), 6 (wiring) |
| `pushThermalSample` uses best outdoor temp | 6 |
| Advisory phrasing + unchanged key format | 4 |
| Status attributes (`outdoorDewPoint`, `indoorDewPoint`, `humidityVeto`) | 7 |
| Backward compatibility (no changed settings/state/attributes) | all tasks additive; default-arg `buildAdvisory` keeps old signature |
| Tests for all of the above | 1-7 |
