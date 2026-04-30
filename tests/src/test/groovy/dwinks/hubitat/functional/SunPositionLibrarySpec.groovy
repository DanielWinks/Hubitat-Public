package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Logical correctness for SunPositionLibrary. We check the public entry point
 * {@code getPosition(lat, lng)} returns sane values:
 *  - azimuth between 0 and 360 degrees
 *  - altitude between -90 and 90 degrees
 *  - same inputs produce the same output (pure function)
 *
 * Exact astronomical accuracy is out of scope - the goal is to catch
 * structural / algebraic bugs (eg. degree/radian mix-ups) rather than to
 * validate against an ephemeris.
 */
class SunPositionLibrarySpec extends Specification {

  @Shared HubitatScriptHarness lib

  def setupSpec() {
    File f = new File('../Libraries/SunPositionLibrary.groovy')
    assert f.exists(), "Could not find ${f.absolutePath}"
    lib = ScriptLoader.load(f)
  }

  def "getPosition returns azimuth in [0, 360] and altitude in [-90, 90]"() {
    when:
    def position = lib.getPosition(40.7128 as BigDecimal, -74.0060 as BigDecimal)

    then:
    position.containsKey('azimuth')
    position.containsKey('altitude')
    position.azimuth >= 0
    position.azimuth <= 360
    position.altitude >= -90
    position.altitude <= 90
  }

  def "getPosition is deterministic for the same inputs at the same instant"() {
    given:
    BigDecimal lat = 51.5074 as BigDecimal
    BigDecimal lng = -0.1278 as BigDecimal

    when:
    def a = lib.getPosition(lat, lng)
    def b = lib.getPosition(lat, lng)

    then:
    // Two calls within the same millisecond should produce nearly identical
    // values; tolerate tiny floating-point drift across the two invocations.
    Math.abs(a.azimuth - b.azimuth) < 0.01
    Math.abs(a.altitude - b.altitude) < 0.01
  }

  def "different latitudes produce different altitudes (sanity check)"() {
    when:
    def equator = lib.getPosition(0 as BigDecimal, 0 as BigDecimal)
    def arctic  = lib.getPosition(80 as BigDecimal, 0 as BigDecimal)

    then:
    equator.altitude != arctic.altitude
  }
}
