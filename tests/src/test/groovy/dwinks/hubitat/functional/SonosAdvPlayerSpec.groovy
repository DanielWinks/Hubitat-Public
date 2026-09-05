package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import spock.lang.Shared
import spock.lang.Specification

class SonosAdvPlayerSpec extends Specification {

  @Shared HubitatScriptHarness driver
  @Shared List<String> websocketMessages = []

  def setupSpec() {
    File file = new File('../Drivers/Component/SonosAdvPlayer.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file, new File('../Libraries'), true)
    driver.binding.setVariable('interfaces', [
      webSocket: new Expando(
        sendMessage: { String message -> websocketMessages << message },
        connect: { Object... ignored -> },
        close: { -> }
      )
    ])
  }

  def setup() {
    driver.settings = [logEnable: true, debugLogEnable: true, traceLogEnable: true]
    driver.logs.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    websocketMessages.clear()
    driver.device.deviceNetworkId = 'SONOS-TEST-DNI'
    driver.device.dataValues.clear()
    driver.device.currentValues.clear()
    driver.device.dataValues.id = 'RINCON_TEST'
    driver.device.dataValues.groupId = 'GROUP_TEST'
    driver.device.dataValues.isGroupCoordinator = 'true'
    driver.clearFavoritesMap()
    driver.clearPlaylistsMap()
    driver.favoriteRetryState.clear()
    driver.playlistRetryState.clear()
    driver.pendingGroupedPlayerVolumes.clear()
    driver.groupDeviceUpdateRetryAttempts.clear()
    driver.lastPlaybackState.clear()
    driver.lastMetadataContainerId.clear()
  }

  def "ZGT XML guards reject blank outer and nested payloads"() {
    expect:
    !driver.isPlausibleXmlPayload(null)
    !driver.isPlausibleXmlPayload('   \n\t')
    !driver.isPlausibleXmlPayload('not xml')
    driver.isPlausibleXmlPayload('  <propertyset/>  ')

    and:
    !driver.processZoneGroupTopologyMessages('   ', new LinkedHashSet<String>())
    !driver.processZoneGroupTopologyMessages(
      '<propertyset><property><ZoneGroupState>   </ZoneGroupState></property></propertyset>',
      new LinkedHashSet<String>()
    )
  }

  def "favorite lookup finds an item by Sonos favorite ID instead of map key"() {
    given:
    driver.getFavoritesMap()['object-service-account'] = [
      id: '353', name: 'Amazon Favorite', service: 'Amazon Music'
    ]

    expect:
    driver.findFavoriteById('353').name == 'Amazon Favorite'
    driver.isAmazonMusicService(driver.findFavoriteById('353').service)
    driver.findFavoriteById('999') == null
  }

  def "Amazon favorite gets its play workaround before the first confirmation check"() {
    given:
    driver.getFavoritesMap()['object-service-account'] = [
      id: '353', name: 'Amazon Favorite', service: 'Amazon Music'
    ]

    when:
    driver.loadFavoriteFull('353', 'repeat all', 'replace', 'off', 'true', 'on')

    then:
    driver.scheduled.find { List call -> call[1] == 'playerPlay' }?.getAt(0) == 3
    driver.scheduled.find { List call -> call[1] == 'checkFavoritePlaybackAndRetry' }?.getAt(0) == 12
    driver.favoriteRetryState['SONOS-TEST-DNI'].isAmazon == true
  }

  def "favorite confirmation polls first and does not immediately reload"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver.checkFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.size() == 2
    websocketMessages.any { String message -> message.contains('getPlayback') }
    websocketMessages.any { String message -> message.contains('getMetadataStatus') }
    websocketMessages.every { String message -> !message.contains('loadFavorite') }
    driver.scheduled.last() == [
      2,
      'evaluateFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "favorite evaluation performs one bounded reload only after confirmation fails"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.count { String message -> message.contains('loadFavorite') } == 1
    retryState.attemptNumber == 1
    driver.scheduled.last() == [
      5,
      'checkFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "active pre-existing playback is not interrupted while target confirmation is ambiguous"() {
    given:
    driver.device.currentValues.transportStatus = 'playing'
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.favoriteRetryState['SONOS-TEST-DNI']
    retryState.playbackObserved = true
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.every { String message -> !message.contains('loadFavorite') }
    retryState.attemptNumber == 0
    retryState.ambiguousConfirmationPasses == 1
    driver.scheduled.last() == [
      2,
      'checkFavoritePlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "playlist confirmation also polls before performing a bounded reload"() {
    given:
    driver.loadPlaylistFull('playlist-7', 'repeat all', 'replace', 'off', 'true', 'on')
    Map retryState = driver.playlistRetryState['SONOS-TEST-DNI']
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver.checkPlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.size() == 2
    websocketMessages.any { String message -> message.contains('getPlayback') }
    websocketMessages.any { String message -> message.contains('getMetadataStatus') }
    websocketMessages.every { String message -> !message.contains('loadPlaylist') }

    when:
    websocketMessages.clear()
    driver.evaluatePlaylistPlaybackAndRetry([operationId: retryState.operationId])

    then:
    websocketMessages.count { String message -> message.contains('loadPlaylist') } == 1
    retryState.attemptNumber == 1
    driver.scheduled.last() == [
      5,
      'checkPlaylistPlaybackAndRetry',
      [overwrite: true, data: [operationId: retryState.operationId]]
    ]
  }

  def "stale favorite callbacks cannot act on a newer load operation"() {
    given:
    driver.loadFavoriteFull('42', 'repeat all', 'replace', 'off', 'true', 'on')
    String staleOperationId = driver.favoriteRetryState['SONOS-TEST-DNI'].operationId
    driver.loadFavoriteFull('43', 'repeat all', 'replace', 'off', 'true', 'on')
    websocketMessages.clear()
    driver.scheduled.clear()

    when:
    driver.evaluateFavoritePlaybackAndRetry([operationId: staleOperationId])

    then:
    websocketMessages.empty
    driver.scheduled.empty
    driver.favoriteRetryState['SONOS-TEST-DNI'].favoriteId == '43'
  }

  def "topology membership updates are deferred until the player callback returns"() {
    when:
    driver.parentUpdateGroupDevices('RINCON_COORD', ['RINCON_FOLLOW'])

    then:
    driver.scheduled == [[
      1,
      'deferredParentUpdateGroupDevices',
      [overwrite: true, data: [coordinatorId: 'RINCON_COORD', playersInGroup: ['RINCON_FOLLOW']]]
    ]]
  }

  def "follower group volume forwarding is deferred through the parent boundary"() {
    given:
    driver.device.dataValues.isGroupCoordinator = 'false'
    driver.device.currentValues.isGrouped = 'on'

    when:
    driver.setGroupVolume(40G)

    then:
    driver.scheduled.find { List call -> call[1] == 'emitParentCoordinatorCommand' }?.getAt(0) == 1
    driver.scheduled.find { List call -> call[1] == 'emitParentCoordinatorCommand' }?.getAt(2)?.data?.payload?.command == 'setGroupVolume'
    websocketMessages.empty
  }

  def "grouped player volume is staggered without issuing playback commands"() {
    given:
    driver.device.currentValues.isGrouped = 'on'
    driver.device.dataValues.groupPlayerIds = 'RINCON_FIRST,RINCON_TEST,RINCON_THIRD'

    when:
    driver.setLevel(40G)
    driver.setLevel(41G)

    then:
    websocketMessages.empty
    driver.pendingGroupedPlayerVolumes['RINCON_TEST'] == 41
    driver.scheduled.find { List call -> call[1] == 'flushPendingGroupedPlayerVolume' } == [
      500,
      'flushPendingGroupedPlayerVolume',
      [overwrite: true]
    ]

    when:
    driver.flushPendingGroupedPlayerVolume()

    then:
    websocketMessages.size() == 1
    websocketMessages[0].contains('"namespace":"playerVolume"')
    websocketMessages[0].contains('"command":"setVolume"')
    !websocketMessages[0].contains('"command":"play"')
    !websocketMessages[0].contains('"command":"pause"')
  }
}
