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

definition(name: 'ESPHome Device Helper', namespace: 'dwinks', author: 'Daniel Winks',
    category: 'Convenience', description: 'Discovers ESPHome API devices using native mDNS',
    iconUrl: '', iconX2Url: '', iconX3Url: '', installOnOpen: true,
    singleInstance: true, singleThreaded: false)

preferences {
    page(name: 'mainPage', install: true, uninstall: true)
}

Map mainPage() {
    ensureState()
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
        section('Logging', hideable: true) {
            input name: 'logLevel', type: 'enum', title: 'Overall logging level', options: levelOptions, defaultValue: overallLevel, submitOnChange: true
            input name: 'displayLogLevel', type: 'enum', title: 'In-app log level (X and above)', options: displayOptions, defaultValue: displayLevel, submitOnChange: true
            paragraph "<pre class='app-state-${app.id}-recentLogs' style='white-space:pre-wrap; font-size:12px; line-height:1.2;'>${escapeHtml(buildRecentLogPayload())}</pre>"
        }
    }
}

void configure() {
    ensureState()
    String newLogLevel = normalizeLogLevel(settings?.logLevel ?: (state.logLevel ?: 'debug'))
    String newDisplayLevel = normalizeLogLevel(settings?.displayLogLevel ?: (state.displayLogLevel ?: newLogLevel))
    if (state.displayLogLevel && state.displayLogLevel != newDisplayLevel) { pruneDisplayedLogs(newDisplayLevel) }
    state.logLevel = newLogLevel
    state.displayLogLevel = newDisplayLevel
    unsubscribe()
    subscribe(location, 'systemStart', 'systemStartHandler')
    startMdnsDiscovery()
}
void initialize() { configure() }

void onUninstalled() {
    stopDiscovery()
    unregisterMdnsListener()
}

void appButtonHandler(String buttonName) {
    if (buttonName == 'btnExtendDiscovery') { extendDiscovery(DISCOVERY_DURATION_SECONDS) }
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
            sendEvent(name: 'discoveryTable', value: 'updated')
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
    html.append("<style>.esphome-discovery{width:100%;border-collapse:collapse}.esphome-discovery th,.esphome-discovery td{padding:6px 8px;border:1px solid #ddd;text-align:left;white-space:nowrap}.esphome-discovery th{background:#f5f5f5}</style>")
    html.append("<div style='overflow-x:auto'><table class='esphome-discovery'><thead><tr><th>Name</th><th>Hostname</th><th>IP</th><th>Port</th><th>MAC</th><th>Version</th><th>Platform / Board</th><th>Network</th><th>API encryption</th><th>Last seen</th></tr></thead><tbody>")
    List<Map> rows = devices.values().collect { Object value -> value as Map }.sort { Map left, Map right -> (left.friendlyName ?: left.hostname).toString().toLowerCase() <=> (right.friendlyName ?: right.hostname).toString().toLowerCase() }
    rows.each { Map device ->
        String platformBoard = [device.platform, device.board].findAll { Object value -> value }.join(' / ')
        html.append('<tr>')
        html.append("<td>${escapeHtml(device.friendlyName)}</td><td>${escapeHtml(device.hostname)}</td><td>${escapeHtml(device.ipAddress)}</td><td>${escapeHtml(device.port)}</td><td>${escapeHtml(device.mac)}</td><td>${escapeHtml(device.version)}</td><td>${escapeHtml(platformBoard)}</td><td>${escapeHtml(device.network)}</td><td>${escapeHtml(device.apiEncryption ? 'Yes' : 'No')}</td><td>${escapeHtml(formatTimestamp(device.lastSeen))}</td>")
        html.append('</tr>')
    }
    html.append('</tbody></table></div>')
    return html.toString()
}

private String displayDiscoveryTable() {
    return "<span class='ssr-app-state-${app.id}-discoveryTable'><div id='discovery-table-wrapper'>${renderDiscoveryTable()}</div></span>"
}

private String normalizeLogLevel(Object value) {
    List<String> levels = ['trace', 'debug', 'info', 'warn', 'error', 'off']
    String level = value?.toString()?.toLowerCase() ?: 'debug'
    return levels.contains(level) ? level : 'debug'
}

private Integer logLevelPriority(String level) {
    return ['trace', 'debug', 'info', 'warn', 'error', 'off'].indexOf(normalizeLogLevel(level))
}

private Boolean shouldLogOverall(String level) {
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
