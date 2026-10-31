package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvPlaylistSpec extends Specification {

  @Shared HubitatScriptHarness playlistDriver
  List<List> parentCalls

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvPlaylist.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    playlistDriver = ScriptLoader.load(file, null, true)
  }

  def setup() {
    parentCalls = []
    Expando parentDevice = new Expando()
    parentDevice.loadPlaylistFull = { Object[] args -> parentCalls << args.toList() }
    playlistDriver.binding.setVariable('parent', parentDevice)
  }

  def "append remains append while append-and-play inserts and starts the exact Sonos playlist ID"() {
    when:
    playlistDriver.loadPlaylistAndAppend('playlist-963')
    playlistDriver.loadPlaylistAppendAndPlay('playlist-963')

    then:
    parentCalls == [
      ['playlist-963', 'repeat all', 'append', 'off', 'false', 'on'],
      ['playlist-963', 'repeat all', 'insert', 'off', 'true', 'on']
    ]
  }
}
