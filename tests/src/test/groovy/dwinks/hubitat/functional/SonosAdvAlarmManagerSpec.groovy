package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvAlarmManagerSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvAlarmManager.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    driver.cachedTTSVoiceNames = null
    driver.cachedTTSDefaultVoice = null
    driver.logs.clear()
    driver.events.clear()
  }

  def "TTS voice cache accepts an ArrayList and stores the selected default"() {
    given:
    ArrayList<String> voiceNames = ['Joanna', 'Matthew']

    when:
    driver.updateTTSVoiceCache(voiceNames, 'Joanna')

    then:
    driver.cachedTTSVoiceNames.is(voiceNames)
    driver.cachedTTSVoiceNames == ['Joanna', 'Matthew']
    driver.cachedTTSDefaultVoice == 'Joanna'
    driver.getCachedTTSVoiceNames() == ['Joanna', 'Matthew']
    driver.getCurrentTTSVoice() == 'Joanna'
  }

  def "alarm manager sends refresh requests as device events for the parent app"() {
    given:
    driver.device.deviceNetworkId = 'APP-SONOSALARM-MANAGER-HOUSEHOLD'
    driver.device.dataValues.householdId = 'HOUSEHOLD-1'

    when:
    driver.refresh()

    then:
    driver.events.size() == 1
    driver.events[0].name == 'sonosAlarmRequest'
    Map request = (Map)new JsonSlurper().parseText(driver.events[0].value.toString())
    request.channel == 'alarmManager'
    request.action == 'refresh'
    request.sourceDni == 'APP-SONOSALARM-MANAGER-HOUSEHOLD'
    request.householdId == 'HOUSEHOLD-1'
    request.requestId
  }

  def "alarm manager create validates input and serializes its parent app request"() {
    given:
    driver.device.deviceNetworkId = 'APP-SONOSALARM-MANAGER-HOUSEHOLD'
    driver.device.dataValues.householdId = 'HOUSEHOLD-1'

    when:
    driver.createAlarm('07:30:00', '00:30:00', 'DAILY', 'RINCON_ROOM',
        'x-rincon-mp3radio:test', '', 'NORMAL', 8G, 'false', 'true')

    then:
    driver.events.size() == 1
    Map request = (Map)new JsonSlurper().parseText(driver.events[0].value.toString())
    request.action == 'create'
    request.args.StartLocalTime == '07:30:00'
    request.args.Volume == '8'
    request.args.Enabled == '1'
  }
}
