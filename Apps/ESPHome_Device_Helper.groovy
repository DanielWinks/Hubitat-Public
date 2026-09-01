/**
 * MIT License
 * Copyright 2023 Daniel Winks (daniel.winks@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 **/

import groovy.transform.Field
import hubitat.scheduling.AsyncResponse

@Field static final String ESPHOME_MDNS_SERVICE = '_esphomelib._tcp'
@Field static final String ESPHOME_HTTP_MDNS_SERVICE = '_http._tcp'
@Field static final Integer DISCOVERY_DURATION_SECONDS = 60
@Field static final Integer DISCOVERY_POLL_SECONDS = 5

void installed() {
    logDebug('Installed')
    initialize()
}

void updated() {
    logDebug('Updated')
    configure()
}

void uninstalled() {
    logDebug('Uninstalled')
    unschedule()
    onUninstalled()
}

void logError(String message) {
    if (!shouldLogOverall('error')) { return }
    if (app) { log.error("${app.label ?: app.name}: ${message}") }
    if (shouldDisplay('error')) { appendLog('error', message) }
}

void logWarn(String message) {
    if (!shouldLogOverall('warn')) { return }
    if (app) { log.warn("${app.label ?: app.name}: ${message}") }
    if (shouldDisplay('warn')) { appendLog('warn', message) }
}

void logInfo(String message) {
    if (!shouldLogOverall('info')) { return }
    if (app) { log.info("${app.label ?: app.name}: ${message}") }
    if (shouldDisplay('info')) { appendLog('info', message) }
}

void logDebug(String message) {
    if (!shouldLogOverall('debug')) { return }
    if (app) { log.debug("${app.label ?: app.name}: ${message}") }
    if (shouldDisplay('debug')) { appendLog('debug', message) }
}

void logTrace(String message) {
    if (!shouldLogOverall('trace')) { return }
    if (app) { log.trace("${app.label ?: app.name}: ${message}") }
    if (shouldDisplay('trace')) { appendLog('trace', message) }
}

// Compatibility callbacks for schedules created by older app revisions. The
// current app uses level dropdowns and does not schedule automatic log shutdown.
void logsOff() { logInfo('Legacy logsOff callback ignored; logging is controlled by the level dropdown') }
void debugLogsOff() { logInfo('Legacy debugLogsOff callback ignored; logging is controlled by the level dropdown') }
void traceLogsOff() { logInfo('Legacy traceLogsOff callback ignored; logging is controlled by the level dropdown') }

definition(name: 'ESPHome Device Helper', namespace: 'dwinks', author: 'Daniel Winks',
    category: 'Convenience', description: 'Discovers ESPHome API devices using native mDNS',
    iconUrl: '', iconX2Url: '', iconX3Url: '', installOnOpen: true,
    singleInstance: true, singleThreaded: false)

preferences {
    page(name: 'mainPage', install: true, uninstall: true)
}

Map mainPage() {
    ensureState()
    applyPendingESPHomeLabel()
    syncProvisionedChildSettings()
    if (!isDiscoveryRunning()) { startDiscovery(false) }
    Integer remainingSeconds = getRemainingDiscoverySeconds()
    List<String> levelOrder = ['trace', 'debug', 'info', 'warn', 'error', 'off']
    Map<String, String> levelLabels = [trace: 'Trace', debug: 'Debug', info: 'Info', warn: 'Warn', error: 'Error', off: 'Off']
    String overallLevel = normalizeLogLevel(settings?.logLevel ?: 'debug')
    String storedDisplayLevel = normalizeLogLevel(settings?.displayLogLevel ?: (state.displayLogLevel ?: overallLevel))
    Integer overallIndex = levelOrder.indexOf(overallLevel)
    List<String> allowedDisplayLevels = levelOrder[overallIndex..-1]
    String displayLevel = allowedDisplayLevels.contains(storedDisplayLevel) ? storedDisplayLevel : overallLevel
    Map<String, String> levelOptions = levelOrder.collectEntries { String level -> [(levelLabels[level]): level] }
    Map<String, String> displayOptions = allowedDisplayLevels.collectEntries { String level -> [(levelLabels[level]): level] }
    if (storedDisplayLevel != displayLevel) {
        app.removeSetting('displayLogLevel')
        state.displayLogLevel = displayLevel
        pruneDisplayedLogs(displayLevel)
        state.pendingDisplayLevel = displayLevel
        runInMillis(200, 'applyPendingDisplayLevel')
    }
    // Do not use dynamicPage's periodic refresh here. A refresh re-enters this
    // method and would start a new scan as soon as the current scan expires.
    // Discovery results and the timer are refreshed through SSR events instead.
    return dynamicPage(name: 'mainPage', title: 'ESPHome Device Helper', refreshInterval: 0, install: true, uninstall: true) {
        section {
            paragraph '<p>Discovery listens on ESPHome API and HTTP mDNS records and verifies each candidate with the ESPHome web server.</p>'
            String timerText = isDiscoveryRunning() ? "<b>Discovery running:</b> ${remainingSeconds} seconds remaining" : '<b>Discovery is stopped.</b>'
            paragraph "<span class='ssr-app-state-${app.id}-discoveryTimer'>${timerText}</span>"
            input name: 'btnExtendDiscovery', type: 'button', title: 'Extend discovery (60 seconds)', submitOnChange: true
        }
        section('Discovered ESPHome devices') { paragraph displayDiscoveryTable() }
        section {
            paragraph '<small>Use the green add button in the discovery table to create an ESPHome child device. Candidates are verified over HTTP before appearing here.</small>'
        }
        if (state.pendingCreateESPHome) {
            Map pendingCreate = (atomicState.discoveredESPHome ?: [:])[state.pendingCreateESPHome] as Map
            section {
                paragraph "<b>Create '${escapeHtml(pendingCreate?.friendlyName ?: state.pendingCreateESPHome)}'</b>"
                input name: 'espHomeDriverSelection', type: 'enum', title: 'Driver', options: availableESPHomeDrivers(), defaultValue: state.pendingESPHomeDriver ?: 'ESPHome Power Monitoring Switch', required: true, submitOnChange: true
                input name: 'btnConfirmCreateESPHome', type: 'button', title: 'Create device', submitOnChange: true
                input name: 'btnCancelCreateESPHome', type: 'button', title: 'Cancel', submitOnChange: true
            }
        }
        if (state.pendingDeleteESPHome) {
            Map pending = (atomicState.discoveredESPHome ?: [:])[state.pendingDeleteESPHome] as Map
            section {
                paragraph "<b>Remove '${escapeHtml(pending?.friendlyName ?: state.pendingDeleteESPHome)}'?</b> This deletes its Hubitat child device."
                input name: 'btnConfirmRemoveESPHome', type: 'button', title: 'Yes, remove device', submitOnChange: true
                input name: 'btnCancelRemoveESPHome', type: 'button', title: 'Cancel', submitOnChange: true
            }
        }
        if (state.pendingLabelEditESPHome) {
            Map pendingLabel = (atomicState.discoveredESPHome ?: [:])[state.pendingLabelEditESPHome] as Map
            section {
                input name: 'espHomeLabelValue', type: 'text', title: "Name for ${pendingLabel?.hostname ?: state.pendingLabelEditESPHome}", defaultValue: pendingLabel?.friendlyName ?: pendingLabel?.hostname, required: true, submitOnChange: true
                input name: 'btnSaveESPHomeLabel', type: 'button', title: 'Save name', submitOnChange: true
                input name: 'btnCancelESPHomeLabel', type: 'button', title: 'Cancel', submitOnChange: true
            }
        }
        section('Logging', hideable: true) {
            input name: 'logLevel', type: 'enum', title: 'Overall logging level', options: levelOptions, defaultValue: overallLevel, submitOnChange: true
            input name: 'displayLogLevel', type: 'enum', title: 'In-app log level (X and above)', options: displayOptions, defaultValue: displayLevel, submitOnChange: true
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap; font-size:12px; line-height:1.2;'>${escapeHtml(buildRecentLogPayload())}</pre>"
        }
    }
}

void configure() {
    ensureState()
    syncProvisionedChildSettings()
    // Cancel callbacks left behind by pre-level-dropdown versions.
    unschedule('logsOff')
    unschedule('debugLogsOff')
    unschedule('traceLogsOff')
    String newLogLevel = normalizeLogLevel(settings?.logLevel ?: (state.logLevel ?: 'debug'))
    String newDisplayLevel = normalizeLogLevel(settings?.displayLogLevel ?: (state.displayLogLevel ?: newLogLevel))
    if (state.displayLogLevel && state.displayLogLevel != newDisplayLevel) { pruneDisplayedLogs(newDisplayLevel) }
    state.logLevel = newLogLevel
    state.displayLogLevel = newDisplayLevel
    unsubscribe()
    subscribe(location, 'systemStart', 'systemStartHandler')
    startMdnsDiscovery()
}

private void syncProvisionedChildSettings() {
    Map records = (atomicState.discoveredESPHome ?: [:]) as Map
    records.each { String key, Map record ->
        String dni = record?.childDni?.toString()
        if (!dni || !record?.ipAddress) { return }
        Object child = getChildDevices().find { Object candidate -> candidate.deviceNetworkId == dni }
        if (!child) { return }
        String driverName = record.driverName?.toString() ?: child.typeName?.toString()
        configureChildConnection(child, record, driverName, "http://${location.hub.localIP}:39501", false)
        if (!child.currentValue('networkStatus') || child.currentValue('networkStatus') == 'offline') { child.initialize() }
    }
}
void initialize() { configure() }

void onUninstalled() {
    stopDiscovery()
    unregisterMdnsListener()
}

void appButtonHandler(String buttonName) {
    if (buttonName == 'btnExtendDiscovery') { extendDiscovery(DISCOVERY_DURATION_SECONDS) }
    if (buttonName?.startsWith('createESPHome|')) { state.pendingCreateESPHome = buttonName.substring('createESPHome|'.length()) }
    if (buttonName == 'btnConfirmCreateESPHome') {
        String selected = state.pendingCreateESPHome?.toString()
        String driver = settings?.espHomeDriverSelection?.toString() ?: 'ESPHome Power Monitoring Switch'
        state.remove('pendingCreateESPHome')
        state.remove('pendingESPHomeDriver')
        createESPHomeDevice(selected, driver)
    }
    if (buttonName == 'btnCancelCreateESPHome') { state.remove('pendingCreateESPHome') }
    if (buttonName?.startsWith('removeESPHome|')) { state.pendingDeleteESPHome = buttonName.substring('removeESPHome|'.length()) }
    if (buttonName == 'btnConfirmRemoveESPHome') {
        String selected = state.pendingDeleteESPHome?.toString()
        state.remove('pendingDeleteESPHome')
        removeESPHomeDevice(selected)
    }
    if (buttonName == 'btnCancelRemoveESPHome') { state.remove('pendingDeleteESPHome') }
    if (buttonName?.startsWith('editESPHome|')) {
        state.pendingLabelEditESPHome = buttonName.substring('editESPHome|'.length())
        app.removeSetting('espHomeLabelValue')
    }
    if (buttonName == 'btnSaveESPHomeLabel') { /* Applied on the following page render. */ }
    if (buttonName == 'btnCancelESPHomeLabel') {
        state.remove('pendingLabelEditESPHome')
        app.removeSetting('espHomeLabelValue')
    }
}

private void applyPendingESPHomeLabel() {
    String selected = state.pendingLabelEditESPHome?.toString()
    String label = settings?.espHomeLabelValue?.toString()?.trim()
    Map record = selected ? ((atomicState.discoveredESPHome ?: [:]) as Map)[selected] as Map : null
    if (!record || !label) { logWarn('Enter a name before saving'); return }
    record.friendlyName = label
    if (record.childDni) {
        Object child = getChildDevices().find { Object candidate -> candidate.deviceNetworkId == record.childDni.toString() }
        if (child) { child.setLabel(label) }
    }
    Map devices = new LinkedHashMap((atomicState.discoveredESPHome ?: [:]) as Map)
    devices[selected] = record
    atomicState.discoveredESPHome = devices
    state.remove('pendingLabelEditESPHome')
    app.removeSetting('espHomeLabelValue')
    logInfo("Updated ESPHome device name to ${label}")
    app.sendEvent(name: 'discoveryTable', value: 'updated')
}

private void createESPHomeDevice(String selected, String requestedDriver = 'ESPHome Power Monitoring Switch') {
    ensureState()
    Map record = selected ? ((atomicState.discoveredESPHome ?: [:]) as Map)[selected] as Map : null
    if (!record) { logWarn('Select a discovered ESPHome device before creating a child device'); return }
    if (!record.ipAddress) { logWarn("${record.friendlyName ?: record.hostname} has no IPv4 address"); return }
    String dni = stableChildDni(record)
    String driverName = resolveESPHomeDriver(record, requestedDriver)
    String callbackUrl = "http://${location.hub.localIP}:39501"
    Map props = [label: record.friendlyName ?: record.hostname, name: record.friendlyName ?: record.hostname,
                 data: [ipAddress: record.ipAddress, port: '80', hubitatCallbackUrl: callbackUrl]]
    try {
        Object child = getChildDevices().find { Object candidate -> candidate.deviceNetworkId == dni }
        if (!child && record.childDni) {
            // Adopt children created by older versions, which prefixed the MAC.
            child = getChildDevices().find { Object candidate -> candidate.deviceNetworkId == record.childDni.toString() }
            if (child) { child.setDeviceNetworkId(dni) }
        }
        if (child) {
            configureChildConnection(child, record, driverName, callbackUrl)
            record.childDni = dni
            record.childDeviceId = child.id
            record.driverName = driverName
            record.apiStatus = 'updated'
            logInfo("Updated ESPHome child device ${dni}")
        } else {
            child = addChildDevice('dwinks', driverName, dni, props)
            configureChildConnection(child, record, driverName, callbackUrl)
            record.childDni = dni
            record.childDeviceId = child?.id
            record.driverName = driverName
            record.apiStatus = 'created'
            logInfo("Created ESPHome power-monitoring child device ${dni} using ${driverName}")
        }
        Map devices = new LinkedHashMap((atomicState.discoveredESPHome ?: [:]) as Map)
        devices[selected] = record
        atomicState.discoveredESPHome = devices
        app.sendEvent(name: 'discoveryTable', value: 'updated')
    } catch (Exception exception) {
        record.apiStatus = 'error'
        record.lastError = exception.message?.toString()
        logError("Could not create ESPHome child device: ${exception.message}")
    }
}

private Map<String, String> availableESPHomeDrivers() {
    return [
        'ESPHome Power Monitoring Switch': 'Power Monitoring Switch',
        'ESPHome RATGDO Garage Door': 'RATGDO Garage Door'
    ]
}

private String resolveESPHomeDriver(Map record, String requestedDriver) {
    // Keep driver selection centralized so additional ESPHome device profiles can
    // be added without changing the discovery table or button handling.
    return availableESPHomeDrivers().containsKey(requestedDriver) ? requestedDriver : 'ESPHome Power Monitoring Switch'
}

private void configureChildConnection(Object child, Map record, String driverName, String callbackUrl, Boolean refreshAfter = true) {
    if (driverName == 'ESPHome RATGDO Garage Door') {
        child.updateDataValue('ipAddress', record.ipAddress.toString())
        child.updateDataValue('port', '80')
        child.updateDataValue('hubitatCallbackUrl', callbackUrl)
    } else {
        child.updateDataValue('ipAddress', record.ipAddress.toString())
        child.updateDataValue('port', '80')
        child.updateDataValue('hubitatCallbackUrl', callbackUrl)
    }
    child.initialize()
    // Seed the child with current ESPHome values immediately after creation.
    if (refreshAfter) { child.refresh() }
}

private void removeESPHomeDevice(String selected) {
    Map record = selected ? ((atomicState.discoveredESPHome ?: [:]) as Map)[selected] as Map : null
    String dni = record?.childDni?.toString()
    if (!record || !dni) { logWarn('No created ESPHome child device is associated with this discovery record'); return }
    try {
        deleteChildDevice(dni)
        record.remove('childDni')
        record.remove('childDeviceId')
        record.apiStatus = 'discovered'
        record.remove('lastError')
        Map devices = new LinkedHashMap((atomicState.discoveredESPHome ?: [:]) as Map)
        devices[selected] = record
        atomicState.discoveredESPHome = devices
        logInfo("Removed ESPHome child device ${dni}")
        app.sendEvent(name: 'discoveryTable', value: 'updated')
    } catch (Exception exception) {
        logError("Could not remove ESPHome child device ${dni}: ${exception.message}")
    }
}

private String stableChildDni(Map record) {
    String source = (record.mac ?: record.hostname ?: record.ipAddress ?: 'unknown').toString()
    String normalized = source.replaceAll('[^A-Za-z0-9]', '').toUpperCase()
    return normalized ?: "esphome-${Math.abs(source.hashCode())}"
}

void systemStartHandler(Map event) { startMdnsDiscovery() }

void startMdnsDiscovery() {
    try {
        registerMDNSListener(ESPHOME_MDNS_SERVICE)
        registerMDNSListener(ESPHOME_HTTP_MDNS_SERVICE)
        logDebug("Registered mDNS listener: ${ESPHOME_MDNS_SERVICE}")
    } catch (Exception exception) {
        logWarn("Could not register ${ESPHOME_MDNS_SERVICE}: ${exception.message}")
    }
}

void unregisterMdnsListener() {
    try {
        unregisterMDNSListener(ESPHOME_MDNS_SERVICE)
        unregisterMDNSListener(ESPHOME_HTTP_MDNS_SERVICE)
    }
    catch (Exception exception) { logTrace("Could not unregister ${ESPHOME_MDNS_SERVICE}: ${exception.message}") }
}

void startDiscovery(Boolean resetFound) {
    ensureState()
    if (resetFound) { atomicState.discoveredESPHome = [:] }
    atomicState.discoveryRunning = true
    atomicState.discoveryEndTime = now() + (DISCOVERY_DURATION_SECONDS * 1000L)
    startMdnsDiscovery()
    unschedule('stopDiscovery')
    unschedule('processMdnsDiscovery')
    unschedule('updateDiscoveryTimer')
    runIn(DISCOVERY_DURATION_SECONDS, 'stopDiscovery')
    runIn(1, 'processMdnsDiscovery')
    runIn(1, 'updateDiscoveryTimer')
    logDebug("Started ESPHome mDNS discovery for ${DISCOVERY_DURATION_SECONDS} seconds")
}

void extendDiscovery(Integer seconds) {
    Integer extension = Math.max(1, seconds ?: DISCOVERY_DURATION_SECONDS)
    Long currentEnd = atomicState.discoveryEndTime as Long
    Long newEnd = Math.max(currentEnd ?: now(), now()) + (extension * 1000L)
    atomicState.discoveryRunning = true
    atomicState.discoveryEndTime = newEnd
    startMdnsDiscovery()
    unschedule('stopDiscovery')
    unschedule('processMdnsDiscovery')
    unschedule('updateDiscoveryTimer')
    Integer remaining = getRemainingDiscoverySeconds()
    runIn(remaining, 'stopDiscovery')
    runIn(1, 'processMdnsDiscovery')
    runIn(1, 'updateDiscoveryTimer')
    logInfo("Extended ESPHome discovery; ${remaining} seconds remain")
}

void stopDiscovery() {
    atomicState.discoveryRunning = false
    atomicState.discoveryEndTime = null
    unschedule('stopDiscovery')
    unschedule('processMdnsDiscovery')
    unschedule('updateDiscoveryTimer')
    sendEvent(name: 'discoveryTimer', value: '0')
    logDebug('ESPHome mDNS discovery stopped; listener remains registered')
}

void updateDiscoveryTimer() {
    if (!isDiscoveryRunning()) { return }
    sendEvent(name: 'discoveryTimer', value: getRemainingDiscoverySeconds().toString())
    if (getRemainingDiscoverySeconds() > 0) { runIn(1, 'updateDiscoveryTimer') }
}

void processMdnsDiscovery() {
    if (!isDiscoveryRunning()) { return }
    try {
        [ESPHOME_MDNS_SERVICE, ESPHOME_HTTP_MDNS_SERVICE].each { String service ->
            List<Map> entries = (getMDNSEntries(service) ?: []) as List<Map>
            entries.each { Map entry ->
                logTrace("mDNS ${service} raw entry: ${entry}")
                if (service == ESPHOME_MDNS_SERVICE) {
                    // _esphomelib._tcp is the ESPHome native API service. Its
                    // port is normally 6053, so it cannot be HTTP-verified.
                    mergeMdnsEntry(entry)
                    app.sendEvent(name: 'discoveryTable', value: 'updated')
                } else {
                    verifyHttpMdnsEntry(entry)
                }
            }
            logTrace("Processed ${entries.size()} ${service} candidates")
        }
    } catch (Exception exception) {
        logWarn("Error processing ESPHome mDNS entries: ${exception.message}")
    }
    if (isDiscoveryRunning() && getRemainingDiscoverySeconds() > 0) { runIn(DISCOVERY_POLL_SECONDS, 'processMdnsDiscovery') }
}

private void mergeMdnsEntry(Map entry) {
    String hostname = cleanHostname(entry?.server ?: entry?.name ?: '')
    String ipAddress = extractIpv4(entry?.ip4Addresses ?: entry?.ipAddress)
    Integer port = parseInteger(entry?.port) ?: 80
    Map properties = mdnsProperties(entry)
    String mac = firstText(entry?.mac, entry?.macAddress, properties.mac, properties.macaddress)
    String key = (mac ?: hostname ?: ipAddress).toLowerCase()
    logTrace("mDNS merge: hostname=${hostname}, ip=${ipAddress}, port=${port}, mac=${mac}, version=${firstText(entry?.version, entry?.ver, properties.version)}, properties=${properties}")
    if (!key) { return }
    Map discoveredBefore = (atomicState.discoveredESPHome ?: [:]) as Map
    String existingKey = discoveredBefore.keySet().find { Object candidateKey ->
        Map candidate = discoveredBefore[candidateKey] as Map
        return candidateKey.toString().equalsIgnoreCase(key) ||
            (hostname && candidate?.hostname?.toString()?.equalsIgnoreCase(hostname)) ||
            (ipAddress && candidate?.ipAddress?.toString() == ipAddress)
    }?.toString()
    Map existing = (existingKey ? discoveredBefore[existingKey] : discoveredBefore[key] ?: [:]) as Map
    Map updated = new LinkedHashMap(existing)
    updated.id = key
    updated.hostname = hostname ?: existing.hostname ?: key
    updated.ipAddress = ipAddress ?: existing.ipAddress ?: ''
    updated.port = port
    updated.mac = mac ?: existing.mac ?: ''
    updated.friendlyName = firstText(entry?.friendly_name, entry?.friendlyName, properties.friendly_name, properties.friendlyName) ?: existing.friendlyName ?: updated.hostname
    updated.version = firstText(entry?.version, entry?.ver, properties.version) ?: existing.version ?: ''
    updated.platform = firstText(entry?.platform, properties.platform) ?: existing.platform ?: ''
    updated.board = firstText(entry?.board, properties.board) ?: existing.board ?: ''
    updated.network = firstText(entry?.network, properties.network) ?: existing.network ?: ''
    updated.apiEncryption = firstText(entry?.api_encryption, entry?.apiEncryption, properties.api_encryption, properties.apiEncryption) ?: existing.apiEncryption ?: ''
    updated.firstSeen = existing.firstSeen ?: now()
    updated.lastSeen = now()
    Map discovered = new LinkedHashMap((atomicState.discoveredESPHome ?: [:]) as Map)
    if (existingKey && existingKey != key) { discovered.remove(existingKey) }
    discovered[key] = updated
    atomicState.discoveredESPHome = discovered
}

private void verifyHttpMdnsEntry(Map entry) {
    String ipAddress = extractIpv4(entry?.ip4Addresses ?: entry?.ipAddress)
    Integer port = parseInteger(entry?.port) ?: 80
    String key = mdnsEntryKey(entry, ipAddress)
    logTrace("mDNS candidate: key=${key}, ip=${ipAddress}, port=${port}, properties=${mdnsProperties(entry)}")
    if (!key || !ipAddress) { return }
    Map pending = (atomicState.pendingHttpVerification ?: [:]) as Map
    if (pending[key]) { return }
    if (((atomicState.discoveredESPHome ?: [:]) as Map).containsKey(key)) {
        mergeMdnsEntry(entry)
        app.sendEvent(name: 'discoveryTable', value: 'updated')
        return
    }
    pending[key] = now()
    atomicState.pendingHttpVerification = pending
    try {
        asynchttpGet('verifyEspHomeHttpCallback', [uri: "http://${ipAddress}:${port}/", timeout: 3], [key: key, entry: entry])
    } catch (Exception exception) {
        pending.remove(key)
        atomicState.pendingHttpVerification = pending
        logTrace("HTTP verification failed for ${ipAddress}: ${exception.message}")
    }
}

void verifyEspHomeHttpCallback(AsyncResponse response, Map data = null) {
    String key = data?.key?.toString()
    Map pending = (atomicState.pendingHttpVerification ?: [:]) as Map
    pending.remove(key)
    atomicState.pendingHttpVerification = pending
    if (response?.hasError()) { return }
    String body = response?.getData()?.toString()?.toLowerCase() ?: ''
    if (!(body.contains('esphome') || body.contains('/events') || body.contains('web_server'))) {
        logTrace("Rejected non-ESPHome HTTP mDNS candidate ${key}")
        return
    }
    mergeMdnsEntry(data?.entry as Map)
    logInfo("Verified ESPHome device ${key} over HTTP")
    app.sendEvent(name: 'discoveryTable', value: 'updated')
}

private String mdnsEntryKey(Map entry, String ipAddress) {
    String hostname = cleanHostname(entry?.server ?: entry?.name ?: '')
    Map properties = mdnsProperties(entry)
    String mac = firstText(entry?.mac, entry?.macAddress, properties.mac, properties.macaddress)
    return (mac ?: hostname ?: ipAddress).toLowerCase()
}

/**
 * Hubitat has returned mDNS TXT records as both maps and collections of
 * key=value strings across platform releases. ESPHome publishes its identity
 * (including MAC and firmware version) in those TXT records.
 */
private Map mdnsProperties(Map entry) {
    Map properties = [:]
    addMdnsProperties(properties, entry)
    addMdnsProperties(properties, entry?.properties)
    addMdnsProperties(properties, entry?.txt)
    addMdnsProperties(properties, entry?.txtRecords)
    addMdnsProperties(properties, entry?.serviceProperties)
    return properties
}

private void addMdnsProperties(Map target, Object source) {
    if (source instanceof Map) {
        (source as Map).each { Object key, Object value ->
            String propertyName = key?.toString()?.trim()?.toLowerCase()
            if (propertyName) { target[propertyName] = value }
        }
        return
    }
    if (source instanceof Collection) {
        (source as Collection).each { Object item -> addMdnsProperties(target, item) }
        return
    }
    String text = source?.toString()?.trim()?.replaceFirst(/^\[/, '')?.replaceFirst(/\]$/, '') ?: ''
    Integer separator = text.indexOf('=')
    if (separator > 0) {
        String propertyName = text.substring(0, separator).trim().replaceAll(/^['"]|['"]$/, '').toLowerCase()
        String propertyValue = text.substring(separator + 1).trim().replaceAll(/^['"]|['"]$/, '')
        if (propertyName) { target[propertyName] = propertyValue }
    }
}

private String firstText(Object... candidates) {
    Object candidate = candidates.find { Object value -> value != null && value.toString().trim() && value.toString() != 'null' }
    return candidate ? candidate.toString().trim() : ''
}

private Integer parseInteger(Object value) {
    try { return value == null ? null : Integer.valueOf(value.toString()) }
    catch (Exception ignored) { return null }
}

private String extractIpv4(Object value) {
    if (value instanceof List) {
        Object address = (value as List).find { Object item -> item && item.toString().count('.') == 3 && !item.toString().contains(':') }
        return address ? address.toString().trim() : ''
    }
    String text = value?.toString()?.replaceAll(/[\[\]]/, '')?.trim() ?: ''
    return text.contains(':') ? '' : text
}

private String cleanHostname(String value) {
    return (value ?: '').toString().replaceFirst(/\.$/, '').replaceFirst(/\.local$/, '')
}

private Boolean isDiscoveryRunning() { return atomicState.discoveryRunning == true && getRemainingDiscoverySeconds() > 0 }

private Integer getRemainingDiscoverySeconds() {
    Long endTime = atomicState.discoveryEndTime as Long
    return endTime ? Math.max(0, ((endTime - now()) / 1000L) as Integer) : 0
}

private void ensureState() {
    if (!(atomicState.discoveredESPHome instanceof Map)) { atomicState.discoveredESPHome = [:] }
    if (!(atomicState.pendingHttpVerification instanceof Map)) { atomicState.pendingHttpVerification = [:] }
    if (!(atomicState.recentLogs instanceof List)) { atomicState.recentLogs = [] }
    if (atomicState.discoveryRunning == null) { atomicState.discoveryRunning = false }
}

private String renderDiscoveryTable() {
    Map devices = (atomicState.discoveredESPHome ?: [:]) as Map
    if (devices.isEmpty()) { return '<p>No ESPHome devices discovered yet. Discovery is running...</p>' }
    StringBuilder html = new StringBuilder()
    html.append("<style>.esphome-discovery{width:100%;border-collapse:collapse}.esphome-discovery th,.esphome-discovery td{box-sizing:border-box;padding:6px 8px;border:1px solid #ddd;text-align:left;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;vertical-align:middle}.esphome-discovery th{background:#f5f5f5}.esphome-discovery th:first-child,.esphome-discovery td:first-child{text-align:center;padding-left:4px;padding-right:4px}.esphome-discovery td:first-child .form-group{display:none}.esphome-discovery td:first-child .submitOnChange{line-height:20px;vertical-align:middle}.esphome-discovery .device-link a{color:#2196F3;text-decoration:none;font-weight:500}.esphome-discovery .device-link a:hover{text-decoration:underline}</style>")
    html.append("<div style='overflow-x:auto'><table class='esphome-discovery' style='table-layout:fixed'><colgroup><col style='width:5%'><col style='width:24.5%'><col style='width:24.5%'><col style='width:16%'><col style='width:10%'><col style='width:9%'><col style='width:11%'></colgroup><thead><tr><th>Add</th><th>Name</th><th>Hostname</th><th>IP</th><th>MAC</th><th>Version</th><th>Last seen</th></tr></thead><tbody>")
    List<Map> rows = devices.values().collect { Object value -> value as Map }.sort { Map left, Map right -> (left.friendlyName ?: left.hostname).toString().toLowerCase() <=> (right.friendlyName ?: right.hostname).toString().toLowerCase() }
    rows.each { Map device ->
        String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded' style='font-size:20px;vertical-align:middle'></iconify-icon>"
        String actionCell
        if (device.apiStatus in ['created', 'updated']) {
            String removeIcon = "<iconify-icon icon='material-symbols:delete-outline' style='font-size:20px;vertical-align:middle'></iconify-icon>"
            actionCell = "<td>${buttonLink("removeESPHome|${device.id}", removeIcon, '#F44336', '20px')}</td>"
        } else {
            actionCell = "<td>${buttonLink("createESPHome|${device.id}", addIcon, '#4CAF50', '20px')}</td>"
        }
        html.append('<tr>')
        String safeName = escapeHtml(device.friendlyName)
        if (device.childDeviceId) {
            safeName = "<a href='/device/edit/${escapeHtml(device.childDeviceId)}' target='_blank'>${safeName}</a>"
        }
        String editButton = ''
        if (device.childDeviceId) {
            String editIcon = "<iconify-icon icon='material-symbols:edit' style='font-size:14px;vertical-align:middle;margin-left:4px'></iconify-icon>"
            editButton = buttonLink("editESPHome|${device.id}", editIcon, '#424242', '14px')
        }
        html.append("${actionCell}<td class='device-link'>${safeName}${editButton}</td><td>${escapeHtml(device.hostname)}</td><td>${escapeHtml(device.ipAddress)}</td><td>${escapeHtml(device.mac)}</td><td>${escapeHtml(device.version)}</td><td>${escapeHtml(formatTimestamp(device.lastSeen))}</td>")
        html.append('</tr>')
    }
    html.append('</tbody></table></div>')
    return html.toString()
}

private String buttonLink(String buttonName, String linkText, String color = '#1A77C9', String font = '15px') {
    return "<div class='form-group'><input type='hidden' name='${buttonName}.type' value='button'></div>" +
        "<div style='display:inline-block'><div class='submitOnChange' onclick='buttonClick(this)' style='color:${color};cursor:pointer;font-size:${font}'>${linkText}</div></div>" +
        "<input type='hidden' name='settings[${buttonName}]' value=''>"
}

private String displayDiscoveryTable() {
    String iconifyScript = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
    return "${iconifyScript}<span class='ssr-app-state-${app.id}-discoveryTable'><div id='discovery-table-wrapper'>${renderDiscoveryTable()}</div></span>"
}

private String normalizeLogLevel(Object value) {
    List<String> levels = ['trace', 'debug', 'info', 'warn', 'error', 'off']
    String level = value?.toString()?.toLowerCase() ?: 'debug'
    return levels.contains(level) ? level : 'debug'
}

private Integer logLevelPriority(String level) {
    return ['trace', 'debug', 'info', 'warn', 'error', 'off'].indexOf(normalizeLogLevel(level))
}

Boolean shouldLogOverall(String level) {
    return logLevelPriority(level) >= logLevelPriority(settings?.logLevel ?: 'debug')
}

private Boolean shouldDisplay(String level) {
    return logLevelPriority(level) >= logLevelPriority(settings?.displayLogLevel ?: (state.displayLogLevel ?: settings?.logLevel ?: 'debug'))
}

private String buildRecentLogPayload() {
    List<String> logs = (atomicState.recentLogs ?: []) as List<String>
    String recent = logs ? logs.reverse().take(10).join('\n') : 'No logs yet.'
    return "Recent log lines (most recent first):\n${recent}"
}

private void appendLog(String level, String message) {
    String displayLevel = normalizeLogLevel(settings?.displayLogLevel ?: (state.displayLogLevel ?: 'debug'))
    if (logLevelPriority(level) < logLevelPriority(displayLevel)) { return }
    List<String> logs = new ArrayList((atomicState.recentLogs ?: []) as List<String>)
    logs.add("${new Date().format('yyyy-MM-dd HH:mm:ss')} - ${normalizeLogLevel(level).toUpperCase()}: ${message}")
    if (logs.size() > 300) { logs = logs[-300..-1] }
    atomicState.recentLogs = logs
    Long lastEvent = (atomicState.lastLogEventTimestamp ?: 0L) as Long
    if (now() - lastEvent >= 1000L) {
        app.sendEvent(name: 'recentLogs', value: buildRecentLogPayload())
        atomicState.lastLogEventTimestamp = now()
    }
}

private void pruneDisplayedLogs(String displayLevel) {
    List<String> logs = new ArrayList((atomicState.recentLogs ?: []) as List<String>)
    List<String> kept = logs.findAll { String entry ->
        java.util.regex.Matcher matcher = (entry =~ /(?i)-\s*(TRACE|DEBUG|INFO|WARN|ERROR):/)
        return !matcher.find() || logLevelPriority(matcher.group(1)) >= logLevelPriority(displayLevel)
    }
    atomicState.recentLogs = kept
    app.sendEvent(name: 'recentLogs', value: buildRecentLogPayload())
}

private void applyPendingDisplayLevel() {
    String pending = state.pendingDisplayLevel?.toString()
    if (pending) {
        app.updateSetting('displayLogLevel', [type: 'enum', value: pending])
        state.remove('pendingDisplayLevel')
    }
}

private String formatTimestamp(Object timestamp) { return timestamp ? new Date(timestamp as Long).format('yyyy-MM-dd HH:mm:ss') : '' }

private String escapeHtml(Object value) {
    String text = value?.toString() ?: ''
    return text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&#39;')
}

String processServerSideRender(Map event) {
    if (event?.name == 'discoveryTable') { return "<div id='discovery-table-wrapper'>${renderDiscoveryTable()}</div>" }
    if (event?.name == 'discoveryTimer') { return "${getRemainingDiscoverySeconds()} seconds" }
    return ''
}
