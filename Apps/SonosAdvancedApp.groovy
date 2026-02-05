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
  name: 'Sonos Advanced Controller',
  version: '0.7.21',
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
      paragraph 'This application provides Advanced Sonos Player control, including announcements and grouping.'

      if(state.updateAvailable == true) {
        section('<b style="color: #ff6b6b;">⚠️ Update Available</b>', hideable: false) {
          paragraph "<b>Version ${state.latestVersion} is available!</b><br/>" +
                    "Current version: ${getActualInstalledAppVersion()}<br/>" +
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

      // Show message if update was just completed
      if(state.updateJustCompleted) {
        paragraph "<span style='color:green'><b>✓ Update completed successfully!</b></span><br><i>Click 'Check Versions of Installed Files' to verify all files are at the correct version.</i>"
        state.remove('updateJustCompleted')
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

      // Duplicate cleanup section
      paragraph "<hr><b>Maintenance</b>"
      input 'btnCleanupDuplicates', 'button', title: 'Check and Remove Duplicate Apps/Drivers', submitOnChange: false
      if(state.duplicateCleanupResult) {
        paragraph "<i>Last cleanup result: ${state.duplicateCleanupResult}</i>"
      }

      // Library management section
      paragraph "<hr><b>Library Management</b>"
      paragraph "<i>Enter the library IDs from your hub's Library Code section to enable library publishing. Find these by going to Apps Code → Libraries in the Hubitat interface.</i>"
      input 'libraryIdSMAPI', 'number', title: 'SMAPILibrary ID', required: false, submitOnChange: true
      input 'libraryIdUtilities', 'number', title: 'UtilitiesAndLoggingLibrary ID', required: false, submitOnChange: true

      if(settings.libraryIdSMAPI && settings.libraryIdUtilities) {
        input 'btnPublishLibraries', 'button', title: 'Publish Libraries to Hub', submitOnChange: false
        if(state.lastLibraryPublish) {
          paragraph "<i>Last publish: ${state.lastLibraryPublish}</i>"
        }
      } else {
        paragraph "<i>⚠ Enter both library IDs above to enable library publishing.</i>"
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
    section("Please wait while we discover your Sonos devices. Using both SSDP and mDNS discovery methods. Click Next when all your devices have been discovered.") {
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

  // Unregister mDNS listeners as discovery is complete
  try {
    unregisterMDNSListener('_sonos._tcp')
    unregisterMDNSListener('_http._tcp')
    logDebug("Unregistered mDNS listeners")
  } catch(Exception e) {
    logTrace("Could not unregister mDNS listeners: ${e.message}")
  }

  LinkedHashMap newlyDiscovered = discoveredSonoses.collectEntries{id, player -> [(id.toString()): player.name]}
  LinkedHashMap previouslyCreated = getCurrentPlayerDevices().collectEntries{[(it.getDeviceNetworkId().toString()): it.getDataValue('name')]}

  // Additional duplicate check: ensure no device appears twice with different MACs
  // This can happen if a device changes IP or MAC spoofing occurs
  Set<String> seenPlayerIds = [] as Set
  LinkedHashMap deduplicatedDiscovered = [:]
  newlyDiscovered.each { mac, name ->
    Map deviceInfo = discoveredSonoses[mac]
    String playerId = deviceInfo?.id
    if(playerId && !seenPlayerIds.contains(playerId)) {
      deduplicatedDiscovered[mac] = name
      seenPlayerIds.add(playerId)
    } else if(playerId) {
      logWarn("Duplicate device detected: ${name} (${mac}) has same playerId as another device. Skipping.")
    } else {
      deduplicatedDiscovered[mac] = name
    }
  }

  LinkedHashMap selectionOptions = previouslyCreated
  Integer newlyFoundCount = 0
  deduplicatedDiscovered.each{ k,v ->
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
  if(buttonName == 'btnCleanupDuplicates') { cleanupDuplicates() }
  if(buttonName == 'btnPublishLibraries') { publishLibraries() }
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

        // First, update all basic player info
        playerInfo.each { key, value ->
          if(key != null && value != null) {
            cd.updateDataValue(key as String, value as String)
          }
        }

        // Then handle secondaries
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

        // Validate critical device data fields are populated
        List<String> criticalFields = ['id', 'deviceIp', 'localUpnpHost', 'localUpnpUrl', 'websocketUrl']
        List<String> missingFields = []
        criticalFields.each { field ->
          String value = cd.getDataValue(field)
          if(!value || value == 'null') {
            missingFields << field
          }
        }

        if(missingFields.size() > 0) {
          logWarn("Device ${cd.label} is missing critical data fields: ${missingFields.join(', ')}. This may cause issues.")
          logWarn("Player info available: ${playerInfo.keySet().join(', ')}")
          // Schedule a retry of secondaryConfiguration in case data gets populated later
          runIn(10, 'retryDeviceConfiguration', [data: [dni: dni]])
        }

        // Small delay to ensure all data values are committed before secondaryConfiguration
        pauseExecution(500)
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

void retryDeviceConfiguration(Map data) {
  String dni = data?.dni
  if(!dni) {
    logWarn('retryDeviceConfiguration called without DNI')
    return
  }

  ChildDeviceWrapper cd = app.getChildDevice(dni)
  if(!cd) {
    logWarn("Cannot retry configuration for ${dni} - device not found")
    return
  }

  Map playerInfo = discoveredSonoses[dni]
  if(!playerInfo) {
    logWarn("Cannot retry configuration for ${dni} - no player info available")
    return
  }

  logInfo("Retrying device configuration for ${cd.label}...")

  // Re-apply all device data
  playerInfo.each { key, value ->
    if(key != null && value != null) {
      String currentValue = cd.getDataValue(key as String)
      if(!currentValue || currentValue == 'null') {
        logDebug("Setting missing field ${key} = ${value}")
        cd.updateDataValue(key as String, value as String)
      }
    }
  }

  // Validate again
  List<String> criticalFields = ['id', 'deviceIp', 'localUpnpHost', 'localUpnpUrl', 'websocketUrl']
  List<String> stillMissing = []
  criticalFields.each { field ->
    String value = cd.getDataValue(field)
    if(!value || value == 'null') {
      stillMissing << field
    }
  }

  if(stillMissing.size() > 0) {
    logError("Device ${cd.label} still missing critical fields after retry: ${stillMissing.join(', ')}")
    logError("This device may not function correctly. Try deleting and re-discovering it.")
  } else {
    logInfo("Device ${cd.label} configuration completed successfully on retry")
    pauseExecution(500)
    cd.secondaryConfiguration()
  }
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
//
// This section implements dual discovery methods for Sonos devices:
// 1. SSDP (Simple Service Discovery Protocol) - Legacy method, works on most networks
// 2. mDNS (Multicast DNS) - Modern method added in Hubitat 2.4.1+, more reliable
//
// Duplicate Prevention:
// - Devices are keyed by MAC address (primary identifier)
// - Additional checks prevent duplicates by playerId and IP address
// - Selection page deduplicates by playerId to handle edge cases
// =============================================================================
void ssdpDiscover() {
  logDebug("Starting SSDP Discovery...")
  discoveredSonoses = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
  discoveredSonosSecondaries = new java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap>()
  discoveryQueue = new ConcurrentLinkedQueue<LinkedHashMap>()
	sendHubCommand(new hubitat.device.HubAction("lan discovery upnp:rootdevice", hubitat.device.Protocol.LAN))
	sendHubCommand(new hubitat.device.HubAction("lan discovery ssdp:all", hubitat.device.Protocol.LAN))

  // Also start mDNS discovery
  startMdnsDiscovery()
}

void startMdnsDiscovery() {
  try {
    // Register mDNS listener for Sonos devices
    // Sonos uses _sonos._tcp for its mDNS service
    registerMDNSListener('_sonos._tcp')
    logDebug("Registered mDNS listener for Sonos devices (_sonos._tcp)")

    // Also listen for general device services that Sonos might broadcast
    registerMDNSListener('_http._tcp')
    logDebug("Registered mDNS listener for HTTP services (_http._tcp)")

    // Schedule processing of mDNS entries after a delay to allow discovery
    runIn(5, 'processMdnsDiscovery')
  } catch(Exception e) {
    logWarn("mDNS discovery not available or failed: ${e.message}")
  }
}

void processMdnsDiscovery() {
  try {
    logDebug("Processing mDNS discovered devices...")

    // Get all mDNS entries
    def mdnsEntries = getMDNSEntries()
    if(!mdnsEntries) {
      logDebug("No mDNS entries found")
      return
    }

    logDebug("Found ${mdnsEntries.size()} mDNS entries")

    mdnsEntries.each { entry ->
      // Filter for Sonos devices - they typically have 'Sonos' in the name or specific service types
      String serviceName = entry.name ?: ''
      String serviceType = entry.type ?: ''
      String ipAddress = entry.ipAddress ?: ''
      Integer port = entry.port ?: 0

      logTrace("mDNS entry: name=${serviceName}, type=${serviceType}, ip=${ipAddress}, port=${port}")

      // Check if this looks like a Sonos device
      if(serviceName.toLowerCase().contains('sonos') || serviceType == '_sonos._tcp') {
        logDebug("Found potential Sonos device via mDNS: ${serviceName} at ${ipAddress}")
        processDiscoveredSonosDevice(ipAddress)
      } else if(ipAddress && port == 1400) {
        // Port 1400 is Sonos UPnP port - worth checking
        logTrace("Found device on Sonos UPnP port 1400: ${ipAddress}")
        processDiscoveredSonosDevice(ipAddress)
      }
    }

    sendFoundSonosEvents()
  } catch(Exception e) {
    logWarn("Error processing mDNS discovery: ${e.message}")
  }
}

void processDiscoveredSonosDevice(String ipAddress) {
  if(!ipAddress) { return }

  // Check if we already discovered this IP
  boolean alreadyDiscovered = discoveredSonoses.values().any { it.deviceIp == ipAddress }
  if(alreadyDiscovered) {
    logTrace("Device at ${ipAddress} already discovered, skipping")
    return
  }

  // Try to get player info
  LinkedHashMap playerInfo = getPlayerInfoLocalSync("${ipAddress}:1443")
  if(!playerInfo) {
    logTrace("Could not get player info for ${ipAddress}")
    return
  }

  GPathResult deviceDescription = getDeviceDescriptionLocalSync("${ipAddress}")
  if(!deviceDescription) {
    logTrace("Could not get device description for ${ipAddress}")
    return
  }

  // Process using the same logic as SSDP
  processSonosDeviceInfo(playerInfo, deviceDescription, ipAddress)
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

  processSonosDeviceInfo(playerInfo, deviceDescription, ipAddress)
}

@CompileStatic
void processSonosDeviceInfo(LinkedHashMap playerInfo, GPathResult deviceDescription, String ipAddress) {
  LinkedHashMap playerInfoDevice = playerInfo?.device as LinkedHashMap

  String modelName = deviceDescription['device']['modelName']
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
    logTrace("Processing Sonos device: MAC=${mac}, name=${playerInfoDevice?.name}, IP=${ipAddress}")
  }

  // Use MAC as primary key, but also check for duplicate IPs to prevent same device listed multiple times
  // Check if this device is already discovered by MAC or playerId
  boolean isDuplicate = false
  discoveredSonoses.each { key, value ->
    LinkedHashMap existingDevice = value as LinkedHashMap
    if(key == mac || existingDevice.id == playerId || existingDevice.deviceIp == ipAddress) {
      logTrace("Device already discovered - MAC: ${mac}, playerId: ${playerId}, IP: ${ipAddress}")
      isDuplicate = true
      // Update the existing entry with latest info to keep it fresh
      if(key == mac) {
        existingDevice.deviceIp = ipAddress
        existingDevice.localApiUrl = "https://${ipAddress}:1443/api/v1/"
        existingDevice.localUpnpUrl = "http://${ipAddress}:1400"
        existingDevice.localUpnpHost = "${ipAddress}:1400"
      }
    }
  }

  if(isDuplicate) { return }

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
    // Check for duplicate secondaries
    boolean secondaryExists = discoveredSonosSecondaries.values().any { secondaryDevice ->
      LinkedHashMap secondary = secondaryDevice as LinkedHashMap
      return secondary.id == playerId
    }
    if(!secondaryExists) {
      discoveredSonosSecondaries[mac] = discoveredSonosSecondary
      logTrace("Found secondary for ${playerInfoDevice?.primaryDeviceId}")
    }
  }
  if(discoveredSonos?.name != null && discoveredSonos?.name != 'null') {
    discoveredSonoses[mac] = discoveredSonos
    logInfo("Discovered Sonos device: ${playerName} (${modelName}) at ${ipAddress}")
    sendFoundSonosEvents()
  } else {
    logTrace("Device id:${discoveredSonos?.id} responded to discovery, but did not provide device name. This is expected for right channel speakers on stereo pairs, subwoofers, and other 'non primary' devices.")
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

/**
 * Forward group volume, mute state, and switch state to all group devices that use this coordinator
 * Called by the coordinator player when groupVolume, groupMute, or grouping state changes
 * @param coordinatorId The RINCON ID of the coordinator
 * @param groupVolume The current group volume (0-100)
 * @param groupMute The current group mute state ('muted' or 'unmuted')
 * @param isGroupedWithFollowers Whether speakers are actually grouped (coordinator has followers)
 */
void updateGroupDeviceVolumeState(String coordinatorId, Integer groupVolume, String groupMute, Boolean isGroupedWithFollowers = null) {
  if(!coordinatorId) { return }

  List<ChildDeviceWrapper> groupsForCoord = getCurrentGroupDevices().findAll {
    it.getDataValue('groupCoordinatorId') == coordinatorId
  }

  groupsForCoord.each { gd ->
    if(groupVolume != null) {
      gd.sendEvent(name: 'volume', value: groupVolume, unit: '%')
    }
    if(groupMute != null) {
      gd.sendEvent(name: 'mute', value: groupMute)
    }
    if(isGroupedWithFollowers != null) {
      // Update switch state: 'on' when grouped with followers, 'off' when standalone
      gd.sendEvent(name: 'switch', value: isGroupedWithFollowers ? 'on' : 'off')
    }
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

  // Get the ACTUAL version from the hub's app code, not the running instance
  // This is critical after updates when the instance may have the old version
  String appVersion = getActualInstalledAppVersion()
  logDebug("Current app version from hub: ${appVersion}")

  // Get package manifest for the CURRENT installed version (not main branch)
  // This ensures we're comparing against the correct expected versions
  String manifestUrl = "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/PackageManifests/SonosAdvancedController/packageManifest.json"
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
    logWarn("Failed to retrieve package manifest for v${appVersion}: ${e.message}")
    logDebug("Trying main branch manifest as fallback...")

    // Fallback to main branch if version-specific manifest not found
    try {
      manifestUrl = 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/PackageManifests/SonosAdvancedController/packageManifest.json'
      Map fallbackParams = [
        uri: manifestUrl,
        contentType: 'application/json',
        timeout: 15
      ]
      httpGet(fallbackParams) { resp ->
        if(resp?.status == 200 && resp.data) {
          manifest = resp.data
        }
      }
    } catch(Exception e2) {
      logWarn("Failed to retrieve fallback manifest: ${e2.message}")
    }
  }

  if(!manifest) {
    logWarn('Failed to retrieve package manifest for version checking')
    return
  }

  logDebug("Using manifest version: ${manifest.version}")

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
        location: driver.location,
        type: 'driver'
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

  // Check library files
  // Libraries can be checked if library IDs are configured
  logInfo("Library checking: libraryIdSMAPI=${libraryIdSMAPI}, libraryIdUtilities=${libraryIdUtilities}, manifest.files count=${manifest.files?.size() ?: 0}")
  logInfo("Settings check: libraryIdSMAPI=${settings.libraryIdSMAPI}, libraryIdUtilities=${settings.libraryIdUtilities}")

  if(settings.libraryIdSMAPI && settings.libraryIdUtilities) {
    logInfo("Checking library versions using configured IDs (SMAPI: ${settings.libraryIdSMAPI}, Utilities: ${settings.libraryIdUtilities})...")

    if(!manifest.files || manifest.files.size() == 0) {
      logWarn("No library files found in manifest!")
    } else {
      logInfo("Found ${manifest.files.size()} file(s) in manifest to check")
    }

    manifest.files?.each { file ->
      String fileName = file.name
      String expectedVersion = appVersion

      logInfo("Processing file from manifest: ${fileName}")

      // Determine which library ID to use based on filename
      Integer libraryId = null
      if(fileName.contains('SMAPILibrary')) {
        libraryId = settings.libraryIdSMAPI as Integer
      } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
        libraryId = settings.libraryIdUtilities as Integer
      }

      if(libraryId) {
        logInfo("Checking library ${fileName} (ID: ${libraryId})...")

        // Determine expected library name and namespace
        String expectedName = ''
        String expectedNamespace = 'dwinks'
        if(fileName.contains('SMAPILibrary')) {
          expectedName = 'SMAPILibrary'
        } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
          expectedName = 'UtilitiesAndLoggingLibrary'
        }

        Map libraryResult = getInstalledLibraryVersionWithValidation(libraryId, expectedName, expectedNamespace)
        String installedVersion = libraryResult.version
        String error = libraryResult.error

        logInfo("Library ${fileName} installed version: ${installedVersion ?: 'Not found'}, expected: ${expectedVersion}")

        if(error) {
          // Library ID is wrong or other error occurred
          logWarn("Library check failed: ${error}")
          state.installedVersions << [
            name: fileName,
            installedVersion: 'Error',
            expectedVersion: expectedVersion,
            status: error
          ]
        } else if(installedVersion && installedVersion != expectedVersion) {
          logWarn("Version mismatch: ${fileName} - installed: ${installedVersion}, expected: ${expectedVersion}")
          state.versionMismatches << [
            name: expectedName,
            fileName: fileName,
            namespace: expectedNamespace,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            libraryId: libraryId,
            type: 'library'
          ]
          state.installedVersions << [
            name: fileName,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            status: 'Mismatch'
          ]
        } else if(installedVersion) {
          logInfo("${fileName} version OK: ${installedVersion}")
          state.installedVersions << [
            name: fileName,
            installedVersion: installedVersion,
            expectedVersion: expectedVersion,
            status: 'OK'
          ]
        } else {
          logInfo("${fileName} not found or could not determine version")
          state.installedVersions << [
            name: fileName,
            installedVersion: 'Not Installed',
            expectedVersion: expectedVersion,
            status: 'Not Found'
          ]
        }
      } else {
        // Determine which specific library ID is missing
        String missingLibrary = ''
        if(fileName.contains('SMAPILibrary')) {
          missingLibrary = 'SMAPILibrary ID'
        } else if(fileName.contains('UtilitiesAndLoggingLibrary')) {
          missingLibrary = 'UtilitiesAndLoggingLibrary ID'
        }
        logDebug("Library file ${fileName} - no library ID configured for checking")
        state.installedVersions << [
          name: fileName,
          installedVersion: 'N/A',
          expectedVersion: expectedVersion,
          status: "⚠ Configure ${missingLibrary} in settings"
        ]
      }
    }
  } else {
    // No library IDs configured, skip library version checking
    logInfo("Library IDs not configured - skipping library version checks")
    manifest.files?.each { file ->
      String fileName = file.name
      String expectedVersion = appVersion

      logDebug("Library file ${fileName} - version checking not available (configure library IDs in settings)")
      state.installedVersions << [
        name: fileName,
        installedVersion: 'N/A',
        expectedVersion: expectedVersion,
        status: '⚠ Configure Library IDs in settings'
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

/**
 * Get the installed version of a library from the hub and validate its identity.
 * @param libraryId The library ID on the hub
 * @param expectedName The expected library name (e.g., 'SMAPILibrary')
 * @param expectedNamespace The expected namespace (e.g., 'dwinks')
 * @return Map with [version: String, error: String] - version is null if error occurred
 */
Map getInstalledLibraryVersionWithValidation(Integer libraryId, String expectedName, String expectedNamespace) {
  Map result = [version: null, error: null]

  try {
    logInfo("Getting library version for ID ${libraryId}...")
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      result.error = 'Failed to authenticate with hub'
      logWarn(result.error)
      return result
    }

    // Get library code using /library/ajax/code
    logInfo("Fetching library code for ID ${libraryId}...")
    Map libraryData = getLibraryCode(libraryId, cookie)
    logInfo("Library data received: ${libraryData ? 'Yes' : 'No'}, has source: ${libraryData?.source ? 'Yes' : 'No'}")

    if(!libraryData?.source) {
      result.error = "Library ID ${libraryId} not found on hub"
      logWarn(result.error)
      return result
    }

    String source = libraryData.source

    // Extract library name and namespace from source
    def nameMatch = (source =~ /library\s*\([^)]*name\s*:\s*['"]([^'"]+)['"]/)
    def namespaceMatch = (source =~ /library\s*\([^)]*namespace\s*:\s*['"]([^'"]+)['"]/)

    String actualName = nameMatch ? nameMatch[0][1] : null
    String actualNamespace = namespaceMatch ? namespaceMatch[0][1] : null

    logInfo("Library ID ${libraryId} - Expected: ${expectedName}/${expectedNamespace}, Found: ${actualName}/${actualNamespace}")

    // Validate library identity
    if(actualName != expectedName || actualNamespace != expectedNamespace) {
      result.error = "⚠ Library ID ${libraryId} is '${actualName}' (${actualNamespace}), not '${expectedName}' (${expectedNamespace})"
      logWarn(result.error)
      return result
    }

    // Extract version from library() definition
    result.version = extractLibraryVersion(source)
    logInfo("Extracted version ${result.version} from library ${actualName}")

  } catch(Exception e) {
    result.error = "Error: ${e.message}"
    logWarn("Error getting library version for ID ${libraryId}: ${e.message}")
    logError("Stack trace:", e)
  }

  return result
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

  // Process libraries FIRST, then drivers (since drivers depend on libraries)
  def libraryMismatches = state.versionMismatches.findAll { it.type == 'library' }
  def driverMismatches = state.versionMismatches.findAll { it.type == 'driver' }

  // Step 1: Update libraries if any are out of date
  if(libraryMismatches.size() > 0) {
    logInfo("Updating ${libraryMismatches.size()} library/libraries first...")

    libraryMismatches.each { mismatch ->
      logInfo("Updating library ${mismatch.name} from ${mismatch.installedVersion} to ${mismatch.expectedVersion}...")

      // Get current app version to download correct library files
      String appVersion = getActualInstalledAppVersion()
      String libraryUrl = "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/${mismatch.fileName}"

      String sourceCode = downloadFile(libraryUrl)

      if(sourceCode) {
        String libraryVersion = extractLibraryVersion(sourceCode)
        Boolean success = publishLibraryToHub(mismatch.libraryId, sourceCode, libraryVersion)
        if(success) {
          logInfo("Successfully updated library ${mismatch.name}")
        } else {
          logWarn("Failed to update library ${mismatch.name}")
        }
      } else {
        logWarn("Failed to download source code for library ${mismatch.name} from ${libraryUrl}")
      }
    }

    // Pause to allow hub to process library updates
    logInfo("Pausing to allow hub to process library updates...")
    pauseExecution(3000)
  }

  // Step 2: Update drivers after libraries are updated
  if(driverMismatches.size() > 0) {
    logInfo("Updating ${driverMismatches.size()} driver(s)...")

    driverMismatches.each { mismatch ->
      logInfo("Updating driver ${mismatch.name} from ${mismatch.installedVersion} to ${mismatch.expectedVersion}...")

      String sourceCode = downloadFile(mismatch.location)

      if(sourceCode) {
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
  }

  // Re-check versions after updates
  pauseExecution(2000)
  checkInstalledVersions()
}

void cleanupDuplicates() {
  logInfo("Checking for duplicate apps and drivers...")

  // Clear cached cookie to ensure fresh auth
  state.remove('hubCookie')

  Map results = findAndRemoveDuplicates()

  if(results.appsRemoved > 0 || results.driversRemoved > 0) {
    state.duplicateCleanupResult = "Removed ${results.appsRemoved} duplicate app(s) and ${results.driversRemoved} duplicate driver(s)"
    logInfo(state.duplicateCleanupResult)
  } else if(results.errors.size() > 0) {
    state.duplicateCleanupResult = "Errors during cleanup: ${results.errors.join(', ')}"
    logWarn(state.duplicateCleanupResult)
  } else {
    state.duplicateCleanupResult = "No duplicates found"
    logInfo("No duplicate apps or drivers found")
  }
}

/**
 * Publish libraries to the hub using the configured library IDs.
 * Downloads the latest library files from GitHub and publishes them to the hub.
 */
void publishLibraries() {
  logInfo("Publishing libraries to hub...")

  // Validate library IDs are configured
  if(!settings.libraryIdSMAPI || !settings.libraryIdUtilities) {
    logWarn("Library IDs not configured. Please enter both library IDs in settings.")
    state.lastLibraryPublish = "Failed: Library IDs not configured"
    return
  }

  // Get current app version to determine which files to publish
  String appVersion = getActualInstalledAppVersion()
  if(!appVersion) {
    logWarn("Failed to determine app version")
    state.lastLibraryPublish = "Failed: Could not determine app version"
    return
  }

  logInfo("Publishing libraries for version ${appVersion}")

  // Define libraries to publish
  List libraries = [
    [
      id: settings.libraryIdSMAPI as Integer,
      name: 'SMAPILibrary',
      namespace: 'dwinks',
      location: "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/SMAPILibrary.groovy"
    ],
    [
      id: settings.libraryIdUtilities as Integer,
      name: 'UtilitiesAndLoggingLibrary',
      namespace: 'dwinks',
      location: "https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/v${appVersion}/Libraries/UtilitiesAndLoggingLibrary.groovy"
    ]
  ]

  Integer successCount = 0
  Integer failCount = 0
  List errors = []

  libraries.each { library ->
    logInfo("Publishing ${library.name}...")

    try {
      // Download library source code
      String sourceCode = downloadFile(library.location)

      if(!sourceCode) {
        String error = "Failed to download ${library.name}"
        logWarn(error)
        errors << error
        failCount++
        return
      }

      // Extract version from library source
      String libraryVersion = extractLibraryVersion(sourceCode)
      logInfo("Library ${library.name} version: ${libraryVersion}")

      // Publish library to hub
      Boolean success = publishLibraryToHub(library.id, sourceCode, libraryVersion)

      if(success) {
        logInfo("Successfully published ${library.name} version ${libraryVersion}")
        successCount++
      } else {
        String error = "Failed to publish ${library.name}"
        logWarn(error)
        errors << error
        failCount++
      }
    } catch(Exception e) {
      String error = "Error publishing ${library.name}: ${e.message}"
      logError(error, e)
      errors << error
      failCount++
    }
  }

  // Update status message
  String timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
  if(successCount == libraries.size()) {
    state.lastLibraryPublish = "✓ ${timestamp}: Successfully published ${successCount} libraries"
    logInfo("Library publish completed successfully: ${successCount} published")
  } else if(successCount > 0) {
    state.lastLibraryPublish = "⚠ ${timestamp}: Published ${successCount}, failed ${failCount} (${errors.join(', ')})"
    logWarn("Library publish partially completed: ${successCount} published, ${failCount} failed")
  } else {
    state.lastLibraryPublish = "✗ ${timestamp}: Failed to publish any libraries (${errors.join(', ')})"
    logWarn("Library publish failed: ${errors.join(', ')}")
  }

  // Re-check installed versions to reflect the published libraries
  if(successCount > 0) {
    pauseExecution(1000) // Brief pause to allow hub to process the updates
    checkInstalledVersions()
  }
}

/**
 * Extract version from library source code by parsing the library() definition.
 * @param sourceCode The library source code
 * @return The version string, or '1.0.0' if not found
 */
String extractLibraryVersion(String sourceCode) {
  logWarn("Extracting library version from source code...")
  logWarn(sourceCode)
  try {
    // Look for version: 'x.x.x' or version: "x.x.x" in library() block
    def versionMatch = (sourceCode =~ /library\s*\([^)]*version\s*:\s*['"]([\d.]+)['"]/)
    if(versionMatch) {
      return versionMatch[0][1]
    }
  } catch(Exception e) {
    logWarn("Failed to extract library version: ${e.message}")
  }
  return '1.0.0' // Default version if not found
}

/**
 * Publish a library to the hub using the library ID.
 * Uses the same endpoint pattern as apps/drivers: /library/ajax/update
 * @param libraryId The library ID on the hub
 * @param sourceCode The library source code
 * @param version The library version (for reference)
 * @return true if successful, false otherwise
 */
Boolean publishLibraryToHub(Integer libraryId, String sourceCode, String version) {
  try {
    // Get authentication cookie
    String cookie = login()
    if(!cookie) {
      logWarn("Failed to authenticate with hub for library publish")
      return false
    }

    // First, check if library exists and get current version
    Map currentLibrary = getLibraryCode(libraryId, cookie)

    if(currentLibrary == null) {
      logWarn("Library with ID ${libraryId} not found on hub. Please verify the library ID is correct.")
      return false
    }

    logInfo("Current library version on hub: ${currentLibrary.version}")

    // Prepare form body for update (same format as apps/drivers)
    String body = "id=${libraryId}&version=${currentLibrary.version ?: 0}&source=${java.net.URLEncoder.encode(sourceCode, 'UTF-8')}"

    // Post update to hub
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/library/ajax/update',
      headers: [
        'Cookie': cookie,
        'Content-Type': 'application/x-www-form-urlencoded'
      ],
      body: body,
      timeout: 30,
      ignoreSSLIssues: true
    ]

    Map result = null
    httpPost(params) { resp ->
      if(resp?.status == 200 && resp?.data) {
        result = resp.data
        logDebug("Library update response: ${result}")
      } else {
        logWarn("Unexpected response status: ${resp?.status}")
        return false
      }
    }

    // Check if update was successful
    if(result?.status == 'success') {
      logInfo("Library update successful")
      return true
    } else {
      logWarn("Library update failed: ${result?.errorMessage ?: 'Unknown error'}")
      return false
    }

  } catch(Exception e) {
    logError("Error publishing library to hub: ${e.message}", e)
    return false
  }
}

/**
 * Get library code from hub by ID.
 * @param libraryId The library ID
 * @param cookie Authentication cookie
 * @return Map with id, version, source, or null if not found
 */
Map getLibraryCode(Integer libraryId, String cookie) {
  try {
    logInfo("Requesting library code for ID ${libraryId} from /library/ajax/code...")
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/library/ajax/code',
      query: [id: libraryId.toString()],
      headers: ['Cookie': cookie],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    Map libraryData = null
    httpGet(params) { resp ->
      logInfo("Library code response status: ${resp?.status}")
      if(resp?.status == 200 && resp?.data) {
        libraryData = resp.data
        logInfo("Library data ID: ${libraryData?.id}, has source: ${libraryData?.source ? 'Yes' : 'No'}")
      } else {
        logWarn("Unexpected response: status ${resp?.status}, data: ${resp?.data}")
      }
    }

    // Verify the library ID matches (apps return 200 with empty payload when not found)
    if(libraryData?.id == libraryId) {
      logInfo("Library ID ${libraryId} found and verified")
      return libraryData
    } else {
      logWarn("Library ID mismatch or not found. Requested: ${libraryId}, Received: ${libraryData?.id}")
      return null
    }

  } catch(Exception e) {
    logWarn("Error getting library code: ${e.message}")
    logError("Stack trace:", e)
    return null
  }
}

void uploadBundle(Map bundle) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return
    }

    logInfo("Uploading bundle: ${bundle.name}")

    // Download the bundle ZIP file
    byte[] zipData = null
    Map params = [
      uri: bundle.location,
      contentType: 'application/zip',
      timeout: 300,
      ignoreSSLIssues: true
    ]

    httpGet(params) { resp ->
      zipData = resp.data.bytes
    }

    if(!zipData) {
      logWarn("Failed to download bundle ${bundle.name}")
      return
    }

    // Upload using HPM's installFile pattern
    String boundary = "----WebKitFormBoundaryDtoO2QfPwfhTjOuS"
    String zipFilename = "${bundle.id}-${bundle.name.replaceAll(/[^a-zA-Z0-9]/, '')}.zip"

    Map uploadParams = [
      uri: "http://127.0.0.1:8080",
      path: "/hub/fileManager/upload",
      query: ['folder': '/'],
      headers: [
        'Cookie': cookie,
        'Content-Type': "multipart/form-data; boundary=${boundary}"
      ],
      body: """--${boundary}\nContent-Disposition: form-data; name="uploadFile"; filename="${zipFilename}"\nContent-Type: application/zip\n\n${new String(zipData, 'ISO-8859-1')}\n\n--${boundary}\nContent-Disposition: form-data; name="folder"\n\n\n--${boundary}--""",
      timeout: 300,
      ignoreSSLIssues: true
    ]

    httpPost(uploadParams) { resp ->
      if(resp?.status == 200) {
        logInfo("Successfully uploaded bundle ${bundle.name}")
      }
    }
  } catch(Exception e) {
    logError("Failed to upload bundle ${bundle.name}: ${e.message}")
  }
}

Map downloadManifest(String url) {
  try {
    Map params = [
      uri: url,
      contentType: 'application/json',
      timeout: 15
    ]
    Map manifest = null
    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        manifest = resp.data
      }
    }
    return manifest
  } catch(Exception e) {
    logWarn("Failed to download manifest: ${e.message}")
    return null
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
// Update Management
// =============================================================================

String getCurrentVersion() {
  return app.version ?: '0.7.10'
}

// Get the ACTUAL current version from the hub's app code (not the running instance)
// This is important after updates, as the running instance may have the old version
String getActualInstalledAppVersion() {
  try {
    String cookie = login()
    if(!cookie) {
      logWarn('Failed to authenticate to get app version')
      return getCurrentVersion() // Fallback to instance version
    }

    // Find this app's code ID
    List allApps = getAppCodeList()
    Map targetApp = allApps.find { it.name == 'Sonos Advanced Controller' && it.namespace == 'dwinks' }

    if(!targetApp) {
      logWarn('Could not find app code entry')
      return getCurrentVersion() // Fallback to instance version
    }

    // Query the app code to get its version
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/code',
      headers: ['Cookie': cookie],
      query: [id: targetApp.id],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    String version = null
    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data?.source) {
        // Parse the version from the source code definition
        def matcher = resp.data.source =~ /version:\s*['"]([^'"]+)['"]/
        if(matcher) {
          version = matcher[0][1]
        }
      }
    }

    if(version) {
      logDebug("Got actual installed app version from hub: ${version}")
      return version
    } else {
      logWarn('Could not parse version from app code')
      return getCurrentVersion() // Fallback to instance version
    }
  } catch(Exception e) {
    logError("Error getting actual app version: ${e.message}")
    return getCurrentVersion() // Fallback to instance version
  }
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
          String currentVersion = getActualInstalledAppVersion()
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

            // Update app label - keep simple since updateLabel doesn't render HTML
            app.updateLabel("Sonos Advanced Controller [Update Available]")

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
    // First, check for and remove any duplicate apps/drivers that may have been created
    logDebug("Checking for duplicate app/driver entries...")
    Map duplicateResults = findAndRemoveDuplicates()
    if(duplicateResults.appsRemoved > 0 || duplicateResults.driversRemoved > 0) {
      logInfo("Cleaned up ${duplicateResults.appsRemoved} duplicate apps and ${duplicateResults.driversRemoved} duplicate drivers")
    }
    if(duplicateResults.errors.size() > 0) {
      logWarn("Some errors occurred during duplicate cleanup: ${duplicateResults.errors}")
    }

    // Clear cached cookie to ensure fresh auth for updates
    state.remove('hubCookie')

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
        logDebug("Downloaded app code (${appCode.length()} bytes)")
        Boolean success = updateThisApp(appCode, version)
        updateStatus.app = success

        if(success) {
          logInfo("App updated successfully to version ${version}")
          // Trigger page refresh after successful app update
          runIn(2, 'refreshPage')
        } else {
          updateStatus.errors << "Failed to update app"
          logError("Failed to update app - check logs for details")
          // Don't stop - continue with driver updates even if app fails
        }
      } else {
        updateStatus.errors << "Failed to download app code"
        logError("Failed to download app code from ${appLocation}")
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
          logDebug("Downloaded driver ${driverName} code (${driverCode.length()} bytes)")
          Boolean success = updateDriver(driverName, driverNamespace, driverCode, version)
          updateStatus.drivers[driverName] = success

          if(success) {
            logInfo("Driver ${driverName} updated successfully")
          } else {
            updateStatus.errors << "Failed to update driver: ${driverName}"
            logError("Failed to update driver: ${driverName} - check logs for details")
          }
        } else {
          updateStatus.errors << "Failed to download driver: ${driverName}"
          logError("Failed to download driver: ${driverName} from ${driverLocation}")
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

      // Clear stale version check data - the page will refresh and new code will run
      // User can re-run version check after refresh to see current state
      state.remove('versionMismatches')
      state.remove('installedVersions')
      state.remove('lastVersionCheck')

      // Set flag to show success message after page refresh
      state.updateJustCompleted = true

      // Clear update label
      app.updateLabel("Sonos Advanced Controller")

      // Clear any scheduled auto-install
      unschedule('performScheduledInstall')

      logInfo("Update completed successfully! The app will refresh in 3 seconds...")
      // Force page refresh to load new code
      runIn(3, 'refreshPage')
    } else {
      logError("Update completed with errors: ${updateStatus.errors.join(', ')}")
      // Even with errors, if drivers updated, recommend checking versions
      if(updateStatus.drivers.any { it.value == true }) {
        logInfo("Some drivers were updated successfully. The page will refresh in 3 seconds...")
        runIn(3, 'refreshPage')
      }
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

Boolean updateThisApp(String sourceCode, String newVersionForLogging) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    // Find the app code ID by matching name and namespace
    // Note: app.id is the INSTANCE id, not the CODE id. We need the code ID from /hub2/userAppTypes
    List allApps = getAppCodeList()
    String thisAppName = 'Sonos Advanced Controller'
    String thisAppNamespace = 'dwinks'

    Map targetApp = allApps.find { appEntry ->
      appEntry.name == thisAppName && appEntry.namespace == thisAppNamespace
    }

    if(!targetApp) {
      logError("Could not find app code for ${thisAppName} in namespace ${thisAppNamespace}")
      logDebug("Available apps: ${allApps}")
      return false
    }

    String appCodeId = targetApp.id
    logDebug("Found app code ID: ${appCodeId} for ${thisAppName}")

    // Get the app's current internal version from the hub (HPM uses this for updates)
    Integer currentHubVersion = null
    Map getAppParams = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/code',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: ['Cookie': cookie],
      query: [id: appCodeId],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    try {
      httpGet(getAppParams) { getResp ->
        logDebug("App code response status: ${getResp?.status}")
        logDebug("App code response data: ${getResp.data}")
        if(getResp?.status == 200 && getResp.data?.version != null) {
          currentHubVersion = getResp.data.version as Integer
          logDebug("Got current app version from hub: ${currentHubVersion}")
        } else {
          logWarn("Could not get app version from hub - response: ${getResp.data}")
        }
      }
    } catch(Exception e) {
      logError("Error getting app version: ${e.message}")
    }

    // If we couldn't get the version, we cannot update
    if(currentHubVersion == null) {
      logError("Cannot update app: unable to retrieve current version from hub")
      return false
    }

    logDebug("Updating app to version ${newVersionForLogging}, hub internal version: ${currentHubVersion}")

    // HPM uses /app/ajax/update for existing apps with version parameter
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/app/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Connection': 'keep-alive',
        'Cookie': cookie
      ],
      body: [
        id: appCodeId,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 420,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(params) { resp ->
      logDebug("App update response: ${resp.data}")
      // HPM checks for resp.data.status == "success"
      if(resp.data?.status == 'success') {
        logInfo("App updated successfully to version ${newVersionForLogging}")
        result = true
      } else {
        logError("App update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating app: ${e.message}")
    return false
  }
}

Boolean updateDriver(String driverName, String namespace, String sourceCode, String newVersionForLogging) {
  try {
    String cookie = login()
    if(!cookie) {
      logError('Failed to authenticate with hub')
      return false
    }

    // Find the driver by name and namespace
    List allDrivers = getDriverList()
    Map targetDriver = allDrivers.find { driver ->
      driver.name == driverName && driver.namespace == namespace
    }

    if(!targetDriver) {
      // Driver doesn't exist - create it using /driver/save (HPM pattern)
      logInfo("Driver not found, creating new driver: ${namespace}.${driverName}")

      Map createParams = [
        uri: "http://127.0.0.1:8080",
        path: '/driver/save',
        requestContentType: 'application/x-www-form-urlencoded',
        headers: [
          'Cookie': cookie
        ],
        body: [
          id: '',
          version: '',
          create: '',
          source: sourceCode
        ],
        timeout: 300,
        ignoreSSLIssues: true
      ]

      Boolean result = false
      httpPost(createParams) { resp ->
        // HPM checks for Location header on successful create
        if(resp.headers?.Location != null) {
          String newId = resp.headers.Location.replaceAll("https?://127.0.0.1:(?:8080|8443)/driver/editor/", "")
          logInfo("Successfully created driver ${driverName} with id ${newId}")
          result = true
        } else {
          logError("Driver ${driverName} creation failed - no Location header")
          result = false
        }
      }
      return result
    }

    // Driver exists - update it using /driver/ajax/update (HPM pattern)
    logInfo("Updating existing driver ${driverName} (id: ${targetDriver.id})")

    // Get driver's current version
    String currentHubVersion = getDriverVersionForUpdate(driverName, namespace) ?: ''
    logDebug("Updating driver ${driverName}: hub version=${currentHubVersion}, new version=${newVersionForLogging}")

    Map updateParams = [
      uri: "http://127.0.0.1:8080",
      path: '/driver/ajax/update',
      requestContentType: 'application/x-www-form-urlencoded',
      headers: [
        'Cookie': cookie
      ],
      body: [
        id: targetDriver.id,
        version: currentHubVersion,
        source: sourceCode
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpPost(updateParams) { resp ->
      logDebug("Driver update response: ${resp.data}")
      // HPM checks for resp.data.status == "success"
      if(resp.data?.status == 'success') {
        logInfo("Successfully updated driver ${driverName}")
        result = true
      } else {
        logError("Driver ${driverName} update failed - response: ${resp.data}")
        result = false
      }
    }
    return result
  } catch(Exception e) {
    logError("Error updating/creating driver ${driverName}: ${e.message}")
    return false
  }
}

List getDriverList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/device/drivers',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15
    ]

    List drivers = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data?.drivers) {
        // Filter to only user drivers
        drivers = resp.data.drivers.findAll { it.type == 'usr' }
      }
    }

    return drivers
  } catch(Exception e) {
    logError("Error getting driver list: ${e.message}")
    return []
  }
}

// Get list of app code entries (not installed app instances)
List getAppCodeList() {
  try {
    String cookie = login()
    if(!cookie) { return [] }

    // Use /hub2/userAppTypes endpoint like HPM does (requires 2.3.6+)
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/hub2/userAppTypes',
      headers: [
        'Cookie': cookie
      ],
      timeout: 15,
      ignoreSSLIssues: true
    ]

    List apps = []

    httpGet(params) { resp ->
      if(resp?.status == 200 && resp.data) {
        resp.data.each { appEntry ->
          apps << [id: appEntry.id.toString(), name: appEntry.name, namespace: appEntry.namespace]
        }
      }
    }

    return apps
  } catch(Exception e) {
    logError("Error getting app code list: ${e.message}")
    return []
  }
}

// Find and remove duplicate app or driver entries (keeps the one with lowest ID, removes others)
Map findAndRemoveDuplicates() {
  Map results = [appsRemoved: 0, driversRemoved: 0, errors: []]

  try {
    String cookie = login()
    if(!cookie) {
      results.errors << "Failed to authenticate"
      return results
    }

    // Check for duplicate apps
    List allApps = getAppCodeList()
    Map appsByNamespace = [:].withDefault { [] }
    allApps.each { appEntry ->
      String key = "${appEntry.namespace}:${appEntry.name}"
      appsByNamespace[key] << appEntry
    }

    appsByNamespace.each { key, entries ->
      if(entries.size() > 1) {
        logWarn("Found ${entries.size()} duplicate apps for ${key}")
        // Sort by ID (keep lowest, which is usually the original)
        entries.sort { Integer.parseInt(it.id) }
        // Remove all but the first (lowest ID)
        entries.drop(1).each { duplicate ->
          logInfo("Removing duplicate app: ${duplicate.name} (id: ${duplicate.id})")
          if(uninstallAppCode(duplicate.id, cookie)) {
            results.appsRemoved++
          } else {
            results.errors << "Failed to remove app ${duplicate.name} (id: ${duplicate.id})"
          }
        }
      }
    }

    // Check for duplicate drivers
    List allDrivers = getDriverList()
    Map driversByNamespace = [:].withDefault { [] }
    allDrivers.each { driverEntry ->
      String key = "${driverEntry.namespace}:${driverEntry.name}"
      driversByNamespace[key] << driverEntry
    }

    driversByNamespace.each { key, entries ->
      if(entries.size() > 1) {
        logWarn("Found ${entries.size()} duplicate drivers for ${key}")
        // Sort by ID (keep lowest, which is usually the original)
        entries.sort { it.id as Integer }
        // Remove all but the first (lowest ID)
        entries.drop(1).each { duplicate ->
          logInfo("Removing duplicate driver: ${duplicate.name} (id: ${duplicate.id})")
          if(uninstallDriverCode(duplicate.id.toString(), cookie)) {
            results.driversRemoved++
          } else {
            results.errors << "Failed to remove driver ${duplicate.name} (id: ${duplicate.id})"
          }
        }
      }
    }

  } catch(Exception e) {
    logError("Error finding/removing duplicates: ${e.message}")
    results.errors << e.message
  }

  if(results.appsRemoved > 0 || results.driversRemoved > 0) {
    logInfo("Removed ${results.appsRemoved} duplicate apps and ${results.driversRemoved} duplicate drivers")
  }

  return results
}

// Uninstall app code (not app instance) - uses HPM-style endpoint
Boolean uninstallAppCode(String appCodeId, String cookie) {
  try {
    // Use newer endpoint if available (2.3.8+), fallback to older method
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: "/app/edit/deleteJsonSafe/${appCodeId}",
      headers: [
        'Cookie': cookie
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpGet(params) { resp ->
      if(resp.data?.status == true) {
        result = true
      }
    }
    return result
  } catch(Exception e) {
    logError("Error uninstalling app code ${appCodeId}: ${e.message}")
    return false
  }
}

// Uninstall driver code - uses HPM-style endpoint
Boolean uninstallDriverCode(String driverCodeId, String cookie) {
  try {
    // Use newer endpoint if available (2.3.7+)
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: "/driver/editor/deleteJson/${driverCodeId}",
      headers: [
        'Cookie': cookie
      ],
      timeout: 300,
      ignoreSSLIssues: true
    ]

    Boolean result = false
    httpGet(params) { resp ->
      if(resp.data?.status == true) {
        result = true
      }
    }
    return result
  } catch(Exception e) {
    logError("Error uninstalling driver code ${driverCodeId}: ${e.message}")
    return false
  }
}

String login() {
  // If we already have a valid cookie, try to reuse it
  if(state.hubCookie) {
    logDebug("Reusing existing cookie")
    return state.hubCookie
  }

  try {
    Map params = [
      uri: "http://127.0.0.1:8080",
      path: '/login',
      requestContentType: 'application/x-www-form-urlencoded',
      body: [
        username: '',
        password: '',
        submit: 'Login'
      ],
      followRedirects: false,
      textParser: true,
      timeout: 15
    ]

    String cookie = null

    httpPost(params) { resp ->
      logDebug("Login response status: ${resp?.status}")
      if(resp?.status == 200 || resp?.status == 302) {
        def setCookieHeader = resp.headers['Set-Cookie']
        if(setCookieHeader) {
          String cookieValue = setCookieHeader.value ?: setCookieHeader.toString()
          cookie = cookieValue.split(';')[0]
          state.hubCookie = cookie  // Store for reuse like HPM
          logDebug("Got cookie: ${cookie?.take(20)}...")
        } else {
          logWarn("No Set-Cookie header in login response")
        }
      } else {
        logWarn("Unexpected login status: ${resp?.status}")
      }
    }

    if(!cookie) {
      logWarn("Failed to get authentication cookie")
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
