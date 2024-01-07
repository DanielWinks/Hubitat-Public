/**
 *  MIT License
 *  Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
*/

#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.SMAPILibrary

definition(
  name: 'Sonos Advanced Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'Audio',
  description: 'Sonos speaker integration with advanced functionality, such as non-interrupting announcements and grouping control',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: false,
  iconX3Url: '',
  singleThreaded: true,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosAdvancedApp.groovy'
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
  page(name: 'localPlayerPage')
  page(name: 'localPlayerSelectionPage')
  page(name: 'groupPage')
}

@CompileStatic
String getLocalApiPrefix(String ipAddress) {
  return "https://${ipAddress}/api/v1"
}


// =============================================================================
// Main Page
// =============================================================================

Map mainPage() {
  state.remove("discoveryRunning")
  boolean configured = settings.clientKey != null && settings.clientSecret != null
  boolean authenticated = state.authToken != null
  dynamicPage(title: 'Sonos Advanced Controller') {
    section {
      label title: 'Sonos Advanced Controller',
      required: false
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'

      href (
        page: 'localPlayerPage',
        title: 'Sonos Virtual Player Devices',
        description: 'Select to create Sonos player devices using local discovery'
      )
      href (
        page: 'groupPage',
        title: 'Sonos Virtual Group Devices',
        description: 'Select to create/delete Sonos group devices'
      )
    }

    section {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input 'applySettingsButton', 'button', title: 'Apply Settings'
      input 'createPlayerDevices', 'button', title: 'Create Players'
    }
  }
}

// =============================================================================
// Local Discovery Helpers
// =============================================================================

void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
	sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
	sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))
}

void subscribeToSsdpEvents() {
  logDebug("Subscribing to SSDP Discovery events...")
	subscribe(location, 'ssdpTerm.upnp:rootdevice', 'ssdpEventHandler')
	subscribe(location, 'sdpTerm.ssdp:all', 'ssdpEventHandler')
}

void ssdpEventHandler(Event event) {
	Map parsedEvent = parseLanMessage(event.description)

  String ipAddress = convertHexToIP(parsedEvent.networkAddress)
	String ipPort = convertHexToInt(parsedEvent.deviceAddress)

  Map playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")

  String modelName = deviceDescription['device']['modelName']
	String ssdpPath = parsedEvent.ssdpPath
	String mac = parsedEvent.mac as String
  String playerName = playerInfo?.device?.name
  String playerDni = parsedEvent.mac
  String swGen = playerInfo?.device?.swGen
  String websocketUrl = playerInfo?.device?.websocketUrl
  String householdId = playerInfo?.householdId
  String playerId = playerInfo?.playerId
  String groupId = playerInfo?.groupId
  List<String> deviceCapabilities = playerInfo?.device?.capabilities

  discoveredSonoses[mac] = [
    name: playerInfo?.device?.name,
    id: playerInfo?.playerId,
    swGen: playerInfo?.device?.swGen,
    capabilities: playerInfo?.device?.capabilities,
    modelName: deviceDescription['device']['modelName'],
    householdId: playerInfo?.householdId,
    websocketUrl: playerInfo?.device?.websocketUrl,
    deviceIp: "${ipAddress}",
    localApiUrl: "https://${ipAddress}:1443/api/v1/",
    localUpnpUrl: "http://${ipAddress}:1400",
    localUpnpHost: "${ipAddress}:1400"
  ]
}

// =============================================================================
// Player and Group Pages
// =============================================================================

@Field static Map playerSelectionOptions = new java.util.concurrent.ConcurrentHashMap()
@Field static Map discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()

Map localPlayerPage() {
  if(!state.discoveryRunning) {
    subscribeToSsdpEvents()
    ssdpDiscover()
    state.discoveryRunning = true
    discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()
  }
  app.removeSetting('playerDevices')
  app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  String foundDevices = ''
  discoveredSonoses.each{ discoveredSonos -> foundDevices += "\n${discoveredSonos.value?.name}" }

  dynamicPage(
		name: "localPlayerPage",
		title: "Discovery Started!",
		nextPage: 'localPlayerSelectionPage',
    refreshInterval: 3,
		install: false,
		uninstall: false
  ) {
    section("Please wait while we discover your Sonos. Discovery can take five minutes or more, so sit back and relax! Click Next once discovered.") {
      paragraph ("Found Devices (${discoveredSonoses.size()}): ${foundDevices}")
    }
  }
}

Map localPlayerSelectionPage() {
  state.remove("discoveryRunning")
	unsubscribe(location, 'ssdpTerm.upnp:rootdevice')
	unsubscribe(location, 'sdpTerm.ssdp:all')
  Map selectionOptions = discoveredSonoses.collectEntries{id, player -> [(id): player.name]}
  dynamicPage(
		name: "localPlayerSelectionPage",
		title: "Select Sonos Devices",
		nextPage: 'mainPage',
		install: false,
		uninstall: false
  ) {
    section("Select your device(s) below.") {
      input (
        name: 'playerDevices',
        title: "Select Sonos (${discoveredSonoses.size()} found)",
        type: 'enum',
        options: selectionOptions,
        multiple: true,
        submitOnChange: true
      )
      href (
        page: 'localPlayerPage',
        title: 'Continue Search',
        description: 'Click to show'
      )
      List<ChildDeviceWrapper> willBeRemoved = getCurrentPlayerDevices().findAll { p -> (!settings.playerDevices.contains(p.getDeviceNetworkId()) )}
      if(willBeRemoved.size() > 0) {
        paragraph("The following devices will be removed: ${willBeRemoved.collect{it.getDataValue('name')}.join(', ')}")
      }
      List<String> willBeCreated = (settings.playerDevices - getCreatedPlayerDevices())
      if(willBeCreated.size() > 0) {
        String s = willBeCreated.collect{p -> selectionOptions.get(p)}.join(', ')
        paragraph("The following devices will be created: ${s}")
      }
    }
  }
}

List<String> getCreatedPlayerDevices() {
  List<ChildDeviceWrapper> childDevices = app.getCurrentPlayerDevices()
  List<String> pds = []
  childDevices.each() {cd -> pds.add("${cd.getDeviceNetworkId()}")}
  return pds
}

List<String> getCreatedGroupDevices() {
  List<ChildDeviceWrapper> childDevices = app.getCurrentGroupDevices()
  List<String> pds = []
  childDevices.each() {cd -> pds.add("${cd.getDeviceNetworkId()}")}
  return pds
}

List<String> getUserGroupsDNIsFromUserGroups() {
  List<String> dnis = state.userGroups.collect{ k,v -> "${app.id}-SonosGroupDevice-${k}" }
  return dnis
}

List<ChildDeviceWrapper> getCurrentPlayerDevices() {
  return app.getChildDevices().findAll{ child -> child.getDataValue('id')}
}

List<ChildDeviceWrapper> getCurrentGroupDevices() {
  return app.getChildDevices().findAll{ child -> child.getDataValue('id') == null}
}

Map groupPage() {
  if(!state.userGroups) { state.userGroups = [:] }
  Map coordinatorSelectionOptions = getCurrentPlayerDevices().collectEntries { player -> [(player.getDataValue('id')): player.getDataValue('name')] }
  Map playerSelectionOptions = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator }

  String edg = app.getSetting('editDeleteGroup')
  if(edg) {
    if(!app.getSetting('newGroupName')) {
      app.updateSetting('newGroupName', edg)
    }
    if(!app.getSetting('newGroupPlayers')) {
      app.updateSetting('newGroupPlayers', [type: 'enum', value: state.userGroups[edg]?.playerIds])
    }
    if(!app.getSetting('newGroupCoordinator')) {
      app.updateSetting('newGroupCoordinator', [type: 'enum', value: state.userGroups[edg]?.coordinatorId])
    }
    playerSelectionOptions = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator }
  }
  if(app.getSetting('newGroupName') == null || app.getSetting('newGroupPlayers') == null || app.getSetting('newGroupCoordinator') == null) {
    state.groupPageError = 'You must select a coordinator, at least 1 follower player, and provide a name for the group'
  } else {
    state.remove('groupPageError')
  }

  dynamicPage(title: 'Sonos Player Virtual Groups', nextPage: 'mainPage') {
    section {
      paragraph ('This page allows you to create and delete Sonos group devices.')
    }

    section ('Select players to add to new group:', hideable: true){
      if(state.groupPageError) {
        paragraph (state.groupPageError)
      }
      input(name: 'newGroupName', type: 'text', title: 'Group Name:', required: false, submitOnChange: true)
      input(name: 'newGroupCoordinator', type: 'enum', title: 'Select Coordinator (leader):', multiple: false, options: coordinatorSelectionOptions, required: false, submitOnChange: true)
      input(name: 'newGroupPlayers', type: 'enum', title: 'Select Players (followers):', multiple: true, options: playerSelectionOptions, required: false, submitOnChange: true, offerAll: true)
      input(name: 'saveGroup', type: 'button', title: 'Save Group?', submitOnChange: true, width: 2, disabled: state.groupPageError != null)
      input(name: 'deleteGroup', type: 'button', title: 'Delete Group?', submitOnChange: true, width: 2)
      input(name: 'cancelGroupEdit', type: 'button', title: 'Cancel Edit?', submitOnChange: true, width: 2)
    }

    section ("Select virtual groups to edit/delete (${state.userGroups.size()} active groups found):", hideable: true) {
      input (name: 'editDeleteGroup', title: '', type: 'enum', multiple: false, options: state.userGroups.keySet(), submitOnChange: true)
    }
    if(state.refreshGroupPage) {
      section() {
        state.remove('refreshGroupPage')
        app.removeSetting('newGroupName')
        app.removeSetting('newGroupPlayers')
        app.removeSetting('newGroupCoordinator')
        paragraph "<script>{changeSubmit(this)}</script>"
      }
    }
  }
}

// =============================================================================
// Button Handlers
// =============================================================================
void appButtonHandler(String buttonName) {
  if(buttonName == 'applySettingsButton') { applySettingsButton() }
  if(buttonName == 'saveGroup') { saveGroup() }
  if(buttonName == 'deleteGroup') { deleteGroup() }
  if(buttonName == 'cancelGroupEdit') { cancelGroupEdit() }
  if(buttonName == 'createPlayerDevices') { createPlayerDevices() }
}

void applySettingsButton() { configure() }

void saveGroup() {
  state.userGroups[app.getSetting('newGroupName')] = [coordinatorId:app.getSetting('newGroupCoordinator'), playerIds:app.getSetting('newGroupPlayers')]
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  app.removeSetting('editDeleteGroup')

  createGroupDevices()
}

void deleteGroup() {
  state.userGroups.remove(app.getSetting('newGroupName'))
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  state.refreshGroupPage = true
  removeOrphans()
}

void cancelGroupEdit() {
  app.removeSetting('newGroupName')
  app.removeSetting('newGroupPlayers')
  app.removeSetting('newGroupCoordinator')
  app.removeSetting('editDeleteGroup')
}

// =============================================================================
// Intilize() and Configure()
// =============================================================================

void initialize() {
  tryCreateAccessToken()
  configure()
}

void configure() {
  logInfo("${app.name} updated")
  unsubscribe()
  try { createPlayerDevices() }
  catch (Exception e) { logError("createPlayerDevices() Failed: ${e}")}
  try { createGroupDevices() }
  catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
  // runIn(3, 'appGetFavorites', [overwrite: true])
}

// =============================================================================
// Create Group and Player Devices
// =============================================================================

void createGroupDevices() {
  if(!state.userGroups) {return}
  logDebug('Creating group devices...')
  state.userGroups.each{ it ->
    String dni = "${app.id}-SonosGroupDevice-${it.key}"
    DeviceWrapper device = getChildDevice(dni)
    if (device == null) {
      try {
        logDebug("Creating group device for ${it.key}")
        device = addChildDevice('dwinks', 'Sonos Advanced Group', dni, [name: 'Sonos Group', label: "Sonos Group: ${it.key}"])
        String coordinatorId = it.value.coordinatorId as String
        String playerIds =  it.value.playerIds.join(',')
        ChildDeviceWrapper coordDev = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId}
        String householdId = coordDev.getDataValue('householdId')
        device.updateDataValue('coordinatorId', coordinatorId)
        device.updateDataValue('playerIds', playerIds)
        device.updateDataValue('householdId', householdId)
      } catch (UnknownDeviceTypeException e) {
        logException('createGroupDevices', e)
      }
    }
  }
  app.removeSetting('groupDevices')
  app.updateSetting('groupDevices', [type: 'enum', value: getCreatedGroupDevices()])
  removeOrphans()
}

void createPlayerDevices() {
  List<String> willBeCreated = (settings.playerDevices - getCreatedPlayerDevices())
  logDebug(willBeCreated)
  logDebug(discoveredSonoses.keySet())

  willBeCreated.each{ dni ->
    ChildDeviceWrapper cd = app.getChildDevice(dni)
    if(cd) {
      logDebug("Not creating ${cd.getDataValue('name')}, child already exists.")
    } else {
      Map playerInfo = discoveredSonoses[dni]
      logInfo("Creating Sonos Advanced Player device for ${playerInfo?.name}")
      try {
        device = addChildDevice('dwinks', 'Sonos Advanced Player', dni, [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo?.name}"])
        playerInfo.each { key, value -> device.updateDataValue(key, value as String) }
        device.secondaryConfiguration()
      } catch (UnknownDeviceTypeException e) {
        logException('Sonos Advanced Player driver not found', e)
      }
    }
  }
  removeOrphans()
}

void removeOrphans() {
  app.getChildDevices().each{ child ->
    String dni = child.getDeviceNetworkId()
    if(dni in settings.playerDevices || dni in settings.groupDevices) { return }
    else {
      logInfo("Removing child not found in selected devices list: ${child}")
      app.deleteChildDevice(dni)
    }
  }
}

// =============================================================================
// Helper methods
// =============================================================================

List<String> getAllPlayersForGroupDevice(DeviceWrapper device) {
  List<String> playerIds = [device.getDataValue('coordinatorId')]
  playerIds.addAll(device.getDataValue('playerIds').split(','))
  return playerIds
}

Map getPlayerInfoLocalSync(String ipAddress) {
  ipAddress = ipAddress.contains(':') ? ipAddress : "${ipAddress}:1443"
  Map params = [
    uri:  "${getLocalApiPrefix(ipAddress)}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json',
    contentType: 'application/json',
    ignoreSSLIssues: true
  ]
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data }
    else { logError(resp.data) }
  }
}

GPathResult getDeviceDescriptionLocalSync(String ipAddress) {
  if(!ipAddress.contains(':')) { ipAddress = "${ipAddress}:1400"}
  Map params = [
    uri:  "http://${ipAddress}/xml/device_description.xml",,
    requestContentType: 'application/xml',
    contentType: 'application/xml'
  ]
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data }
    else { logError(resp.data) }
  }
}

void getPlayerInfoLocal(String ipAddress) {
  Map params = [
    uri:  "${getLocalApiPrefix(ipAddress)}/players/local/info",
    headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
    requestContentType: 'application/json',
    contentType: 'application/json'
  ]
  sendCommandAsync(params, 'getPlayerInfoLocalCallback')
}

void getPlayerInfoLocalCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getPlayerInfoLocalCallback')) { return }
  Map respJson = response.getJson()
  String playerName = respJson?.device?.name
  String playerDni = (respJson?.device?.serialNumber.replace('-','').tokenize(':'))[0]
  String swGen = respJson?.device?.swGen
  String websocketUrl = respJson?.device?.websocketUrl
  String householdId = respJson?.householdId
  String playerId = respJson?.playerId
  String groupId = respJson?.groupId
}

String getGroupForPlayerDevice(DeviceWrapper device) {
  logDebug("Got groupId for player: ${device.getDataValue('groupId')}")
  return device.getDataValue('groupId')
}

void getZoneGroupAttributesAsync(DeviceWrapper device, String callbackMethod = 'getZoneGroupAttributesAsyncCallback', Map data = null) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  asynchttpPost(callbackMethod, params, data)
}

void getZoneGroupAttributesAsyncCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getZoneGroupAttributesAsyncCallback')) { return }
}

List<DeviceWrapper> getGroupedPlayerDevicesFromGetZoneGroupAttributes(GPathResult xml, String rincon) {
  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  List<String> groupedRincons = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text().tokenize(',')
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}
  return groupedDevices
}

String getGroupForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZoneGroupID'].text()
    }
    else { logError(resp.data) }
  }
  return groupId
}

String getHouseholdForPlayerDeviceLocal(DeviceWrapper device) {
  logDebug(device)
  String ip = device.getDataValue('localUpnpHost')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentMuseHouseholdId'].text()
    }
    else { logError(resp.data) }
  }
  return groupId
}

// =============================================================================
// Component Methods for Child Events
// =============================================================================

void processAVTransportMessages(DeviceWrapper cd, Map message) {
  Boolean isGroupCoordinator = cd.getDataValue('id') == cd.getDataValue('groupCoordinatorId')
  if(!isGroupCoordinator) { return }

  GPathResult propertyset = parseSonosMessageXML(message)

  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  List<String> groupedRincons = (cd.getDataValue('groupIds')).tokenize(',')
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}

  String trackUri = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackURI']['@val']
  Boolean isAirPlay = trackUri.toLowerCase().contains('airplay')

  String status = propertyset['property']['LastChange']['Event']['InstanceID']['TransportState']['@val']
  status = status.toLowerCase().replace('_playback','')

  groupedDevices.each{dev ->
    if(dev) {
      dev.sendEvent(name:'status', value: status)
      dev.sendEvent(name:'transportStatus', value: status)
    }
  }

  String currentPlayMode = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentPlayMode']['@val']
  groupedDevices.each{dev -> if(dev) { dev.setPlayMode(currentPlayMode)}}

  String currentCrossfadeMode = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentCrossfadeMode']['@val']
  currentCrossfadeMode = currentCrossfadeMode=='1' ? 'on' : 'off'
  groupedDevices.each{dev -> if(dev) {dev.setCrossfadeMode(currentCrossfadeMode)}}

  String currentTrackDuration = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackDuration']['@val']
  groupedDevices.each{dev -> if(dev) {dev.setCurrentTrackDuration(currentTrackDuration)}}

  String currentTrackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackMetaData']['@val']
  GPathResult currentTrackMetaDataXML
  if(currentTrackMetaData) {currentTrackMetaDataXML = parseXML(currentTrackMetaData)}
  if(currentTrackMetaDataXML) {
    String currentArtistName = status != "stopped" ? currentTrackMetaDataXML['item']['creator'] : null
    String currentAlbumName = status != "stopped" ? currentTrackMetaDataXML['item']['title'] : null
    String currentTrackName = status != "stopped" ? currentTrackMetaDataXML['item']['album'] : null
    String trackNumber = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrack']['@val']
    groupedDevices.each{dev -> if(dev) { dev.setCurrentArtistAlbumTrack(currentArtistName, currentAlbumName, currentTrackName, trackNumber as Integer)}}

    String uri = propertyset['property']['LastChange']['Event']['InstanceID']['AVTransportURI']['@val']
    // String transportUri = uri ?? Seems to be the same on the built-in driver
    String enqueuedUri = propertyset['property']['LastChange']['Event']['InstanceID']['EnqueuedTransportURI']['@val']
    String metaData = propertyset['property']['LastChange']['Event']['InstanceID']['EnqueuedTransportURIMetaData']['@val']
    String trackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['CurrentTrackMetaData']['@val']

    Boolean favFound = false
    String foundFavId = null
    String foundFavImageUrl = null
    String foundFavName = null
    if(state.favs != null) {
      state.favs.keySet().each{key ->
        // logDebug("URI: ${enqueuedUri} <=> Key: ${key}")
        if(enqueuedUri.contains(key)) {
          favFound = true
          foundFavId = state.favs[key].id
          foundFavImageUrl = state.favs[key].imageUrl
          foundFavName = state.favs[key].name
          groupedDevices.each{dev -> if(dev) { dev.sendEvent(
            name: 'currentFavorite',
            value: "Favorite #${foundFavId} ${foundFavName} <img src=\"${foundFavImageUrl}\" width=\"200\" height=\"200\" >"
            )}
          }
        }
      }
      if(!favFound) {
        groupedDevices.each{dev -> if(dev) { dev.sendEvent(name: 'currentFavorite', value: 'No favorite playing')}}
      }
    }

    Map trackData = [
      audioSource: "Sonos Q",
      station: null,
      name: currentAlbumName,
      artist: currentArtistName,
      album: currentAlbumName,
      trackNumber: trackNumber,
      status: status,
      uri: uri,
      trackUri: trackUri,
      transportUri: uri,
      enqueuedUri: enqueuedUri,
      metaData: metaData,
      trackMetaData: trackMetaData
    ]
    groupedDevices.each{dev -> if(dev) {dev.setTrackDataEvents(trackData)}}
  } else {
    String currentArtistName = ""
    String currentAlbumName = ""
    String currentTrackName = ""
    Integer trackNumber = 0
    groupedDevices.each{dev -> if(dev) {
      dev.setCurrentArtistAlbumTrack(currentArtistName, currentAlbumName, currentTrackName, trackNumber)
      dev.setTrackDataEvents([:])
      }
    }
  }

  String nextTrackMetaData = propertyset['property']['LastChange']['Event']['InstanceID']['NextTrackMetaData']['@val']
  GPathResult nextTrackMetaDataXML
  if(nextTrackMetaData) {nextTrackMetaDataXML = parseXML(nextTrackMetaData)}
  if(nextTrackMetaDataXML) {
    String nextArtistName = status != "stopped" ? nextTrackMetaDataXML['item']['creator'] : ""
    String nextAlbumName = status != "stopped" ? nextTrackMetaDataXML['item']['title'] : ""
    String nextTrackName = status != "stopped" ? nextTrackMetaDataXML['item']['album'] : ""
    groupedDevices.each{dev -> if(dev) {dev.setNextArtistAlbumTrack(nextArtistName, nextAlbumName, nextTrackName)}}
  } else {
    String nextArtistName = ""
    String nextAlbumName = ""
    String nextTrackName = ""
    groupedDevices.each{dev -> if(dev) {dev.setNextArtistAlbumTrack(nextArtistName, nextAlbumName, nextTrackName)}}
  }
}

void processZoneGroupTopologyMessages(DeviceWrapper cd, Map message) {
  GPathResult propertyset = parseSonosMessageXML(message)
  GPathResult zoneGroups = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups']

  String rincon = cd.getDataValue('id')
  String currentGroupCoordinatorId = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent()['@Coordinator']
  Boolean isGroupCoordinator = currentGroupCoordinatorId == rincon
  Boolean previouslyWasGroupCoordinator = cd.getDataValue('isGroupCoordinator') == 'true'
  if(isGroupCoordinator == true && previouslyWasGroupCoordinator == false) {
    logDebug("Just removed from group!")
    cd.subscribeToEvents()
  }
  if(isGroupCoordinator == false && previouslyWasGroupCoordinator ==  true) {
    logDebug("Just added to group!")
  }
  cd.updateDataValue('isGroupCoordinator', isGroupCoordinator.toString())
  if(!isGroupCoordinator) {return}

  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  List<String> groupedRincons = []
  zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children().each{ groupedRincons.add(it['@UUID'].toString()) }
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}

  groupedDevices.each{dev -> if(currentGroupCoordinatorId && dev) {dev.updateDataValue('groupCoordinatorId', currentGroupCoordinatorId)}}
  groupedDevices.each{dev -> if(groupedRincons && dev && groupedRincons.size() > 0) {dev.updateDataValue('groupIds', groupedRincons.join(','))}}

  String groupId = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent()['@ID']
  String currentGroupCoordinatorName = zoneGroups.children().children().findAll{it['@UUID'] == rincon}['@ZoneName']
  Integer currentGroupMemberCount = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children().size()

  List currentGroupMemberNames = []
  zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children().each{ currentGroupMemberNames.add(it['@ZoneName']) }
  String groupName = propertyset['property']['ZoneGroupName'].text()

  groupedDevices.each{dev ->
    if(dev) {
      dev.updateDataValue('groupId', groupId)
      dev.sendEvent(name: 'groupCoordinatorName', value: currentGroupCoordinatorName)
      dev.sendEvent(name: 'isGrouped', value: currentGroupMemberCount > 1 ? 'on' : 'off')
      dev.sendEvent(name: 'isGroupCoordinator', value: isGroupCoordinator ? 'on' : 'off')
      dev.sendEvent(name: 'groupMemberCount', value: currentGroupMemberCount)
      dev.sendEvent(name: 'groupMemberNames' , value: currentGroupMemberNames)
      dev.sendEvent(name: 'groupName', value: groupName)
    }
  }

  List<Map> events = [
    [name:'currentCrossfadeMode', value: cd.currentState('currentCrossfadeMode')?.value],
    [name:'currentRepeatAllMode', value: cd.currentState('currentRepeatAllMode')?.value],
    [name:'currentRepeatOneMode',  value: cd.currentState('currentRepeatOneMode')?.value],
    [name:'currentShuffleMode',  value: cd.currentState('currentShuffleMode')?.value],
    [name:'currentTrackDuration', value: cd.currentState('currentTrackDuration')?.value],
    [name:'currentTrackName',  value: cd.currentState('currentTrackName')?.value],
    [name:'nextAlbumName',  value: cd.currentState('nextAlbumName')?.value],
    [name:'nextArtistName', value: cd.currentState('nextArtistName')?.value],
    [name:'nextTrackName',  value: cd.currentState('nextTrackName')?.value]
  ]
  groupedDevices.each{dev -> if(dev && dev.getDataValue('isGroupCoordinator') == 'false') { events.each{dev.sendEvent(it)}}}

  // Update group device with current on/off state
  getCurrentGroupDevices().findAll{gds -> gds.getDataValue('coordinatorId') == currentGroupCoordinatorId }.each{gd ->
    List<String> playerIds = [gd.getDataValue('coordinatorId')] + gd.getDataValue('playerIds').tokenize(',')
    Boolean allPlayersAreGrouped = groupedRincons.containsAll(playerIds) && groupedRincons.size() == playerIds.size()
    if(allPlayersAreGrouped) { gd.sendEvent(name: 'switch', value: 'on') }
    else { gd.sendEvent(name: 'switch', value: 'off') }
  }
}

// =============================================================================
// Get and Post Helpers
// =============================================================================
void sendLocalCommandAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args.params.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args.params.contentType ?: 'application/json'
  params.requestContentType = args.params.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args.params.ignoreSSLIssues ?: true
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  sendCommandAsync(params, callbackMethod, args.data)
}

void sendLocalQueryAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args.params.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args.params.contentType ?: 'application/json'
  params.requestContentType = args.params.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args.params.ignoreSSLIssues ?: true
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  asynchttpGet(callbackMethod, params, args.data)
}

void sendLocalJsonAsync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
  String callbackMethod = args.callbackMethod ?: 'localControlCallback'
  Map params = args.params ?: [:]
  params.uri = args.params.uri ?: "${getLocalApiPrefix(args.ipAddress)}${args.endpoint}"
  params.contentType = args.params.contentType ?: 'application/json'
  params.requestContentType = args.params.requestContentType ?: 'application/json'
  params.ignoreSSLIssues = args.params.ignoreSSLIssues ?: true
  params.body = JsonOutput.toJson(args.data)
  if(params.headers == null) {
    params.headers = ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000']
  } else if(params.headers != null && params.headers['X-Sonos-Api-Key'] == null) {
    params.headers['X-Sonos-Api-Key'] = '123e4567-e89b-12d3-a456-426655440000'
  }
  asynchttpPost('localControlCallback', params)
}

void sendQueryAsync(Map params, String callbackMethod = 'localControlCallback', Map data = null) {
  if (now() >= state.authTokenExpires - 600) { refreshToken() }
  try { asynchttpGet(callbackMethod, params, data)}
  catch (Exception e) {logDebug("Call failed: ${e.message}")}
}

void sendCommandAsync(Map params, String callbackMethod = 'localControlCallback', Map data = null) {
  logDebug(prettyJson(params))
  try{ asynchttpPost(callbackMethod, params, data) }
  catch(Exception e){ if(e.message.toString() != 'OK') { logError(e.message) } }
}

void localControlCallback(AsyncResponse response, Map data) {
  if (response.status != 200) {logError("post request returned HTTP status ${response.status}")}
  if (response.hasError()) {logError("post request error: ${response.getErrorMessage()}")}
  logDebug("localControlCallback: ${response.getData()}")
}

Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if (response.status != 200) {
    logError("${requestName} request returned HTTP status ${response.status}")
  }
  if (response.hasError()) {
    logError("${requestName} request error: ${response.getErrorMessage()}")
    return false
  } else { return true }
}

// =============================================================================
// Local Control Component Methods
// =============================================================================

void componentPlayTextLocal(DeviceWrapper device, String text, BigDecimal volume = null, String voice = null) {
  String playerId = device.getDataValue('id')
  logDebug "${device} play text ${text} (volume ${volume ?: 'not set'})"
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  Map tts = textToSpeech(text, voice)
  data.streamUrl = tts.uri
  if (volume) data.volume = (int)volume

  String localApiUrl = "${device.getDataValue('localApiUrl')}"
  String endpoint = "players/${playerId}/audioClip"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentPlayTrackLocal(DeviceWrapper device, String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play track ${streamUrl} (volume ${volume ?: 'not set'})")
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  if (streamUrl?.toUpperCase() != 'CHIME') { data.streamUrl = streamUrl }
  if (volume) { data['volume'] = (int)volume }

  String localApiUrl = "${device.getDataValue('localApiUrl')}"
  String endpoint = "players/${playerId}/audioClip"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void getDeviceStateAsync(DeviceWrapper device, String callbackMethod = 'localControlCallback', Map service, String action, Map data = null, Map controlValues = null) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, service, action, controlValues)
  asynchttpPost(callbackMethod, params, data)
}

void componentSetPlayerLevelLocal(DeviceWrapper device, BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentMuteGroupLocal(DeviceWrapper device, Boolean desiredMute) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetPlayerRelativeLevelLocal(DeviceWrapper device, Integer adjustment) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [Adjustment: adjustment]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetRelativeVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupRelativeLevelLocal(DeviceWrapper device, Integer adjustment) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [Adjustment: adjustment]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetRelativeGroupVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupLevelLocal(DeviceWrapper device, BigDecimal level) {
  getZoneGroupAttributesAsync(device, 'componentSetGroupLevelLocalCallback', [level:level, rincon:device.getDataValue('id')])
}

void componentSetGroupLevelLocalCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'componentSetGroupLevelLocalCallback')) { return }
  GPathResult xml = new XmlSlurper().parseText(unescapeXML(response.getData()))
  List<DeviceWrapper> groupedDevices = getGroupedPlayerDevicesFromGetZoneGroupAttributes(xml, data.rincon)
  groupedDevices.each{ componentSetPlayerLevelLocal(it, data.level) }
}

void componentMutePlayerLocal(DeviceWrapper device, Boolean desiredMute) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentPlayLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(coordinatorId)
  Map params = getSoapActionParams(ip, AVTransport, 'Play')
  asynchttpPost('localControlCallback', params)
}

void componentPauseLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(coordinatorId)
  Map params = getSoapActionParams(ip, AVTransport, 'Pause')
  asynchttpPost('localControlCallback', params)
}

void componentStopLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(coordinatorId)
  Map params = getSoapActionParams(ip, AVTransport, 'Stop')
  asynchttpPost('localControlCallback', params)
}

void componentNextTrackLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(coordinatorId)
  Map params = getSoapActionParams(ip, AVTransport, 'Next')
  asynchttpPost('localControlCallback', params)
}

void componentPreviousTrackLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(coordinatorId)
  Map params = getSoapActionParams(ip, AVTransport, 'Previous')
  asynchttpPost('localControlCallback', params)
}

void componentUngroupPlayerLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
  logDebug("Params: ${params}")
  asynchttpPost('localControlCallback', params)
}

void componentUngroupPlayersLocal(DeviceWrapper device) {
  String coordinatorId = device.getDataValue('coordinatorId')
  ChildDeviceWrapper cd = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId }
  getZoneGroupAttributesAsync(cd, 'componentUngroupPlayersLocalCallback', [householdId:device.getDataValue('householdId'),  rincon:device.getDataValue('coordinatorId')])
}

void componentUngroupPlayersLocalCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'componentUngroupPlayersLocalCallback')) { return }
  GPathResult xml = new XmlSlurper().parseText(unescapeXML(response.getData()))
  List<DeviceWrapper> groupedDevices = getGroupedPlayerDevicesFromGetZoneGroupAttributes(xml, data.rincon)

  String endpoint = "/households/${data.householdId}/groups/createGroup"
  logDebug(groupedDevices)
  groupedDevices.each{ cd ->
    if(cd) {
      Map params = [body: [playerIds: [cd.getDataValue('id')]]]
      String ip = "${cd.getDataValue('deviceIp').tokenize(':')[0]}:1443"
      sendLocalCommandAsync(endpoint: endpoint, params: params, ipAddress: ip)
    }
  }
}

void componentLoadFavoriteFullLocal(DeviceWrapper device, String favoriteId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  logDebug('Loading favorites full options...')
  String groupId = device.getDataValue('groupId')
  Map data = [
    action:"REPLACE",
    favoriteId:favoriteId,
    playOnCompletion:true,
    playModes:['repeat': false,'repeatOne': true],
  ]

  String localApiUrl = "${device.getDataValue('localApiUrl')}"
  String endpoint = "groups/${groupId}/favorites"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentGetFavoritesLocal(DeviceWrapper device) {
  logDebug('Getting favorites...')
  String householdId = device.getDataValue('householdId')

  String localApiUrl = "${device.getDataValue('localApiUrl')}"
  String endpoint = "households/${householdId}/favorites"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalQueryAsync(params: params, callbackMethod: 'getFavoritesLocalCallback', data:[dni: device.getDeviceNetworkId()])
  appGetFavoritesLocal()
}

void getFavoritesLocalCallback(AsyncResponse response, Map data = null) {
  ChildDeviceWrapper child = app.getChildDevice(data.dni)
  if (response.hasError()) {
    logError("componentGetFavoritesLocal error: ${response.getErrorData()}")
    return
  }
  List respData = response.getJson().items
  Map formatted = respData.collectEntries() { [it.id, [name:it.name, imageUrl:it.imageUrl]] }
  // state.favs
  // logDebug("formatted response: ${prettyJson(formatted)}")
  formatted.each(){it ->
    child.sendEvent(
      name: "Favorite #${it.key} ${it.value.name}",
      value: "<img src=\"${it.value.imageUrl}\" width=\"200\" height=\"200\" >",
      isStateChange: false
    )
  }
}

List<String> getUniqueHouseholds() {
  List<ChildDeviceWrapper> children = app.getChildDevices()
  List<String> households = children.collect{cd -> cd.getDataValue('householdId')}.unique{a,b -> a <=> b}
  return households
}

void appGetFavoritesLocal() {
  logDebug("Getting (app) favorites...")
  unschedule('appGetFavorites')
  List<ChildDeviceWrapper> children = app.getChildDevices().findAll{ child -> child.getDataValue('isGroupCoordinator') == 'true'}
  Map households = children.collectEntries{cd -> [cd.getDataValue('householdId'), [localApiUrl: cd.getDataValue('localApiUrl'), dni: cd.getDeviceNetworkId()]]}

  households.each{householdId, data ->
    String endpoint = "households/${householdId}/favorites"
    String uri = "${data.localApiUrl}${endpoint}"
    Map params = [uri: uri]
    sendLocalQueryAsync(params: params, callbackMethod: 'appGetFavoritesLocalCallback', data:[dni: data.dni])
  }
  runIn(60*60*3, 'appGetFavoritesLocal', [overwrite: true])
}

void appGetFavoritesLocalCallback(AsyncResponse response, Map data = null) {
  if (response.hasError()) {
    logError("appGetFavorites error: ${response.getErrorData()}")
    return
  }
  List respData = response.getJson().items
  if(respData.size() == 0) {
    // logDebug("Response returned from getFavorites API: ${response.getJson()}")
    return
  }

  Map favs = state.favs ?: [:]
  respData.each{
    if(it?.resource?.id?.objectId != null) {
      // logDebug("ObjectId: ${it?.resource?.id?.objectId}")
      favs["${URLEncoder.encode(it?.resource?.id?.objectId).toLowerCase()}"] = [name:it?.name, imageUrl:it?.imageUrl]
    }
  }
  state.favs = favs
  logDebug("App favorites updated!")
  // logDebug("formatted response: ${prettyJson(favs)}")
}

void removePlayersLocal(String coordinatorId) {
  ChildDeviceWrapper cd = app.getChildDevices().find{cd ->  cd.getDataValue('id') == coordinatorId}
  String groupCoordinatorId = cd.getDataValue('groupCoordinatorId')
  Map data = ['playerIds': [coordinatorId]]

  String localApiUrl = "${cd.getDataValue('localApiUrl')}"
  String endpoint = "groups/${groupCoordinatorId}/groups/setGroupMembers"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentRemovePlayersFromCoordinatorLocal(DeviceWrapper device) {
  logDebug('Removing players from coordinator...')
  String coordinatorId = device.getDataValue('coordinatorId')
  ChildDeviceWrapper cd = app.getChildDevices().find{cd ->  cd.getDataValue('id') == coordinatorId}
  if(cd.getDataValue('isGroupCoordinator') == 'false') {
    logWarn('Can not remove players from coordinator. Coordinator for this defined group is not currently a group coordinator.')
    return
  }
  List<String> followers = device.getDataValue('playerIds').tokenize(',')
  List<ChildDeviceWrapper> followerDevices = app.getChildDevices().findAll{ it.getDataValue('id') in followers }
  logDebug("Follower Devices ${followerDevices}")
  followerDevices.each{player -> componentUngroupPlayerLocal(player)}
}

void componentUpdatePlayerInfo(DeviceWrapper device) {
  Map playerInfo = getPlayerInfoLocalSync("${device.getDataValue('deviceIp')}:1443")
  String id = playerInfo?.playerId
  String isGroupCoordinator = playerInfo?.groupId.tokenize(':')[0] ==id  ? 'true' : 'false'
  device.updateDataValue('isGroupCoordinator', isGroupCoordinator)
  // logDebug(prettyJson(playerInfo))
}

void componentSetPlayModesLocal(DeviceWrapper device, Map playModes) {
  logDebug('Setting Play Modes...')
  String groupId = device.getDataValue('groupId')
  String localApiUrl = getLocalApiUrlForPlayer(device)
  String endpoint = "groups/${groupId}/playback/playMode"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: playModes)
}

String getLocalApiUrlForPlayer(DeviceWrapper device) {
  String groupCoordinatorId = device.getDataValue('groupCoordinatorId')
  String localApiUrl = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId }.getDataValue('localApiUrl')
  return localApiUrl
}

String getLocalApiUrlForCoordinatorId(String coordinatorId) {
  String localApiUrl = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId }.getDataValue('localApiUrl')
  return localApiUrl
}

String getLocalUpnpHostForCoordinatorId(String coordinatorId) {
  String localUpnpHost = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId }.getDataValue('localUpnpHost')
  return localUpnpHost
}

String getCoordinatorGroupId(String coordinatorId) {
  String coordinatorGroupId = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId }.getDataValue('groupId')
  return coordinatorGroupId
}

void componentGroupPlayersLocal(DeviceWrapper device) {
  logDebug('Adding players to group...')
  String householdId = device.getDataValue('householdId')
  String coordinatorId = device.getDataValue('coordinatorId')
  List playerIds = device.getDataValue('playerIds').split(',')
  String coordinatorGroupId = getCoordinatorGroupId(coordinatorId)
  Map data = ['playerIds': [coordinatorId] + playerIds, musicContextGroupId: coordinatorGroupId]
  String localApiUrl = getLocalApiUrlForCoordinatorId(coordinatorId)
  String endpoint = "households/${householdId}/groups/createGroup"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentJoinPlayersToCoordinatorLocal(DeviceWrapper device) {
  logDebug('Adding players to coordinator...')
  // String householdId = device.getDataValue('householdId')
  String coordinatorId = device.getDataValue('coordinatorId')
  List playerIds = device.getDataValue('playerIds').split(',')
  String coordinatorGroupId = getCoordinatorGroupId(coordinatorId)
  Map data = ['playerIds': [coordinatorId] + playerIds]
  String localApiUrl = getLocalApiUrlForCoordinatorId(coordinatorId)
  String endpoint = "groups/${coordinatorGroupId}/groups/setGroupMembers"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}