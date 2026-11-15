package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvAlarmSwitchSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvAlarmSwitch.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    driver.device.deviceNetworkId = 'APP-SonosAlarm-HOUSEHOLD-ALARM'
    driver.device.dataValues.clear()
    driver.events.clear()
    driver.device.dataValues.householdId = 'HOUSEHOLD-1'
  }

  def "switch commands publish typed parent app requests"() {
    when:
    driver.on()
    driver.setAlarmTime('07:30:00')
    driver.setAlarmVolume(8G)
    driver.setAlarmSource('x-rincon-mp3radio:test', 'metadata')
    driver.setIncludeGroupedRooms('true')

    then:
    driver.events.size() == 5
    List<Map> requests = driver.events.collect { Map event ->
      (Map)new JsonSlurper().parseText(event.value.toString())
    }
    requests*.channel.every { String channel -> channel == 'alarmSwitch' }
    requests*.action == ['setEnabled', 'setTime', 'setVolume', 'setSource', 'setIncludeGroupedRooms']
    requests[0].value == true
    requests[2].value == 8
    requests[3].value == [programUri: 'x-rincon-mp3radio:test', programMetadata: 'metadata']
    requests[4].value == true
    requests.every { Map request -> request.sourceDni == 'APP-SonosAlarm-HOUSEHOLD-ALARM' && request.requestId }
    driver.events.every { Map event -> event.name == 'sonosAlarmRequest' && event.isStateChange == true }
  }

  def "refresh request carries the configured household id"() {
    when:
    driver.refresh()

    then:
    Map request = (Map)new JsonSlurper().parseText(driver.events[0].value.toString())
    request.action == 'refresh'
    request.value == 'HOUSEHOLD-1'
  }

  def "switch exposes every remaining alarm setting through app request events"() {
    when:
    driver.off()
    driver.setRecurrence('WEEKDAYS')
    driver.setDuration('00:15:00')
    driver.setAlarmRoom('RINCON_ROOM')
    driver.setPlayMode('NORMAL')

    then:
    List<Map> requests = driver.events.collect { Map event ->
      (Map)new JsonSlurper().parseText(event.value.toString())
    }
    requests*.action == ['setEnabled', 'setRecurrence', 'setDuration', 'setRoom', 'setPlayMode']
    requests*.value == [false, 'WEEKDAYS', '00:15:00', 'RINCON_ROOM', 'NORMAL']
  }
}
