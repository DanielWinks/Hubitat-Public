package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import com.hubitat.app.DeviceWrapper
import spock.lang.Shared
import spock.lang.Specification

/**
 * Regression coverage for the bathroom controller's external humidity floor
 * and slowly adapting control baseline.
 */
class BathroomFanControllerSpec extends Specification {

  @Shared HubitatScriptHarness app

  def setupSpec() {
    File appFile = new File('../Apps/BathroomFanController/BathroomFanControllerChild.groovy')
    assert appFile.exists(), "Could not find ${appFile.absolutePath}"
    app = ScriptLoader.load(appFile, null, true)
  }

  def setup() {
    app.state.clear()
    app.settings.clear()
  }

  def "household humidity is the floor for the control baseline candidate"() {
    expect:
    app.selectControlBaselineCandidate(52.5G, 60G) == 60G
    app.selectControlBaselineCandidate(63G, 60G) == 63G
    app.selectControlBaselineCandidate(52.5G, null) == 52.5G
  }

  def "stable control baseline limits a long-gap correction"() {
    when:
    BigDecimal result = app.calculateStableControlBaseline(52.5G, 60G, 8L * 60L * 60L * 1000L)

    then:
    result == 54.375G
  }

  def "stale household readings are not used as the external floor"() {
    given:
    Long now = System.currentTimeMillis()
    app.state.householdHumidity = '60'
    app.state.householdHumidityAt = (now - (7L * 60L * 60L * 1000L)).toString()

    expect:
    app.getFreshHouseholdHumidity(now) == null
  }

  def "overnight bathroom rise below the household reading cannot qualify"() {
    expect:
    !app.isAboveHouseholdBaseline(55G, 60G, 0G)
    app.isAboveHouseholdBaseline(61G, 60G, 0G)
    app.isAboveHouseholdBaseline(55G, null, 0G)
  }

  def "a post-gap reading cannot start the fan without a local derivative"() {
    given:
    Long now = System.currentTimeMillis()
    DeviceWrapper fan = new DeviceWrapper(currentValues: [switch: 'off'])
    app.settings.putAll([
      fanSwitch: fan,
      absoluteCeiling: 95,
      highCertaintyModes: [],
      disallowedModes: []
    ])
    app.state.putAll([
      lastHumidity: '54',
      householdHumidity: '60',
      householdHumidityAt: now.toString()
    ])

    when:
    app.evaluateFanDecision(55G, 60G, null, null, true)

    then:
    fan.currentValue('switch') == 'off'
    app.state.riseCandidateCount == 0
  }
}
