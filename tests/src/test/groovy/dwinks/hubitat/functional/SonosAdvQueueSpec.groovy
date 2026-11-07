package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvQueueSpec extends Specification {

  @Shared HubitatScriptHarness queueDriver
  List<List> parentCalls

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvQueue.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    queueDriver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    parentCalls = []
    queueDriver.binding.setVariable('parent', new Expando(
      getQueue: { -> parentCalls << ['getQueue'] },
      removeCurrentlyPlayingItemFromQueue: { -> parentCalls << ['removeCurrentlyPlayingItemFromQueue'] },
      playQueuePosition: { String position -> parentCalls << ['playQueuePosition', position] },
      removeQueueItem: { String itemId, String updateId -> parentCalls << ['removeQueueItem', itemId, updateId] }
    ))
  }

  def "queue child forwards refresh, play, and remove commands to its player"() {
    when:
    queueDriver.getQueue()
    queueDriver.removeCurrentlyPlayingItemFromQueue()
    queueDriver.playQueuePosition('3')
    queueDriver.removeQueueItem('Q:0/4', '12')

    then:
    parentCalls == [
      ['getQueue'],
      ['removeCurrentlyPlayingItemFromQueue'],
      ['playQueuePosition', '3'],
      ['removeQueueItem', 'Q:0/4', '12']
    ]
  }

  def "queue child retains its rendered queue markup in state"() {
    when:
    queueDriver.setQueue('<p>Current queue</p>')

    then:
    queueDriver.state.queue == '<p>Current queue</p>'

    when:
    queueDriver.setQueue(null)

    then:
    queueDriver.state.queue == '<p>The Sonos queue is empty.</p>'
  }
}
