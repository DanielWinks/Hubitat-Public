/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
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

import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

metadata {
  definition(
    name: 'Generic Z-Wave Association Manager',
    version: '1.1.0',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    description: 'Temporary utility driver for creating and verifying direct Z-Wave associations',
    singleThreaded: true,
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/ZWave/GenericZWaveAssociation.groovy'
  ) {
    capability 'Configuration'
    capability 'Refresh'

    attribute 'associationStatus', 'enum', ['idle', 'querying', 'refreshed', 'refreshPartial', 'pending', 'complete', 'failed', 'invalid']
    attribute 'currentAssociations', 'string'
    attribute 'pendingAssociations', 'string'
    attribute 'failedAssociations', 'string'
    attribute 'supportedAssociationGroups', 'number'
    attribute 'associationMetadataSource', 'string'
    attribute 'lastAssociationOperation', 'string'

    command 'applyAssociations'
  }

  preferences {
    section('Association Settings') {
      Integer groupCount = getDiscoveredAssociationGroupCount()
      if (groupCount > 0) {
        for (Integer group = MIN_ASSOCIATION_GROUP; group <= groupCount; group++) {
          input(
            name: associationSettingName(group),
            type: 'text',
            title: associationGroupPreferenceTitle(group),
            description: associationGroupPreferenceDescription(group),
            required: false
          )
        }
      } else {
        input(
          name: 'associationDiscoveryInstructions',
          type: 'hidden',
          title: 'Association groups have not been discovered yet',
          description: 'Press Refresh, wait for associationStatus to become refreshed, then reload this device page.'
        )
      }
    }

    section('Logging') {
      input(name: 'logEnable', type: 'bool', title: 'Enable logging', required: false, defaultValue: true)
      input(name: 'debugLogEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: true)
      input(name: 'traceLogEnable', type: 'bool', title: 'Enable trace logging', required: false, defaultValue: false)
      input(name: 'descriptionTextEnable', type: 'bool', title: 'Enable description text logging', required: false, defaultValue: true)
    }

    section('Association Group Metadata') {
      input(
        name: 'externalMetadataFallback',
        type: 'bool',
        title: 'Use external database when the device does not report group names',
        description: 'Queries OpenSmartHouse using only the device manufacturer, product type, product ID, and firmware. Results are cached on the hub.',
        required: false,
        defaultValue: true
      )
    }
  }
}

@Field static final Map<Integer, Integer> COMMAND_CLASS_VERSIONS = [
  0x59: 3, // Association Group Information
  0x85: 2  // Association
]
@Field static final List<Integer> RETRY_TARGET_SECONDS = [5, 10, 15, 30]
@Field static final Integer FINAL_RESPONSE_WAIT_SECONDS = 5
@Field static final Integer REFRESH_TIMEOUT_SECONDS = 30
@Field static final Integer METADATA_GRACE_SECONDS = 2
@Field static final Integer MAX_CLASSIC_ZWAVE_NODE_ID = 0xE8
@Field static final Integer MIN_ASSOCIATION_GROUP = 1
@Field static final Integer MAX_ASSOCIATION_GROUP = 255
@Field static final Integer COMMAND_DELAY_MS = 250
@Field static final Integer EXTERNAL_METADATA_TIMEOUT_SECONDS = 10
@Field static final String OPEN_SMART_HOUSE_API_BASE = 'https://opensmarthouse.org/dmxConnect/api/zwavedatabase'
@Field static final Map<Integer, String> COMMAND_CLASS_NAMES = [
  0x20: 'Basic',
  0x25: 'Binary Switch',
  0x26: 'Multilevel Switch',
  0x30: 'Binary Sensor',
  0x31: 'Multilevel Sensor',
  0x32: 'Meter',
  0x5A: 'Device Reset Locally',
  0x5B: 'Central Scene',
  0x60: 'Multi Channel',
  0x71: 'Notification'
]
@Field static final Map<Integer, Map<Integer, String>> COMMAND_NAMES = [
  0x20: [0x01: 'Set', 0x02: 'Get', 0x03: 'Report'],
  0x25: [0x01: 'Set', 0x02: 'Get', 0x03: 'Report'],
  0x26: [0x01: 'Set', 0x02: 'Get', 0x03: 'Report', 0x04: 'Start Level Change', 0x05: 'Stop Level Change'],
  0x30: [0x02: 'Get', 0x03: 'Report'],
  0x31: [0x04: 'Get', 0x05: 'Report'],
  0x32: [0x01: 'Get', 0x02: 'Report'],
  0x5A: [0x01: 'Notification'],
  0x5B: [0x03: 'Notification'],
  0x71: [0x04: 'Get', 0x05: 'Report']
]

// =============================================================================
// Lifecycle and commands
// =============================================================================

void installed() {
  logInfo('Installed Generic Z-Wave Association Manager')
  initialize()
  scheduleLogsOff()
}

void updated() {
  logInfo('Preferences saved; applying requested associations')
  configure()
  scheduleLogsOff()
}

void uninstalled() {
  unschedule()
}

void initialize() {
  initializeAttributes()
  refresh()
}

void configure() {
  applyAssociations()
}

void applyAssociations() {
  unschedule('retryPendingAssociations')
  unschedule('finalizeAssociationFailure')

  if (isLongRangeDevice()) {
    failConfiguration('Z-Wave Long Range nodes do not support direct associations')
    return
  }

  Integer groupCount = getDiscoveredAssociationGroupCount()
  if (groupCount <= 0 || state.associationPreferencesReady != true) {
    failConfiguration('Association preferences are not ready. Press Refresh, wait for completion, then reload the device page before saving.')
    return
  }

  Map parsed = readDesiredAssociationsFromSettings(groupCount)
  List<String> errors = (parsed.errors ?: []) as List<String>
  if (!errors.isEmpty()) {
    failConfiguration(errors.join('; '))
    return
  }

  Map<String, List<Integer>> desired = copyAssociationMap(parsed.associations as Map)
  state.desiredAssociations = desired
  state.failureReasons = [:]
  state.failedAssociations = [:]

  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  Map<String, List<Integer>> pending = calculatePendingTargets(desired, current)

  String operationId = now().toString()
  state.operationId = operationId
  state.retryIndex = 0
  state.pendingAssociations = pending
  state.partialAssociationReports = [:]

  sendEvent(name: 'associationStatus', value: pending.isEmpty() ? 'complete' : 'pending')
  sendEvent(name: 'pendingAssociations', value: pending.isEmpty() ? 'None' : formatPendingAssociations(pending, current))
  sendEvent(name: 'failedAssociations', value: 'None')
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - ${pending.isEmpty() ? 'already matched' : 'started'}")

  logInfo("Applying exact requested associations: ${formatAssociationMap(desired, true)}")
  if (pending.isEmpty()) {
    state.operationId = null
    logInfo('All association groups already match their preferences')
    return
  }
  sendAssociationAttempt()
  scheduleNextRetry(operationId)
}

void refresh() {
  unschedule('retryPendingAssociations')
  unschedule('finalizeAssociationFailure')
  unschedule('finishRefreshAfterMetadataGrace')
  state.operationId = null

  String refreshId = now().toString()
  state.refreshId = refreshId
  state.refreshInProgress = true
  state.refreshGroupCountReceived = false
  state.refreshPendingGroups = []
  state.refreshReceivedGroups = []
  state.partialAssociationReports = [:]
  state.currentAssociations = [:]
  state.pendingAssociations = [:]
  state.associationGroupMetadata = [:]
  state.agiNamedGroups = []
  state.externalMetadataLookupInProgress = false
  state.externalMetadataLookupOutcome = null
  state.associationPreferencesReady = false

  if (!hasActiveAssociationOperation()) {
    sendEvent(name: 'associationStatus', value: 'querying')
  }
  sendEvent(name: 'currentAssociations', value: 'Querying...')
  sendEvent(name: 'pendingAssociations', value: 'None')
  sendEvent(name: 'associationMetadataSource', value: 'Querying device AGI...')
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - refresh started")

  List<String> commands = [secureCommand(zwave.associationV2.associationGroupingsGet())]

  logInfo('Querying the device for its current associations')
  sendZWaveCommands(commands)
  runIn(
    REFRESH_TIMEOUT_SECONDS,
    'refreshTimedOut',
    [overwrite: true, data: [refreshId: refreshId]]
  )
}

// =============================================================================
// Z-WaveJS parsing and reports
// =============================================================================

void parse(String description) {
  logTrace("parse(): ${description}")
  if (description?.trim()?.startsWith('{')) {
    state.zwaveJsBackend = true
  }
  hubitat.zwave.Command command = zwave.parse(description, COMMAND_CLASS_VERSIONS)
  if (command == null) {
    logWarn("Unable to parse Z-Wave message: ${description}")
    return
  }
  zwaveEvent(command)
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport command) {
  Integer supportedGroups = command.supportedGroupings as Integer
  state.supportedAssociationGroups = supportedGroups
  sendEvent(name: 'supportedAssociationGroups', value: supportedGroups)
  logInfo("Device reports ${supportedGroups} association group(s)")

  rejectUnsupportedDesiredGroups(supportedGroups)

  List<String> commands = []
  List<String> expectedGroups = []
  if (supportedGroups > 0) {
    for (Integer group = MIN_ASSOCIATION_GROUP; group <= supportedGroups; group++) {
      expectedGroups.add(group.toString())
      commands.add(associationGetCommand(group))
      commands.add(associationGroupNameGetCommand(group))
      commands.add(associationGroupCommandListGetCommand(group))
    }
  }

  if (state.refreshInProgress == true) {
    state.refreshGroupCountReceived = true
    List<String> receivedGroups = ((state.refreshReceivedGroups ?: []) as List).collect { Object item -> item.toString() }
    state.refreshPendingGroups = expectedGroups.findAll { String group -> !receivedGroups.contains(group) }
  }

  if (!commands.isEmpty()) {
    sendZWaveCommands(commands)
  } else {
    scheduleRefreshCompletion()
  }

  finishAssociationOperationIfComplete()
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport command) {
  Integer group = command.groupingIdentifier as Integer
  List<Integer> fragment = ((command.nodeId ?: []) as List).collect { Object node -> node as Integer }
  Map<String, List<Integer>> partialReports = copyAssociationMap(state.partialAssociationReports as Map)
  List<Integer> accumulated = (partialReports[group.toString()] ?: []) as List<Integer>
  accumulated.addAll(fragment)

  if ((command.reportsToFollow as Integer) > 0) {
    partialReports[group.toString()] = accumulated.unique().sort()
    state.partialAssociationReports = partialReports
    logDebug("Received a partial Association Report for Group ${group}")
    return
  }

  partialReports.remove(group.toString())
  state.partialAssociationReports = partialReports

  List<Integer> actualNodes = accumulated.unique().sort()
  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  current[group.toString()] = actualNodes
  state.currentAssociations = current

  Map<String, Integer> maximumNodes = (state.maximumAssociationNodes ?: [:]) as Map<String, Integer>
  maximumNodes[group.toString()] = command.maxNodesSupported as Integer
  state.maximumAssociationNodes = maximumNodes

  logInfo("Association Group ${group}: ${formatNodeList(actualNodes)}")
  sendEvent(name: 'currentAssociations', value: formatAssociationMap(current, true))

  if (state.refreshInProgress == true) {
    device.updateSetting(
      associationSettingName(group),
      [value: formatNodeSetting(actualNodes), type: 'string']
    )
  }

  reconcileDesiredGroup(group, actualNodes, command.maxNodesSupported as Integer)
  recordRefreshGroupComplete(group)
  finishAssociationOperationIfComplete()
}

void zwaveEvent(hubitat.zwave.commands.associationgrpinfov3.AssociationGroupNameReport command) {
  Integer group = command.groupingIdentifier as Integer
  String groupName = decodeZWaveText(command.name)
  if (groupName) {
    updateAssociationGroupMetadata(group, 'name', groupName)
    updateAssociationGroupMetadata(group, 'source', 'Device AGI')
    List<String> namedGroups = ((state.agiNamedGroups ?: []) as List).collect { Object value -> value.toString() }
    if (!namedGroups.contains(group.toString())) {
      namedGroups.add(group.toString())
      state.agiNamedGroups = namedGroups
    }
    logInfo("Association Group ${group} name: ${groupName}")
  }
}

void zwaveEvent(hubitat.zwave.commands.associationgrpinfov3.AssociationGroupCommandListReport command) {
  Integer group = command.groupingIdentifier as Integer
  List<Integer> commandBytes = ((command.command ?: []) as List).collect { Object value -> value as Integer }
  List<String> commands = describeAssociationCommands(commandBytes)
  if (!commands.isEmpty()) {
    updateAssociationGroupMetadata(group, 'commands', commands)
    logInfo("Association Group ${group} commands: ${commands.join(', ')}")
  }
}

void zwaveEvent(hubitat.zwave.Command command) {
  logDebug("Unhandled Z-Wave command: ${command}")
}

// =============================================================================
// Retry and verification
// =============================================================================

void retryPendingAssociations(Map data) {
  String operationId = data?.operationId?.toString()
  Integer callbackIndex = data?.retryIndex as Integer
  if (operationId == null || operationId != state.operationId?.toString()) {
    logDebug('Ignoring a retry from an old association operation')
    return
  }
  if (callbackIndex == null || callbackIndex != (state.retryIndex as Integer)) {
    logDebug('Ignoring an out-of-sequence association retry')
    return
  }
  if (!hasPendingAssociations()) {
    finishAssociationOperationIfComplete()
    return
  }

  Integer targetSeconds = RETRY_TARGET_SECONDS[callbackIndex]
  logWarn("Association verification is still pending; retrying at ${targetSeconds} seconds")
  sendAssociationAttempt()

  state.retryIndex = callbackIndex + 1
  if ((state.retryIndex as Integer) < RETRY_TARGET_SECONDS.size()) {
    scheduleNextRetry(operationId)
  } else {
    runIn(
      FINAL_RESPONSE_WAIT_SECONDS,
      'finalizeAssociationFailure',
      [overwrite: true, data: [operationId: operationId]]
    )
  }
}

void finalizeAssociationFailure(Map data) {
  String operationId = data?.operationId?.toString()
  if (operationId == null || operationId != state.operationId?.toString()) {
    return
  }

  Map<String, List<Integer>> pending = copyAssociationMap(state.pendingAssociations as Map)
  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  if (pending.isEmpty()) {
    finishAssociationOperationIfComplete()
    return
  }

  Map<String, List<Integer>> failed = copyAssociationMap(state.failedAssociations as Map)
  Map<String, String> reasons = (state.failureReasons ?: [:]) as Map<String, String>
  pending.each { String group, List<Integer> nodes ->
    failed[group] = nodes
    reasons[group] = 'not verified after retries at 5, 10, 15, and 30 seconds'
  }

  state.failedAssociations = failed
  state.failureReasons = reasons
  state.pendingAssociations = [:]
  state.operationId = null

  sendEvent(name: 'associationStatus', value: 'failed')
  sendEvent(name: 'pendingAssociations', value: 'None')
  sendEvent(name: 'failedAssociations', value: formatFailedAssociations(failed, reasons, current))
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - failed")
  logError("Failed to verify associations: ${formatFailedAssociations(failed, reasons, current)}")
}

void refreshTimedOut(Map data) {
  String refreshId = data?.refreshId?.toString()
  if (refreshId == null || refreshId != state.refreshId?.toString() || state.refreshInProgress != true) {
    return
  }

  List pendingGroups = (state.refreshPendingGroups ?: []) as List
  Boolean groupCountReceived = state.refreshGroupCountReceived == true
  if (groupCountReceived && pendingGroups.isEmpty() && state.externalMetadataLookupInProgress == true) {
    state.externalMetadataLookupInProgress = false
    state.externalMetadataLookupOutcome = 'External metadata lookup timed out'
    logWarn('External association metadata lookup timed out; completing the live association refresh')
    finishRefreshIfComplete()
    return
  }
  state.refreshInProgress = false
  state.refreshGroupCountReceived = false
  state.refreshPendingGroups = []
  state.refreshReceivedGroups = []
  state.externalMetadataLookupInProgress = false
  String result = !groupCountReceived ? 'group count query timed out' : "partial; no report from Group(s) ${pendingGroups.join(', ')}"
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - refresh ${result}")
  if (!hasActiveAssociationOperation()) {
    sendEvent(name: 'associationStatus', value: 'refreshPartial')
  }
  logWarn("Association refresh was ${result}")
}

void finishRefreshAfterMetadataGrace(Map data) {
  String refreshId = data?.refreshId?.toString()
  if (refreshId == null || refreshId != state.refreshId?.toString()) {
    return
  }
  if (startExternalMetadataFallbackIfNeeded(refreshId)) {
    return
  }
  finishRefreshIfComplete()
}

private void sendAssociationAttempt() {
  Map<String, List<Integer>> pending = copyAssociationMap(state.pendingAssociations as Map)
  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  List<String> commands = []

  pending.each { String groupKey, List<Integer> pendingNodes ->
    Integer group = groupKey.toInteger()
    List<Integer> actualNodes = current[groupKey] ?: []
    List<Integer> missingNodes = pendingNodes.findAll { Integer node -> !actualNodes.contains(node) }
    List<Integer> unwantedNodes = actualNodes.findAll { Integer node -> !pendingNodes.contains(node) }
    if (!unwantedNodes.isEmpty()) {
      commands.add(associationRemoveCommand(group, unwantedNodes))
    }
    if (!missingNodes.isEmpty()) {
      commands.add(associationSetCommand(group, missingNodes))
    }
    commands.add(associationGetCommand(group))
  }

  sendZWaveCommands(commands)
}

private void scheduleNextRetry(String operationId) {
  Integer retryIndex = state.retryIndex as Integer
  if (retryIndex == null || retryIndex >= RETRY_TARGET_SECONDS.size()) {
    return
  }

  Integer previousTarget = retryIndex == 0 ? 0 : RETRY_TARGET_SECONDS[retryIndex - 1]
  Integer delaySeconds = RETRY_TARGET_SECONDS[retryIndex] - previousTarget
  runIn(
    delaySeconds,
    'retryPendingAssociations',
    [overwrite: true, data: [operationId: operationId, retryIndex: retryIndex]]
  )
}

private void reconcileDesiredGroup(Integer group, List<Integer> actualNodes, Integer maximumNodes) {
  String groupKey = group.toString()
  Map<String, List<Integer>> desired = copyAssociationMap(state.desiredAssociations as Map)
  if (!desired.containsKey(groupKey)) {
    return
  }

  List<Integer> desiredNodes = desired[groupKey]
  List<Integer> missingNodes = desiredNodes.findAll { Integer node -> !actualNodes.contains(node) }
  List<Integer> unwantedNodes = actualNodes.findAll { Integer node -> !desiredNodes.contains(node) }
  Map<String, List<Integer>> pending = copyAssociationMap(state.pendingAssociations as Map)
  Map<String, List<Integer>> failed = copyAssociationMap(state.failedAssociations as Map)
  Map<String, String> reasons = (state.failureReasons ?: [:]) as Map<String, String>

  if (missingNodes.isEmpty() && unwantedNodes.isEmpty()) {
    pending.remove(groupKey)
    failed.remove(groupKey)
    reasons.remove(groupKey)
  } else if (hasActiveAssociationOperation() && pending.containsKey(groupKey)) {
    if (maximumNodes > 0 && desiredNodes.size() > maximumNodes) {
      pending.remove(groupKey)
      failed[groupKey] = desiredNodes
      reasons[groupKey] = "preference contains ${desiredNodes.size()} node(s), but Group ${group} supports ${maximumNodes}"
    } else {
      pending[groupKey] = desiredNodes
    }
  } else if (failed.containsKey(groupKey)) {
    failed[groupKey] = desiredNodes
  }

  state.pendingAssociations = pending
  state.failedAssociations = failed
  state.failureReasons = reasons
  sendEvent(name: 'pendingAssociations', value: pending.isEmpty() ? 'None' : formatPendingAssociations(pending, copyAssociationMap(state.currentAssociations as Map)))
  sendEvent(name: 'failedAssociations', value: failed.isEmpty() ? 'None' : formatFailedAssociations(failed, reasons, copyAssociationMap(state.currentAssociations as Map)))
}

private void rejectUnsupportedDesiredGroups(Integer supportedGroups) {
  if (!hasActiveAssociationOperation()) {
    return
  }

  Map<String, List<Integer>> pending = copyAssociationMap(state.pendingAssociations as Map)
  Map<String, List<Integer>> failed = copyAssociationMap(state.failedAssociations as Map)
  Map<String, String> reasons = (state.failureReasons ?: [:]) as Map<String, String>

  pending.keySet().toList().each { String groupKey ->
    Integer group = groupKey.toInteger()
    if (group > supportedGroups) {
      failed[groupKey] = pending.remove(groupKey)
      reasons[groupKey] = "device reports only ${supportedGroups} association group(s)"
    }
  }

  state.pendingAssociations = pending
  state.failedAssociations = failed
  state.failureReasons = reasons
  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  sendEvent(name: 'pendingAssociations', value: pending.isEmpty() ? 'None' : formatPendingAssociations(pending, current))
  sendEvent(name: 'failedAssociations', value: failed.isEmpty() ? 'None' : formatFailedAssociations(failed, reasons, current))
}

private void finishAssociationOperationIfComplete() {
  if (!hasActiveAssociationOperation() || hasPendingAssociations()) {
    return
  }

  unschedule('retryPendingAssociations')
  unschedule('finalizeAssociationFailure')
  state.operationId = null

  Map<String, List<Integer>> failed = copyAssociationMap(state.failedAssociations as Map)
  if (failed.isEmpty()) {
    sendEvent(name: 'associationStatus', value: 'complete')
    sendEvent(name: 'failedAssociations', value: 'None')
    sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - complete")
    logInfo('All requested associations were verified')
  } else {
    Map<String, String> reasons = (state.failureReasons ?: [:]) as Map<String, String>
    sendEvent(name: 'associationStatus', value: 'failed')
    Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
    sendEvent(name: 'failedAssociations', value: formatFailedAssociations(failed, reasons, current))
    sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - failed")
    logError("Some requested associations failed: ${formatFailedAssociations(failed, reasons, current)}")
  }
}

private void recordRefreshGroupComplete(Integer group) {
  if (state.refreshInProgress != true) {
    return
  }

  List<String> receivedGroups = ((state.refreshReceivedGroups ?: []) as List).collect { Object item -> item.toString() }
  if (!receivedGroups.contains(group.toString())) {
    receivedGroups.add(group.toString())
  }
  state.refreshReceivedGroups = receivedGroups

  List<String> pendingGroups = ((state.refreshPendingGroups ?: []) as List).collect { Object item -> item.toString() }
  pendingGroups.remove(group.toString())
  state.refreshPendingGroups = pendingGroups
  scheduleRefreshCompletion()
}

private void scheduleRefreshCompletion() {
  if (state.refreshInProgress != true || state.refreshGroupCountReceived != true || !((state.refreshPendingGroups ?: []) as List).isEmpty()) {
    return
  }

  runIn(
    METADATA_GRACE_SECONDS,
    'finishRefreshAfterMetadataGrace',
    [overwrite: true, data: [refreshId: state.refreshId?.toString()]]
  )
}

private void finishRefreshIfComplete() {
  if (state.refreshInProgress != true || state.refreshGroupCountReceived != true || !((state.refreshPendingGroups ?: []) as List).isEmpty()) {
    return
  }

  state.refreshInProgress = false
  state.refreshGroupCountReceived = false
  state.refreshReceivedGroups = []
  state.externalMetadataLookupInProgress = false
  state.associationPreferencesReady = true
  unschedule('refreshTimedOut')
  updateAssociationMetadataSourceEvent()
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - refresh complete")
  if (!hasActiveAssociationOperation()) {
    Map failed = state.failedAssociations as Map
    sendEvent(name: 'associationStatus', value: failed?.isEmpty() == false ? 'failed' : 'refreshed')
  }
  logInfo('Association refresh complete')
}

// =============================================================================
// External association-group metadata fallback
// =============================================================================

private Boolean startExternalMetadataFallbackIfNeeded(String refreshId) {
  if (settings.externalMetadataFallback == false || missingAgiGroupNames().isEmpty()) {
    return false
  }
  if (state.externalMetadataLookupInProgress == true) {
    return true
  }

  Map<String, String> fingerprint = readDeviceFingerprint()
  if (fingerprint.isEmpty()) {
    state.externalMetadataLookupOutcome = 'External lookup skipped: device fingerprint unavailable'
    logWarn('Cannot query external association metadata because the device fingerprint is unavailable')
    return false
  }

  String fingerprintKey = associationMetadataFingerprintKey(fingerprint)
  Map cache = (state.externalAssociationMetadataCache ?: [:]) as Map
  if (cache.fingerprint?.toString() == fingerprintKey && cache.groups instanceof Map) {
    Integer mergedGroups = mergeExternalAssociationMetadata(cache.groups as Map)
    state.externalMetadataLookupOutcome = mergedGroups > 0 ? 'OpenSmartHouse cache' : 'No external group metadata found'
    logInfo("Used cached OpenSmartHouse metadata for ${mergedGroups} association group(s)")
    return false
  }

  state.externalMetadataLookupInProgress = true
  state.externalMetadataLookupRefreshId = refreshId
  state.externalMetadataLookupOutcome = 'Querying OpenSmartHouse'
  sendEvent(name: 'associationMetadataSource', value: 'Device AGI incomplete; querying OpenSmartHouse...')

  String filter = "manufacturer%3A${fingerprint.manufacturer}%20${fingerprint.productType}%3A${fingerprint.productId}"
  String uri = "${OPEN_SMART_HOUSE_API_BASE}/device/list.php?filter=${filter}&manufacturer=-1&limit=100"
  logInfo("Device AGI did not name Group(s) ${missingAgiGroupNames().join(', ')}; querying OpenSmartHouse by device fingerprint")
  asynchttpGet(
    'handleOpenSmartHouseDeviceListResponse',
    [uri: uri, contentType: 'application/json', timeout: EXTERNAL_METADATA_TIMEOUT_SECONDS],
    [refreshId: refreshId, fingerprint: fingerprint]
  )
  return true
}

void handleOpenSmartHouseDeviceListResponse(AsyncResponse response, Map data) {
  String refreshId = data?.refreshId?.toString()
  if (!isCurrentExternalMetadataLookup(refreshId)) {
    return
  }
  if (isAsyncHttpFailure(response)) {
    finishExternalMetadataLookup(refreshId, "OpenSmartHouse search failed${asyncHttpFailureDescription(response)}")
    return
  }

  Map payload = parseAsyncJsonObject(response)
  List devices = (payload?.devices ?: []) as List
  Map selectedDevice = selectExternalDeviceDefinition(devices, data?.fingerprint as Map)
  if (selectedDevice == null || selectedDevice.id == null) {
    finishExternalMetadataLookup(refreshId, 'No matching OpenSmartHouse device')
    return
  }

  String uri = "${OPEN_SMART_HOUSE_API_BASE}/device/read.php?device_id=${selectedDevice.id}"
  asynchttpGet(
    'handleOpenSmartHouseDeviceResponse',
    [uri: uri, contentType: 'application/json', timeout: EXTERNAL_METADATA_TIMEOUT_SECONDS],
    [refreshId: refreshId, fingerprint: data.fingerprint]
  )
}

void handleOpenSmartHouseDeviceResponse(AsyncResponse response, Map data) {
  String refreshId = data?.refreshId?.toString()
  if (!isCurrentExternalMetadataLookup(refreshId)) {
    return
  }
  if (isAsyncHttpFailure(response)) {
    finishExternalMetadataLookup(refreshId, "OpenSmartHouse device query failed${asyncHttpFailureDescription(response)}")
    return
  }

  Map payload = parseAsyncJsonObject(response)
  List associations = (payload?.associations ?: []) as List
  Map<String, Map> externalMetadata = [:]
  associations.each { Object value ->
    if (!(value instanceof Map)) {
      return
    }
    Map association = value as Map
    Integer group = association.group_id as Integer
    if (group == null || group < MIN_ASSOCIATION_GROUP || group > getDiscoveredAssociationGroupCount()) {
      return
    }

    String description = cleanExternalText(association.description ?: association.overview)
    String name = normalizeExternalGroupName(group, cleanExternalText(association.label), description)
    Map groupMetadata = [source: 'OpenSmartHouse']
    if (name) {
      groupMetadata.name = name
    }
    if (description) {
      groupMetadata.description = description
    }
    if (groupMetadata.size() > 1) {
      externalMetadata[group.toString()] = groupMetadata
    }
  }

  Map<String, String> fingerprint = (data?.fingerprint ?: [:]) as Map<String, String>
  state.externalAssociationMetadataCache = [
    fingerprint: associationMetadataFingerprintKey(fingerprint),
    groups: externalMetadata,
    source: 'OpenSmartHouse',
    cachedAt: now()
  ]
  Integer mergedGroups = mergeExternalAssociationMetadata(externalMetadata)
  finishExternalMetadataLookup(
    refreshId,
    mergedGroups > 0 ? "OpenSmartHouse (${mergedGroups} fallback group(s))" : 'No association metadata in matching OpenSmartHouse device'
  )
}

private void finishExternalMetadataLookup(String refreshId, String outcome) {
  if (!isCurrentExternalMetadataLookup(refreshId)) {
    return
  }
  state.externalMetadataLookupInProgress = false
  state.externalMetadataLookupOutcome = outcome
  if (outcome.startsWith('OpenSmartHouse')) {
    logInfo("External association metadata lookup complete: ${outcome}")
  } else {
    logWarn("External association metadata lookup complete: ${outcome}")
  }
  updateAssociationMetadataSourceEvent()
  finishRefreshIfComplete()
}

private Boolean isCurrentExternalMetadataLookup(String refreshId) {
  return refreshId != null &&
    refreshId == state.refreshId?.toString() &&
    refreshId == state.externalMetadataLookupRefreshId?.toString() &&
    state.refreshInProgress == true &&
    state.externalMetadataLookupInProgress == true
}

private List<String> missingAgiGroupNames() {
  Integer groupCount = getDiscoveredAssociationGroupCount()
  List<String> namedGroups = ((state.agiNamedGroups ?: []) as List).collect { Object value -> value.toString() }
  List<String> missing = []
  for (Integer group = MIN_ASSOCIATION_GROUP; group <= groupCount; group++) {
    if (!namedGroups.contains(group.toString())) {
      missing.add(group.toString())
    }
  }
  return missing
}

private Integer mergeExternalAssociationMetadata(Map externalMetadata) {
  List<String> missingGroups = missingAgiGroupNames()
  Integer mergedGroups = 0
  missingGroups.each { String groupKey ->
    Map fallback = externalMetadata?.get(groupKey) as Map
    if (fallback == null || fallback.isEmpty()) {
      return
    }
    Map<String, Map> metadataByGroup = (state.associationGroupMetadata ?: [:]) as Map<String, Map>
    Map currentMetadata = (metadataByGroup[groupKey] ?: [:]) as Map
    if (fallback.name) {
      currentMetadata.name = fallback.name.toString()
    }
    if (fallback.description) {
      currentMetadata.description = fallback.description.toString()
    }
    currentMetadata.source = fallback.source?.toString() ?: 'External database'
    metadataByGroup[groupKey] = currentMetadata
    state.associationGroupMetadata = metadataByGroup
    mergedGroups++
  }
  return mergedGroups
}

private Map<String, String> readDeviceFingerprint() {
  String manufacturer = firstDeviceDataValue(['manufacturer', 'manufacturerId'])
  String productType = firstDeviceDataValue(['deviceType', 'productType'])
  String productId = firstDeviceDataValue(['deviceId', 'productId'])
  manufacturer = normalizeFingerprintHex(manufacturer)
  productType = normalizeFingerprintHex(productType)
  productId = normalizeFingerprintHex(productId)
  if (!manufacturer || !productType || !productId) {
    return [:]
  }
  String firmware = firstDeviceDataValue(['firmwareVersion', 'firmware']) ?: device.currentValue('firmwareVersion')?.toString()
  return [manufacturer: manufacturer, productType: productType, productId: productId, firmware: firmware ?: '']
}

private String firstDeviceDataValue(List<String> names) {
  for (String name : names) {
    String value = device.getDataValue(name)?.toString()?.trim()
    if (value) {
      return value
    }
  }
  return ''
}

private String normalizeFingerprintHex(String value) {
  String cleaned = value?.trim()?.replaceFirst('(?i)^0x', '')
  if (!cleaned || !(cleaned ==~ /(?i)^[0-9a-f]{1,4}$/)) {
    return ''
  }
  return String.format('%04X', Integer.parseInt(cleaned, 16))
}

private String associationMetadataFingerprintKey(Map fingerprint) {
  if (fingerprint == null || fingerprint.isEmpty()) {
    return ''
  }
  return "${fingerprint.manufacturer}:${fingerprint.productType}:${fingerprint.productId}:${fingerprint.firmware ?: ''}"
}

private Map selectExternalDeviceDefinition(List devices, Map fingerprint) {
  if (devices == null || devices.isEmpty()) {
    return null
  }
  String firmware = fingerprint?.firmware?.toString()?.trim()
  if (firmware) {
    Map firmwareMatch = devices.find { Object value ->
      if (!(value instanceof Map)) {
        return false
      }
      Map candidate = value as Map
      return compareFirmwareVersions(firmware, candidate.version_min?.toString() ?: '0.0') >= 0 &&
        compareFirmwareVersions(firmware, candidate.version_max?.toString() ?: '255.255') <= 0
    } as Map
    if (firmwareMatch != null) {
      return firmwareMatch
    }
  }
  return devices[0] as Map
}

private Integer compareFirmwareVersions(String left, String right) {
  List<Integer> leftParts = versionParts(left)
  List<Integer> rightParts = versionParts(right)
  Integer partCount = Math.max(leftParts.size(), rightParts.size())
  for (Integer index = 0; index < partCount; index++) {
    Integer leftPart = index < leftParts.size() ? leftParts[index] : 0
    Integer rightPart = index < rightParts.size() ? rightParts[index] : 0
    if (leftPart != rightPart) {
      return leftPart <=> rightPart
    }
  }
  return 0
}

private List<Integer> versionParts(String version) {
  List<Integer> parts = []
  (version ?: '').split(/[^0-9]+/).each { String value ->
    if (value) {
      parts.add(value.toInteger())
    }
  }
  return parts.isEmpty() ? [0] : parts
}

private Map parseAsyncJsonObject(AsyncResponse response) {
  String payload = response?.getData()?.toString()
  if (!payload) {
    return [:]
  }
  try {
    Object parsed = parseJson(payload)
    return parsed instanceof Map ? parsed as Map : [:]
  } catch (Exception exception) {
    logWarn("Unable to parse external association metadata: ${exception.message}")
    return [:]
  }
}

private Boolean isAsyncHttpFailure(AsyncResponse response) {
  Integer status = response?.getStatus()
  return response == null || response.hasError() || status == null || status < 200 || status >= 300
}

private String asyncHttpFailureDescription(AsyncResponse response) {
  Integer status = response?.getStatus()
  String error = response?.getErrorMessage()
  if (error) {
    return ": ${error}"
  }
  return status != null ? " (HTTP ${status})" : ''
}

private String normalizeExternalGroupName(Integer group, String label, String description) {
  String normalized = label?.trim() ?: ''
  normalized = normalized.replaceFirst("(?i)^association\\s+group\\s+${group}\\s*[-:–—]?\\s*", '').trim()
  normalized = normalized.replaceFirst("(?i)^group\\s+${group}\\s*[-:–—]?\\s*", '').trim()
  if (normalized) {
    return normalized
  }

  java.util.regex.Matcher commandClassMatch = description =~ /(?i)uses?\s+(?:the\s+)?([a-z][a-z ]+?)\s+command\s+class/
  if (commandClassMatch.find()) {
    String commandClass = commandClassMatch.group(1)?.trim()
    return commandClass ? commandClass.split(/\s+/).collect { String word -> word.capitalize() }.join(' ') : ''
  }
  return ''
}

private String cleanExternalText(Object value) {
  String text = value?.toString() ?: ''
  text = text.replaceAll(/(?i)<br\s*\/?\s*>/, '; ')
  text = text.replaceAll(/<[^>]+>/, ' ')
  text = text.replace('&nbsp;', ' ').replace('&amp;', '&').replace('&quot;', '"').replace('&#39;', "'")
  text = text.replaceAll(/\s+/, ' ').trim()
  return text.size() > 500 ? "${text.take(497)}..." : text
}

private void updateAssociationMetadataSourceEvent() {
  Integer groupCount = getDiscoveredAssociationGroupCount()
  Integer agiCount = groupCount - missingAgiGroupNames().size()
  Map metadataByGroup = (state.associationGroupMetadata ?: [:]) as Map
  Integer externalCount = metadataByGroup.values().count { Object value ->
    Map metadata = value as Map
    return metadata?.source?.toString() == 'OpenSmartHouse'
  }
  String outcome = state.externalMetadataLookupOutcome?.toString()
  String source
  if (groupCount > 0 && agiCount == groupCount) {
    source = 'Device AGI'
  } else if (externalCount > 0) {
    source = "Device AGI (${agiCount}/${groupCount}) + OpenSmartHouse (${externalCount} fallback)"
  } else if (outcome) {
    source = "Device AGI (${agiCount}/${groupCount}); ${outcome}"
  } else {
    source = "Device AGI (${agiCount}/${groupCount}); no external metadata"
  }
  sendEvent(name: 'associationMetadataSource', value: source)
}

// =============================================================================
// Configuration parsing and display formatting
// =============================================================================

Map readDesiredAssociationsFromSettings(Integer groupCount) {
  Map<String, List<Integer>> associations = [:]
  List<String> errors = []
  for (Integer group = MIN_ASSOCIATION_GROUP; group <= groupCount; group++) {
    String settingName = associationSettingName(group)
    Object rawValue = settings[settingName]
    String value = rawValue?.toString()?.trim() ?: ''
    List<Integer> nodes = []
    if (value) {
      value.split(/[,\s]+/).each { String token ->
        Integer node = parseHexNodeId(token)
        if (node == null) {
          errors.add("Group ${group} has invalid hexadecimal node ID '${token}' (valid classic Z-Wave nodes are 01-E8)")
        } else {
          nodes.add(node)
        }
      }
    }
    associations[group.toString()] = nodes.unique().sort()
  }

  return [associations: associations, errors: errors]
}

Map<String, List<Integer>> calculatePendingTargets(Map desiredAssociations, Map currentAssociations) {
  Map<String, List<Integer>> desired = copyAssociationMap(desiredAssociations)
  Map<String, List<Integer>> current = copyAssociationMap(currentAssociations)
  Map<String, List<Integer>> pending = [:]
  desired.each { String group, List<Integer> desiredNodes ->
    List<Integer> currentNodes = current[group] ?: []
    if (desiredNodes != currentNodes) {
      pending[group] = desiredNodes
    }
  }
  return pending
}

Integer parseHexNodeId(String token) {
  String cleaned = token?.trim()?.replaceFirst('(?i)^0x', '')
  if (!cleaned || !(cleaned ==~ /(?i)^[0-9a-f]{1,4}$/)) {
    return null
  }

  Integer node = Integer.parseInt(cleaned, 16)
  return node >= 1 && node <= MAX_CLASSIC_ZWAVE_NODE_ID ? node : null
}

String formatAssociationMap(Map associations, Boolean includeEmptyGroups = false) {
  if (associations == null || associations.isEmpty()) {
    return 'None'
  }

  List<String> groupKeys = associations.keySet().collect { Object key -> key.toString() }
  groupKeys.sort { String left, String right -> left.toInteger() <=> right.toInteger() }

  List<String> formatted = []
  groupKeys.each { String groupKey ->
    List<Integer> nodes = ((associations[groupKey] ?: []) as List).collect { Object node -> node as Integer }.unique().sort()
    if (includeEmptyGroups || !nodes.isEmpty()) {
      formatted.add("Group ${groupKey}: ${formatNodeList(nodes)}")
    }
  }
  return formatted.isEmpty() ? 'None' : formatted.join('; ')
}

String formatPendingAssociations(Map associations, Map currentAssociations) {
  if (associations == null || associations.isEmpty()) {
    return 'None'
  }

  Map<String, List<Integer>> current = copyAssociationMap(currentAssociations)
  List<String> groupKeys = associations.keySet().collect { Object key -> key.toString() }
  groupKeys.sort { String left, String right -> left.toInteger() <=> right.toInteger() }
  return groupKeys.collect { String groupKey ->
    List<Integer> desiredNodes = ((associations[groupKey] ?: []) as List).collect { Object node -> node as Integer }.unique().sort()
    List<Integer> actualNodes = current[groupKey] ?: []
    List<Integer> additions = desiredNodes.findAll { Integer node -> !actualNodes.contains(node) }
    List<Integer> removals = actualNodes.findAll { Integer node -> !desiredNodes.contains(node) }
    List<String> changes = []
    if (!additions.isEmpty()) {
      changes.add("add ${formatNodeList(additions)}")
    }
    if (!removals.isEmpty()) {
      changes.add("remove ${formatNodeList(removals)}")
    }
    "Group ${groupKey}: ${changes.join(', ')}"
  }.join('; ')
}

String formatFailedAssociations(Map associations, Map reasons, Map currentAssociations = [:]) {
  if (associations == null || associations.isEmpty()) {
    return 'None'
  }

  Map<String, List<Integer>> current = copyAssociationMap(currentAssociations)
  List<String> groupKeys = associations.keySet().collect { Object key -> key.toString() }
  groupKeys.sort { String left, String right -> left.toInteger() <=> right.toInteger() }
  return groupKeys.collect { String groupKey ->
    List<Integer> nodes = ((associations[groupKey] ?: []) as List).collect { Object node -> node as Integer }.unique().sort()
    List<Integer> actualNodes = current[groupKey] ?: []
    String reason = reasons?.get(groupKey)?.toString()
    "Group ${groupKey}: wanted ${formatNodeList(nodes)}, found ${formatNodeList(actualNodes)}${reason ? " (${reason})" : ''}"
  }.join('; ')
}

String formatNodeSetting(List<Integer> nodes) {
  if (nodes == null || nodes.isEmpty()) {
    return ''
  }
  return nodes.collect { Integer node -> String.format('%02X', node) }.join(', ')
}

String formatNodeList(List<Integer> nodes) {
  if (nodes == null || nodes.isEmpty()) {
    return '(none)'
  }
  return nodes.collect { Integer node -> String.format('%02X', node) }.join(', ')
}

Integer getDiscoveredAssociationGroupCount() {
  Integer groupCount = state.supportedAssociationGroups as Integer
  return groupCount ?: 0
}

String associationSettingName(Integer group) {
  return "associationGroup${group}"
}

String associationGroupPreferenceTitle(Integer group) {
  Map metadataByGroup = state.associationGroupMetadata as Map
  Map groupMetadata = metadataByGroup?.get(group.toString()) as Map
  String name = groupMetadata?.name?.toString()?.trim()
  List<String> commands = ((groupMetadata?.commands ?: []) as List).collect { Object command -> command.toString() }
  List<String> details = []
  if (name) {
    details.add(name)
  }
  if (!commands.isEmpty()) {
    details.add(commands.take(3).join(', '))
  }
  return "Group ${group}${details.isEmpty() ? '' : ": ${details.join(' — ')}"}"
}

String associationGroupPreferenceDescription(Integer group) {
  Map<String, List<Integer>> current = copyAssociationMap(state.currentAssociations as Map)
  Map<String, Integer> maximumNodes = (state.maximumAssociationNodes ?: [:]) as Map<String, Integer>
  Map metadataByGroup = (state.associationGroupMetadata ?: [:]) as Map
  Map groupMetadata = metadataByGroup[group.toString()] as Map
  String purpose = groupMetadata?.description?.toString()?.trim()
  String currentNodes = formatNodeList(current[group.toString()] ?: [])
  String maximum = maximumNodes[group.toString()] != null ? maximumNodes[group.toString()].toString() : 'unknown'
  String warning = group == 1 ? ' WARNING: Group 1 is normally the hub lifeline; removing the hub node can stop device reports.' : ''
  String purposeText = purpose ? "Purpose: ${purpose}${purpose.endsWith('.') ? '' : '.'} " : ''
  return "${purposeText}Hex node IDs separated by commas. Blank removes every node from this group. Current: ${currentNodes}. Maximum: ${maximum}.${warning}"
}

String decodeZWaveText(Object rawText) {
  if (rawText == null) {
    return ''
  }
  if (rawText instanceof List) {
    byte[] bytes = ((List) rawText).collect { Object value -> (value as Integer).byteValue() } as byte[]
    return new String(bytes, 'UTF-8').replace('\u0000', '').trim()
  }
  return rawText.toString().replace('\u0000', '').trim()
}

List<String> describeAssociationCommands(List<Integer> commandBytes) {
  List<String> descriptions = []
  for (Integer index = 0; index + 1 < commandBytes.size(); index += 2) {
    Integer commandClass = commandBytes[index]
    Integer command = commandBytes[index + 1]
    String className = COMMAND_CLASS_NAMES[commandClass] ?: String.format('Command Class 0x%02X', commandClass)
    String commandName = COMMAND_NAMES[commandClass]?.get(command) ?: String.format('Command 0x%02X', command)
    descriptions.add("${className} ${commandName}")
  }
  return descriptions.unique()
}

private void updateAssociationGroupMetadata(Integer group, String key, Object value) {
  Map<String, Map> metadataByGroup = (state.associationGroupMetadata ?: [:]) as Map<String, Map>
  Map groupMetadata = (metadataByGroup[group.toString()] ?: [:]) as Map
  groupMetadata[key] = value
  metadataByGroup[group.toString()] = groupMetadata
  state.associationGroupMetadata = metadataByGroup
}

private Map<String, List<Integer>> copyAssociationMap(Map source) {
  Map<String, List<Integer>> copy = [:]
  source?.each { Object key, Object value ->
    copy[key.toString()] = ((value ?: []) as List).collect { Object node -> node as Integer }.unique().sort()
  }
  return copy
}

private Boolean hasPendingAssociations() {
  Map pending = state.pendingAssociations as Map
  return pending != null && !pending.isEmpty()
}

private Boolean hasActiveAssociationOperation() {
  return state.operationId != null
}

private Boolean isLongRangeDevice() {
  String dni = device.getDeviceNetworkId()?.replaceFirst('(?i)^0x', '')
  if (!dni || !(dni ==~ /(?i)^[0-9a-f]+$/)) {
    return false
  }
  return Integer.parseInt(dni, 16) > MAX_CLASSIC_ZWAVE_NODE_ID
}

private void failConfiguration(String message) {
  state.operationId = null
  state.pendingAssociations = [:]
  state.failedAssociations = [:]
  state.failureReasons = [:]
  sendEvent(name: 'associationStatus', value: 'invalid')
  sendEvent(name: 'pendingAssociations', value: 'None')
  sendEvent(name: 'failedAssociations', value: message)
  sendEvent(name: 'lastAssociationOperation', value: "${nowFormatted()} - invalid configuration")
  logError(message)
}

private void initializeAttributes() {
  if (device.currentValue('associationStatus') == null) {
    sendEvent(name: 'associationStatus', value: 'idle')
  }
  if (device.currentValue('currentAssociations') == null) {
    sendEvent(name: 'currentAssociations', value: 'Unknown; press Refresh')
  }
  if (device.currentValue('pendingAssociations') == null) {
    sendEvent(name: 'pendingAssociations', value: 'None')
  }
  if (device.currentValue('failedAssociations') == null) {
    sendEvent(name: 'failedAssociations', value: 'None')
  }
  if (device.currentValue('associationMetadataSource') == null) {
    sendEvent(name: 'associationMetadataSource', value: 'Unknown; press Refresh')
  }
}

// =============================================================================
// Z-WaveJS-safe command construction
// =============================================================================

private String associationSetCommand(Integer group, List<Integer> nodes) {
  return secureCommand(zwave.associationV2.associationSet(groupingIdentifier: group, nodeId: nodes))
}

private String associationRemoveCommand(Integer group, List<Integer> nodes) {
  return secureCommand(zwave.associationV2.associationRemove(groupingIdentifier: group, nodeId: nodes))
}

private String associationGetCommand(Integer group) {
  return secureCommand(zwave.associationV2.associationGet(groupingIdentifier: group))
}

private String associationGroupNameGetCommand(Integer group) {
  if (state.zwaveJsBackend == true) {
    // Hubitat's AGI command factory currently formats this command as null on
    // Z-WaveJS. Supplying the well-defined wire command as a non-null String
    // keeps Hubitat's security encapsulation overload unambiguous.
    return secureRawCommand(String.format('5901%02X', group))
  }
  return secureCommand(zwave.associationGrpInfoV1.associationGroupNameGet(groupingIdentifier: group))
}

private String associationGroupCommandListGetCommand(Integer group) {
  if (state.zwaveJsBackend == true) {
    // 59 05 = AGI Command List Get, followed by Allow Cache/Reserved = 00
    // and the association-group identifier.
    return secureRawCommand(String.format('590500%02X', group))
  }
  return secureCommand(zwave.associationGrpInfoV1.associationGroupCommandListGet(groupingIdentifier: group))
}

private String secureRawCommand(String command) {
  if (!command) {
    return null
  }
  return zwaveSecureEncap(command)
}

private String secureCommand(hubitat.zwave.Command command) {
  if (command == null) {
    logDebug('Skipping a Z-Wave command that is unavailable for this device/backend')
    return null
  }
  // Pass the typed command directly. The Z-WaveJS backend may not produce a
  // legacy formatted String, and Hubitat provides this Command overload so it
  // can select the correct security encapsulation itself.
  return zwaveSecureEncap(command)
}

private void sendZWaveCommands(List<String> commands) {
  List<String> validCommands = commands.findAll { String command -> command != null && !command.isEmpty() }
  if (validCommands.isEmpty()) {
    return
  }
  sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(validCommands, COMMAND_DELAY_MS), hubitat.device.Protocol.ZWAVE))
}

// =============================================================================
// Standalone logging helpers
// =============================================================================

void scheduleLogsOff() {
  if (settings.logEnable != false || settings.debugLogEnable != false || settings.traceLogEnable != false) {
    runIn(1800, 'logsOff', [overwrite: true])
  }
}

void logsOff() {
  logInfo('Disabling logging after 30 minutes')
  device.updateSetting('logEnable', [value: 'false', type: 'bool'])
  device.updateSetting('debugLogEnable', [value: 'false', type: 'bool'])
  device.updateSetting('traceLogEnable', [value: 'false', type: 'bool'])
}

void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    log.debug("${device.label ?: device.name}: ${message}")
  }
}

void logInfo(String message) {
  if (settings.logEnable != false) {
    log.info("${device.label ?: device.name}: ${message}")
  }
}

void logWarn(String message) {
  if (settings.logEnable != false) {
    log.warn("${device.label ?: device.name}: ${message}")
  }
}

void logError(String message) {
  if (settings.logEnable != false) {
    log.error("${device.label ?: device.name}: ${message}")
  }
}

void logTrace(String message) {
  if (settings.logEnable != false && settings.traceLogEnable != false) {
    log.trace("${device.label ?: device.name}: ${message}")
  }
}

String nowFormatted() {
  if (location.timeZone != null) {
    return new Date().format('yyyy-MM-dd h:mm:ss a', location.timeZone)
  }
  return new Date().format('yyyy-MM-dd h:mm:ss a')
}
