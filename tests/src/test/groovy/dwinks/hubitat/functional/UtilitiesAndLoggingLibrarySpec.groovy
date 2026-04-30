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

  def "convertHexToIP roundtrips when every octet is >= 16"() {
    given:
    String ip = '192.168.50.100'

    expect:
    // The roundtrip only works when every octet's hex form is two characters.
    // See `convertIPToHex BUG` test below for the gotcha.
    lib.convertHexToIP(lib.convertIPToHex(ip)) == ip
  }

  def "convertIPToHex with byte-sized octets produces 8 uppercase hex chars"() {
    expect:
    lib.convertIPToHex('255.255.255.255') == 'FFFFFFFF'
    lib.convertIPToHex('192.168.50.100') ==~ /[0-9A-F]{8}/
  }

  def "convertIPToHex BUG: octets less than 16 produce non-zero-padded hex"() {
    // Real bug in the library - documenting it here as a regression test.
    // When this is patched to %02X (zero-padded), update the expectation to
    // 'C0A8010A' / length 8.
    expect:
    lib.convertIPToHex('192.168.1.10') == 'C0A81A'
    lib.convertIPToHex('192.168.1.10').length() == 6
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
