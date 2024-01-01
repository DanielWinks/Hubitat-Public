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
  page(name: 'groupPage')
}

@Field static final String apiPrefix = 'https://api.ws.sonos.com/control/api/v1'
@Field static final String authApiPrefix = 'https://api.sonos.com/login/v3/oauth'
@Field static final String xWwwFormUrlencodedUtf8 = 'application/x-www-form-urlencoded;charset=utf-8'

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
  boolean configured = settings.clientKey != null && settings.clientSecret != null
  boolean authenticated = state.authToken != null
  // refreshPlayersAndGroups()
  dynamicPage(title: 'Sonos Cloud Controller') {
    tryCreateAccessToken()
    if(!state.accessToken) {
      section ("<h2 style='color:red;'>OAuth is not enabled for app!! Please enable in Apps Code.</h2>"){      }
    }
    section {
      label title: 'Sonos Cloud Controller',
      required: false
      paragraph 'This application provides an interface to Sonos Cloud based functions including announcements and grouping.'
      href (
        page: 'authorizePage',
        title: 'Sonos Account Authorization',
        description: configured && authenticated ? 'Your Sonos account is connected' : 'Select to setup credentials and authenticate to Sonos'
      )

      if (configured && authenticated) {
        href (
          page: 'playerPage',
          title: 'Sonos Virtual Player Devices',
          description: 'Select to create Sonos player devices'
        )
        href (
          page: 'groupPage',
          title: 'Sonos Virtual Group Devices',
          description: 'Select to create/delete Sonos group devices'
        )
      }
    }

    section {
      input 'logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true
      input 'debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false
      input 'descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
      // input 'testButton', 'button', title: 'Test'
    }
  }
}

// =============================================================================
// Player and Group Pages
// =============================================================================

@Field static Map playerSelectionOptions = new java.util.concurrent.ConcurrentHashMap()
Map playerPage() {
  app.removeSetting('playerDevices')
  refreshPlayersAndGroups()
  logDebug(getObjectClassName(getCurrentPlayerDevices()))
  app.updateSetting('playerDevices', [type: 'enum', value: getCurrentPlayerDevices()])
  dynamicPage(name: 'playerPage', title: 'Sonos Player Virtual Devices', nextPage: 'mainPage') {
    section {
      paragraph ('Select Sonos players that you want to create Hubitat devices for control.<br>To remove a player later simply remove the created device.')
      paragraph ('If no players are listed, or players are missing, wait a few seconds and refresh the page.')
      paragraph ("Select virtual players to create (${playerSelectionOptions.size()} players found)")
      input (name: 'playerDevices', title: '', type: 'enum', options: playerSelectionOptions, multiple: true)
      }
  }
}

List<String> getCurrentPlayerDevices() {
  List<ChildDeviceWrapper> childDevices = app.getChildDevices()
  List<String> pds = []
  childDevices.each() {cd -> pds.add("${cd.getDataValue('id')}")}
  return pds
}

Map groupPage() {
  refreshPlayersAndGroups()
  if(!state.userGroups) { state.userGroups = [:] }
  Map coordinatorSelectionOptions = state.players.collectEntries { id, player -> [(id): player.name] }
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

void appButtonHandler(String buttonName) {
  if(buttonName == 'testButton') { testButton() }
  if(buttonName == 'saveGroup') { saveGroup() }
  if(buttonName == 'deleteGroup') { deleteGroup() }
  if(buttonName == 'cancelGroupEdit') { cancelGroupEdit() }
}

// =============================================================================
// TESTING
// =============================================================================

void testButton() {
  createPlayerDevices()
}

// =============================================================================
// TESTING
// =============================================================================

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
  try { refreshToken()}
  catch (Exception e) { logError("Could not refresh token: ${e}") }
  schedule('0 0 0,6,12,18 * * ?', 'refreshToken')
  try { refreshPlayersAndGroups() }
  catch (Exception e) { logError("refreshPlayersAndGroups() Failed: ${e}")}
  try { createPlayerDevices() }
  catch (Exception e) { logError("createPlayerDevices() Failed: ${e}")}
  try { createGroupDevices() }
  catch (Exception e) { logError("createGroupDevices() Failed: ${e}")}
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
        device = addChildDevice('dwinks', 'Sonos Cloud Group', dni, [name: 'Sonos Group', label: "Sonos Group: ${it.key}"])
      } catch (UnknownDeviceTypeException e) {
        logException('createGroupDevices', e)
      }
    }
    String coordinatorId = it.value.coordinatorId as String
    String playerIds =  it.value.playerIds.join(',')
    String householdId = state.players["${it.value.coordinatorId}"].householdId as String
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
    DeviceWrapper oldDevice = getChildDevice(oldDni)
    if (device == null && oldDevice == null) {
      try {
        logInfo "Creating child audio notification device for ${player.name}"
        device = addChildDevice('dwinks', 'Sonos Cloud Player', dni, [name: 'Sonos Cloud Player', label: "Sonos Cloud - ${player.name}"])
      } catch (UnknownDeviceTypeException e) {
        logException 'Sonos Cloud Player driver not found (needs installing?)', e
      }
    }
    if(oldDevice) {
      //Migrate any devices using old DNI scheme
      oldDevice.setDeviceNetworkId(dni)
      logDebug("Changing device DNI from ${oldDni} to ${dni} to enable local control events.")
      device = oldDevice
    }

    player.each { key, value -> if(key != 'zoneInfo') { device.updateDataValue(key, value as String) }}
    // player.each { key, value -> logDebug("K:V ${key}:${value}")}
    String ip = ((player.websocketUrl.replace('wss://','')).tokenize(':')[0])+':1400'
    player.each { device.updateDataValue('deviceIp', ip)}
    getPlayerDeviceDescription(ip)
  }
  removeOrphans()
}

void getPlayerDeviceDescription(String ipAddress) {
  Map params = [
    uri: "http://${ipAddress}/xml/device_description.xml"
  ]
  asynchttpGet('getPlayerDeviceDescriptionCallback', params)
}

void getPlayerDeviceDescriptionCallback(AsyncResponse response, Map data = null) {
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

// String getGroupForPlayer(String playerId) {
//   logDebug "Getting groupId for player: ${playerId}..."
//   Map groups = state.groups
//   groups = groups.findAll { id, group -> group.playerIds }
//   Map options = groups.collectEntries { groupId, group -> [(groupId): group.playerIds]}
//   String gId = options.collectEntries { k, v -> [k, v.findAll { it.contains(playerId) }] }.findAll { k, v -> v.size() > 0 }.keySet()[0]
//   return gId
// }

String getGroupForPlayer(String playerId) {
  logDebug "Getting groupId for player: ${playerId}..."
  Map groups = state.groups
  groups = groups.findAll { id, group -> group.playerIds }
  Map options = groups.collectEntries { groupId, group -> [(groupId): group.playerIds]}
  String gId = options.collectEntries { k, v -> [k, v.findAll { it.contains(playerId) }] }.findAll { k, v -> v.size() > 0 }.keySet()[0]
  return gId
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
  String groupId = device.currentState('groupId').value
  Map data = [muted:muted]
  postJsonAsync("${apiPrefix}/groups/${groupId}/groupVolume/mute", data)
}

void componentSetGroupLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting volue to ${level}...")
  String groupId = device.currentState('groupId').value
  Map data = [volume:level as Integer]
  postJsonAsync("${apiPrefix}/groups/${groupId}/groupVolume", data)
}

void componentSetGroupRelativeLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting relative group volume to ${level}...")
  String groupId = device.currentState('groupId').value
  Map data = [volumeDelta:level as Integer]
  postJsonAsync("${apiPrefix}/groups/${groupId}/groupVolume/relative", data)
}

void componentSetPlayerLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting volue to ${level}...")
  String playerId = device.getDataValue('id')
  Map data = [volume:level as Integer]
  postJsonAsync("${apiPrefix}/players/${playerId}/playerVolume", data)
}

void componentSetPlayerRelativeLevel(DeviceWrapper device, BigDecimal level) {
  logDebug("Setting player relative volume to ${level}...")
  String playerId = device.getDataValue('id')
  Map data = [volumeDelta:level as Integer]
  postJsonAsync("${apiPrefix}/players/${playerId}/playerVolume/relative", data)
}

void componentPlay(DeviceWrapper device) {
  logDebug('componentPlay()')
  String playerId = device.getDataValue('id')
  String groupId = device.currentState('groupId').value
  postJsonAsync("${apiPrefix}/groups/${groupId}/playback/play")
}

void componentStop(DeviceWrapper device) {
  logDebug('componentStop()')
  String playerId = device.getDataValue('id')
  String groupId = device.currentState('groupId').value
  postJsonAsync("${apiPrefix}/groups/${groupId}/playback/pause")
}

void componentPreviousTrack(DeviceWrapper device) {
  logDebug('componentPreviousTrack()')
  String playerId = device.getDataValue('id')
  String groupId = device.currentState('groupId').value
  postJsonAsync("${apiPrefix}/groups/${groupId}/playback/skipToPreviousTrack")
}

void componentNextTrack(DeviceWrapper device) {
  logDebug('componentNextTrack()')
  String playerId = device.getDataValue('id')
  String groupId = device.currentState('groupId').value
  postJsonAsync("${apiPrefix}/groups/${groupId}/playback/skipToNextTrack")
}

void componentGetFavorites(DeviceWrapper device) {
  logDebug 'Getting favorites...'
  String householdId = device.getDataValue('householdId')
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
  String groupId = device.currentState('groupId').value
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
  String groupId = device.currentState('groupId').value
  postJsonAsync("${apiPrefix}/groups/${group}/playback/playMode", playModes)
}

void componentUngroupPlayer(DeviceWrapper device) {
  logDebug('Removing player from group...')
  String playerId = device.getDataValue('id')
  getPlayersAndGroups([ungroupPlayer: true, playerId: playerId])
}

void ungroupPlayer(String playerId) {
  logDebug("Ungroup Player: ${playerId}")
  Map currentGroup = (state.groups.find{ it -> it.value.playerIds.contains(playerId)}).value
  String coordinatorIdGroup = currentGroup.id.replace(':','%3A')
  List playerIds = currentGroup.playerIds - playerId
  Map data = ['playerIds': playerIds]
  postJsonAsync("${apiPrefix}/groups/${coordinatorIdGroup}/groups/setGroupMembers", data)
}

void componentUngroupPlayers(DeviceWrapper device) {
  logDebug('Removing players from group...')
  String householdId = device.getDataValue('householdId')
  List coordinatorId = [device.getDataValue('coordinatorId')]
  List playerIds = device.getDataValue('playerIds').split(',')
  List allPlayersIds = coordinatorId + playerIds
  allPlayersIds.each(){ playerId ->
    Map data = ['playerIds': [playerId]]
    postJsonAsync("${apiPrefix}/households/${householdId}/groups/createGroup", data)
  }
}

void componentGroupPlayers(DeviceWrapper device) {
  logDebug('Adding players to group...')
  String householdId = device.getDataValue('householdId')
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

void sendQueryAsync(Map params, String callback, Map data = null) {
  if (now() >= state.authTokenExpires - 600) {
    refreshToken()
  }
  try {
    asynchttpGet(callback, params, data)
  } catch (Exception e) {
    logDebug("Call failed: ${e.message}")
    return null
  }
}


// =============================================================================
// Local Control
// =============================================================================

