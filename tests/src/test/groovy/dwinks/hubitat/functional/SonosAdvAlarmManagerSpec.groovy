package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
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
}
