package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.MockDevice
import dwinks.hubitat.stubs.ScriptLoader
import groovy.xml.XmlUtil
import hubitat.scheduling.AsyncResponse
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvQueueManagementSpec extends Specification {

  @Shared HubitatScriptHarness player
  MockDevice queueChild
  String rendered

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvPlayer.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    player = ScriptLoader.load(file, new File('../Libraries'), true)
  }

  def setup() {
    player.state.clear()
    player.runtimeState.clear()
    player.settings.clear()
    player.asyncCalls.clear()
    player.logs.clear()
    player.device.dataValues.clear()
    player.device.currentValues.clear()
    player.device.dataValues.id = 'RINCON_TEST'
    player.device.dataValues.localUpnpHost = '192.168.1.50:1400'
    player.device.dataValues.name = 'Test Player'
    player.device.deviceNetworkId = 'SONOS-TEST-DNI'
    player.device.id = 22L
    player.device.metaClass.getHub = { -> new Expando(localIP: '192.168.1.4') }
    rendered = null
    player.children.clear()
    player.metaClass.getQueueChild = { -> player.getChildDevice(player.getQueueChildDNI()) }
    player.metaClass.getDataValue = { String name -> player.device.getDataValue(name) }
  }

  def cleanup() {
    GroovySystem.metaClassRegistry.removeMetaClass(player.device.getClass())
    if(queueChild != null) { GroovySystem.metaClassRegistry.removeMetaClass(queueChild.getClass()) }
    player.metaClass = null
  }

  String browseResponse(String didl, int returned, int total, int updateId) {
    return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><u:BrowseResponse xmlns:u='urn:schemas-upnp-org:service:ContentDirectory:1'><Result>${XmlUtil.escapeXml(didl)}</Result><NumberReturned>${returned}</NumberReturned><TotalMatches>${total}</TotalMatches><UpdateID>${updateId}</UpdateID></u:BrowseResponse></s:Body></s:Envelope>"
  }

  void createQueueChild() {
    player.createRemoveQueueChildDevice(true)
    queueChild = player.getChildDevice(player.getQueueChildDNI()) as MockDevice
    queueChild.metaClass.getIdAsLong = { -> 644L }
    queueChild.metaClass.setQueue = { String html -> rendered = html }
  }

  def "queue child remains optional and follows the player's create and remove preference"() {
    when:
    player.createRemoveQueueChildDevice(false)

    then:
    player.children.empty

    when:
    player.createRemoveQueueChildDevice(true)

    then:
    player.children.size() == 1
    player.children.first().deviceNetworkId == 'SONOS-TEST-DNI-Queue'

    when:
    player.createRemoveQueueChildDevice(false)

    then:
    player.children.empty
  }

  def "Browse reads the queue container and renders escaped track controls with its UpdateID"() {
    given:
    player.settings.createQueueChildDevice = true
    createQueueChild()
    String didl = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="Q:0/7"><dc:title>Track &amp; &lt;One&gt;</dc:title><dc:creator>Artist</dc:creator><upnp:album>Album</upnp:album><upnp:albumArtURI>http://192.168.1.35:1400/getaa?s=1&amp;u=cover</upnp:albumArtURI></item></DIDL-Lite>'

    when:
    player.getQueue()
    Map request = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(didl, 1, 1, 23)), request.data as Map)

    then:
    request.cb == 'queueBrowseCallback'
    request.params.headers.SOAPAction.endsWith('#Browse')
    request.params.body.contains('<ObjectID>Q:0</ObjectID>')
    player.logs.find { it.contains('Could not parse Sonos queue response') } == null
    rendered.contains('Track &amp; &lt;One&gt;')
    rendered.contains('Artist')
    rendered.contains('Album')
    rendered.contains("src='http://192.168.1.35:1400/getaa?s=1&amp;u=cover'")
    rendered.contains('aspect-ratio:1;object-fit:cover')
    rendered.indexOf("class='queue-artist'") < rendered.indexOf("class='queue-image'")
    rendered.indexOf("class='queue-image'") < rendered.indexOf("class='queue-title'")
    rendered.indexOf("class='queue-title'") < rendered.indexOf("class='queue-actions'")
    rendered.contains('display:flex;justify-content:center;gap:8px')
    rendered.contains("style='flex:1;' onclick=")
    rendered.contains('application/json')
    rendered.contains('playQueuePosition')
    rendered.contains('removeQueueItem')
    rendered.contains('&quot;args')
    rendered.contains('Q:0/7')
    rendered.contains('Remove Currently Playing')
    !rendered.contains('queueCall(')
    !rendered.contains('<script>')
    rendered.contains('192.168.1.4/device/runmethod')
    !player.state.containsKey('queueBrowseRequestId')
  }

  def "Browse pages through a queue and ignores an older refresh response"() {
    given:
    player.getQueue()
    Map oldRequest = player.asyncCalls.last()
    player.getQueue()
    Map currentRequest = player.asyncCalls.last()
    String page = browseResponse('<DIDL-Lite/>', 100, 150, 8)

    when:
    player.queueBrowseCallback(new AsyncResponse(data: page), oldRequest.data as Map)
    player.queueBrowseCallback(new AsyncResponse(data: page), currentRequest.data as Map)

    then:
    player.asyncCalls.size() == 3
    player.asyncCalls.last().data.startingIndex == 100
    player.asyncCalls.last().data.requestId == currentRequest.data.requestId
  }

  def "Browse displays an empty queue and reports request errors"() {
    given:
    player.settings.createQueueChildDevice = true
    createQueueChild()

    when:
    player.getQueue()
    Map request = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse('<DIDL-Lite/>', 0, 0, 1)), request.data as Map)

    then:
    rendered.contains('The Sonos queue is empty')
    rendered.contains('Refresh')

    when:
    player.getQueue()
    request = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(status: 503, error: true, errorMessage: 'unavailable'), request.data as Map)

    then:
    rendered.contains('Could not retrieve the Sonos queue')
    !player.state.containsKey('queueBrowseRequestId')

    when:
    player.getQueue()
    request = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: '<malformed'), request.data as Map)

    then:
    rendered.contains('Could not read the Sonos queue response')
    !player.state.containsKey('queueBrowseRequestId')
  }

  def "missing player endpoint clears and supersedes any pending Browse request"() {
    given:
    player.getQueue()
    Map oldRequest = player.asyncCalls.last()
    player.device.dataValues.remove('localUpnpHost')

    when:
    player.getQueue()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse('<DIDL-Lite/>', 0, 0, 1)), oldRequest.data as Map)

    then:
    player.asyncCalls.size() == 1
    !player.state.containsKey('queueBrowseRequestId')
  }

  def "remove currently playing item browses and removes the matching queue resource"() {
    given:
    player.runtimeState.lastTransportPlaybackSnapshot = [trackUri: 'x-sonos-http:track-7.mp3', transportState: 'PLAYING']
    String didl = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/7"><dc:title>Playing Track</dc:title><res>x-sonos-http:track-7.mp3</res></item></DIDL-Lite>'

    when:
    player.removeCurrentlyPlayingItemFromQueue()
    Map browse = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(didl, 1, 1, 23)), browse.data as Map)

    then:
    browse.cb == 'queueBrowseCallback'
    browse.data.action == 'remove-current'
    player.asyncCalls.size() == 2
    player.asyncCalls.last().params.headers.SOAPAction.endsWith('#RemoveTrackFromQueue')
    player.asyncCalls.last().params.body.contains('<ObjectID>Q:0/7</ObjectID>')
    player.asyncCalls.last().params.body.contains('<UpdateID>23</UpdateID>')
    player.logs.any { it.contains('Removing currently playing queue item') }
  }

  def "remove currently playing item uses the one-based CurrentTrack position for duplicate URIs"() {
    given:
    player.runtimeState.lastTransportPlaybackSnapshot = [trackUri: 'x-sonos-http:track-7.mp3', track: '2', transportState: 'PLAYING']
    String firstPage = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/3"><dc:title>First occurrence</dc:title><res>x-sonos-http:track-7.mp3</res></item></DIDL-Lite>'
    String secondPage = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/9"><dc:title>Second occurrence</dc:title><res>x-sonos-http:track-7.mp3</res></item></DIDL-Lite>'

    when:
    player.removeCurrentlyPlayingItemFromQueue()
    Map browse = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(firstPage, 1, 2, 29)), browse.data as Map)
    Map nextPage = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(secondPage, 1, 2, 29)), nextPage.data as Map)

    then:
    nextPage.data.startingIndex == 1
    nextPage.data.currentTrackPosition == '2'
    player.asyncCalls.size() == 3
    player.asyncCalls.last().params.headers.SOAPAction.endsWith('#RemoveTrackFromQueue')
    player.asyncCalls.last().params.body.contains('<ObjectID>Q:0/9</ObjectID>')
    player.asyncCalls.last().params.body.contains('<UpdateID>29</UpdateID>')
  }

  def "remove currently playing item uses a unique URI match when CurrentTrack position is unavailable"() {
    given:
    player.runtimeState.lastTransportPlaybackSnapshot = [trackUri: 'x-sonos-http:track&amp;7.mp3', transportState: 'PLAYING']
    String didl = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1.0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/11"><dc:title>Unique occurrence</dc:title><res>x-sonos-http:track&amp;7.mp3</res></item></DIDL-Lite>'

    when:
    player.removeCurrentlyPlayingItemFromQueue()
    Map browse = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(didl, 1, 1, 31)), browse.data as Map)

    then:
    player.asyncCalls.size() == 2
    player.asyncCalls.last().params.body.contains('<ObjectID>Q:0/11</ObjectID>')
    player.asyncCalls.last().params.body.contains('<UpdateID>31</UpdateID>')
  }

  def "remove currently playing item leaves ambiguous duplicate URIs unchanged without a position"() {
    given:
    player.runtimeState.lastTransportPlaybackSnapshot = [trackUri: 'x-sonos-http:track-7.mp3', transportState: 'PLAYING']
    String firstPage = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/3"><dc:title>First occurrence</dc:title><res>x-sonos-http:track-7.mp3</res></item></DIDL-Lite>'
    String secondPage = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/9"><dc:title>Second occurrence</dc:title><res>x-sonos-http:track-7.mp3</res></item></DIDL-Lite>'

    when:
    player.removeCurrentlyPlayingItemFromQueue()
    Map browse = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(firstPage, 1, 2, 29)), browse.data as Map)
    Map nextPage = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(secondPage, 1, 2, 29)), nextPage.data as Map)

    then:
    player.asyncCalls.size() == 2
    player.asyncCalls.every { it.params.headers.SOAPAction.endsWith('#Browse') }
    player.logs.any { it.contains('URI matches 2 queue entries') && it.contains('CurrentTrack position is unavailable') }
  }

  def "remove currently playing item logs Info when its resource is not in the queue"() {
    given:
    player.runtimeState.lastTransportPlaybackSnapshot = [trackUri: 'x-sonos-http:playing-track.mp3', transportState: 'PLAYING']
    String didl = '<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/"><item id="Q:0/7"><dc:title>Different Track</dc:title><res>x-sonos-http:other-track.mp3</res></item></DIDL-Lite>'

    when:
    player.removeCurrentlyPlayingItemFromQueue()
    Map browse = player.asyncCalls.last()
    player.queueBrowseCallback(new AsyncResponse(data: browseResponse(didl, 1, 1, 23)), browse.data as Map)

    then:
    player.asyncCalls.size() == 1
    player.logs.any { it.contains('Currently playing item is not in the Sonos queue') }
  }

  def "remove currently playing item logs Info when no active transport item is available"() {
    when:
    player.removeCurrentlyPlayingItemFromQueue()

    then:
    player.asyncCalls.empty
    player.logs.any { it.contains('Currently playing item is not in the Sonos queue') }
  }

  def "play by queue position uses one-based TRACK_NR and rejects invalid positions"() {
    when:
    player.playQueuePosition('4')
    Map request = player.asyncCalls.last()
    player.playQueuePosition('0')
    player.playQueuePosition('invalid')

    then:
    request.params.headers.SOAPAction.endsWith('#Seek')
    request.params.body.contains('<Unit>TRACK_NR</Unit>')
    request.params.body.contains('<Target>4</Target>')
    player.asyncCalls.size() == 1
  }

  def "remove uses the item ObjectID and queue UpdateID then refreshes"() {
    when:
    player.removeQueueItem('Q:0/7', '23')
    Map request = player.asyncCalls.last()
    player.queueMutationCallback(new AsyncResponse(status: 200), request.data as Map)

    then:
    request.params.headers.SOAPAction.endsWith('#RemoveTrackFromQueue')
    request.params.body.contains('<ObjectID>Q:0/7</ObjectID>')
    request.params.body.contains('<UpdateID>23</UpdateID>')
    player.asyncCalls.size() == 2
    player.asyncCalls.last().cb == 'queueBrowseCallback'

    when:
    player.asyncCalls.clear()
    player.removeQueueItem('bad-id', '23')
    player.removeQueueItem('Q:0/7', 'stale')

    then:
    player.asyncCalls.empty
  }
}
