package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Logical-correctness specs for the Solar Shade & Window Advisor app. The app is
 * intentionally STANDALONE (no #include libraries), so the pure math that used
 * to live in SolarShadeLibrary - solar load, occlusion geometry, online
 * regression, forecasting, the four-state decision, and the inlined sun-position
 * trig - is tested directly against the loaded app. Includes the user's two
 * worked max-deviation examples.
 */
class SolarShadeWindowAdvisorSpec extends Specification {

  @Shared HubitatScriptHarness app

  def setupSpec() {
    File f = new File('../Apps/SolarShadeWindowAdvisor/SolarShadeWindowAdvisor.groovy')
    assert f.exists(), "Could not find ${f.absolutePath}"
    app = ScriptLoader.load(f)
  }

  // --- Solar geometry -------------------------------------------------------

  def "solar incidence is full when sun is square-on the wall, zero when off to the side or below horizon"() {
    expect:
    Math.abs(app.solarIncidenceCos(30.0d, 180.0d, 180.0d) - Math.cos(Math.toRadians(30.0d))) < 1e-9
    app.solarIncidenceCos(30.0d, 90.0d, 180.0d) < 1e-9
    app.solarIncidenceCos(30.0d, 0.0d, 180.0d) == 0.0d
    app.solarIncidenceCos(-5.0d, 180.0d, 180.0d) == 0.0d
  }

  def "irradiance on wall combines beam and diffuse; normalized load is 0-100"() {
    when:
    double irr = app.irradianceOnWall(800.0d, 100.0d, 30.0d, 180.0d, 180.0d)
    double load = app.normalizedSolarLoad(800.0d, 100.0d, 30.0d, 180.0d, 180.0d)

    then:
    Math.abs(irr - (800.0d * Math.cos(Math.toRadians(30.0d)) + 50.0d)) < 1e-6
    load > 0.0d && load <= 100.0d
    Math.abs(load - irr / 10.0d) < 1e-6
  }

  def "normalized load clamps to 100 for an extreme irradiance"() {
    expect:
    app.normalizedSolarLoad(5000.0d, 500.0d, 10.0d, 180.0d, 180.0d) == 100.0d
  }

  // --- Occlusion ------------------------------------------------------------

  def "fixed occlusion handles full / partial / none"() {
    expect:
    app.fixedOcclusionFraction('full', 0.0d) == 1.0d
    app.fixedOcclusionFraction('partial', 40.0d) == 0.4d
    app.fixedOcclusionFraction('none', 0.0d) == 0.0d
    app.fixedOcclusionFraction('partial', 250.0d) == 1.0d
  }

  def "shaded occlusion: tall close structure with low sun fully shades; high sun does not; off-axis sun does not"() {
    expect:
    app.shadedOcclusionFraction(20.0d, 180.0d, 180.0d, 10.0d, 5.0d, 10.0d, 10.0d, 8.0d) == 1.0d
    app.shadedOcclusionFraction(70.0d, 180.0d, 180.0d, 10.0d, 5.0d, 10.0d, 10.0d, 8.0d) == 0.0d
    app.shadedOcclusionFraction(20.0d, 90.0d, 180.0d, 10.0d, 5.0d, 10.0d, 10.0d, 8.0d) == 0.0d
  }

  def "effective solar load applies occlusion"() {
    expect:
    Math.abs(app.effectiveSolarLoad(80.0d, 0.25d) - 60.0d) < 1e-9
    app.effectiveSolarLoad(80.0d, 1.0d) == 0.0d
  }

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

  // --- Forecasting & decision logic -----------------------------------------

  def "windows-open trajectory captures min/max/end plus the near-term lookahead value"() {
    given: 'start 72F, hot 88F outdoor, strong solar, b1=1/45min -> house heats steadily'
    double[] tOut = [88.0d, 88.0d, 88.0d, 88.0d, 88.0d, 88.0d, 88.0d, 88.0d] as double[]
    double[] solar = [90.0d, 90.0d, 90.0d, 90.0d, 90.0d, 90.0d, 90.0d, 90.0d] as double[]

    when:
    // signature: (tStart, tOutSeq, solarSeq, stepMin, b0, b1, b2, leadSteps)
    double[] tr = app.forecastWindowsOpenTrajectory(72.0d, tOut, solar, 10.0d, 0.0d, 0.02222d, 0.0007d, 3)

    then:
    tr[2] > 80.0d        // end temp climbed well above the start
    tr[2] <= 95.0d       // bounded near equilibrium (no Euler blow-up)
    tr[1] >= tr[2]       // max >= end while monotonically warming
    tr[3] > 72.0d        // near-term (step 3) already above the start
    tr[3] < tr[2]        // ...but below the end (still warming)
  }

  def "shouldCloseWindows is two-horizon: stays open through a temporary warm spell, closes at the last moment"() {
    expect:
    // signature: (projNearTerm, projEnd, coolSP, heatSP, maxDevHot, maxDevCold, reopenMargin); ceiling = 74+3 = 77
    // Reported morning defect: cool now/near (74) but hot by +3h (85) -> still OPEN (close later)
    !app.shouldCloseWindows(74.0d, 85.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
    // Later that morning the near term finally breaches (80) and the end is hot (88) -> CLOSE
    app.shouldCloseWindows(80.0d, 88.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
    // Evening: warm near term (77) but cooling to 69 by horizon end -> OPEN (tolerate temporary warmth)
    !app.shouldCloseWindows(77.0d, 69.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
    // Hot now and staying hot (HVAC-cooled house, opening projects to 90) -> CLOSE
    app.shouldCloseWindows(82.0d, 90.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
    // Cold tolerated: end 60F (below heat setpoint but within loose floor 56) -> OPEN
    !app.shouldCloseWindows(64.0d, 60.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
    // Genuinely too cold: end 50F (below floor 56) -> CLOSE
    app.shouldCloseWindows(55.0d, 50.0d, 74.0d, 66.0d, 3.0d, 10.0d, 0.0d)
  }

  def "shade hysteresis: draws above threshold, holds within hysteresis band, never draws while heating"() {
    expect:
    app.shadeStateWithHysteresis(false, 70.0d, 60.0d, 10.0d, true)
    app.shadeStateWithHysteresis(true, 55.0d, 60.0d, 10.0d, true)
    !app.shadeStateWithHysteresis(true, 45.0d, 60.0d, 10.0d, true)
    !app.shadeStateWithHysteresis(false, 90.0d, 60.0d, 10.0d, false)
  }

  def "buildAdvisory produces the recommendation key, recs, and an action-phrased message"() {
    expect:
    app.buildAdvisory(true, true).key == 'win:closed|shade:draw'
    app.buildAdvisory(true, false).key == 'win:closed|shade:open'
    app.buildAdvisory(false, true).key == 'win:open|shade:draw'
    app.buildAdvisory(false, false).key == 'win:open|shade:open'
    app.buildAdvisory(true, false).windowRec == 'closed'
    app.buildAdvisory(false, false).shadeRec == 'open'
    // messages are phrased as the action to take
    app.buildAdvisory(true, false).message.startsWith('Close the windows')
    app.buildAdvisory(false, false).message.startsWith('Open the windows')
  }

  // --- Inlined sun-position trig --------------------------------------------

  def "daysSinceJ2000 increases by exactly one per 86,400,000 ms"() {
    when:
    double d0 = app.daysSinceJ2000(1700000000000L)
    double d1 = app.daysSinceJ2000(1700000000000L + 86400000L)

    then:
    Math.abs((d1 - d0) - 1.0d) < 1e-9
  }

  def "getPositionForDays returns azimuth in [0,360], altitude in [-90,90], and varies across the day"() {
    given:
    BigDecimal lat = 40.7128 as BigDecimal
    BigDecimal lng = -74.0060 as BigDecimal
    double d = app.daysSinceJ2000(1700000000000L)

    when:
    def noonish = app.getPositionForDays(lat, lng, d)
    def halfDayLater = app.getPositionForDays(lat, lng, d + 0.5d)

    then:
    noonish.azimuth >= 0 && noonish.azimuth <= 360
    noonish.altitude >= -90 && noonish.altitude <= 90
    // 12 hours apart -> the sun has moved
    noonish.altitude != halfDayLater.altitude
  }
}
