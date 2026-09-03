package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

/**
 * Regression coverage for irregular humidity reporting. The control
 * derivative must use elapsed time, and a sleeping interval must not dilute
 * the first local derivative once frequent shower readings resume.
 */
class HumidityStatisticsSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File driverFile = new File('../Drivers/Component/HumidityStatistics.groovy')
    assert driverFile.exists(), "Could not find ${driverFile.absolutePath}"
    driver = ScriptLoader.load(driverFile, null, true)
  }

  def "short derivative is time weighted across irregular local samples"() {
    given:
    Long start = 1000000000L
    List history = [
      [time: start, humidity: 50G],
      [time: start + 60000L, humidity: 50.5G],
      [time: start + 180000L, humidity: 52G]
    ]

    when:
    BigDecimal rate = driver.calculateShortTermRate(history, start + 180000L)

    then:
    rate.setScale(3, BigDecimal.ROUND_HALF_UP) == 0.667G
  }

  def "long sleep interval is excluded from the resumed local derivative"() {
    given:
    Long start = 1000000000L
    Long resumed = start + (8L * 60L * 60L * 1000L)
    List history = [
      [time: start, humidity: 50G],
      [time: resumed, humidity: 52G],
      [time: resumed + 120000L, humidity: 55G]
    ]

    when:
    BigDecimal rate = driver.calculateShortTermRate(history, resumed + 120000L)

    then:
    rate.setScale(2, BigDecimal.ROUND_HALF_UP) == 1.50G
  }
}
