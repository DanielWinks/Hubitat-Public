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
  version: '0.5.2',
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

// =============================================================================
// Fields
// =============================================================================
@Field static Map playerSelectionOptions = new java.util.concurrent.ConcurrentHashMap()
@Field static Map discoveredSonoses = new java.util.concurrent.ConcurrentHashMap()
@Field static Map discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap()
@Field static Map SOURCES = [
  "\$": "None",
  "x-file-cifs:": "Library",
  "x-rincon-mp3radio:": "Radio",
  "x-sonosapi-stream:": "Radio",
  "x-sonosapi-radio:": "Radio",
  "x-sonosapi-hls:": "Radio",
  "x-sonos-http:sonos": "Radio",
  "aac:": "Radio",
  "hls-radio:": "Radio",
  "https?:": "File",
  "x-rincon-stream:": "LineIn",
  "x-sonos-htastream:": "TV",
  "x-sonos-vli:.*,airplay:": "Airplay",
  "x-sonos-vli:.*,spotify:": "Spotify",
  "x-rincon-queue": "Sonos Q"
]
// =============================================================================
// End Fields
// =============================================================================


// =============================================================================
// Getters and Setters
// =============================================================================
@CompileStatic
String getLocalApiPrefix(String ipAddress) {
  return "https://${ipAddress}/api/v1"
}

String getLocalUpnpHostForCoordinatorId(String groupCoordinatorId) {
  String localUpnpHost = app.getChildDevices().find{ cd -> cd.getDataValue('id') == groupCoordinatorId }.getDataValue('localUpnpHost')
  return localUpnpHost
}


// =============================================================================
// End Getters and Setters
// =============================================================================

// =============================================================================
// App Pages
// =============================================================================
Map mainPage() {
  state.remove("discoveryRunning")
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
    section('Optional Features (disable to reduce resource usage):', hideable: true) {
      input 'favMatching', 'bool', title: 'Enable "Current Favorite" status.', required: false, defaultValue: true
      input 'trackDataMetaData', 'bool', title: 'Include metaData and trackMetaData in trackData JSON', required: false, defaultValue: false
    }

    section('Logging Settings:', hideable: true) {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'traceLogEnable', 'bool', title: 'Enable trace logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      // input 'applySettingsButton', 'button', title: 'Apply Settings'
    }
  }
}

Map localPlayerPage() {
  if(!state.discoveryRunning) {
    subscribeToSsdpEvents(location)
    ssdpDiscover()
    state.discoveryRunning = true
    app.updateSetting('playerDevices', [type: 'enum', value: getCreatedPlayerDevices()])
  }

  dynamicPage(
		name: "localPlayerPage",
		title: "Discovery Started!",
		nextPage: 'localPlayerSelectionPage',
		install: false,
		uninstall: false
  ) {
    section("Please wait while we discover your Sonos. Click Next when all your devices have been discovered.") {
      paragraph (
        "<span class='app-state-${app.id}-discoveryInProgress'></span>"
      )
      paragraph (
        "<span class='app-state-${app.id}-sonosDiscoveredCount'>Found Devices (0): </span>" +
        "<span class='app-state-${app.id}-sonosDiscovered'></span>"
      )
    }
  }
}

Map localPlayerSelectionPage() {
  state.remove("discoveryRunning")
	unsubscribe(location, 'ssdpTerm.upnp:rootdevice')
	unsubscribe(location, 'sdpTerm.ssdp:all')
  LinkedHashMap newlyDiscovered = discoveredSonoses.collectEntries{id, player -> [(id.toString()): player.name]}
  LinkedHashMap previouslyCreated = getCurrentPlayerDevices().collectEntries{[(it.getDeviceNetworkId().toString()): it.getDataValue('name')]}
  LinkedHashMap selectionOptions = previouslyCreated
  Integer newlyFoundCount = 0
  newlyDiscovered.each{ k,v ->
    if(!selectionOptions.containsKey(k)) {
      selectionOptions[k] = v
      newlyFoundCount++
    }
  }

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
        title: "Select Sonos (${newlyFoundCount} newly found primaries, ${getCurrentPlayerDevices().size()} previously created):",
        type: 'enum',
        options: selectionOptions,
        multiple: true,
        submitOnChange: true,
        offerAll: false
      )
      input 'createPlayerDevices', 'button', title: 'Create Players'
      href (
        page: 'localPlayerPage',
        title: 'Continue Search',
        description: 'Click to show'
      )
      List<ChildDeviceWrapper> willBeRemoved = getCurrentPlayerDevices().findAll { p -> (!settings.playerDevices.contains(p.getDeviceNetworkId()) )}
      if(willBeRemoved.size() > 0 && !skipOrphanRemoval) {
        paragraph("The following devices will be removed: ${willBeRemoved.collect{it.getDataValue('name')}.join(', ')}")
      }
      List<String> willBeCreated = (settings.playerDevices - getCreatedPlayerDevices())
      if(willBeCreated.size() > 0) {
        String s = willBeCreated.collect{p -> selectionOptions.get(p)}.join(', ')
        paragraph("The following devices will be created: ${s}")
      }
      input 'skipOrphanRemoval', 'bool', title: 'Add devices only/skip removal', required: false, defaultValue: false, submitOnChange: true
    }
  }
}

Map groupPage() {
  if(!state.userGroups) { state.userGroups = [:] }
  Map coordinatorSelectionOptions = getCurrentPlayerDevices().collectEntries { player -> [(player.getDataValue('id')): player.getDataValue('name')] }
  Boolean coordIsS1 = (newGroupCoordinator && getDeviceFromRincon(newGroupCoordinator)?.getDataValue('swGen') == '1')
  logDebug("Selected group coordinator is S1: ${coordIsS1}")
  Map playerSelectionOptionsS1 = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator && getDeviceFromRincon(it.key).getDataValue('swGen') =='1' }
  Map playerSelectionOptionsS2 = coordinatorSelectionOptions.findAll { it.key != newGroupCoordinator && getDeviceFromRincon(it.key).getDataValue('swGen') =='2'}
  Map playerSelectionOptions = [:]
  if(!newGroupCoordinator) {playerSelectionOptions = playerSelectionOptionsS1 + playerSelectionOptionsS2}
  else if(newGroupCoordinator && coordIsS1) {playerSelectionOptions = playerSelectionOptionsS1}
  else {playerSelectionOptions = playerSelectionOptionsS2}

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
// End App Pages
// =============================================================================



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
// End Button Handlers
// =============================================================================



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

  state.remove('favs')
  unschedule('appGetFavoritesLocal')
}
// =============================================================================
// End Initialize() and Configure()
// =============================================================================



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
      if(playerInfo) {
        logInfo("Creating Sonos Advanced Player device for ${playerInfo?.name}")
        try {
          cd = addChildDevice('dwinks', 'Sonos Advanced Player', dni, [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo?.name}"])
        } catch (UnknownDeviceTypeException e) {logException('Sonos Advanced Player driver not found', e)}
      } else {
        logWarn("Attempted to create child device for ${dni} but did not find playerInfo")
      }
    }
    logInfo("Updating player info with latest info from discovery...")
    playerInfo.each { key, value -> cd.updateDataValue(key, value as String) }

    LinkedHashMap<String,String> macToRincon = discoveredSonoses.collectEntries{ k,v -> [k, v.id]}
    String rincon = macToRincon[dni]
    LinkedHashMap<String,Map> secondaries = discoveredSonosSecondaries.findAll{k,v -> v.primaryDeviceId == rincon}
    if(secondaries){
      List<String> secondaryDeviceIps = secondaries.collect{it.value.deviceIp}
      List<String> secondaryIds = secondaries.collect{it.value.id}
      if(secondaryDeviceIps && secondaryIds) {
        cd.updateDataValue('secondaryDeviceIps', secondaryDeviceIps.join(','))
        cd.updateDataValue('secondaryIds', secondaryIds.join(','))
      }
    }
    cd.secondaryConfiguration()
  }
  if(!skipOrphanRemoval) {removeOrphans()}
}

void removeOrphans() {
  getCurrentGroupDevices().each{ child ->
    String dni = child.getDeviceNetworkId()
    if(dni in settings.groupDevices) { return }
    else {
      logInfo("Removing group device not found in selected devices list: ${child}")
      app.deleteChildDevice(dni)
    }
  }
  if(!skipOrphanRemoval) {
    getCurrentPlayerDevices().each{ child ->
      String dni = child.getDeviceNetworkId()
      if(dni in settings.playerDevices) { return }
      else {
        logInfo("Removing player device not found in selected devices list: ${child}")
        app.deleteChildDevice(dni)
      }
    }
  }
}
// =============================================================================
// End Create Child Devices
// =============================================================================



// =============================================================================
// Local Discovery
// =============================================================================
void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
  discoveredSonoses = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
  discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
  discoveryQueue = new ConcurrentLinkedQueue<LinkedHashMap>()
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
  LinkedHashMap parsedEvent = parseLanMessage(event?.description)
  processParsedSsdpEvent(parsedEvent)
}

@CompileStatic
void processParsedSsdpEvent(LinkedHashMap event) {
  String ipAddress = convertHexToIP(event?.networkAddress)
  String ipPort = convertHexToInt(event?.deviceAddress)

  LinkedHashMap playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  if(playerInfo) {
    logTrace("Discovered playerInfo for ${ipAddress}")
  } else {
    logTrace("Did not receive playerInfo for ${ipAddress}")
    return
  }

  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")
  if(deviceDescription) {
    logTrace("Discovered device description for ${ipAddress}")
  } else {
    logTrace("Did not receive device description for ${ipAddress}")
    return
  }

  LinkedHashMap playerInfoDevice = playerInfo?.device as LinkedHashMap

  String modelName = deviceDescription['device']['modelName']
	String ssdpPath = event?.ssdpPath
	String mac = (deviceDescription['device']['MACAddress']).toString().replace(':','')
  String playerName = playerInfoDevice?.name
  String playerDni = mac
  String swGen = playerInfoDevice?.swGen
  String websocketUrl = playerInfoDevice?.websocketUrl
  String householdId = playerInfo?.householdId
  String playerId = playerInfo?.playerId
  String groupId = playerInfo?.groupId
  List<String> deviceCapabilities = playerInfoDevice?.capabilities as List<String>

  if(mac && playerInfoDevice?.name) {
    logTrace("Received SSDP event response for MAC: ${mac}, device name: ${playerInfoDevice?.name}")
  }

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
  if(playerInfoDevice?.primaryDeviceId && deviceCapabilities.contains('AUDIO_CLIP')) {
    LinkedHashMap discoveredSonosSecondary = [
      primaryDeviceId: playerInfoDevice?.primaryDeviceId,
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
    discoveredSonosSecondaries[mac] = discoveredSonosSecondary
    logTrace("Found secondary for ${playerInfoDevice?.primaryDeviceId}")
  }
  if(discoveredSonos?.name != null && discoveredSonos?.name != 'null') {
    discoveredSonoses[mac] = discoveredSonos
    sendFoundSonosEvents()
  } else {
    logTrace("Device id:${discoveredSonos?.id} responded to SSDP discovery, but did not provide device name. This is expected for right channel speakers on stereo pairs, subwoofers, and other 'non primary' devices.")
  }
}

@CompileStatic
String getFoundSonoses() {
  String foundDevices = ''
  List<String> discoveredSonosesNames = discoveredSonoses.collect{Object k, Object v -> ((LinkedHashMap)v)?.name as String }
  List<String> discoveredSonosesSecondaryPrimaryIds = discoveredSonosSecondaries.collect{Object k, Object v -> ((LinkedHashMap)v)?.primaryDeviceId as String }
  discoveredSonosesSecondaryPrimaryIds.each{
    String primaryDNI = getDNIFromRincon(it)
    if(discoveredSonoses.containsKey(primaryDNI)) {
      LinkedHashMap primary = (LinkedHashMap)discoveredSonoses[primaryDNI]
      discoveredSonosesNames.add("${primary?.name as String} Right Channel".toString())
    }
  }
  discoveredSonosesNames.sort()
  discoveredSonosesNames.each{ discoveredSonos -> foundDevices += "\n${discoveredSonos}" }
  return foundDevices
}

void sendFoundSonosEvents() {
  app.sendEvent(name: 'sonosDiscoveredCount', value: "Found Devices (${discoveredSonoses.size()} primary, ${discoveredSonosSecondaries.size()} secondary): ")
  app.sendEvent(name: 'sonosDiscovered', value: getFoundSonoses())
}
// =============================================================================
// End Local Discovery
// =============================================================================



// =============================================================================
// Helper methods
// =============================================================================
@CompileStatic
String getDNIFromRincon(String rincon) {
  return rincon.tokenize('_')[1][0..-6]
}

ChildDeviceWrapper getDeviceFromRincon(String rincon) {
  List<ChildDeviceWrapper> childDevices = app.getCurrentPlayerDevices()
  ChildDeviceWrapper dev = childDevices.find{ it.getDataValue('id') == rincon}
  return dev
}


List<ChildDeviceWrapper> getDevicesFromRincons(LinkedHashSet<String> rincons) {
  List<ChildDeviceWrapper> children = rincons.collect{ player -> getDeviceFromRincon(player)  }
  children.removeAll([null])
  return children
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
  List<ChildDeviceWrapper> currentPlayers = []
  app.getChildDevices().each{child -> if(child.getDataValue('id')) { currentPlayers.add(child)}}
  return currentPlayers
}

// @CompileStatic
// void registerAllPlayersInRinconMap(DeviceWrapper cd) {
//   cd.addAllPlayersToRinconMap(getCurrentPlayerDevices())
// }

List<ChildDeviceWrapper> getCurrentGroupDevices() {
  List<ChildDeviceWrapper> currentGroupDevs = []
  app.getChildDevices().each{child -> if(child.getDataValue('id') == null) { currentGroupDevs.add(child)}}
  return currentGroupDevs
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
  try {
    httpGet(params) { resp ->
      if (resp && resp.data && resp.success) { return resp.data }
    }
  } catch(Exception e){
    logInfo("Could not connect to: ${ipAddress}. If this is a Sonos player, please report an issue. Note that RIGHT channel speakers on a stereo pair, subwoofers, or rear channel speakers this is expected. Only LEFT channel in stereo pairs (or Arc/Beam in a center + rear setup) will respond.")
  }
}

GPathResult getDeviceDescriptionLocalSync(String ipAddress) {
  if(!ipAddress.contains(':')) { ipAddress = "${ipAddress}:1400"}
  Map params = [
    uri:  "http://${ipAddress}/xml/device_description.xml",,
    requestContentType: 'application/xml',
    contentType: 'application/xml'
  ]
  try {
    httpGet(params) { resp ->
      if (resp && resp.data && resp.success) { return resp.data }
      else { logError(resp.data) }
    }
  } catch(Exception e){
    logInfo("Could not connect to: ${ipAddress}. If this is a Sonos player, please report an issue. Note that RIGHT channel speakers on a stereo pair, subwoofers, or rear channel speakers this is expected. Only LEFT channel in stereo pairs (or Arc/Beam in a center + rear setup) will respond.")
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
  List<DeviceWrapper> groupedDevices = getDevicesFromRincons(groupedRincons)
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

DeviceWrapper getGroupCoordinatorForPlayerDeviceLocal(DeviceWrapper device) {
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
  return getDeviceFromRincon(groupId.tokenize(':')[0])
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
// Helper methods
// =============================================================================



// =============================================================================
// Component Methods for Child Event Processing
// =============================================================================
@CompileStatic
void updateGroupDevices(String coordinatorId, List<String> playersInGroup) {
  logTrace('updateGroupDevices')
  // Update group device with current on/off state
  List<ChildDeviceWrapper> groupsForCoord = getCurrentGroupDevices().findAll{it.getDataValue('groupCoordinatorId') == coordinatorId }
  groupsForCoord.each{gd ->
    List<String> playerIds = gd.getDataValue('playerIds').tokenize(',')
    HashSet<String> list1 = new  HashSet<String>(playerIds)
    HashSet<String> list2 = new  HashSet<String>(playersInGroup)
    list1.add(coordinatorId)
    list2.add(coordinatorId)
    Boolean allPlayersAreGrouped = list1.equals(list2)
    if(allPlayersAreGrouped) { gd.sendEvent(name: 'switch', value: 'on') }
    else { gd.sendEvent(name: 'switch', value: 'off') }
  }
}
// =============================================================================
// Component Methods for Child Event Processing
// =============================================================================



// =============================================================================
// HTTP Helpers
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
  logTrace("sendLocalCommandAsync: ${params}")
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
  logTrace("sendLocalQuerySync: ${params}")
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
  logTrace("sendLocalQueryAsync: ${params}")
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
  logTrace("sendLocalJsonAsync: ${params}")
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
    logTrace("localControlCallback: ${response.getData()}")
  }
}

Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if(response?.status == 499) {
    try{
      Map errData = response.getErrorData()
      if(errData?.groupStatus == 'GROUP_STATUS_MOVED') {
        ChildDeviceWrapper child = getDeviceFromRincon(errData?.playerId)
        if(child) {
          child.subscribeToZgtEvents()
          logDebug('Resubscribed to ZGT to handle "GROUP_STATUS_MOVED" errors')
        }
      }
    } catch(Exception e){}
  } else if (response?.status != 200 || response.hasError()) {
    logError("Request returned HTTP status ${response.status}")
    logError("Request error message: ${response.getErrorMessage()}")
    try{logError("Request ErrorData: ${response.getErrorData()}")} catch(Exception e){}
    try{logErrorJson("Request ErrorJson: ${response.getErrorJson()}")} catch(Exception e){}
    try{logErrorXml("Request ErrorXml: ${response.getErrorXml()}")} catch(Exception e){}
  }
  if (response.hasError()) { return false } else { return true }
}
// =============================================================================
// HTTP Helpers
// =============================================================================



// =============================================================================
// Local Control Component Methods
// =============================================================================
void componentPlayTextNoRestoreLocal(DeviceWrapper device, String text, BigDecimal volume = null, String voice = null) {
  logDebug("${device} play text ${text} (volume ${volume ?: 'not set'})")
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  Map tts = textToSpeech(text, voice)
  String streamUrl = tts.uri
  if (volume) data.volume = (int)volume

  String playerId = device.getDataValue('id')
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURIAndPlay(device, streamUrl)
}

void removeAllTracksFromQueue(DeviceWrapper device, String callbackMethod = 'localControlCallback') {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map params = getSoapActionParams(ip, AVTransport, 'RemoveAllTracksFromQueue')
  asynchttpPost(callbackMethod, params)
}

void setAVTransportURIAndPlay(DeviceWrapper device, String currentURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [CurrentURI: currentURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  Map data = [dni:device.getDeviceNetworkId()]
  asynchttpPost('setAVTransportURIAndPlayCallback', params, data)
}

void setAVTransportURIAndPlayCallback(AsyncResponse response, Map data = null) {
  if(!responseIsValid(response, 'setAVTransportURICallback')) { return }
  ChildDeviceWrapper child = app.getChildDevice(data.dni)
  child.play()
}

void setAVTransportURI(DeviceWrapper device, String currentURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [CurrentURI: currentURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'SetAVTransportURI', controlValues)
  Map data = [dni:device.getDeviceNetworkId()]
  asynchttpPost('localControlCallback', params, data)
}

void addURIToQueue(DeviceWrapper device, String enqueuedURI, String currentURIMetaData = null) {
  String ip = getLocalUpnpHostForCoordinatorId(device.currentValue('groupCoordinatorId', true))
  Map controlValues = [EnqueuedURI: enqueuedURI, CurrentURIMetaData: currentURIMetaData]
  if(currentURIMetaData) {controlValues += [CurrentURIMetaData: currentURIMetaData]}
  Map params = getSoapActionParams(ip, AVTransport, 'AddURIToQueue', controlValues)
  // removeAllTracksFromQueue(device)
  asynchttpPost('localControlCallback', params)
}

void componentLoadStreamUrlLocal(DeviceWrapper device, String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play track ${streamUrl} (volume ${volume ?: 'not set'})")
  if(volume) {componentSetGroupLevelLocal(device, volume)}
  setAVTransportURIAndPlay(device, streamUrl)
}

void componentSetStreamUrlLocal(DeviceWrapper device, String streamUrl, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
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
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupRelativeLevelLocal(DeviceWrapper device, Integer adjustment) {
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [Adjustment: adjustment]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetRelativeGroupVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupLevelLocal(DeviceWrapper device, BigDecimal level) {
  DeviceWrapper coordinator = getGroupCoordinatorForPlayerDeviceLocal(device)
  String ip = coordinator.getDataValue('localUpnpHost')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}
// =============================================================================
// Local Control Component Methods
// =============================================================================
