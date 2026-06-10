# Solar Shade & Window Advisor: Humidity Comfort Gate + Forecast Bias Correction

**Date:** 2026-06-10
**Status:** Approved
**Files affected:**
- `Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy`
- `Drivers/Component/SolarShadeAdvisorStatus.groovy`
- `tests/src/test/groovy/dwinks/hubitat/functional/SolarShadeWindowAdvisorSpec.groovy`

## Problem

The window open/close recommendation is purely temperature-based. Two observed failures:

1. **Humidity blindness.** At 73°F outside / 72°F inside with very high outdoor
   humidity, the app recommended opening the windows. Muggy air should keep
   windows closed unless the indoor-outdoor temperature advantage is large
   enough to compensate (e.g. 63°F humid outside vs 73°F inside is fine).
2. **Forecast lag.** `forecastOutdoorTemp()` reads only the Open-Meteo hourly
   curve, so on a rising morning the trajectory starts 1-2°F below observed
   reality, biasing the decision toward "open".

## Approach decision

Three approaches were considered:

- **Dew-point gate with temperature-difference compensation (CHOSEN).**
  Dew point is the absolute measure of mugginess (RH alone is misleading), and
  dew point can never exceed air temperature, so "quite cool outside" naturally
  bounds how muggy the air can be — matching the requirement that only
  cool-and-humid air qualifies for opening.
- **Enthalpy economizer (REJECTED).** Thermodynamically optimal for HVAC cost,
  but fails the user's own test case: 63°F near-saturated outdoor air has
  HIGHER enthalpy (~48 kJ/kg) than 73°F/50% indoor air (~45 kJ/kg), so it would
  veto a scenario the user explicitly wants open. Comfort is dew-point-driven,
  not enthalpy-driven.
- **Feels-like temperature penalty (REJECTED).** Heat-index math is a no-op
  below ~80°F (would not have caught the 73°F muggy morning), and feeding
  fictitious temperatures into the forecast corrupts the learned thermal model.

## Design

### 1. New inputs (all optional; app degrades gracefully without them)

New "Humidity & Comfort" section on `mainPage`:

| Setting | Type | Default | Purpose |
|---|---|---|---|
| `outdoorHumiditySensors` | `capability.relativeHumidityMeasurement`, multiple | none | Local outdoor RH (averaged) |
| `outdoorTempSensors` | `capability.temperatureMeasurement`, multiple | none | Local current outdoor temperature (averaged) |
| `indoorHumiditySensors` | `capability.relativeHumidityMeasurement`, multiple | none | Indoor dew point for the adaptive threshold |
| `dewPointComfort` | decimal | 60 (°F hub) / 15.5 (°C hub) | Dew point at/above which outdoor air feels humid |
| `dewPointSlope` | decimal | 1.0 | Degrees of temperature advantage required per degree of dew point excess |

Fallback chain per value: physical sensor → Open-Meteo → no data at all →
humidity gate disabled (behavior identical to today). No new event
subscriptions; the periodic `evaluateMinutes` cycle re-reads everything.

### 2. Data acquisition

- Open-Meteo URL: add `relative_humidity_2m,dew_point_2m` to the `current=`
  parameter list (hourly list unchanged). Store `state.wxCurrentRh` and
  `state.wxCurrentDewPoint`. Open-Meteo returns dew point in the requested
  `temperature_unit`, so values are already hub-scale.
- Outdoor dew point: if `outdoorHumiditySensors` set, compute via Magnus from
  (current outdoor temp, averaged RH); outdoor temp comes from
  `outdoorTempSensors` if set, else `state.wxCurrentTemp`. If no outdoor RH
  sensors, use `state.wxCurrentDewPoint` directly.
- Indoor dew point: Magnus from (indoor average temp, indoor average RH) when
  `indoorHumiditySensors` set; else null.
- Magnus formula as pure `@CompileStatic double dewPointC(double tempC,
  double rhPct)` (Celsius internally): `Td = 243.12 * g / (17.62 - g)` where
  `g = ln(RH/100) + 17.62*T/(243.12+T)`. Non-static wrappers convert to/from
  the hub scale via built-in `fahrenheitToCelsius()`/`celsiusToFahrenheit()`.

### 3. Humidity veto (pure function, mirrors `shouldCloseWindows` style)

```
threshold  = max(dewPointComfort, indoorDewPoint if available)
excess     = outdoorDewPoint - threshold
if excess <= 0: vetoActive = false        // air is not muggy; gate disengaged
else:
  required   = dewPointSlope * excess
  vetoActive = (indoorTemp - outdoorTemp) < required + clearMargin
```

- The gate engages ONLY when outdoor dew point exceeds the threshold. With dry
  air the function always returns inactive, so existing temperature-only
  behavior (including "open even though it is briefly warmer out, the forecast
  says it cools soon") is fully preserved.
- `clearMargin` = 1.0 hub-degree when the veto was active on the previous
  evaluation, else 0. Same hysteresis pattern as `reopenMargin`/`WINDOW_HYST`;
  prevents flapping at the boundary. Constant `HUMIDITY_HYST = 1.0d`; tracking
  state key `state.lastHumidityVeto` (boolean, new, additive). If the dew
  point falls to/below the threshold the veto clears immediately regardless of
  the margin.
- When NO outdoor dew point is available (no sensor, no weather data yet), the
  veto is skipped entirely (returns inactive).
- Integration in `evaluate()`:
  `windowsShouldClose = shouldCloseWindows(...) || vetoActive`.
- The veto is symmetric: it blocks an "open" recommendation and flips the
  recommendation to "closed" when windows are open and it turns muggy. The
  existing `windowAction` notification logic handles both with no changes.
- The veto evaluates CURRENT conditions only. The thermal forecast trajectory,
  the learned model, and `pushThermalSample` see no humidity inputs and no
  fictitious temperatures.

Worked examples (indoor dew point ~53°F, comfort 60, slope 1.0):
- 73°F out / dew point 70 / 72°F in: excess 10 → need +10°F advantage, have
  −1°F → VETO (the morning failure case).
- 63°F out / dew point ≤63 / 73°F in: excess ≤3 → need +3°F, have +10°F → open.
- Muggy house (indoor DP 65) + outdoor DP 63: threshold relaxes to 65 →
  opening cannot worsen moisture → gate allows if temps favor it.

### 4. Forecast lag fix (bias correction)

```
bias       = currentOutdoorTemp - hourlyTempAt(now)
tOutSeq[i] = hourlyTempAt(step_i) + bias * decay(minutesAhead_i)
```

- `decay` is linear from 1 at now to 0 at `BIAS_DECAY_MIN = 90` minutes.
  Pure `@CompileStatic double decayedBias(double bias, double minutesAhead,
  double decayMinutes)`.
- `currentOutdoorTemp` = averaged `outdoorTempSensors` if set, else
  `state.wxCurrentTemp`.
- `pushThermalSample` also prefers `outdoorTempSensors` when present so the
  learned model trains on the same reality the decision uses.

### 5. Outputs

- `buildAdvisory` gains a humidity-veto flag used for message phrasing only
  (the `key` format is unchanged), e.g. "Keep the windows closed — outdoor air
  is too humid (dew point 70°F) for only 1°F of cooling benefit."
- `SolarShadeAdvisorStatus` driver gains attributes:
  - `outdoorDewPoint` (number) — sent only when computable
  - `indoorDewPoint` (number) — sent only when computable
  - `humidityVeto` (string) — `active` / `inactive`, initialized `inactive`
- The `evaluate()` `logInfo` line gains dew point and veto fields.

### 6. Backward compatibility (do not break)

- No existing settings, state keys, attributes, or mappings change.
- The `ThermalModelStatistics` contract (`logSample`, learned-coefficient
  attributes) is untouched.
- With no humidity data (sensorless install before the first weather refresh),
  behavior is identical to today.
- New state keys: `state.lastHumidityVeto`, `state.wxCurrentRh`,
  `state.wxCurrentDewPoint` — additive only.

### 7. Testing (Spock, `tests/` harness, same pattern as existing spec)

- `dewPointC`: known psychrometric values (25°C/60% RH ≈ 16.7°C; 100% RH →
  DP == T; low RH → DP well below T).
- Veto rule: both real-world cases above, threshold relaxation from a muggy
  interior, hysteresis (boundary case clears only with margin), veto skipped
  when no dew point data, and — critically — veto INACTIVE with dry air even
  when outdoor is warmer than indoor (preserves existing behavior).
- `decayedBias`: full bias at 0 min, zero at/after 90 min, linear midpoint.
- Bias-corrected trajectory: with +2° current bias the forecast starts warmer
  and converges to the hourly curve.
- `buildAdvisory`: humidity phrasing appears only when the veto flag is set.

Tests run in CI only (`.github/workflows/lint-and-test.yml`) — no local JVM.
