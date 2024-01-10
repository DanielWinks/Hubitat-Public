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
import com.hubitat.hub.domain.Location


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
  singleThreaded: false,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosAdvancedApp.groovy'
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
  page(name: 'localPlayerPage')
  page(name: 'localPlayerSelectionPage')
  page(name: 'groupPage')
}
@Field static Map playerSelectionOptions = new java.util.concurrent.ConcurrentHashMap()
@Field static Map discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()

@Field static Map SOURCES = [
  "\$": "NONE",
  "x-file-cifs:": "LIBRARY",
  "x-rincon-mp3radio:": "RADIO",
  "x-sonosapi-stream:": "RADIO",
  "x-sonosapi-radio:": "RADIO",
  "x-sonosapi-hls:": "RADIO",
  "x-sonos-http:sonos": "RADIO",
  "aac:": "RADIO",
  "hls-radio:": "RADIO",
  "https?:": "WEB_FILE",
  "x-rincon-stream:": "LINE_IN",
  "x-sonos-htastream:": "TV",
  "x-sonos-vli:.*,airplay:": "AIRPLAY",
  "x-sonos-vli:.*,spotify:": "SPOTIFY_CONNECT",
]

@CompileStatic
String getLocalApiPrefix(String ipAddress) {
  return "https://${ipAddress}/api/v1"
}

// =============================================================================
// App Pages
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
      // input 'applySettingsButton', 'button', title: 'Apply Settings'
      // input 'createPlayerDevices', 'button', title: 'Create Players'
    }
  }
}

Map localPlayerPage() {
  if(!state.discoveryRunning) {
    subscribeToSsdpEvents(location)
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
        submitOnChange: true,
        offerAll: false
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
      app.updateSetting('newGroupCoordinator', [type: 'enum', value: state.userGroups[edg]?.groupCoordinatorId])
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
      input(name: 'newGroupCoordinator', type: 'enum', title: 'Select Coordinator (leader):', multiple: false, options: coordinatorSelectionOptions, required: false, submitOnChange: true, offerAll: false)
      input(name: 'newGroupPlayers', type: 'enum', title: 'Select Players (followers):', multiple: true, options: playerSelectionOptions, required: false, submitOnChange: true, offerAll: false)
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
  state.userGroups[app.getSetting('newGroupName')] = [groupCoordinatorId:app.getSetting('newGroupCoordinator'), playerIds:app.getSetting('newGroupPlayers')]
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
// Initialize() and Configure()
// =============================================================================
void initialize() { configure() }
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
// Create Child Devices
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


      } catch (UnknownDeviceTypeException e) {logException('Sonos Advanced Group driver not found', e)}
    }
    String groupCoordinatorId = it.value.groupCoordinatorId as String
    String playerIds =  it.value.playerIds.join(',')
    ChildDeviceWrapper coordDev = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId}
    String householdId = coordDev.getDataValue('householdId')
    device.updateDataValue('groupCoordinatorId', groupCoordinatorId)
    device.updateDataValue('playerIds', playerIds)
    device.updateDataValue('householdId', householdId)
  }
  app.removeSetting('groupDevices')
  app.updateSetting('groupDevices', [type: 'enum', value: getCreatedGroupDevices()])
  removeOrphans()
}

void createPlayerDevices() {
  settings.playerDevices.each{ dni ->
    ChildDeviceWrapper cd = app.getChildDevice(dni)
    Map playerInfo = discoveredSonoses[dni]
    if(cd) {
      logDebug("Not creating ${cd.getDataValue('name')}, child already exists.")
    } else {
      logInfo("Creating Sonos Advanced Player device for ${playerInfo?.name}")
      try {
        cd = addChildDevice('dwinks', 'Sonos Advanced Player', dni, [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo?.name}"])
      } catch (UnknownDeviceTypeException e) {logException('Sonos Advanced Player driver not found', e)}
    }
    logDebug("Updating player info with latest info from discovery...")
    playerInfo.each { key, value -> cd.updateDataValue(key, value as String) }
    cd.secondaryConfiguration()
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
// Local Discovery
// =============================================================================
@CompileStatic
void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
  discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()
	sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
	sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))
}

@CompileStatic
void subscribeToSsdpEvents(Location location) {
  logDebug("Subscribing to SSDP Discovery events...")
	subscribe(location, 'ssdpTerm.upnp:rootdevice', 'ssdpEventHandler')
	subscribe(location, 'sdpTerm.ssdp:all', 'ssdpEventHandler')
}

void ssdpEventHandler(Event event) {
	LinkedHashMap parsedEvent = parseLanMessage(event.description)
  processParsedSsdpEvent(parsedEvent)
}

@CompileStatic
void processParsedSsdpEvent(LinkedHashMap event) {
  String ipAddress = convertHexToIP(event.networkAddress)
  String ipPort = convertHexToInt(event.deviceAddress)

  LinkedHashMap playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")

  LinkedHashMap playerInfoDevice = playerInfo.device as LinkedHashMap

  String modelName = deviceDescription['device']['modelName']
	String ssdpPath = event.ssdpPath
	String mac = event.mac as String
  String playerName = playerInfoDevice.name
  String playerDni = event.mac
  String swGen = playerInfoDevice.swGen
  String websocketUrl = playerInfoDevice.websocketUrl
  String householdId = playerInfo?.householdId
  String playerId = playerInfo?.playerId
  String groupId = playerInfo?.groupId
  List<String> deviceCapabilities = playerInfoDevice.capabilities as List<String>

  LinkedHashMap discoveredSonos = [
    name: playerName,
    id: playerId,
    swGen: swGen,
    capabilities: deviceCapabilities,
    modelName: modelName,
    householdId: householdId,
    websocketUrl: websocketUrl,
    deviceIp: "${ipAddress}",
    localApiUrl: "https://${ipAddress}:1443/api/v1/",
    localUpnpUrl: "http://${ipAddress}:1400",
    localUpnpHost: "${ipAddress}:1400"
  ]
  if(discoveredSonos?.name != null) {
    discoveredSonoses[mac] = discoveredSonos
  } else {
    logInfo("Device responded to SSDP discovery, but did not provide device description: ${discoveredSonos}")
  }
}

// =============================================================================
// Helper methods
// =============================================================================

ChildDeviceWrapper getDeviceFromRincon(String rincon) {
  List<ChildDeviceWrapper> childDevices = app.getCurrentPlayerDevices()
  ChildDeviceWrapper dev = childDevices.find{ it.getDataValue('id') == rincon}
  return dev
}

List<ChildDeviceWrapper> getDevicesFromRincons(List<String> rincons) {
  List<ChildDeviceWrapper> children = rincons.collect{ player -> getDeviceFromRincon(player)  }
  children.removeAll([null])
  return children
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

List<String> getAllPlayersForGroupDevice(DeviceWrapper device) {
  List<String> playerIds = [device.getDataValue('groupCoordinatorId')]
  playerIds.addAll(device.getDataValue('playerIds').split(','))
  return playerIds
}

LinkedHashMap getPlayerInfoLocalSync(String ipAddress) {
  ipAddress = ipAddress.contains(':') ? ipAddress : "${ipAddress}:1443"
  LinkedHashMap params = [
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

List<DeviceWrapper> getCurrentGroupedDevices(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  List<String> groupedRincons
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupedRincons = (xml['Body']['GetZoneGroupAttributesResponse']['CurrentZonePlayerUUIDsInGroup'].text()).toString().tokenize(',')
    }
    else { logError(resp.data) }
  }
  List<DeviceWrapper> groupedDevices = []
  List<String> groupIds = []
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{
    ChildDeviceWrapper child = getChildDevice(it)
    if(child) {groupedDevices.add(child)}
  }
  return groupedDevices
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
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZoneGroupID'].text().toString()
    }
    else { logError(resp.data) }
  }
  return groupId
}

String getHouseholdForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentMuseHouseholdId'].text().toString()
    }
    else { logError(resp.data) }
  }
  return groupId
}

Boolean hasLeftAndRightChannelsSync(DeviceWrapper device) {
  String householdId = device.getDataValue('householdId')
  String localApiUrl = device.getDataValue('localApiUrl')
  String endpoint = "households/${householdId}/groups"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  Map json = sendLocalJsonQuerySync([params:params])
  Map playerInfo = json?.players.find{it?.id == device.getDataValue('id')}
  Boolean hasLeftChannel = playerInfo?.zoneInfo?.members.find{it?.channelMap.contains('LF')}
  Boolean hasRightChannel = playerInfo?.zoneInfo?.members.find{it?.channelMap.contains('RF')}
  return hasLeftChannel && hasRightChannel
}

String unEscapeOnce(String text) {
  return text.replace('&lt;','<').replace('&gt;','>').replace('&quot;','"')
}

String unEscapeMetaData(String text) {
  return text.replace('&amp;lt;','<').replace('&amp;gt;','>').replace('&amp;quot;','"')
}

// =============================================================================
// Component Methods for Child Events
// =============================================================================
void processAVTransportMessages(DeviceWrapper cd, Map message) {
  Boolean isGroupCoordinator = cd.getDataValue('id') == cd.getDataValue('groupCoordinatorId')
  if(!isGroupCoordinator) { return }

  GPathResult propertyset = new XmlSlurper().parseText(message.body as String)
  String lastChange = propertyset['property']['LastChange'].text()

  GPathResult event = new XmlSlurper().parseText(lastChange)
  GPathResult instanceId = event['InstanceID']

  List<DeviceWrapper> groupedDevices = getCurrentGroupedDevices(cd)

  String status = (instanceId['TransportState']['@val'].toString()).toLowerCase().replace('_playback','')
  String currentPlayMode = instanceId['CurrentPlayMode']['@val']
  String numberOfTracks = instanceId['NumberOfTracks']['@val']
  String trackNumber = instanceId['CurrentTrack']['@val']
  String trackUri = ((instanceId['CurrentTrackURI']['@val']).toString()).replace('&amp;','&').replace('&amp;','&')
  Boolean isAirPlay = trackUri.toLowerCase().contains('airplay')
  String currentTrackDuration = instanceId['CurrentTrackDuration']['@val']
  String currentCrossfadeMode = instanceId['CurrentCrossfadeMode']['@val']
  currentCrossfadeMode = currentCrossfadeMode=='1' ? 'on' : 'off'
  groupedDevices.each{dev ->
    dev.sendEvent(name:'status', value: status)
    dev.sendEvent(name:'transportStatus', value: status)
    dev.setPlayMode(currentPlayMode)
    dev.setCrossfadeMode(currentCrossfadeMode)
    dev.setCurrentTrackDuration(currentTrackDuration)
  }




  String currentTrackMetaDataString = (instanceId['CurrentTrackMetaData']['@val']).toString()
  if(currentTrackMetaDataString) {
    GPathResult currentTrackMetaData = new XmlSlurper().parseText(unEscapeMetaData(currentTrackMetaDataString))
    String albumArtURI = (currentTrackMetaData['item']['albumArtURI'].text()).toString()
    while(albumArtURI.contains('&amp;')) { albumArtURI = albumArtURI.replace('&amp;','&') }
    String currentArtistName = status != "stopped" ? currentTrackMetaData['item']['creator'] : null
    String currentAlbumName = status != "stopped" ? currentTrackMetaData['item']['album'] : null
    String currentTrackName = status != "stopped" ? currentTrackMetaData['item']['title'] : null
    String streamContent = status != "stopped" ? currentTrackMetaData['item']['streamContent'] : null

    groupedDevices.each{dev ->
      if(albumArtURI.startsWith('/')) {
        dev.sendEvent(name:'albumArtURI', value: "<br><img src=\"${dev.getDataValue('localUpnpUrl')}${albumArtURI}\" width=\"200\" height=\"200\" >")
      } else {
        dev.sendEvent(name:'albumArtURI', value: "<br><img src=\"${albumArtURI}\" width=\"200\" height=\"200\" >")
      }
      dev.setCurrentArtistAlbumTrack(currentArtistName, currentAlbumName, currentTrackName, trackNumber as Integer)
      if(streamContent && (!currentArtistName && !currentAlbumName)) {
        dev.sendEvent(name: 'trackDescription', value: streamContent)
      }
    }

    String enqueuedUri = instanceId['EnqueuedTransportURI']['@val']
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
          groupedDevices.each{dev -> dev.sendEvent(
            name: 'currentFavorite',
            value: "Favorite #${foundFavId} ${foundFavName} <br><img src=\"${foundFavImageUrl}\" width=\"200\" height=\"200\" >"
            )
          }
        }
      }
      if(!favFound) {
        groupedDevices.each{dev -> dev.sendEvent(name: 'currentFavorite', value: 'No favorite playing')}
      }
    }


    String avTransportURIMetaDataString = (instanceId['AVTransportURIMetaData']['@val']).toString()
    if(avTransportURIMetaDataString) {
      GPathResult avTransportURIMetaData = new XmlSlurper().parseText(unEscapeMetaData(avTransportURIMetaDataString))

      String metaData = instanceId['EnqueuedTransportURIMetaData']['@val']
      String uri = instanceId['AVTransportURI']['@val']
      // String transportUri = uri ?? Seems to be the same on the built-in driver
      String audioSource = SOURCES[(enqueuedUri.tokenize(':')[0]).toString()]
      Map trackData = [
        audioSource: audioSource,
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
        trackMetaData: unEscapeMetaData(currentTrackMetaDataString)
      ]
      groupedDevices.each{dev -> if(dev) {dev.setTrackDataEvents(trackData)}}
    }
  } else {
    currentArtistName = "Not Available"
    currentAlbumName = "Not Available"
    currentTrackName = "Not Available"
    trackNumber = 0
    groupedDevices.each{dev ->
      dev.setCurrentArtistAlbumTrack(currentArtistName, currentAlbumName, currentTrackName, trackNumber as Integer)
      dev.setTrackDataEvents([:])
    }
  }

  String nextTrackMetaData = instanceId['NextTrackMetaData']['@val']
  GPathResult nextTrackMetaDataXML
  if(nextTrackMetaData) {nextTrackMetaDataXML = parseXML(nextTrackMetaData)}
  if(nextTrackMetaDataXML) {
    String nextArtistName = status != "stopped" ? nextTrackMetaDataXML['item']['creator'] : "Not Available"
    String nextAlbumName = status != "stopped" ? nextTrackMetaDataXML['item']['album'] : "Not Available"
    String nextTrackName = status != "stopped" ? nextTrackMetaDataXML['item']['title'] : "Not Available"
    groupedDevices.each{dev -> dev.setNextArtistAlbumTrack(nextArtistName, nextAlbumName, nextTrackName)}
  } else {
    String nextArtistName = "Not Available"
    String nextAlbumName = "Not Available"
    String nextTrackName = "Not Available"
    groupedDevices.each{dev -> dev.setNextArtistAlbumTrack(nextArtistName, nextAlbumName, nextTrackName)}
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
  }
  if(isGroupCoordinator == false && previouslyWasGroupCoordinator ==  true) {
    logDebug("Just added to group!")
  }
  cd.updateDataValue('isGroupCoordinator', isGroupCoordinator.toString())
  if(!isGroupCoordinator) {return}

  List<String> groupedRincons = []
  GPathResult currentGroupMembers = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children()

  currentGroupMembers.each{ groupedRincons.add(it['@UUID'].toString()) }
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  List<DeviceWrapper> groupedDevices = getDevicesFromRincons(groupedRincons)

  groupedDevices.each{dev -> if(currentGroupCoordinatorId && dev) {dev.updateDataValue('groupCoordinatorId', currentGroupCoordinatorId)}}
  groupedDevices.each{dev -> if(groupedRincons && dev && groupedRincons.size() > 0) {dev.updateDataValue('groupIds', groupedRincons.join(','))}}

  String groupId = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent()['@ID']
  String currentGroupCoordinatorName = zoneGroups.children().children().findAll{it['@UUID'] == rincon}['@ZoneName']


  LinkedHashSet currentGroupMemberNames = []
  currentGroupMembers.each{ currentGroupMemberNames.add(it['@ZoneName']) }
  Integer currentGroupMemberCount = currentGroupMemberNames.size()


  String groupName = propertyset['property']['ZoneGroupName'].text()

  groupedDevices.each{dev ->
    dev.updateDataValue('groupId', groupId)
    dev.sendEvent(name: 'groupCoordinatorName', value: currentGroupCoordinatorName)
    dev.sendEvent(name: 'isGrouped', value: currentGroupMemberCount > 1 ? 'on' : 'off')
    dev.sendEvent(name: 'isGroupCoordinator', value: isGroupCoordinator ? 'on' : 'off')
    dev.sendEvent(name: 'groupMemberCount', value: currentGroupMemberCount)
    dev.sendEvent(name: 'groupMemberNames' , value: currentGroupMemberNames)
    dev.sendEvent(name: 'groupName', value: groupName)
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
  getCurrentGroupDevices().findAll{gds -> gds.getDataValue('groupCoordinatorId') == currentGroupCoordinatorId }.each{gd ->
    List<String> playerIds = [gd.getDataValue('groupCoordinatorId')] + gd.getDataValue('playerIds').tokenize(',')
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
  logDebug("sendLocalCommandAsync: ${params}")
  asynchttpPost(callbackMethod, params, args.data)
}

Map sendLocalJsonQuerySync(Map args) {
  if(args?.endpoint == null && args?.params?.uri == null) { return }
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
  logDebug("sendLocalQuerySync: ${params}")
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) { return resp.data }
    else { logError(resp.data) }
  }
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
  logDebug("sendLocalQueryAsync: ${params}")
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
  logDebug("sendLocalJsonAsync: ${params}")
  asynchttpPost(callbackMethod, params)
}

void localControlCallback(AsyncResponse response, Map data) {
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
  }
  if(response?.status == 200 && response && response.hasError() == false) {
    logDebug("localControlCallback: ${response.getData()}")
  }
}

Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
  }
  if (response.hasError()) { return false } else { return true }
}

// =============================================================================
// Local Control Component Methods
// =============================================================================

void componentPlayTextLocal(DeviceWrapper device, String text, BigDecimal volume = null, String voice = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play text ${text} (volume ${volume ?: 'not set'})")
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

void componentPlayAudioClipLocal(DeviceWrapper device, String streamUrl, BigDecimal volume = null) {
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

void removeAllTracksFromQueue(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'RemoveAllTracksFromQueue')
  asynchttpPost('localControlCallback', params)
}

void setAVTransportURI(DeviceWrapper device, String currentURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map controlValues = [CurrentURI: currentURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  asynchttpPost('localControlCallback', params)
}

String getMetaData(String title = '', String service = 'SA_RINCON65031_') {
  return  (
    '<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" ' +
    'xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">' +
    '<item id="R:0/0/0" parentID="R:0/0" restricted="true"> ' +
    "<dc:title>${title}</dc:title>"+'<upnp:class>object.item.audioItem.audioBroadcast</upnp:class>' +
    '<desc id="cdudn" nameSpace="urn:schemas-rinconnetworks-com:metadata-1-0/">'+"${service}"+'</desc></item></DIDL-Lite>'
  )
}

void componentLoadStreamUrlLocal(DeviceWrapper device, String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  if(streamUrl.startsWith('http')) {streamUrl = streamUrl.replace('http','x-rincon-mp3radio')}
  if(streamUrl.startsWith('https')) {streamUrl = streamUrl.replace('https','x-rincon-mp3radio')}
  logDebug("${device} play track ${streamUrl} (volume ${volume ?: 'not set'})")
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURI(device, streamUrl)
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

void componentSetTrebleLocal(DeviceWrapper device, BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredTreble: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetTreble', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetBassLocal(DeviceWrapper device, BigDecimal level) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredBass: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetBass', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetBalanceLocal(DeviceWrapper device, BigDecimal level) {
  if(!hasLeftAndRightChannelsSync(device)) {
    logWarn("Can not set balance on non-stereo pair.")
    return
  }
  String ip = device.getDataValue('localUpnpHost')
  level *= 5
  if(level < 0) {
    level = level < -100 ? -100 : level
    Map controlValues = [DesiredVolume: 100 + level, Channel: "RF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "LF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  } else if(level > 0) {
    level = level > 100 ? 100 : level
    Map controlValues = [DesiredVolume: 100 - level, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  } else {
    Map controlValues = [DesiredVolume: 100, Channel: "LF"]
    Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
    controlValues = [DesiredVolume: 100, Channel: "RF"]
    params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
    asynchttpPost('localControlCallback', params)
  }
}

void componentSetLoudnessLocal(DeviceWrapper device, Boolean desiredLoudness) {
  String ip = device.getDataValue('localUpnpHost')
  Map controlValues = [DesiredLoudness: desiredLoudness]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetLoudness', controlValues)
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
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'Play')
  asynchttpPost('localControlCallback', params)
}

void componentPauseLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'Pause')
  asynchttpPost('localControlCallback', params)
}

void componentStopLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'Stop')
  asynchttpPost('localControlCallback', params)
}

void componentNextTrackLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'Next')
  asynchttpPost('localControlCallback', params)
}

void componentPreviousTrackLocal(DeviceWrapper device) {
  String ip = getLocalUpnpHostForCoordinatorId(device.getDataValue('groupCoordinatorId'))
  Map params = getSoapActionParams(ip, AVTransport, 'Previous')
  asynchttpPost('localControlCallback', params)
}

// /////////////////////////////////////////////////////////////////////////////
// Grouping and Ungrouping
// /////////////////////////////////////////////////////////////////////////////
void componentGroupPlayersLocal(DeviceWrapper device) {
  logDebug('Adding players to group...')
  String householdId = device.getDataValue('householdId')
  String groupCoordinatorId = device.getDataValue('groupCoordinatorId')
  List playerIds = device.getDataValue('playerIds').split(',')
  ChildDeviceWrapper coordinator = getDeviceFromRincon(groupCoordinatorId)
  if(coordinator.getDataValue('isGroupCoordinator') == 'false') {
    componentUngroupPlayerLocalSync(coordinator)
  }
  String coordinatorGroupId = getCoordinatorGroupId(groupCoordinatorId)
  Map data = ['playerIds': [groupCoordinatorId] + playerIds, musicContextGroupId: coordinatorGroupId]
  String localApiUrl = getLocalApiUrlForCoordinatorId(groupCoordinatorId)
  String endpoint = "households/${householdId}/groups/createGroup"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentJoinPlayersToCoordinatorLocal(DeviceWrapper device) {
  logDebug('Adding players to coordinator...')
  String groupCoordinatorId = device.getDataValue('groupCoordinatorId')
  List playerIds = device.getDataValue('playerIds').split(',')
  String coordinatorGroupId = getCoordinatorGroupId(groupCoordinatorId)
  Map data = ['playerIds': [groupCoordinatorId] + playerIds]
  String localApiUrl = getLocalApiUrlForCoordinatorId(groupCoordinatorId)
  String endpoint = "groups/${coordinatorGroupId}/groups/setGroupMembers"
  String uri = "${localApiUrl}${endpoint}"
  Map params = [uri: uri]
  sendLocalJsonAsync(params: params, data: data)
}

void componentRemovePlayersFromCoordinatorLocal(DeviceWrapper device) {
  logDebug('Removing players from coordinator...')
  String groupCoordinatorId = device.getDataValue('groupCoordinatorId')
  ChildDeviceWrapper cd = app.getChildDevices().find{cd ->  cd.getDataValue('id') == groupCoordinatorId}
  if(cd.getDataValue('isGroupCoordinator') == 'false') {
    logWarn('Can not remove players from coordinator. Coordinator for this defined group is not currently a group coordinator.')
    return
  }
  List<String> followers = device.getDataValue('playerIds').tokenize(',')
  List<ChildDeviceWrapper> followerDevices = app.getChildDevices().findAll{ it.getDataValue('id') in followers }
  logDebug("Follower Devices ${followerDevices}")
  followerDevices.each{player -> componentUngroupPlayerLocal(player)}
}

void componentUngroupPlayerLocal(DeviceWrapper device, String callbackMethod = 'localControlCallback') {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
  logDebug("componentUngroupPlayerLocal Params: ${params}")
  asynchttpPost(callbackMethod, params)
}

void componentUngroupPlayerLocalSync(DeviceWrapper device) {
  String ip = device.getDataValue('localUpnpHost')
  Map params = getSoapActionParams(ip, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
  logDebug("componentUngroupPlayerLocalSync Params: ${params}")
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      logXml(resp.data)
    }
    else { logError(resp.data) }
  }
}

void componentUngroupPlayersLocal(DeviceWrapper device) {
  String groupCoordinatorId = device.getDataValue('groupCoordinatorId')
  List<String> playerIds = device.getDataValue('playerIds').tokenize(',')
  List<String> allPlayers = [groupCoordinatorId] + playerIds
  List<ChildDeviceWrapper> allPlayerDevices = getDevicesFromRincons(allPlayers)
  allPlayerDevices.each{player -> componentUngroupPlayerLocalSync(player) }
}

// /////////////////////////////////////////////////////////////////////////////
// Favorites
// /////////////////////////////////////////////////////////////////////////////
void componentLoadFavoriteFullLocal(DeviceWrapper device, String favoriteId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  logDebug('Loading favorites full options...')
  Map data = [
    action:action,
    favoriteId:favoriteId,
    playOnCompletion:playOnCompletion,
    playModes:['repeat': repeat,'repeatOne': repeatOne, 'shuffle': shuffle, 'crossfade': crossfade],
  ]
  String groupId = getGroupForPlayerDeviceLocal(device)
  ChildDeviceWrapper coordinator = getDeviceFromRincon(groupId.tokenize(':')[0])
  String localApiUrl = "${coordinator.getDataValue('localApiUrl')}"
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
      value: "<br><img src=\"${it.value.imageUrl}\" width=\"200\" height=\"200\" >",
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
    logDebug("Response returned from getFavorites API: ${response.getJson()}")
    return
  }
  // logDebug(prettyJson(response.getJson()))
  Map favs = state.favs ?: [:]
  respData.each{
    if(it?.resource?.id?.objectId != null) {
      // logDebug("ObjectId: ${it?.resource?.id?.objectId}")
      favs["${URLEncoder.encode(it?.resource?.id?.objectId).toLowerCase()}"] = [id:it?.id, name:it?.name, imageUrl:it?.imageUrl]
    }
  }
  state.favs = favs
  logDebug("App favorites updated!")
  // logDebug("formatted response: ${prettyJson(favs)}")
}

// void removePlayersLocal(String groupCoordinatorId) {
//   ChildDeviceWrapper cd = app.getChildDevices().find{cd ->  cd.getDataValue('id') == groupCoordinatorId}
//   String coordinatorId = cd.getDataValue('groupCoordinatorId')
//   Map data = ['playerIds': [groupCoordinatorId]]

//   String localApiUrl = "${cd.getDataValue('localApiUrl')}"
//   String endpoint = "groups/${coordinatorId}/groups/setGroupMembers"
//   String uri = "${localApiUrl}${endpoint}"
//   Map params = [uri: uri]
//   sendLocalJsonAsync(params: params, data: data)
// }

void componentUpdateBatteryStatus(DeviceWrapper player) {
  String baseUrl = player.getDataValue('localUpnpUrl')
  String uri = "${baseUrl}/status/batterystatus"
  Map params = [uri: uri, contentType: 'text/xml',]
  asynchttpGet('componentUpdateBatteryStatusCallback', params, [dni: player.getDeviceNetworkId()])
}

void componentUpdateBatteryStatusCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'componentUpdateBatteryStatusCallback')) { return }
  GPathResult xml = response.getXml()
  String battery = xml['LocalBatteryStatus'].children().find{it['@name'] == "Level"}.text().toString()
  String powerSource = xml['LocalBatteryStatus'].children().find{it['@name'] == "PowerSource"}.text().toString().replace('USB_POWER','mains').toLowerCase()
  String health = xml['LocalBatteryStatus'].children().find{it['@name'] == "Health"}.text().toString()
  String temperature = xml['LocalBatteryStatus'].children().find{it['@name'] == "Temperature"}.text().toString()
  List<Event> stats = [
    [name: 'battery', value: battery ],
    [name: 'powerSource', value: powerSource ],
    [name: 'health', value: health ],
    [name: 'temperature', value: temperature ],
  ]
  ChildDeviceWrapper child = app.getChildDevice(data.dni)
  stats.each{ child.updateChildBatteryStatus(it) }
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

String getLocalApiUrlForCoordinatorId(String groupCoordinatorId) {
  String localApiUrl = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId }.getDataValue('localApiUrl')
  return localApiUrl
}

String getLocalUpnpHostForCoordinatorId(String groupCoordinatorId) {
  String localUpnpHost = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId }.getDataValue('localUpnpHost')
  return localUpnpHost
}

String getCoordinatorGroupId(String groupCoordinatorId) {
  ChildDeviceWrapper coordinator = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId }
  String coordinatorGroupId = getGroupForPlayerDeviceLocal(coordinator)
  return coordinatorGroupId
}

