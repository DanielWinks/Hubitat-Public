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
  name: 'Sonos Cloud Controller',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  category: 'Audio',
  description: 'Sonos speaker integration with cloud functionality, such as non-interrupting announcements',
  iconUrl: '',
  iconX2Url: '',
  installOnOpen: false,
  iconX3Url: '',
  singleThreaded: true,
  importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Apps/SonosCloudApp.groovy'
)

preferences {
  page(name: 'mainPage', install: true, uninstall: true)
  page(name: 'authorizePage')
  page(name: 'playerPage')
  page(name: 'localPlayerPage')
  page(name: 'localPlayerSelectionPage')
  page(name: 'groupPage')
}

@Field static final String apiPrefix = 'https://api.ws.sonos.com/control/api/v1'
@Field static final String authApiPrefix = 'https://api.sonos.com/login/v3/oauth'
@Field static final String xWwwFormUrlencodedUtf8 = 'application/x-www-form-urlencoded;charset=utf-8'

@CompileStatic
String getLocalApiPrefix(String ipAddress) {
  return "https://${ipAddress}/api/v1"
}

String getHubitatStateRedirect() { return URLEncoder.encode('https://cloud.hubitat.com/oauth/stateredirect') }

// =============================================================================
// OAuth
// =============================================================================

mappings {
  path('/oauth/initialize') {
  action: [ GET: 'oauthInitialize' ]
  }
  path('/callback') {
  action: [ GET: 'oauthCallback' ]
  }
  path('/oauth/callback') {
  action: [ GET: 'oauthCallback' ]
  }
}

String getAuthorizationB64() {
  if (settings.clientKey == null || settings.clientSecret == null) {
    logError('Client key or Secret not set')
    return
  }
  return "${settings.clientKey}:${settings.clientSecret}".bytes.encodeBase64()
}

Map authorizePage() {
  boolean configured = settings.clientKey != null && settings.clientSecret != null
  boolean authenticated = state.authToken != null

  dynamicPage(title: 'Sonos Developer Authorization', nextPage: 'mainPage') {
    section {
      paragraph ('You need an account on the <a href=\'https://developer.sonos.com/\' target=\'_blank\'>Sonos Developer site</a>, ' +
          'create a new <b>Control Integration</b>. Provide an Integration name, description and key name ' +
          'and set the redirect URI to <u>https://cloud.hubitat.com/oauth/stateredirect</u> and save the Integration. ' +
          'Enter the provided Key and Secret values below then select the account authorization button.')

      input (name: 'clientKey', type: 'text', title: 'Client Key', description: '', required: true, defaultValue: '', submitOnChange: true)
      input (name: 'clientSecret', type: 'password', title: 'Client Secret', description: '', required: true, defaultValue: '', submitOnChange: true)
    }
  if (configured) {
    section {
      href (
        url: oauthInitialize(),
        title: 'Sonos Account Authorization',
        style: 'external',
        description: authenticated ? 'Your Sonos account is connected, select to re-authenticate' : '<b>Select to authenticate to your Sonos account</b>'
        )
      }
    }
  }
}

String oauthInitialize() {
  tryCreateAccessToken()
  state.oauthState = URLEncoder.encode("${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}")
  logDebug("CallBack URL: ${URLEncoder.encode("https://cloud.hubitat.com/apps/${app.id}/callback?access_token=${state.accessToken}")}}")
  logDebug "oauthState: ${getApiServerUrl()}/${hubUID}/apps/${app.id}/events?access_token=${state.accessToken}"
  String link = "${authApiPrefix}?client_id=${settings.clientKey}&response_type=code&state=${state.oauthState}&scope=playback-control-all&redirect_uri=${getHubitatStateRedirect()}"
  logInfo "oauth link: ${link}"
  return link
}

Map oauthCallback() {
  state.remove('authToken')
  state.remove('refreshToken')
  state.remove('authTokenExpires')
  state.remove('scope')
  state.remove('oauthReqStart')

  logInfo "oauthCallback: ${params}"
  if (URLEncoder.encode(params.state) != state.oauthState) {
    logError 'Aborted due to security problem: OAuth state does not match expected value'
    return oauthFailure()
  }

  if (params.code == null) {
    logError 'Aborted: OAuth one-time use authorization code not received'
    return oauthFailure()
  }

  try {
    logInfo 'Requesting access token'
    httpPost([
      uri: "${authApiPrefix}/access?grant_type=authorization_code&code=${params.code}&redirect_uri=${getHubitatStateRedirect()}",
      headers: [
        authorization: "Basic ${getAuthorizationB64()}",
        'Content-Type': "${xWwwFormUrlencodedUtf8}"
      ]
    ]) {resp ->
          logDebug 'Token request response: ' + resp.data
          if (resp && resp.data && resp.success) {
            logInfo 'Received access token'
            parseAuthorizationToken(resp.data)
          } else {
            logError 'OAuth error: ' + resp.data
          }
      }
  } catch (e) {
    logException "OAuth error: ${e}", e
  }
  return state.authToken == null ? oauthFailure() : oauthSuccess()
}

Map oauthSuccess() {
  refreshPlayersAndGroups()
  return render (
    contentType: 'text/html',
    data: """
      <h2 style='color:green;'>Success!</h2 >
      <p>Your Sonos Account is now connected to Hubitat</p>
      <p>Close this window to continue setup.</p>
      """
    )
}

Map oauthFailure() {
  return render (
    contentType: 'text/html',
    data: """
      <h2 style='color:red;'>Failed!</h2>
      <p>The connection could not be established!</p>
      <p>Close this window to try again.</p>
      """
    )
}

void refreshToken() {
  logInfo('Refreshing access token')

  if (state.refreshToken == null) {
    logError('Unable to authenticate as refresh token is not set, please re-authorize app via Sonos oAuth')
    return
  }

  Map params = [
    uri: "${authApiPrefix}/access?grant_type=refresh_token&refresh_token=${state.refreshToken}",
    headers: [authorization: "Basic ${getAuthorizationB64()}"],
    requestContentType: 'application/json',
    contentType: 'application/json'
    ]
  sendCommandAsync(params, "refreshTokenCallback")
}

void refreshTokenCallback(AsyncResponse response, Map data = null) {
  if (!response.hasError()) {
    logInfo('Received access token')
    logDebug("Token refresh response: ${response.getJson()}")
    state.refreshAttemptCounter = 0
    parseAuthorizationToken(response.getJson())
  } else {
    logError("OAuth error: ${response.getErrorData()}.")
    if(state.refreshAttemptCounter < 10) {
      logWarn('Scheduling another refresh attempt in 60 seconds.')
      runIn(60, 'refreshToken')
      state.refreshAttemptCounter++
    } else if (state.refreshAttemptCounter >= 10) {
      logError('Max OAuth token refresh attempts reached. Unable to refresh token within 10 attempts.')
    }
  }
}

void parseAuthorizationToken(Map data) {
  logDebug('Parsing tokens...')
  logDebug(data.inspect())
  state.authToken = data.access_token
  logDebug("authToken: ${state.authToken}")
  state.refreshToken = data.refresh_token
  state.authTokenExpires = now() + (data.expires_in * 1000)
  state.scope = data.scope
}

// =============================================================================
// Main Page
// =============================================================================

Map mainPage() {
  state.remove("discoveryRunning")
  boolean configured = settings.clientKey != null && settings.clientSecret != null
  boolean authenticated = state.authToken != null
  dynamicPage(title: 'Sonos Advanced Controller') {
    if(cloudEnable) {
      tryCreateAccessToken()
      if(!state.accessToken) {
        section ("<h2 style='color:red;'>OAuth is not enabled for app!! Please enable in Apps Code.</h2>"){      }
      }
    }

    section {
      label title: 'Sonos Advanced Controller',
      required: false
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'
      if(cloudEnable) {
        href (
          page: 'authorizePage',
          title: 'Sonos Account Authorization',
          description: configured && authenticated ? 'Your Sonos account is connected' : 'Select to setup credentials and authenticate to Sonos'
        )
      }
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
      input 'cloudEnable', 'bool', title: 'Enable Cloud API Functionality', required: false, defaultValue: false
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      input 'applySettingsButton', 'button', title: 'Apply Settings'
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
	String mac = parsedEvent.mac
  String playerName = playerInfo?.device?.name
  String playerDni = parsedEvent.mac
  String swGen = playerInfo?.device?.swGen
  String websocketUrl = playerInfo?.device?.websocketUrl
  String householdId = playerInfo?.householdId
  String playerId = playerInfo?.playerId
  String groupId = playerInfo?.groupId
  List<String> deviceCapabilities = playerInfo?.device?.capabilities

  discoveredSonoses.put("${mac}", [
    name: playerInfo?.device?.name,
    id: parsedEvent.mac,
    swGen: playerInfo?.device?.swGen,
    capabilities: playerInfo?.device?.capabilities,
    modelName: deviceDescription['device']['modelName'],
    householdId: playerInfo?.householdId,
    websocketUrl: playerInfo?.device?.websocketUrl,
    deviceIp: "${ipAddress}:1400"
  ])
  state.playerPageRefresh = true
  logDebug("Should refresh now...")
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
  }
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
  unsubscribe()
  Map selectionOptions = discoveredSonoses.collectEntries{id, player -> [(id): player.name]}
  dynamicPage(
		name: "localPlayerSelectionPage",
		title: "Select Sonos Devices",
		nextPage: 'mainPage',
		install: true,
		uninstall: true
  ) {
    section("Select your device(s) below.") {
      input (name: 'playerDevices', title: "Select Sonos (${discoveredSonoses.size()} found)", type: 'enum', options: selectionOptions, multiple: true)
      href (
        page: 'localPlayerPage',
        title: 'Continue Search',
        description: 'Click to show'
      )
    }
  }
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
  }
}

void refreshPlayersAndGroups() {
  getHouseholds()
  getPlayersAndGroups()
}

// =============================================================================
// Button Handlers
// =============================================================================
void appButtonHandler(String buttonName) {
  if(buttonName == 'applySettingsButton') { applySettingsButton() }
  if(buttonName == 'saveGroup') { saveGroup() }
  if(buttonName == 'deleteGroup') { deleteGroup() }
  if(buttonName == 'cancelGroupEdit') { cancelGroupEdit() }
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
  if(cloudEnable) {
    try { refreshToken()}
    catch (Exception e) { logError("Could not refresh token: ${e}") }
    schedule('0 0 0,6,12,18 * * ?', 'refreshToken')
  }


  // try { refreshPlayersAndGroups() }
  // catch (Exception e) { logError("refreshPlayersAndGroups() Failed: ${e}")}
  // try { createPlayerDevices() }
  // catch (Exception e) { logError("createPlayerDevices() Failed: ${e}")}
  // try { createGroupDevices() }
  // catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
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
      } catch (UnknownDeviceTypeException e) {
        logException('createGroupDevices', e)
      }
    }
    String coordinatorId = it.value.coordinatorId as String
    String playerIds =  it.value.playerIds.join(',')
    ChildDeviceWrapper coordDev = app.getChildDevices().find{ cd -> cd.getDataValue('id') == coordinatorId}
    String householdId = coordDev.getDataValue('householdId')
    device.updateDataValue('coordinatorId', coordinatorId)
    device.updateDataValue('playerIds', playerIds)
    device.updateDataValue('householdId', householdId)
  }
  removeOrphans()
}

void createPlayerDevices() {
  if(!state.players) {return}
  List<Map> devicesToCreate = state.players.values().findAll { it -> it.id in playerDevices } ?: []
  logDebug(devicesToCreate)
  for (Map player in devicesToCreate) {
    String oldDni = "${app.id}-${player.id}"
    String dni = "${player.id}".tokenize('_')[1][0..-6] // Get MAC address from RINCON_XXXX names
    DeviceWrapper device = getChildDevice(dni)
    if (device == null) {
      try {
        logInfo "Creating child audio notification device for ${player.name}"

        device = addChildDevice('dwinks', 'Sonos Cloud Player', dni, [name: 'Sonos Cloud Player', label: "Sonos Cloud - ${player.name}"])
      } catch (UnknownDeviceTypeException e) {
        logException 'Sonos Cloud Player driver not found (needs installing?)', e
      }
    } else if(device.getDeviceNetworkId() == oldDni) {
      //Migrate any devices using old DNI scheme
      String newDni = dni.tokenize('_')[1][0..-6]
      if(dni != newDni) {  }
      device.setDeviceNetworkId(newDni)
      logDebug("Changing device DNI from ${dni} to ${newDni} to enable local control events.")
    }

    player.each { key, value -> if(key != 'zoneInfo') { device.updateDataValue(key, value as String) }}
    player.each { key, value -> logDebug("K:V ${key}:${value}")}
    String ip = ((player.websocketUrl.replace('wss://','')).tokenize(':')[0])+':1400'
    player.each { device.updateDataValue('deviceIp', ip)}
    getPlayerDeviceDescription(ip)
  }
  removeOrphans()
}

void getPlayerDeviceDescription(String ipAddress) {
  Map params = [
    uri: 'http://192.168.1.36:1400/xml/device_description.xml'
  ]
  asynchttpGet('getPlayerDeviceDescriptionCallback', params)
}

void getPlayerDeviceDescriptionCallback(AsyncResponse response, Map data = null) {
  logDebug("response.status = ${response.status}")
  if(response.hasError()) {
    logDebug("${response.getErrorData()}")
    return
  }
  GPathResult xmlData = response.getXml()
  String modelName = xmlData['device']['modelName'].text()
  String swGen = xmlData['device']['swGen'].text()
  String roomName = xmlData['device']['roomName'].text()
  String dni = xmlData['device']['MACAddress'].text().replace(':','')

  DeviceWrapper device = getChildDevice(dni)
  if (device != null) {
    logDebug("Setting device data: ${modelName} ${swGen} ${roomName}")
    device.updateDataValue('modelName', modelName)
    device.updateDataValue('swGen', swGen)
    device.updateDataValue('roomName', roomName)
  }
  // logDebug("Response: ${().getName()}")
}

void removeOrphans() {
  for (ChildDeviceWrapper child in app.getChildDevices()) {
    String dni = child.getDeviceNetworkId()
    if(child.getDataValue('id') in playerDevices) {
      return
    } else if(!groupsDNIs.any{it -> it.toString() == dni}) {
      logDebug("Removing child not found in user groups: ${child}")
      app.deleteChildDevice(dni)
    } else {
      logDebug("Removing child not found in selected devices list: ${child}")
      app.deleteChildDevice(dni)
    }
  }
}

// =============================================================================
// Get all music players WIP FOR FOLLOWING "REAL" SONOS PLAYERS
// =============================================================================

void getAllMusicPlayers() {
  Map params = [
    uri:"http://127.0.0.1:8080/device/listJson?capability=capability.musicPlayer",
    requestContentType: 'application/json',
    contentType: 'application/json',
    textParser: true
  ]
  logDebug(prettyJson(params))
  asynchttpGet('showMusic', params, null)
}

void showMusic(AsyncResponse response, Map data = null) {
  logDebug(response.getStatus())
  logDebug(response.getData())
}

// =============================================================================
// Helper methods
// =============================================================================

List<String> getAllPlayersForGroupDevice(DeviceWrapper device) {
  List<String> playerIds = [device.getDataValue('coordinatorId')]
  playerIds.addAll(device.getDataValue('playerIds').split(','))
  return playerIds
}

void getHouseholds() {
  if(!state.householdsLastUpdated) { state.householdsLastUpdated = now() }
  if(now() - state.householdsLastUpdated < 1000 * 60 * 2) { return } //Only update every 2 minutes
  Map params = [uri: "${apiPrefix}/households",headers: [authorization: "Bearer ${state.authToken}"]]
  sendQueryAsync(params, "getHouseholdsCallback")
  state.householdsLastUpdated = now()
}

void getHouseholdsCallback(AsyncResponse response, Map data = null) {
  logDebug("getHouseholds request response: ${response.getData()}")
  if (response.hasError()) {
    logError("getHouseholds error: ${response.getErrorData()}")
    return
  }

  List<String> households = []
  logDebug(response.getJson()['households'])
  households = response.getJson()['households'] *.id ?: []
  logDebug("Received ${households.size()} households")
  state.households = households
}

void getPlayersAndGroups(Map data = null) {
  state.households.each{ householdId -> getPlayersAndGroupsAsync(householdId, data) }
}

void getPlayersAndGroupsAsync(String householdId, Map data = null) {
  logDebug("Requesting players and groups for ${householdId}")
  Map params = [
    uri: "${apiPrefix}/households/${householdId}/groups",
    headers: [ authorization: 'Bearer ' + state.authToken, contentType: 'application/json' ]
  ]
  data = data != null ? data + [householdId: householdId] : [householdId: householdId]
  sendQueryAsync(params, "getPlayersAndGroupsCallback", data)
}

void getPlayersAndGroupsCallback(AsyncResponse response, Map data = null) {
  logDebug("getPlayersAndGroupsCallback Data: ${data}")
  if(response.hasError()) {
    String errorData = response.getErrorData()
    if(!errorData.contains("Invalid Access Token")) { //Expected as we're closing app before async returns?
      logError("getPlayers error: ${response.getErrorData()}")
    }
    return
  }

  Map<String, List<Map>> result = response.getJson()
  logDebug("getPlayers result: ${prettyJson(result)}")
  Map household = [householdId: data.householdId]

  state.players = result.players.collectEntries { p -> [(p.id): p + household] }
  state.groups = result.groups.collectEntries { g -> [(g.id): g + household] }
  if(data.containsKey(joinPlayers)) { joinPlayers(data.coordinatorId, data.playerIds) }
  if(data.containsKey(removePlayers)) { removePlayers(data.coordinatorId) }
  if(data.containsKey(ungroupPlayer)) { ungroupPlayer(data.playerId) }

  playerSelectionOptions = state.players.collectEntries {id, player ->  [(id): player.name]  }
}

Map getPlayerInfoLocalSync(String ipAddress) {
  if(!ipAddress.contains(':')) { ipAddress = "${ipAddress}:1443"}
  Map params = [
  uri:  "${getLocalApiPrefix(ipAddress)}/players/local/info",
  headers: ['X-Sonos-Api-Key': '123e4567-e89b-12d3-a456-426655440000'],
  requestContentType: 'application/json',
  contentType: 'application/json',
  ignoreSSLIssues: true
  ]
  httpGet(params) { resp ->
    if (resp && resp.data && resp.success) {
      return resp.data
    } else {
      logError(resp.data)
    }
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
    if (resp && resp.data && resp.success) {
      return resp.data
    } else {
      logError(resp.data)
    }
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

// =============================================================================
// Component Methods for Child Devices
// =============================================================================

void componentPlayText(DeviceWrapper device, String text, BigDecimal volume = null, String voice = null) {
  String playerId = device.getDataValue('id')
  logDebug "${device} play text ${text} (volume ${volume ?: 'not set'})"
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  Map tts = textToSpeech(text, voice)
  data.streamUrl = tts.uri
  if (volume) data.volume = (int)volume
  postJsonAsync("${apiPrefix}/players/${playerId}/audioClip", data)
}

void componentPlayTrack(DeviceWrapper device, String uri, BigDecimal volume = null) {
  String playerId = device.getDataValue('id')
  logDebug("${device} play track ${uri} (volume ${volume ?: 'not set'})")
  Map data = ['name': 'HE Audio Clip', 'appId': 'com.hubitat.sonos']
  if (uri?.toUpperCase() != 'CHIME') { data['streamUrl'] = uri }
  if (volume) { data['volume'] = (int)volume }
  postJsonAsync("${apiPrefix}/players/${playerId}/audioClip", data)
}

void componentMutePlayer(DeviceWrapper device, Boolean muted) {
  logDebug('Muting...')
  String playerId = device.getDataValue('id')
  Map data = [muted:muted]
  postJsonAsync("${apiPrefix}/players/${playerId}/playerVolume/mute", data)
}

void componentMuteGroup(DeviceWrapper device, Boolean muted) {
  logDebug('Muting...')
  String playerId = device.getDataValue('id')
  String groupId = getGroupForPlayerDeviceLocal(device)
  Map data = [muted:muted]
  postJsonAsync("${apiPrefix}/groups/${groupId}/groupVolume/mute", data)
}

void componentSetGroupRelativeLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting relative group volume to ${level}...")
  String groupId = getGroupForPlayerDeviceLocal(device)
  Map data = [volumeDelta:level as Integer]
  postJsonAsync("${apiPrefix}/groups/${groupId}/groupVolume/relative", data)
}

void componentSetPlayerRelativeLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting player relative volume to ${level}...")
  String playerId = device.getDataValue('id')
  Map data = [volumeDelta:level as Integer]
  postJsonAsync("${apiPrefix}/players/${playerId}/playerVolume/relative", data)
}

void componentGetFavorites(DeviceWrapper device) {
  logDebug('Getting favorites...')
  String householdId = getHouseholdForPlayerDeviceLocal(device)
  Map params = [uri: "${apiPrefix}/households/${householdId}/favorites", headers: [authorization: 'Bearer ' + state.authToken, contentType: 'application/json']]
  sendQueryAsync(params, "getFavoritesCallback", [dni:device.getDeviceNetworkId()])
}

void getFavoritesCallback(AsyncResponse response, Map data = null) {
  ChildDeviceWrapper child = app.getChildDevice(data.dni)
  if (response.hasError()) {
    logError("getHouseholds error: ${response.getErrorData()}")
    return
  }
  List respData = response.getJson().items
  Map formatted = respData.collectEntries() { [it.id, [name:it.name, imageUrl:it.imageUrl]] }
  logDebug("formatted response: ${prettyJson(formatted)}")
  formatted.each(){it ->
    child.sendEvent(
      name: "Favorite #${it.key} ${it.value.name}",
      value: "<img src=\"${it.value.imageUrl}\" width=\"200\" height=\"200\" >",
      isStateChange: false
    )
  }
}

void componentLoadFavorite(DeviceWrapper device, String favoriteId) {
  logDebug('Loading favorites...')
  String action = "REPLACE"
  Boolean playOnCompletion = true
  Boolean repeat = false
  Boolean repeatOne = true
  Boolean shuffle = false
  Boolean crossfade = true
  componentLoadFavoriteFull(device, favoriteId, action, repeat, repeatOne, shuffle, crossfade, playOnCompletion)
}

void componentLoadFavoriteFull(DeviceWrapper device, String favoriteId, String action, Boolean repeat, Boolean repeatOne, Boolean shuffle, Boolean crossfade, Boolean playOnCompletion) {
  logDebug('Loading favorites full options...')
  String groupId = getGroupForPlayerDeviceLocal(device)
  Map data = [
    action:"REPLACE",
    favoriteId:favoriteId,
    playOnCompletion:true,
    playModes:['repeat': false,'repeatOne': true],
  ]
  postJsonAsync("${apiPrefix}/groups/${groupId}/favorites", data)
}

void componentSetPlayModes(DeviceWrapper device, Map playModes) {
  logDebug 'Setting Play Modes...'
  String groupId = getGroupForPlayerDeviceLocal(device)
  postJsonAsync("${apiPrefix}/groups/${groupId}/playback/playMode", playModes)
}

void componentUngroupPlayer(DeviceWrapper device) {
  logDebug('Removing player from group...')
  String playerId = device.getDataValue('id')
  getPlayersAndGroups([ungroupPlayer: true, playerId: playerId])
}

void componentGroupPlayers(DeviceWrapper device) {
  logDebug('Adding players to group...')
  String householdId = getHouseholdForPlayerDeviceLocal(device)
  List coordinatorId = [device.getDataValue('coordinatorId')]
  List playerIds = device.getDataValue('playerIds').split(',')
  Map data = ['playerIds': coordinatorId + playerIds]
  postJsonAsync("${apiPrefix}/households/${householdId}/groups/createGroup", data)
}

void componentJoinPlayersToCoordinator(DeviceWrapper device) {
  logDebug('Joining players to coordinator...')
  String coordinatorId = device.getDataValue('coordinatorId')
  String playerIds = device.getDataValue('playerIds')
  getPlayersAndGroups([joinPlayers: true, coordinatorId: coordinatorId, playerIds: playerIds])
}

void componentRemovePlayersFromCoordinator(DeviceWrapper device) {
  logDebug('Removing players from coordinator...')
  String coordinatorId = device.getDataValue('coordinatorId')
  String playerIds = device.getDataValue('playerIds')
  getPlayersAndGroups([removePlayers: true, coordinatorId: coordinatorId, playerIds: playerIds])
}

void joinPlayers(String coordinatorId, String playerIdsJoined) {
  logDebug("PlayerIds=: ${playerIdsJoined}")
  String coordinatorIdGroup = ((state.groups.find{ it -> it.value.coordinatorId  == coordinatorId}).key).replace(':','%3A')
  List playerIds = playerIdsJoined.split(',') as List
  playerIds.add(coordinatorId)
  Map data = ['playerIds': playerIds]
  postJsonAsync("${apiPrefix}/groups/${coordinatorIdGroup}/groups/setGroupMembers", data)
}

void removePlayers(String coordinatorId) {
  String coordinatorIdGroup = ((state.groups.find{ it -> it.value.coordinatorId  == coordinatorId}).key).replace(':','%3A')
  Map data = ['playerIds': [coordinatorId]]
  postJsonAsync("${apiPrefix}/groups/${coordinatorIdGroup}/groups/setGroupMembers", data)
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
  String rincon = cd.getDataValue('id')
  String currentGroupCoordinatorId = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent()['@Coordinator']
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
  propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent().children().each{ groupedRincons.add(it['@UUID']) }
  if(groupedRincons.size() == 0) {
    logDebug("No grouped rincons found!")
    return
  }
  groupedRincons.each{groupIds.add("${it}".tokenize('_')[1][0..-6])}
  groupIds.each{groupedDevices.add(getChildDevice(it))}

  groupedDevices.each{dev -> if(currentGroupCoordinatorId && dev) {dev.updateDataValue('groupCoordinatorId', currentGroupCoordinatorId)}}
  groupedDevices.each{dev -> if(groupedRincons && dev && groupedRincons.size() > 0) {dev.updateDataValue('groupIds', groupedRincons.join(','))}}

  String groupId = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent()['@ID']
  String currentGroupCoordinatorName = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}['@ZoneName']
  Integer currentGroupMemberCount = propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent().children().size()

  List currentGroupMemberNames = []
  propertyset['property']['ZoneGroupState']['ZoneGroupState']['ZoneGroups'].children().children().findAll{it['@UUID'] == rincon}.parent().children().each{ currentGroupMemberNames.add(it['@ZoneName']) }
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
}

// =============================================================================
// Get and Post Helpers
// =============================================================================

void postJsonAsync(String uri, Map data = [:]) {
  if (state.authToken == null) {
    logError 'Authorization token not set, please connect the sonos account'
    return
  }
  if (now() >= state.authTokenExpires - 600) {
    refreshToken()
  }
  logDebug "post ${uri}: ${prettyJson(data)}"
  asynchttpPost(
    'postJsonAsyncCallback', [
      uri: uri,
      headers: [authorization: 'Bearer ' + state.authToken],
      contentType: 'application/json',
      body: JsonOutput.toJson(data)
    ]
  )
}

void postJsonAsyncCallback(AsyncResponse response, Map data) {
  if (response.status != 200) {
    logError("post request returned HTTP status ${response.status}")
  }
  if (response.hasError()) {
    logError("post request error: ${response.getErrorMessage()}")
  } else {
    logDebug("postJsonAsyncCallback: ${response.data}")
  }
}

void sendCommandAsync(Map params, String callbackMethod, Map data = null) {
  logDebug(prettyJson(params))
  try{
    asynchttpPost(callbackMethod, params, data)
  }
  catch(Exception e){
    if(e.message.toString() != 'OK') {
      logError(e.message)
    }
  }
}

void sendLocalCommandAsync(Map args) {
  if(args.endpoint == null) { return }
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

void sendQueryAsync(Map params, String callbackMethod, Map data = null) {
  if (now() >= state.authTokenExpires - 600) {
    refreshToken()
  }
  try {
    logDebug("sendQueryAsync params for callback ${callbackMethod}: ${params}")
    asynchttpGet(callbackMethod, params, data)
  } catch (Exception e) {
    logDebug("Call failed: ${e.message}")
    return null
  }
}

Boolean responseIsValid(AsyncResponse response, String requestName = null) {
  if (response.status != 200) {
    logError("${requestName} request returned HTTP status ${response.status}")
  }
  if (response.hasError()) {
    logError("${requestName} request error: ${response.getErrorMessage()}")
    return false
  } else {
    return true
  }
}


// =============================================================================
// Local Control
// =============================================================================

void localControlCallback(AsyncResponse response, Map data) {
  if (response.status != 200) {
    logError("post request returned HTTP status ${response.status}")
  }
  if (response.hasError()) {
    logError("post request error: ${response.getErrorMessage()}")
  }
}

void getDeviceStateAsync(DeviceWrapper device, String callbackMethod = 'localControlCallback', Map service, String action, Map data = null, Map controlValues = null) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, service, action, controlValues)
  asynchttpPost(callbackMethod, params, data)
}

void componentSetPlayerLevelLocal(DeviceWrapper device, BigDecimal level) {
  String ip = device.getDataValue('deviceIp')
  Map controlValues = [DesiredVolume: level]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetVolume', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentMuteGroupLocal(DeviceWrapper device, Boolean desiredMute) {
  String ip = device.getDataValue('deviceIp')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, GroupRenderingControl, 'SetGroupMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentSetGroupRelativeLevelLocal(DeviceWrapper device, BigDecimal adjustment) {
  String ip = device.getDataValue('deviceIp')
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

void getZoneGroupAttributesAsync(DeviceWrapper device, String callbackMethod = 'getGetGetZoneGroupAttributesAsync', Map data = null) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  asynchttpPost(callbackMethod, params, data)
}

void getGetGetZoneGroupAttributesAsync(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'getGetGetZoneGroupAttributesAsync')) { return }

}

void componentMutePlayerLocal(DeviceWrapper device, Boolean desiredMute) {
  String ip = device.getDataValue('deviceIp')
  Map controlValues = [DesiredMute: desiredMute]
  Map params = getSoapActionParams(ip, RenderingControl, 'SetMute', controlValues)
  asynchttpPost('localControlCallback', params)
}

void componentPlayLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'Play')
  asynchttpPost('localControlCallback', params)
}

void componentPauseLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'Pause')
  asynchttpPost('localControlCallback', params)
}

void componentStopLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'Stop')
  asynchttpPost('localControlCallback', params)
}

void componentNextTrackLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'Next')
  asynchttpPost('localControlCallback', params)
}

void componentPreviousTrackLocal(DeviceWrapper device) {
  getDeviceStateAsync()
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'Previous')
  asynchttpPost('localControlCallback', params)
}

void componentPreviousTrackLocalCallback(AsyncResponse response, Map data) {
  if(!responseIsValid(response, 'componentPreviousTrackLocalCallback')) { return }
  GPathResult xml = new XmlSlurper().parseText(unescapeXML(response.getData()))
  Boolean has = xml['Body']['GetMediaInfoResponse']['NextURI'].text()
  GPathResult currentZoneGroup = zoneGroups.children().children().findAll{it['@UUID'] == rincon}.parent().children()
  groupedDevices.each{ componentSetPlayerLevelLocal(it, data.level) }
}

String getGroupForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentZoneGroupID'].text()
    } else {
      logError(resp.data)
    }
  }
  return groupId
}

String getHouseholdForPlayerDeviceLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  String groupId
  Map params = getSoapActionParams(ip, ZoneGroupTopology, 'GetZoneGroupAttributes')
  httpPost(params) { resp ->
    if (resp && resp.data && resp.success) {
      GPathResult xml = resp.data
      groupId = xml['Body']['GetZoneGroupAttributesResponse']['CurrentMuseHouseholdId'].text()
    } else {
      logError(resp.data)
    }
  }
  return groupId
}

void componentUngroupPlayerLocal(DeviceWrapper device) {
  String ip = device.getDataValue('deviceIp')
  Map params = getSoapActionParams(ip, AVTransport, 'BecomeCoordinatorOfStandaloneGroup')
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