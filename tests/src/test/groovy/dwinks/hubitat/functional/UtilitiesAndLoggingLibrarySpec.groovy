package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Logical-correctness tests for the small, side-effect-free utility methods
 * in {@code Libraries/UtilitiesAndLoggingLibrary.groovy}. Methods that touch
 * Hubitat runtime state (state, settings, scheduling) are tested via the
 * harness's recording stubs - assertions check that the right API calls were
 * made, not real Hubitat behavior.
 */
class UtilitiesAndLoggingLibrarySpec extends Specification {

  @Shared HubitatScriptHarness lib

  def setupSpec() {
    File f = new File('../Libraries/UtilitiesAndLoggingLibrary.groovy')
    assert f.exists(), "Could not find ${f.absolutePath}"
    lib = ScriptLoader.load(f)
  }

  def "convertHexToInt parses a hex string"() {
    expect:
    lib.convertHexToInt('FF')   == 255
    lib.convertHexToInt('1A')   == 26
    lib.convertHexToInt('0A0B') == 2571
  }

  def "convertHexToIP roundtrips with convertIPToHex for any IP"() {
    expect:
    lib.convertHexToIP(lib.convertIPToHex(ip)) == ip

    where:
    ip << ['192.168.50.100', '192.168.1.10', '10.0.0.1', '255.255.255.255', '0.0.0.0']
  }

  def "convertIPToHex always produces 8 uppercase hex chars"() {
    expect:
    lib.convertIPToHex(ip) ==~ /[0-9A-F]{8}/

    where:
    ip << ['192.168.50.100', '192.168.1.10', '10.0.0.1', '0.0.0.0']
  }

  def "convertIPToHex zero-pads single-digit octets"() {
    // Regression test for the original bug where 192.168.1.10 produced
    // 'C0A81A' (6 chars) because the format string was %X instead of %02X.
    expect:
    lib.convertIPToHex('192.168.1.10') == 'C0A8010A'
  }

  def "tryCreateAccessToken populates state.accessToken on first call"() {
    when:
    lib.state.clear()
    lib.tryCreateAccessToken()

    then:
    lib.state.accessToken != null
  }

  def "installed() schedules logsOff after 30 minutes when log preferences are enabled"() {
    given:
    lib.scheduled.clear()
    lib.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: false]

    when:
    lib.installed()

    then:
    lib.scheduled.find { it[1] == 'logsOff' }?.with { it[0] == 1800 }
    lib.scheduled.find { it[1] == 'debugLogsOff' }?.with { it[0] == 1800 }
    lib.scheduled.find { it[1] == 'traceLogsOff' } == null
  }
}
