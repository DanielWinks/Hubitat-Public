/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 *  THERMAL MODEL STATISTICS - child device for the Solar Shade & Window Advisor.
 *
 *  This is a self-contained "calculator" device (no library dependencies). The
 *  parent app feeds it a sample each evaluation cycle via logSample(); the device
 *  learns the home's NATURAL (HVAC-off) rate of indoor-temperature change as a
 *  function of (outdoor - indoor) temperature difference and the solar load on the
 *  most-exposed wall, using an exponentially-weighted online linear regression:
 *
 *      dIndoor/dt (deg/min)  =  b0  +  b1 * (Tout - Tin)  +  b2 * solarLoad
 *
 *  Two segmented models are kept: one for WINDOWS-OPEN intervals (used by the app
 *  to predict "what would happen if I opened the windows now"), and one for
 *  WINDOWS-CLOSED HVAC-off intervals (informational). 1/b1 is the learned thermal
 *  time-constant; running sums are stored as scalars in device state, and the
 *  fitted coefficients are exposed as attributes the parent reads back.
 */

import groovy.transform.CompileStatic
import groovy.transform.Field

// Exponential forgetting factor applied to the running sums each sample, so the
// model slowly adapts to seasonal change instead of averaging over all of history.
@Field static final double LEARN_DECAY = 0.999d
// Reject intervals shorter than this (noise) or longer than this (HVAC ran, or a
// gap) so only clean natural-dynamics intervals are learned.
@Field static final double MIN_INTERVAL_MIN = 1.0d
@Field static final double MAX_INTERVAL_MIN = 30.0d

metadata {
  definition(name: 'Solar Shade Thermal Model', namespace: 'dwinks', author: 'Daniel Winks', component: true) {
    capability 'Sensor'

    command 'resetModel'
    command 'logSample', [
      [name: 'indoorTemp', type: 'NUMBER'],
      [name: 'outdoorTemp', type: 'NUMBER'],
      [name: 'solarLoad', type: 'NUMBER'],
      [name: 'windowsOpen', type: 'ENUM', constraints: ['true', 'false']],
      [name: 'hvacIdle', type: 'ENUM', constraints: ['true', 'false']]
    ]

    // Windows-OPEN natural-response model (the app uses these to forecast opening up)
    attribute 'openSamples', 'NUMBER'
    attribute 'openIntercept', 'NUMBER'    // b0, deg/min
    attribute 'openCoupling', 'NUMBER'     // b1, per-min (= 1 / time-constant)
    attribute 'openSolarGain', 'NUMBER'    // b2, deg/min per load unit (0-100)
    attribute 'openTauMinutes', 'NUMBER'   // 1 / b1
    attribute 'openRSquared', 'NUMBER'

    // Windows-CLOSED, HVAC-off natural-response model (informational)
    attribute 'closedSamples', 'NUMBER'
    attribute 'closedIntercept', 'NUMBER'
    attribute 'closedCoupling', 'NUMBER'
    attribute 'closedSolarGain', 'NUMBER'
    attribute 'closedTauMinutes', 'NUMBER'
    attribute 'closedRSquared', 'NUMBER'

    // Most-recent observation
    attribute 'lastNaturalRate', 'NUMBER'  // observed rate, deg/HOUR
    attribute 'lastWindowState', 'STRING'  // open | closed
    attribute 'lastSampleTime', 'STRING'
  }
}

void installed() { resetModel() }

void updated() { }

void resetModel() {
  state.clear()
  ['open', 'closed'].each { String k ->
    sendEvent(name: "${k}Samples", value: 0)
    sendEvent(name: "${k}RSquared", value: 0)
  }
}

/**
 * Records one sample from the parent app. Called every evaluation cycle. The
 * device tracks the previous sample and accumulates a regression observation for
 * the interval between them, but only when that interval was clean: HVAC idle at
 * both ends, a consistent window state, and a sensible duration.
 */
void logSample(BigDecimal indoorTemp, BigDecimal outdoorTemp, BigDecimal solarLoad, String windowsOpen, String hvacIdle) {
  double tIn = indoorTemp != null ? indoorTemp.doubleValue() : 0.0d
  double tOut = outdoorTemp != null ? outdoorTemp.doubleValue() : 0.0d
  double solar = solarLoad != null ? solarLoad.doubleValue() : 0.0d
  boolean open = (windowsOpen == 'true')
  boolean idle = (hvacIdle == 'true')
  long t = now()

  if (state.prevTime != null && state.prevIndoor != null) {
    double dtMin = (t - (state.prevTime as long)) / 60000.0d
    boolean prevIdle = (state.prevHvacIdle == true)
    boolean prevOpen = (state.prevWindowsOpen == true)
    // Learn an interval only when it was clean: HVAC idle at both ends, a CONSISTENT
    // window state throughout (a transition mixes open/closed dynamics), and a
    // sensible duration. Otherwise just advance the baseline below.
    if (idle && prevIdle && (open == prevOpen) && dtMin >= MIN_INTERVAL_MIN && dtMin <= MAX_INTERVAL_MIN) {
      double prevIn = state.prevIndoor as double
      double rate = (tIn - prevIn) / dtMin                       // deg/min over the interval
      double tempDiff = (state.prevOutdoor as double) - prevIn   // (Tout - Tin) during the interval
      double prevSolar = state.prevSolar as double
      String k = prevOpen ? 'open' : 'closed'
      accumulate(k, tempDiff, prevSolar, rate)
      fitAndEmit(k)
      sendEvent(name: 'lastNaturalRate', value: round2(rate * 60.0d))   // deg/hour
      sendEvent(name: 'lastWindowState', value: k)
    }
  }

  state.prevIndoor = tIn
  state.prevOutdoor = tOut
  state.prevSolar = solar
  state.prevWindowsOpen = open
  state.prevHvacIdle = idle
  state.prevTime = t
  sendEvent(name: 'lastSampleTime', value: nowStamp())
}

// Accumulate one exponentially-weighted observation into the running sums for the
// 3-feature model [1, (Tout-Tin), solar]. Stored as individual scalars in state.
void accumulate(String k, double tempDiff, double solar, double y) {
  double l = LEARN_DECAY
  state["${k}_n"]   = sd("${k}_n")   * l + 1.0d
  state["${k}_s12"] = sd("${k}_s12") * l + tempDiff
  state["${k}_s13"] = sd("${k}_s13") * l + solar
  state["${k}_s22"] = sd("${k}_s22") * l + tempDiff * tempDiff
  state["${k}_s23"] = sd("${k}_s23") * l + tempDiff * solar
  state["${k}_s33"] = sd("${k}_s33") * l + solar * solar
  state["${k}_t1"]  = sd("${k}_t1")  * l + y
  state["${k}_t2"]  = sd("${k}_t2")  * l + tempDiff * y
  state["${k}_t3"]  = sd("${k}_t3")  * l + solar * y
  state["${k}_syy"] = sd("${k}_syy") * l + y * y
}

// Solve the 3x3 normal equations and compute R^2 for one model. Returns a Map
// [ok, n, b0, b1, b2, r2]; ok=false until there are at least 3 samples or if the
// system is singular (a feature never varied).
Map fit(String k) {
  double n = sd("${k}_n")
  if (n < 3.0d) { return [ok: false, n: n] }

  double s12 = sd("${k}_s12"); double s13 = sd("${k}_s13")
  double s22 = sd("${k}_s22"); double s23 = sd("${k}_s23"); double s33 = sd("${k}_s33")
  double t1 = sd("${k}_t1"); double t2 = sd("${k}_t2"); double t3 = sd("${k}_t3")
  double syy = sd("${k}_syy")

  double[][] a = [[n, s12, s13], [s12, s22, s23], [s13, s23, s33]] as double[][]
  double[] b = [t1, t2, t3] as double[]
  double[] beta = solveLinearSystem(a, b)
  if (beta == null) { return [ok: false, n: n] }

  double ssTot = syy - (t1 * t1) / n
  double ssRes = syy - (beta[0] * t1 + beta[1] * t2 + beta[2] * t3)
  double r2 = ssTot > 1e-9d ? (1.0d - ssRes / ssTot) : 0.0d
  return [ok: true, n: n, b0: beta[0], b1: beta[1], b2: beta[2], r2: Math.max(-1.0d, Math.min(1.0d, r2))]
}

// Fit one model and publish its coefficients as device attributes.
void fitAndEmit(String k) {
  sendEvent(name: "${k}Samples", value: Math.round(sd("${k}_n")))
  Map f = fit(k)
  if (!f.ok) { return }
  double b0 = f.b0 as double
  double b1 = f.b1 as double
  double b2 = f.b2 as double
  sendEvent(name: "${k}Intercept", value: round5(b0))
  sendEvent(name: "${k}Coupling", value: round5(b1))
  sendEvent(name: "${k}SolarGain", value: round5(b2))
  sendEvent(name: "${k}TauMinutes", value: b1 > 1e-5d ? round1(1.0d / b1) : 0)
  sendEvent(name: "${k}RSquared", value: round3(f.r2 as double))
}

// Gauss-Jordan elimination with partial pivoting; returns null if singular.
@CompileStatic
double[] solveLinearSystem(double[][] matrix, double[] rhs) {
  int n = rhs.length
  double[][] m = new double[n][n + 1]
  for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) { m[i][j] = matrix[i][j] }
    m[i][n] = rhs[i]
  }
  for (int col = 0; col < n; col++) {
    int pivot = col
    double best = Math.abs(m[col][col])
    for (int r = col + 1; r < n; r++) {
      double v = Math.abs(m[r][col])
      if (v > best) { best = v; pivot = r }
    }
    if (best < 1e-12d) { return (double[]) null }
    if (pivot != col) { double[] tmp = m[pivot]; m[pivot] = m[col]; m[col] = tmp }
    double pv = m[col][col]
    for (int r = 0; r < n; r++) {
      if (r == col) { continue }
      double factor = m[r][col] / pv
      for (int c = col; c <= n; c++) { m[r][c] -= factor * m[col][c] }
    }
  }
  double[] x = new double[n]
  for (int i = 0; i < n; i++) { x[i] = m[i][n] / m[i][i] }
  return x
}

// --- small helpers ---------------------------------------------------------

double sd(String key) {
  Object v = state[key]
  return v != null ? (v as double) : 0.0d
}

String nowStamp() {
  if (location?.timeZone) { return new Date().format('yyyy-MMM-dd h:mm:ss a', location.timeZone) }
  return new Date().format('yyyy-MMM-dd h:mm:ss a')
}

BigDecimal round1(double v) { return (Math.round(v * 10.0d) / 10.0d) as BigDecimal }
BigDecimal round2(double v) { return (Math.round(v * 100.0d) / 100.0d) as BigDecimal }
BigDecimal round3(double v) { return (Math.round(v * 1000.0d) / 1000.0d) as BigDecimal }
BigDecimal round5(double v) { return (Math.round(v * 100000.0d) / 100000.0d) as BigDecimal }
