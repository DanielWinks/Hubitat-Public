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
  version: '0.7.12',
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
  checkForUpdates()
  dynamicPage(title: 'Sonos Advanced Controller') {
    section {
      label title: 'Sonos Advanced Controller',
      required: false
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'

      if(state.updateAvailable == true) {
        section('<b style="color: #ff6b6b;">⚠️ Update Available</b>', hideable: false) {
          paragraph "<b>Version ${state.latestVersion} is available!</b><br/>" +
                    "Current version: ${getCurrentVersion()}<br/>" +
                    "Released: ${state.latestReleaseDate}<br/><br/>" +
                    "<a href='${state.latestReleaseUrl}' target='_blank'>View Release Notes</a>"
          input 'btnInstallUpdate', 'button', title: 'Install Update', submitOnChange: false
          input 'btnDismissUpdate', 'button', title: 'Dismiss', submitOnChange: false
        }
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
    section('Optional Features (disable to reduce resource usage):', hideable: true) {
      input 'favMatching', 'bool', title: 'Enable "Current Favorite" status.', required: false, defaultValue: true
      input 'trackDataMetaData', 'bool', title: 'Include metaData and trackMetaData in trackData JSON', required: false, defaultValue: false
    }

    section('Update Settings:', hideable: true) {
      input 'autoCheckUpdates', 'bool', title: 'Automatically check for updates', required: false, defaultValue: true, submitOnChange: true
      if(autoCheckUpdates) {
        input 'updateCheckFrequency', 'enum', title: 'Check frequency', required: false, defaultValue: 'Daily',
              options: ['Daily', 'Weekly', 'Manual']
      }
      input 'autoInstallUpdates', 'bool', title: 'Automatically install updates', required: false, defaultValue: false, submitOnChange: true
      if(autoInstallUpdates) {
        input 'autoInstallTime', 'time', title: 'Install updates at this time', required: true, defaultValue: '02:00'
        input 'autoInstallNextOccurrence', 'bool', title: 'Install at next occurrence of selected time (regardless of day)', required: false, defaultValue: false, submitOnChange: true
        if(!autoInstallNextOccurrence) {
          input 'autoInstallDayOfWeek', 'enum', title: 'Install on this day of week', required: false, defaultValue: 'Sunday',
                options: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
        }
      }
      input 'btnCheckForUpdates', 'button', title: 'Check for Updates Now', submitOnChange: false
      if(state.lastUpdateCheckFormatted) {
        paragraph "Last checked: ${state.lastUpdateCheckFormatted}"
      }

      input 'btnCheckInstalledVersions', 'button', title: 'Check Versions of Installed Files', submitOnChange: false
      if(state.lastVersionCheck) {
        paragraph "Last version check: ${new Date(state.lastVersionCheck).format('yyyy-MM-dd HH:mm:ss')}"
      }

      // Display installed file versions
      if(state.installedVersions && state.installedVersions.size() > 0) {
        paragraph "<b>Installed File Versions:</b>"
        String versionList = "<ul>"
        state.installedVersions.each { file ->
          String statusColor = file.status == 'OK' ? 'green' : (file.status == 'Mismatch' ? 'red' : 'gray')
          String statusIcon = file.status == 'OK' ? '✓' : (file.status == 'Mismatch' ? '✗' : '?')
          versionList += "<li><span style='color:${statusColor}'><b>${statusIcon}</b></span> <b>${file.name}</b>: ${file.installedVersion}"
          if(file.status == 'Mismatch') {
            versionList += " <span style='color:red'>(expected ${file.expectedVersion})</span>"
          }
          versionList += "</li>"
        }
        versionList += "</ul>"
        paragraph versionList
      }

      if(state.versionMismatches && state.versionMismatches.size() > 0) {
        paragraph "<span style='color:red'><b>⚠ ${state.versionMismatches.size()} Version Mismatch(es) Found</b></span>"
        input 'btnFixVersionMismatches', 'button', title: 'Update All Mismatched Versions', submitOnChange: false
      } else if(state.lastVersionCheck && state.versionMismatches != null) {
        paragraph "<span style='color:green'><b>✓ All installed files are at the correct version</b></span>"
      }
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
      if(willBeRemoved.size() > 0 && !settings.skipOrphanRemoval) {
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

  // Update management buttons
  if(buttonName == 'btnCheckForUpdates') { checkForUpdates(true) }
  if(buttonName == 'btnInstallUpdate') { installUpdate() }
  if(buttonName == 'btnCheckInstalledVersions') { checkInstalledVersions() }
  if(buttonName == 'btnFixVersionMismatches') { fixVersionMismatches() }
  if(buttonName == 'btnDismissUpdate') {
    state.updateAvailable = false
    state.latestVersion = null
    state.latestReleaseDate = null
    state.latestReleaseUrl = null
  }
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
  scheduleUpdateCheck()
  checkForUpdates()

  // Initialize settings with defaults
  if(settings.favMatching == null) { settings.favMatching = true }
  if(settings.trackDataMetaData == null) { settings.trackDataMetaData = false }
  if(settings.skipOrphanRemoval == null) { settings.skipOrphanRemoval = false }
  if(settings.logEnable == null) { settings.logEnable = true }
  if(settings.debugLogEnable == null) { settings.debugLogEnable = false }
  if(settings.traceLogEnable == null) { settings.traceLogEnable = false }
  if(settings.descriptionTextEnable == null) { settings.descriptionTextEnable = true }

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
  // Safety check: ensure playerDevices setting exists
  if(!settings.playerDevices) {
    logWarn("No player devices selected, skipping device creation")
    return
  }

  // Safety check: ensure discovery maps are initialized
  if(discoveredSonoses == null) {
    logError("discoveredSonoses is null, cannot create devices")
    return
  }
  if(discoveredSonosSecondaries == null) {
    discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
    logDebug("Initialized empty discoveredSonosSecondaries map")
  }

  settings.playerDevices.each{ dni ->
    if(!dni) {
      logWarn("Encountered null or empty DNI in playerDevices, skipping")
      return
    }

    ChildDeviceWrapper cd = app.getChildDevice(dni)
    Map playerInfo = discoveredSonoses[dni]

    if(cd) {
      String deviceName = cd.getDataValue('name')
      logDebug("Not creating ${deviceName ?: dni}, child already exists.")
    } else {
      if(playerInfo) {
        logInfo("Creating Sonos Advanced Player device for ${playerInfo?.name}")
        try {
          cd = addChildDevice('dwinks', 'Sonos Advanced Player', dni, [name: 'Sonos Advanced Player', label: "Sonos Advanced - ${playerInfo?.name}"])
        } catch (UnknownDeviceTypeException e) {
          logException('Sonos Advanced Player driver not found', e)
        } catch (Exception e) {
          logException("Failed to create device for ${dni}", e)
        }
      } else {
        logWarn("Attempted to create child device for ${dni} but did not find playerInfo")
      }
    }

    // Only update device info if we have both a valid device and player info
    if(cd && playerInfo) {
      try {
        logInfo("Updating player info with latest info from discovery...")
        playerInfo.each { key, value ->
          if(key != null && value != null) {
            cd.updateDataValue(key as String, value as String)
          }
        }

        LinkedHashMap<String,String> macToRincon = discoveredSonoses.collectEntries{ k,v ->
          if(k != null && v != null && v.id != null) {
            return [(k as String): (v.id as String)]
          }
          return [:]
        }
        String rincon = macToRincon[dni]

        if(rincon) {
          LinkedHashMap<String,Map> secondaries = discoveredSonosSecondaries.findAll{k,v ->
            v != null && v.primaryDeviceId != null && v.primaryDeviceId == rincon
          }
          if(secondaries){
            List<String> secondaryDeviceIps = secondaries.collect{it.value?.deviceIp}.findAll{it != null}
            List<String> secondaryIds = secondaries.collect{it.value?.id}.findAll{it != null}
            if(secondaryDeviceIps && secondaryIds) {
              cd.updateDataValue('secondaryDeviceIps', secondaryDeviceIps.join(','))
              cd.updateDataValue('secondaryIds', secondaryIds.join(','))
            }
          }
        }

        cd.secondaryConfiguration()
      } catch (Exception e) {
        logException("Failed to configure device ${dni}", e)
      }
    } else if(!cd) {
      logWarn("Skipping device configuration for ${dni} - device not created")
    } else if(!playerInfo) {
      logWarn("Skipping device configuration for ${dni} - no player info available")
    }
  }
  if(!settings.skipOrphanRemoval) {removeOrphans()}
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
  if(!settings.skipOrphanRemoval) {
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
  String ipAddress = convertHexToIP(event?.networkAddress as String)
  String ipPort = convertHexToInt(event?.deviceAddress as String).toString()

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
// Version Checking for Installed Files
// =============================================================================
void checkInstalledVersions() {
  logInfo('Checking versions of all installed files...')
  state.lastVersionCheck = now()
  state.versionMismatches = []
  state.installedVersions = []

  // Get package manifest to know which drivers to check
  String manifestUrl = 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json'
  Map manifest = null

  try {
    Map params = [
      uri: manifestUrl,
      contentType: 'application/json',
      timeout: 15
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        manifest = resp.data
      }
    }
  } catch(Exception e) {
    logWarn("Failed to retrieve package manifest: ${e.message}")
    return
  }

  if(!manifest) {
    logWarn('Failed to retrieve package manifest for version checking')
    return
  }

  String appVersion = getCurrentVersion()
  logDebug("Current app version: ${appVersion}")

  // Check each driver in the manifest
  manifest.drivers?.each { driver ->
    String driverName = driver.name
    String namespace = driver.namespace ?: 'dwinks'
    String expectedVersion = driver.version ?: appVersion

    logDebug("Checking ${driverName} (${namespace})...")
    String installedVersion = getInstalledDriverVersion(driverName, namespace)

    if(installedVersion && installedVersion != expectedVersion) {
      logWarn("Version mismatch: ${driverName} - installed: ${installedVersion}, expected: ${expectedVersion}")
      state.versionMismatches << [
        name: driverName,
        namespace: namespace,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        location: driver.location
      ]
      state.installedVersions << [
        name: driverName,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        status: 'Mismatch'
      ]
    } else if(installedVersion) {
      logDebug("${driverName} version OK: ${installedVersion}")
      state.installedVersions << [
        name: driverName,
        installedVersion: installedVersion,
        expectedVersion: expectedVersion,
        status: 'OK'
      ]
    } else {
      logDebug("${driverName} not found or could not determine version")
      state.installedVersions << [
        name: driverName,
        installedVersion: 'Not Installed',
        expectedVersion: expectedVersion,
        status: 'Not Found'
      ]
    }
  }

  if(state.versionMismatches.size() > 0) {
    logInfo("Found ${state.versionMismatches.size()} version mismatch(es)")
  } else {
    logInfo('All installed files are up to date')
  }
}

String getInstalledDriverVersion(String driverName, String namespace) {
  String foundVersion = null

  try {
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      logWarn('Failed to authenticate with hub')
      return null
    }

    // Get list of installed drivers - HPM uses /device/drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        logDebug("Driver list response received, checking for ${driverName} in namespace ${namespace}")

        // Log all user drivers for debugging
        def userDrivers = resp.data?.drivers?.findAll { it.type == 'usr' }
        logDebug("Found ${userDrivers?.size()} user drivers")
        userDrivers?.each { d ->
          logDebug("  - ${d.name} (${d.namespace})")
        }

        // Find driver by name and namespace
        def driver = resp.data?.drivers?.find {
          it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
        }

        if(driver && driver.id) {
          Integer driverId = driver.id
          logDebug("Found driver ${driverName}, getting source code...")

          // Get driver source code using /driver/ajax/code with query parameter
          Map codeParams = [
            uri: "http://127.0.0.1:8080",
            path: '/driver/ajax/code',
            headers: [Cookie: cookie],
            query: [id: driverId]
          ]

          httpGet(codeParams) { codeResp ->
            if(codeResp?.status == 200 && codeResp.data?.source) {
              String source = codeResp.data.source
              // Extract semantic version from source code for comparison
              def matcher = (source =~ /version:\s*['"]([^'"]+)['"]/)
              if(matcher.find()) {
                foundVersion = matcher.group(1)
                logDebug("Extracted version ${foundVersion} from ${driverName}")
              } else {
                logWarn("Could not find version pattern in source for ${driverName}")
              }
            } else {
              logWarn("Failed to get source code for ${driverName}, status: ${codeResp?.status}")
            }
          }
        } else if(!driver) {
          logDebug("Driver not found in hub: ${driverName} (${namespace})")
        }
      } else {
        logWarn("Failed to get driver list, status: ${resp?.status}")
      }
    }
  } catch(Exception e) {
    logWarn("Error getting version for ${driverName}: ${e.message}")
  }

  return foundVersion
}

String getDriverVersionForUpdate(String driverName, String namespace) {
  String hubVersion = null

  try {
    String cookie = login()
    if(!cookie) { return null }

    // Get list of installed drivers
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [Cookie: cookie]
    ]

    httpGet(params) { resp ->
      def driver = resp.data?.drivers?.find {
        it.type == 'usr' && it?.name == driverName && it?.namespace == namespace
      }

      if(driver?.id) {
        // Get hub's internal version using /driver/ajax/code
        Map codeParams = [
          uri: "http://127.0.0.1:8080",
          path: '/driver/ajax/code',
          headers: [Cookie: cookie],
          query: [id: driver.id]
        ]

        httpGet(codeParams) { codeResp ->
          if(codeResp?.status == 200 && codeResp.data?.version) {
            hubVersion = codeResp.data.version.toString()
            logDebug("Got hub version ${hubVersion} for ${driverName}")
          }
        }
      }
    }
  } catch(Exception e) {
    logWarn("Error getting hub version for ${driverName}: ${e.message}")
  }

  return hubVersion
}

void fixVersionMismatches() {
  if(!state.versionMismatches || state.versionMismatches.size() == 0) {
    logInfo('No version mismatches to fix')
    return
  }

  logInfo("Fixing ${state.versionMismatches.size()} version mismatch(es)...")

  state.versionMismatches.each { mismatch ->
    logInfo("Updating ${mismatch.name} from ${mismatch.installedVersion} to ${mismatch.expectedVersion}...")

    // Download the correct version - location already contains the full URL
    String sourceCode = downloadFile(mismatch.location)

    if(sourceCode) {
      // Update the driver - need to pass version parameter
      Boolean success = updateDriver(mismatch.name, mismatch.namespace, sourceCode, mismatch.expectedVersion)
      if(success) {
        logInfo("Successfully updated ${mismatch.name}")
      } else {
        logWarn("Failed to update ${mismatch.name}")
      }
    } else {
      logWarn("Failed to download source code for ${mismatch.name}")
    }
  }

  // Re-check versions after updates
  pauseExecution(2000)
  checkInstalledVersions()
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
// Update Management
// =============================================================================

String getCurrentVersion() {
  return app.version ?: '0.7.10'
}

void checkForUpdates(Boolean manual = false) {
  // Check if auto-check is enabled or if this is a manual check
  if(!manual && autoCheckUpdates != true) { return }

  // Check frequency limits for automatic checks
  if(!manual && state.lastUpdateCheck) {
    Long lastCheck = state.lastUpdateCheck as Long
    Long now = now()
    Long dayInMs = 86400000L
    Long weekInMs = dayInMs * 7

    if(updateCheckFrequency == 'Daily' && (now - lastCheck) < dayInMs) { return }
    if(updateCheckFrequency == 'Weekly' && (now - lastCheck) < weekInMs) { return }
  }

  try {
    String manifestUrl = 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json'

    Map params = [
      uri: manifestUrl,
      contentType: 'application/json',
      timeout: 15
    ]

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        Map manifest = resp.data
        String latestVersion = manifest.version ?: null

        if(latestVersion) {
          String currentVersion = getCurrentVersion()
          Integer comparison = compareVersions(currentVersion, latestVersion)

          state.lastUpdateCheck = now()
          state.lastUpdateCheckFormatted = new Date().format('yyyy-MM-dd HH:mm:ss')

          if(comparison < 0) {
            // Update available
            state.updateAvailable = true
            state.latestVersion = latestVersion
            state.latestManifest = manifest
            state.latestReleaseDate = manifest.releaseDate ?: 'Unknown'
            state.latestReleaseUrl = "https://github.com/DanielWinks/Hubitat-Public/releases/tag/v${latestVersion}"

            // Update app label to show update available on main apps screen
            app.updateLabel("Sonos Advanced Controller <span style='color:green'>Update Available</span>")

            logInfo("Update available: ${latestVersion} (current: ${currentVersion})")

            if(manual) {
              // Force page refresh to show update notification
              runIn(1, 'refreshPage')
            }

            // Auto-install if enabled
            if(autoInstallUpdates == true && !manual) {
              scheduleAutoInstall()
            }
          } else {
            state.updateAvailable = false
            state.latestVersion = null
            state.latestManifest = null
            state.latestReleaseDate = null
            state.latestReleaseUrl = null

            // Clear update label
            app.updateLabel("Sonos Advanced Controller")

            if(manual) {
              logInfo("No updates available. Current version ${currentVersion} is up to date.")
            }
          }
        }
      }
    }
  } catch(Exception e) {
    logError("Error checking for updates: ${e.message}")
    state.lastUpdateCheck = now()
    state.lastUpdateCheckFormatted = new Date().format('yyyy-MM-dd HH:mm:ss')
  }
}

void refreshPage() {
  // Dummy method to trigger page refresh
}

Integer compareVersions(String version1, String version2) {
  // Remove 'v' prefix if present
  version1 = version1?.replaceAll(/^v/, '') ?: '0.0.0'
  version2 = version2?.replaceAll(/^v/, '') ?: '0.0.0'

  List parts1 = version1.tokenize('.')
  List parts2 = version2.tokenize('.')

  Integer maxLength = Math.max(parts1.size(), parts2.size())

  for(Integer i = 0; i < maxLength; i++) {
    Integer num1 = i < parts1.size() ? (parts1[i] as Integer) : 0
    Integer num2 = i < parts2.size() ? (parts2[i] as Integer) : 0

    if(num1 < num2) { return -1 }
    if(num1 > num2) { return 1 }
  }

  return 0
}

void installUpdate() {
  if(!state.updateAvailable || !state.latestManifest) {
    logError('No update available to install')
    return
  }

  try {
    Map manifest = state.latestManifest
    String version = state.latestVersion

    logInfo("Starting update to version ${version}...")

    // Track what needs updating
    Map updateStatus = [
      app: false,
      drivers: [:],
      errors: []
    ]

    // Update the main app
    if(manifest.apps && manifest.apps.size() > 0) {
      Map appInfo = manifest.apps[0]
      String appLocation = appInfo.location

      logDebug("Downloading app from: ${appLocation}")
      String appCode = downloadFile(appLocation)

      if(appCode) {
        Boolean success = updateThisApp(appCode, version)
        updateStatus.app = success

        if(success) {
          logInfo("App updated successfully to version ${version}")
        } else {
          updateStatus.errors << "Failed to update app"
          logError("Failed to update app")
        }
      } else {
        updateStatus.errors << "Failed to download app code"
        logError("Failed to download app code")
      }
    }

    // Update drivers
    if(manifest.drivers) {
      manifest.drivers.each { driverInfo ->
        String driverName = driverInfo.name
        String driverLocation = driverInfo.location
        String driverNamespace = driverInfo.namespace ?: 'dwinks'

        logDebug("Downloading driver ${driverName} from: ${driverLocation}")
        String driverCode = downloadFile(driverLocation)

        if(driverCode) {
          Boolean success = updateDriver(driverName, driverNamespace, driverCode, version)
          updateStatus.drivers[driverName] = success

          if(success) {
            logInfo("Driver ${driverName} updated successfully")
          } else {
            updateStatus.errors << "Failed to update driver: ${driverName}"
            logError("Failed to update driver: ${driverName}")
          }
        } else {
          updateStatus.errors << "Failed to download driver: ${driverName}"
          logError("Failed to download driver: ${driverName}")
        }
      }
    }

    // Clear update notification if successful
    if(updateStatus.errors.size() == 0) {
      state.updateAvailable = false
      state.latestVersion = null
      state.latestManifest = null
      state.latestReleaseDate = null
      state.latestReleaseUrl = null

      // Clear update label
      app.updateLabel("Sonos Advanced Controller")

      // Clear any scheduled auto-install
      unschedule('performScheduledInstall')

      logInfo("Update completed successfully! Please refresh the page.")
    } else {
      logError("Update completed with errors: ${updateStatus.errors.join(', ')}")
    }

  } catch(Exception e) {
    logError("Error installing update: ${e.message}")
  }
}

String downloadFile(String uri) {
  try {
    Map params = [
      uri: uri,
      contentType: 'text/plain',
      timeout: 30
    ]

    String fileContent = null

    httpGet(params) { resp ->
      if(resp?.status == 200) {
        fileContent = resp.data.text
      }
    }

    return fileContent
  } catch(Exception e) {
    logError("Error downloading file from ${uri}: ${e.message}")
    return null
  }
}

Boolean updateThisApp(String sourceCode, String version) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/update',
      headers: [
        'Cookie': cookie
      ],
      body: [
        id: app.id,
        version: version,
        source: sourceCode
      ],
      timeout: 30
    ]

    httpPost(params) { resp ->
      if(resp?.status == 200) {
        logDebug("App update API call successful")
        return true
      } else {
        logError("App update API returned status: ${resp?.status}")
        return false
      }
    }
  } catch(Exception e) {
    logError("Error updating app: ${e.message}")
    return false
  }
}

Boolean updateDriver(String driverName, String namespace, String sourceCode, String newVersionForLogging) {
  try {
    // Find the driver by name and namespace
    List allDrivers = getDriverList()
    Map targetDriver = allDrivers.find { driver ->
      driver.name == driverName && driver.namespace == namespace
    }

    if(!targetDriver) {
      logError("Driver not found: ${namespace}.${driverName}")
      return false
    }

    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    // Get the hub's internal version counter - this is what the update API expects
    String currentHubVersion = getDriverVersionForUpdate(driverName, namespace) ?: ''
    logDebug("Updating driver ${driverName}: hub version=${currentHubVersion}, new version=${newVersionForLogging}")

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/driver/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Cookie': cookie
      ],
      body: [
        id: targetDriver.id,
        version: currentHubVersion,  // Must be hub's internal version, not semantic version
        source: sourceCode
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(params) { resp ->
      if(resp.data?.status == 'success') {
        logDebug("Successfully updated driver ${driverName}")
        result = true
      } else {
        logError("Driver ${driverName} update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating driver ${driverName}: ${e.message}")
    return false
  }
}

List getDriverList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/driver/list/data',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15
    ]

    List drivers = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        drivers = resp.data
      }
    }

    return drivers
  } catch(Exception e) {
    logError("Error getting driver list: ${e.message}")
    return []
  }
}

String login() {
  try {
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/login',
      body: [
        username: '',
        password: '',
        submit: 'Login'
      ],
      textParser: true,
      timeout: 15
    ]

    String cookie = null

    httpPost(params) { resp ->
      if(resp?.status == 200 || resp?.status == 302) {
        def setCookieHeader = resp.headers['Set-Cookie']
        if(setCookieHeader) {
          String cookieValue = setCookieHeader.value ?: setCookieHeader.toString()
          cookie = cookieValue.split(';')[0]
        }
      }
    }

    return cookie
  } catch(Exception e) {
    logError("Login error: ${e.message}")
    return null
  }
}

void scheduleUpdateCheck() {
  unschedule('checkForUpdates')

  if(autoCheckUpdates == true) {
    if(updateCheckFrequency == 'Daily') {
      schedule('0 0 2 * * ?', 'checkForUpdates')  // 2 AM daily
    } else if(updateCheckFrequency == 'Weekly') {
      schedule('0 0 2 ? * MON', 'checkForUpdates')  // 2 AM Monday
    }
  }
}

void scheduleAutoInstall() {
  if(!autoInstallUpdates || !autoInstallTime || !state.updateAvailable) {
    unschedule('performScheduledInstall')
    return
  }

  try {
    Date installTime = timeToday(autoInstallTime, location.timeZone)
    String cronExpression

    if(autoInstallNextOccurrence == true) {
      // Install at next occurrence of time, regardless of day
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} * * ?"

      Date now = new Date()
      if(installTime.before(now)) {
        logDebug("Auto-install time has passed today, will install tomorrow at ${autoInstallTime}")
      } else {
        logDebug("Auto-install scheduled for today at ${autoInstallTime}")
      }
    } else {
      // Install on specific day of week
      String dayOfWeek = autoInstallDayOfWeek ?: 'Sunday'
      Map dayMap = [
        'Sunday': 'SUN',
        'Monday': 'MON',
        'Tuesday': 'TUE',
        'Wednesday': 'WED',
        'Thursday': 'THU',
        'Friday': 'FRI',
        'Saturday': 'SAT'
      ]
      String cronDay = dayMap[dayOfWeek]
      cronExpression = "0 ${installTime.minutes} ${installTime.hours} ? * ${cronDay}"

      logDebug("Auto-install scheduled for ${dayOfWeek}s at ${autoInstallTime}")
    }

    schedule(cronExpression, 'performScheduledInstall')
  } catch(Exception e) {
    logError("Error scheduling auto-install: ${e.message}")
  }
}

void performScheduledInstall() {
  if(!state.updateAvailable) {
    logInfo("Scheduled install called but no update available")
    return
  }

  logInfo("Performing scheduled automatic update installation...")
  installUpdate()
}
