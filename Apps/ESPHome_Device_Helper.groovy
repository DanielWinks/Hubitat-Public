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

@Field static final String ESPHOME_MDNS_SERVICE = '_esphomelib._tcp'
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
            paragraph '<p>Discovery listens only for ESPHome\'s <code>_esphomelib._tcp</code> mDNS service.</p>'
            String timerText = isDiscoveryRunning() ? "<b>Discovery running:</b> ${remainingSeconds} seconds remaining" : '<b>Discovery is stopped.</b>'
            paragraph "<span class='ssr-app-state-${app.id}-discoveryTimer'>${timerText}</span>"
            input name: 'btnExtendDiscovery', type: 'button', title: 'Extend discovery (60 seconds)', submitOnChange: true
        }
        section('Discovered ESPHome devices') { paragraph displayDiscoveryTable() }
        section {
            paragraph '<small>Use the green add button in the discovery table to create a power-monitoring child device. Only plaintext ESPHome native API devices are supported.</small>'
        }
        if (state.pendingDeleteESPHome) {
            Map pending = (atomicState.discoveredESPHome ?: [:])[state.pendingDeleteESPHome] as Map
            section {
                paragraph "<b>Remove '${escapeHtml(pending?.friendlyName ?: state.pendingDeleteESPHome)}'?</b> This deletes its Hubitat child device."
                input name: 'btnConfirmRemoveESPHome', type: 'button', title: 'Yes, remove device', submitOnChange: true
                input name: 'btnCancelRemoveESPHome', type: 'button', title: 'Cancel', submitOnChange: true
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
        child.updateSetting('ipAddress', [value: record.ipAddress.toString(), type: 'text'])
        child.updateSetting('port', [value: (record.port ?: 6053).toString(), type: 'number'])
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
    if (buttonName?.startsWith('createESPHome|')) { createESPHomeDevice(buttonName.substring('createESPHome|'.length())) }
    if (buttonName?.startsWith('removeESPHome|')) { state.pendingDeleteESPHome = buttonName.substring('removeESPHome|'.length()) }
    if (buttonName == 'btnConfirmRemoveESPHome') {
        String selected = state.pendingDeleteESPHome?.toString()
        state.remove('pendingDeleteESPHome')
        removeESPHomeDevice(selected)
    }
    if (buttonName == 'btnCancelRemoveESPHome') { state.remove('pendingDeleteESPHome') }
}

private void createESPHomeDevice(String selected) {
    ensureState()
    Map record = selected ? ((atomicState.discoveredESPHome ?: [:]) as Map)[selected] as Map : null
    if (!record) { logWarn('Select a discovered ESPHome device before creating a child device'); return }
    if (!record.ipAddress) { logWarn("${record.friendlyName ?: record.hostname} has no IPv4 address"); return }
    if (isEncryptedApi(record.apiEncryption)) {
        logWarn("${record.friendlyName ?: record.hostname} advertises encrypted API; plaintext native API support is required")
        return
    }
    String dni = stableChildDni(record)
    String driverName = resolveESPHomeDriver(record)
    Map props = [label: record.friendlyName ?: record.hostname, name: record.friendlyName ?: record.hostname,
                 ipAddress: record.ipAddress, port: (record.port ?: 6053) as Integer]
    try {
        Object child = getChildDevices().find { Object candidate -> candidate.deviceNetworkId == dni }
        if (child) {
            child.updateSetting('ipAddress', [value: record.ipAddress.toString(), type: 'text'])
            child.updateSetting('port', [value: (record.port ?: 6053).toString(), type: 'number'])
            child.initialize()
            record.childDni = dni
            record.apiStatus = 'updated'
            logInfo("Updated ESPHome child device ${dni}")
        } else {
            child = addChildDevice('dwinks', driverName, dni, props)
            child.updateSetting('ipAddress', [value: record.ipAddress.toString(), type: 'text'])
            child.updateSetting('port', [value: (record.port ?: 6053).toString(), type: 'number'])
            child.initialize()
            record.childDni = dni
            record.childDeviceId = child?.id
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

private String resolveESPHomeDriver(Map record) {
    // Keep driver selection centralized so additional ESPHome device profiles can
    // be added without changing the discovery table or button handling.
    return 'ESPHome Power Monitoring Switch'
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

private Boolean isEncryptedApi(Object value) {
    return value != null && ['true', 'yes', '1', 'noise'].contains(value.toString().trim().toLowerCase())
}

private String stableChildDni(Map record) {
    String source = (record.mac ?: record.hostname ?: record.ipAddress ?: 'unknown').toString()
    String normalized = source.replaceAll('[^A-Za-z0-9]', '').toUpperCase()
    return "esphome-${normalized ?: Math.abs(source.hashCode())}"
}

void systemStartHandler(Map event) { startMdnsDiscovery() }

void startMdnsDiscovery() {
    try {
        registerMDNSListener(ESPHOME_MDNS_SERVICE)
        logDebug("Registered mDNS listener: ${ESPHOME_MDNS_SERVICE}")
    } catch (Exception exception) {
        logWarn("Could not register ${ESPHOME_MDNS_SERVICE}: ${exception.message}")
    }
}

void unregisterMdnsListener() {
    try { unregisterMDNSListener(ESPHOME_MDNS_SERVICE) }
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
        List<Map> entries = (getMDNSEntries(ESPHOME_MDNS_SERVICE) ?: []) as List<Map>
        Integer beforeCount = (atomicState.discoveredESPHome as Map).size()
        entries.each { Map entry -> mergeMdnsEntry(entry) }
        Integer afterCount = (atomicState.discoveredESPHome as Map).size()
        if (afterCount != beforeCount) {
            logInfo("Discovered ${afterCount - beforeCount} new ESPHome device(s); ${afterCount} total")
            app.sendEvent(name: 'discoveryTable', value: 'updated')
        }
        logTrace("Processed ${entries.size()} ${ESPHOME_MDNS_SERVICE} entries")
    } catch (Exception exception) {
        logWarn("Error processing ESPHome mDNS entries: ${exception.message}")
    }
    if (isDiscoveryRunning() && getRemainingDiscoverySeconds() > 0) { runIn(DISCOVERY_POLL_SECONDS, 'processMdnsDiscovery') }
}

private void mergeMdnsEntry(Map entry) {
    String hostname = cleanHostname(entry?.server ?: entry?.name ?: '')
    String ipAddress = extractIpv4(entry?.ip4Addresses ?: entry?.ipAddress)
    Integer port = parseInteger(entry?.port) ?: 6053
    Map properties = entry?.properties instanceof Map ? (entry.properties as Map) : [:]
    if (entry?.txt instanceof Map) { properties.putAll(entry.txt as Map) }
    String mac = firstText(entry?.mac, entry?.macAddress, properties.mac)
    String key = (mac ?: hostname ?: ipAddress).toLowerCase()
    if (!key) { return }
    Map existing = ((atomicState.discoveredESPHome as Map)[key] ?: [:]) as Map
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
    discovered[key] = updated
    atomicState.discoveredESPHome = discovered
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
    if (!(atomicState.recentLogs instanceof List)) { atomicState.recentLogs = [] }
    if (atomicState.discoveryRunning == null) { atomicState.discoveryRunning = false }
}

private String renderDiscoveryTable() {
    Map devices = (atomicState.discoveredESPHome ?: [:]) as Map
    if (devices.isEmpty()) { return '<p>No ESPHome devices discovered yet. Discovery is running...</p>' }
    StringBuilder html = new StringBuilder()
    html.append("<style>.esphome-discovery{width:100%;border-collapse:collapse}.esphome-discovery th,.esphome-discovery td{padding:6px 8px;border:1px solid #ddd;text-align:left;white-space:nowrap;vertical-align:middle}.esphome-discovery th{background:#f5f5f5}.esphome-discovery th:first-child,.esphome-discovery td:first-child{text-align:center;width:42px;padding-left:4px;padding-right:4px}.esphome-discovery td:first-child .form-group{display:none}.esphome-discovery td:first-child .submitOnChange{line-height:20px;vertical-align:middle}.esphome-discovery .device-link a{color:#2196F3;text-decoration:none;font-weight:500}.esphome-discovery .device-link a:hover{text-decoration:underline}</style>")
    html.append("<div style='overflow-x:auto'><table class='esphome-discovery'><thead><tr><th>Add</th><th>Name</th><th>Hostname</th><th>IP</th><th>Port</th><th>MAC</th><th>Version</th><th>Platform / Board</th><th>Network</th><th>API encryption</th><th>Status</th><th>Last seen</th></tr></thead><tbody>")
    List<Map> rows = devices.values().collect { Object value -> value as Map }.sort { Map left, Map right -> (left.friendlyName ?: left.hostname).toString().toLowerCase() <=> (right.friendlyName ?: right.hostname).toString().toLowerCase() }
    rows.each { Map device ->
        String platformBoard = [device.platform, device.board].findAll { Object value -> value }.join(' / ')
        String addIcon = "<iconify-icon icon='material-symbols:add-circle-outline-rounded' style='font-size:20px;vertical-align:middle'></iconify-icon>"
        String actionCell
        if (isEncryptedApi(device.apiEncryption)) {
            actionCell = "<td title='Encrypted ESPHome API is not supported'><span style='color:#9E9E9E'>${addIcon}</span></td>"
        } else if (device.apiStatus in ['created', 'updated']) {
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
        html.append("${actionCell}<td class='device-link'>${safeName}</td><td>${escapeHtml(device.hostname)}</td><td>${escapeHtml(device.ipAddress)}</td><td>${escapeHtml(device.port)}</td><td>${escapeHtml(device.mac)}</td><td>${escapeHtml(device.version)}</td><td>${escapeHtml(platformBoard)}</td><td>${escapeHtml(device.network)}</td><td>${escapeHtml(isEncryptedApi(device.apiEncryption) ? 'Yes' : 'No')}</td><td>${escapeHtml(device.apiStatus ?: 'discovered')}</td><td>${escapeHtml(formatTimestamp(device.lastSeen))}</td>")
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
