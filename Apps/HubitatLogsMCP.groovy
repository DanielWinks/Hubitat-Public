/*
 * Hubitat Logs MCP Server
 * MIT License
 * Copyright (c) 2026 Daniel Winks
 */

import groovy.json.JsonOutput
import groovy.transform.Field

@Field static final String APP_NAME = "Hubitat Logs MCP Server"
@Field static final String LOGS_URI = "http://127.0.0.1:8080/logs/past/json"
@Field static final String LAN_LOGS_URI = "http://192.168.1.4/logs/past/json"
@Field static final String MCP_TOOL_NAME = "hubitat_get_logs"
@Field static final String SUMMARY_TOOL_NAME = "hubitat_get_log_summary"
@Field static final String LIVE_TOOL_NAME = "hubitat_get_live_logs"
@Field static final String STREAM_CHILD_DNI = "hubitat-log-stream"
@Field static final int DEFAULT_PAGE_SIZE = 25
@Field static final int MAX_PAGE_SIZE = 50
@Field static final int MAX_RESPONSE_BYTES = 65000
@Field static final long LOG_CACHE_TTL_MS = 3000L
@Field static final long SEARCH_SNAPSHOT_TTL_MS = 60000L
@Field static final int MAX_SEARCH_SNAPSHOTS = 8
@Field static final int MAX_LIVE_LOG_ENTRIES = 100
@Field static final Map LOG_CACHE = [fetchedAt: 0L, entries: [], rawCount: 0]
@Field static final Map LOG_SEARCH_SNAPSHOTS = new LinkedHashMap()
@Field static final List LIVE_LOG_BUFFER = new ArrayList()
@Field static int SEARCH_SNAPSHOT_SEQUENCE = 0

definition(
    name: "Hubitat Logs MCP Server",
    namespace: "dwinks",
    author: "Daniel Winks",
    description: "Read Hubitat's recent logs through a read-only MCP tool.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    oauth: [displayName: APP_NAME, displayLink: ""],
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

mappings {
    path("/mcp") {
        action: [GET: "handleMcpGet", POST: "handleMcpPost"]
    }
}

// Lifecycle and configuration
void installed() {
    configure()
}

void updated() {
    configure()
}

void initialize() {
    configure()
}

void configure() {
    unschedule()
    unsubscribe()
    state.logStreamSubscriptionReady = false
    tryCreateAccessToken()
}

void tryCreateAccessToken() {
    if (state.accessToken == null) {
        try {
            createAccessToken()
        } catch (Exception e) {
            logWarn("OAuth is not enabled for this app yet. Enable OAuth to create the MCP URL.")
        }
    }
}

void uninstalled() {
    unschedule()
    unsubscribe()
    Object streamDevice = getChildDevice(STREAM_CHILD_DNI)
    if (streamDevice != null) {
        try {
            streamDevice.disconnectStream()
        } catch (Exception ignored) {
        }
        deleteChildDevice(STREAM_CHILD_DNI)
    }
}

// Configuration page and endpoint display
Map mainPage() {
    dynamicPage(name: "mainPage", title: APP_NAME, install: true, uninstall: true) {
        section("MCP endpoint") {
            if (state.accessToken) {
                paragraph("Local MCP URL: ${getFullLocalApiServerUrl()}/mcp?access_token=${state.accessToken}")
            } else {
                paragraph("Save this app, then reopen it to view the OAuth-protected MCP URL.")
            }
            paragraph("This server exposes historical log search, summaries, and a live log stream.")
            paragraph("This reads the same logs route as ${LAN_LOGS_URI} through Hubitat's internal loopback address.")
        }
        section("Logging") {
            input(name: "logLevel", type: "enum", title: "Log level", options: [
                "trace": "Trace", "debug": "Debug", "info": "Info",
                "warn": "Warn", "error": "Error", "off": "Off"
            ], defaultValue: "info", required: true)
        }
    }
}

// MCP HTTP transport
Map handleMcpGet() {
    return render(contentType: "application/json", data: JsonOutput.toJson([
        jsonrpc: "2.0",
        error: [code: -32000, message: "This MCP endpoint accepts POST requests only."],
        id: null
    ]), status: 405)
}

Map handleMcpPost() {
    Map message
    try {
        Object body = request.JSON
        if (!(body instanceof Map)) {
            return renderJsonRpcError(null, -32600, "Invalid Request: expected a JSON-RPC object.", 400)
        }
        message = (Map) body
    } catch (Exception e) {
        return renderJsonRpcError(null, -32700, "Parse error: request body must be valid JSON.", 400)
    }

    if (message.jsonrpc != "2.0" || !(message.method instanceof String)) {
        return renderJsonRpcError(message.id, -32600, "Invalid Request: expected JSON-RPC 2.0 with a method.", 400)
    }

    // MCP notifications have no id and must not receive a JSON-RPC response body.
    if (message.id == null) {
        return render(contentType: "application/json", data: "", status: 202)
    }

    Map result
    try {
        switch (message.method) {
            case "initialize":
                result = initializeResult(message)
                break
            case "ping":
                result = [:]
                break
            case "tools/list":
                result = [tools: [logsToolDefinition(), logSummaryToolDefinition(), liveLogsToolDefinition()]]
                break
            case "tools/call":
                result = dispatchTool(message.params)
                break
            default:
                return renderJsonRpcError(message.id, -32601, "Method not found: ${message.method}", 200)
        }
        return render(contentType: "application/json", data: JsonOutput.toJson([
            jsonrpc: "2.0", id: message.id, result: result
        ]), status: 200)
    } catch (Exception e) {
        logError("MCP request failed: ${e.class.simpleName}: ${e.message}")
        return renderJsonRpcError(message.id, -32603, "Internal error while handling MCP request.", 200)
    }
}

Map initializeResult(Map message) {
    Map params = message.params instanceof Map ? (Map) message.params : [:]
    String requestedVersion = params.protocolVersion?.toString()
    List<String> supportedVersions = ["2025-06-18", "2025-03-26", "2024-11-05"]
    String version = supportedVersions.contains(requestedVersion) ? requestedVersion : "2025-03-26"
    return [
        protocolVersion: version,
        capabilities: [tools: [listChanged: false]],
        serverInfo: [name: "hubitat-logs-mcp", version: "1.1.0"],
        instructions: "Use ${MCP_TOOL_NAME} for filtered historical log pages, ${SUMMARY_TOOL_NAME} for compact counts and recurring issues, or ${LIVE_TOOL_NAME} for the live stream. All tools are read-only."
    ]
}

Map logsToolDefinition() {
    return [
        name: MCP_TOOL_NAME,
        description: "Search historical Hubitat logs. Results are structured, newest-first, filtered before pagination, and use a short shared cache. Pass the returned cursor to continue through the same snapshot.",
        inputSchema: [
            type: "object",
            properties: [
                appId: [type: "integer", minimum: 1, description: "Filter to an app instance ID."],
                level: [type: "string", enum: ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"], description: "Filter by log level."],
                contains: [type: "string", maxLength: 120, description: "Case-insensitive substring to find in source or message."],
                since: [type: "string", description: "Inclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."],
                before: [type: "string", description: "Exclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."],
                maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Page size (default ${DEFAULT_PAGE_SIZE}, maximum ${MAX_PAGE_SIZE})."],
                cursor: [type: "string", description: "Opaque cursor returned by the previous page."]
            ],
            additionalProperties: false
        ]
    ]
}

Map logSummaryToolDefinition() {
    return [
        name: SUMMARY_TOOL_NAME,
        description: "Summarize matching historical logs with counts by severity and source, repeated errors, and the latest warnings/errors. Returns no full log list.",
        inputSchema: [
            type: "object",
            properties: [
                appId: [type: "integer", minimum: 1, description: "Optional app instance ID."],
                contains: [type: "string", maxLength: 120],
                since: [type: "string", description: "Inclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."],
                before: [type: "string", description: "Exclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."]
            ],
            additionalProperties: false
        ]
    ]
}

Map liveLogsToolDefinition() {
    return [
        name: LIVE_TOOL_NAME,
        description: "Start or maintain a live Hubitat log WebSocket stream and return a compact buffer of recent arrivals. The first call creates the app's child stream device if needed.",
        inputSchema: [
            type: "object",
            properties: [
                maxEntries: [type: "integer", minimum: 1, maximum: MAX_LIVE_LOG_ENTRIES, description: "Maximum entries to return (default 25)."],
                since: [type: "string", description: "Optional inclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."]
            ],
            additionalProperties: false
        ]
    ]
}

Map dispatchTool(Object rawParams) {
    Map params = rawParams instanceof Map ? (Map) rawParams : [:]
    Map arguments = params.arguments instanceof Map ? (Map) params.arguments : [:]
    switch (params.name) {
        case MCP_TOOL_NAME:
            return searchLogs(arguments)
        case SUMMARY_TOOL_NAME:
            return summarizeLogs(arguments)
        case LIVE_TOOL_NAME:
            return getLiveLogs(arguments)
        default:
            return mcpError("Unknown tool: ${params.name ?: "(missing)"}".toString())
    }
}

Map searchLogs(Map arguments) {
    Map validation = validateLogFilters(arguments, true)
    if (validation.error) { return mcpError(validation.error.toString()) }
    int pageSize = (Integer) validation.pageSize
    if (arguments.cursor) {
        return continueLogSearch(arguments.cursor.toString(), pageSize)
    }

    Map snapshot
    try {
        Map cache = getParsedLogSnapshot()
        List<Map> matches = filterLogEntries((List<Map>) cache.entries, validation.filters as Map)
        matches = matches.reverse()
        snapshot = createSearchSnapshot(matches, cache.fetchedAt as Long, cache.rawCount as Integer, cache.cacheHit as Boolean)
    } catch (Exception e) {
        logWarn("Historical log search failed: ${e.class.simpleName}")
        return mcpError("Could not load historical logs: ${e.message ?: e.class.simpleName}".toString())
    }
    return renderLogPage(snapshot, 0, pageSize, (Boolean) snapshot.cacheHit)
}

Map summarizeLogs(Map arguments) {
    Map validation = validateLogFilters(arguments, false)
    if (validation.error) { return mcpError(validation.error.toString()) }
    try {
        Map cache = getParsedLogSnapshot()
        List<Map> matches = filterLogEntries((List<Map>) cache.entries, validation.filters as Map)
        matches = matches.reverse()
        Map<String, Integer> byLevel = [:]
        Map<String, Integer> bySource = [:]
        Map<String, Integer> repeatedErrors = [:]
        List<Map> latestIssues = []
        for (Map entry : matches) {
            String level = entry.level?.toString() ?: "UNKNOWN"
            String source = entry.sourceName?.toString() ?: "Unknown source"
            byLevel.put(level, (byLevel.get(level) ?: 0) + 1)
            bySource.put(source, (bySource.get(source) ?: 0) + 1)
            if (level == "ERROR" || level == "WARN") {
                String message = entry.message?.toString() ?: ""
                repeatedErrors.put(message, (repeatedErrors.get(message) ?: 0) + 1)
                if (latestIssues.size() < 8) { latestIssues.add(entry) }
            }
        }
        List<Map> repeated = repeatedErrors.entrySet().toList().sort { a, b -> b.value <=> a.value }.take(8).collect { Map.Entry item ->
            [count: item.value, message: item.key]
        }
        List<Map> sources = bySource.entrySet().toList().sort { a, b -> b.value <=> a.value }.take(8).collect { Map.Entry item ->
            [source: item.key, count: item.value]
        }
        Map summary = [
            totalMatches: matches.size(),
            byLevel: byLevel,
            topSources: sources,
            repeatedIssues: repeated,
            latestWarningsAndErrors: latestIssues,
            cacheAgeMs: Math.max(0L, now() - (cache.fetchedAt as Long))
        ]
        return mcpJsonResult(summary)
    } catch (Exception e) {
        logWarn("Historical log summary failed: ${e.class.simpleName}")
        return mcpError("Could not summarize historical logs: ${e.message ?: e.class.simpleName}".toString())
    }
}

Map getLiveLogs(Map arguments) {
    int maxEntries = 25
    if (arguments.maxEntries != null) {
        try {
            maxEntries = Integer.parseInt(arguments.maxEntries.toString())
        } catch (NumberFormatException ignored) {
            return mcpError("maxEntries must be an integer from 1 to ${MAX_LIVE_LOG_ENTRIES}.")
        }
    }
    if (maxEntries < 1 || maxEntries > MAX_LIVE_LOG_ENTRIES) {
        return mcpError("maxEntries must be between 1 and ${MAX_LIVE_LOG_ENTRIES}.")
    }
    String since = arguments.since?.toString()
    if (since && !isLogTimestamp(since)) {
        return mcpError("since must use YYYY-MM-DD HH:mm:ss.SSS format.")
    }
    Object streamDevice
    try {
        streamDevice = ensureLiveStreamDevice()
        if (state.logStreamSubscriptionReady != true) {
            subscribe(streamDevice, "logEntry", "handleLiveLogEntry")
            state.logStreamSubscriptionReady = true
        }
        streamDevice.connectStream()
    } catch (Exception e) {
        return mcpError("Could not start the live log stream: ${e.message ?: e.class.simpleName}".toString())
    }
    List<Map> entries
    synchronized (LIVE_LOG_BUFFER) {
        entries = new ArrayList<Map>(LIVE_LOG_BUFFER)
    }
    if (since) { entries = entries.findAll { Map row -> row.timestamp?.toString() >= since } }
    entries = entries.reverse().take(maxEntries)
    Map data = [
        connectionStatus: streamDevice.currentValue("connectionStatus") ?: "connecting",
        bufferedEntries: entries.size(),
        entries: entries
    ]
    return mcpJsonResult(data)
}

Object ensureLiveStreamDevice() {
    Object streamDevice = getChildDevice(STREAM_CHILD_DNI)
    if (streamDevice != null) { return streamDevice }
    return addChildDevice("dwinks", "Hubitat Log Stream", STREAM_CHILD_DNI, [
        name: "Hubitat Log Stream",
        label: "Hubitat Log Stream",
        isComponent: true
    ])
}

void handleLiveLogEntry(Object event) {
    try {
        Object parsed = parseJson(event.value.toString())
        if (!(parsed instanceof Map)) { return }
        Map entry = (Map) parsed
        synchronized (LIVE_LOG_BUFFER) {
            LIVE_LOG_BUFFER.add(entry)
            while (LIVE_LOG_BUFFER.size() > MAX_LIVE_LOG_ENTRIES) {
                LIVE_LOG_BUFFER.remove(0)
            }
        }
    } catch (Exception e) {
        logDebug("Ignored a malformed live log event (${e.class.simpleName}).")
    }
}

Map validateLogFilters(Map arguments, boolean includePaging) {
    Map filters = [:]
    if (arguments.appId != null) {
        try {
            Integer appId = Integer.valueOf(arguments.appId.toString())
            if (appId < 1) { return [error: "appId must be a positive integer."] }
            filters.appId = appId
        } catch (NumberFormatException ignored) {
            return [error: "appId must be a positive integer."]
        }
    }
    if (arguments.level != null) {
        String level = arguments.level.toString().trim().toUpperCase()
        if (!(level in ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"])) {
            return [error: "level must be ERROR, WARN, INFO, DEBUG, or TRACE."]
        }
        filters.level = level
    }
    String containsText = arguments.contains?.toString()?.trim()
    if (containsText) {
        if (containsText.length() > 120) { return [error: "contains must be 120 characters or fewer."] }
        filters.contains = containsText.toLowerCase()
    }
    for (String bound : ["since", "before"]) {
        String value = arguments.get(bound)?.toString()
        if (value) {
            if (!isLogTimestamp(value)) { return [error: "${bound} must use YYYY-MM-DD HH:mm:ss.SSS format."] }
            filters.put(bound, value)
        }
    }
    if (includePaging) {
        int pageSize = DEFAULT_PAGE_SIZE
        if (arguments.maxEntries != null) {
            try {
                pageSize = Integer.parseInt(arguments.maxEntries.toString())
            } catch (NumberFormatException ignored) {
                return [error: "maxEntries must be an integer from 1 to ${MAX_PAGE_SIZE}."]
            }
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            return [error: "maxEntries must be between 1 and ${MAX_PAGE_SIZE}."]
        }
        return [filters: filters, pageSize: pageSize]
    }
    return [filters: filters]
}

boolean isLogTimestamp(String value) {
    return value ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{1,3}/
}

Map getParsedLogSnapshot() {
    long currentTime = now()
    synchronized (LOG_CACHE) {
        long cachedAt = (LOG_CACHE.fetchedAt ?: 0L) as Long
        if (LOG_CACHE.entries instanceof List && !((List) LOG_CACHE.entries).isEmpty() && currentTime - cachedAt < LOG_CACHE_TTL_MS) {
            return [entries: new ArrayList<Map>(LOG_CACHE.entries as List<Map>), fetchedAt: cachedAt, rawCount: LOG_CACHE.rawCount, cacheHit: true]
        }
    }
    Object responseData
    httpGet([uri: LOGS_URI, timeout: 15, contentType: "application/json"]) { response ->
        if (response.status < 200 || response.status >= 300) {
            throw new RuntimeException("Logs endpoint returned HTTP ${response.status}.")
        }
        responseData = response.data
    }
    if (responseData instanceof String) { responseData = parseJson((String) responseData) }
    if (!(responseData instanceof List)) {
        throw new RuntimeException("Logs endpoint returned an unexpected JSON shape.")
    }
    List<Map> parsedEntries = []
    for (Object row : (List) responseData) {
        Map entry = parseHistoricalLogRow(row)
        if (entry != null) { parsedEntries.add(entry) }
    }
    long fetchedAt = now()
    synchronized (LOG_CACHE) {
        LOG_CACHE.fetchedAt = fetchedAt
        LOG_CACHE.entries = parsedEntries
        LOG_CACHE.rawCount = ((List) responseData).size()
    }
    return [entries: parsedEntries, fetchedAt: fetchedAt, rawCount: ((List) responseData).size(), cacheHit: false]
}

Map parseHistoricalLogRow(Object rawRow) {
    String raw = rawRow?.toString() ?: ""
    String[] columns = raw.split(/\t/, 3)
    if (columns.length < 3) {
        return [timestamp: "", level: "UNKNOWN", sourceType: "", sourceId: "", sourceName: "", message: compactLogText(raw, 1400)]
    }
    String[] source = columns[2].split(/\|/, 4)
    String sourceType = source.length > 0 ? source[0].trim() : ""
    String sourceId = source.length > 1 ? source[1].trim() : ""
    String sourceName = source.length > 2 ? source[2] : ""
    String message = source.length > 3 ? source[3] : columns[2]
    return [
        timestamp: columns[0].trim(),
        level: columns[1].trim().toUpperCase(),
        sourceType: sourceType,
        sourceId: sourceId,
        sourceName: compactLogText(sourceName, 120),
        message: compactLogText(message, 1400)
    ]
}

String compactLogText(String value, int maxLength) {
    String compact = value.replaceAll(/<[^>]*>/, " ")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'").replace("&amp;", "&")
        .replaceAll(/[\r\n\t]+/, " ").replaceAll(/ {2,}/, " ").trim()
    return compact.length() > maxLength ? compact.substring(0, maxLength) : compact
}

List<Map> filterLogEntries(List<Map> entries, Map filters) {
    return entries.findAll { Map entry ->
        if (filters.appId != null && !(entry.sourceType == "app" && entry.sourceId == filters.appId.toString())) { return false }
        if (filters.level != null && entry.level != filters.level) { return false }
        if (filters.since != null && entry.timestamp < filters.since) { return false }
        if (filters.before != null && entry.timestamp >= filters.before) { return false }
        if (filters.contains != null) {
            String searchable = "${entry.sourceName} ${entry.message}".toLowerCase()
            if (!searchable.contains(filters.contains.toString())) { return false }
        }
        return true
    }
}

Map createSearchSnapshot(List<Map> entries, long fetchedAt, int rawCount, boolean cacheHit) {
    String snapshotId
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        SEARCH_SNAPSHOT_SEQUENCE++
        snapshotId = "${now()}-${SEARCH_SNAPSHOT_SEQUENCE}"
        LOG_SEARCH_SNAPSHOTS.put(snapshotId, [entries: entries, createdAt: now(), fetchedAt: fetchedAt, rawCount: rawCount, cacheHit: cacheHit])
        expireSearchSnapshots()
    }
    return [id: snapshotId, entries: entries, fetchedAt: fetchedAt, rawCount: rawCount, cacheHit: cacheHit]
}

void expireSearchSnapshots() {
    List<String> expired = LOG_SEARCH_SNAPSHOTS.entrySet().findAll { Map.Entry item ->
        now() - ((item.value.createdAt ?: 0L) as Long) > SEARCH_SNAPSHOT_TTL_MS
    }.collect { Map.Entry item -> item.key.toString() }
    expired.each { String key -> LOG_SEARCH_SNAPSHOTS.remove(key) }
    while (LOG_SEARCH_SNAPSHOTS.size() > MAX_SEARCH_SNAPSHOTS) {
        Object firstKey = LOG_SEARCH_SNAPSHOTS.keySet().iterator().next()
        LOG_SEARCH_SNAPSHOTS.remove(firstKey)
    }
}

Map continueLogSearch(String cursor, int pageSize) {
    int separator = cursor.lastIndexOf(":")
    if (separator < 1) { return mcpError("Invalid log cursor. Start a new search without a cursor.") }
    String snapshotId = cursor.substring(0, separator)
    int offset
    try {
        offset = Integer.parseInt(cursor.substring(separator + 1))
    } catch (NumberFormatException ignored) {
        return mcpError("Invalid log cursor. Start a new search without a cursor.")
    }
    Map snapshot
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        expireSearchSnapshots()
        snapshot = LOG_SEARCH_SNAPSHOTS.get(snapshotId) as Map
    }
    if (snapshot == null) { return mcpError("This log cursor has expired. Start a new search.") }
    return renderLogPage(snapshot + [id: snapshotId], offset, pageSize, (Boolean) (snapshot.cacheHit ?: false))
}

Map renderLogPage(Map snapshot, int offset, int pageSize, boolean cacheHit) {
    List<Map> entries = snapshot.entries as List<Map>
    if (offset < 0 || offset >= entries.size()) { return mcpError("The log cursor points past the end of this result set.") }
    int end = Math.min(entries.size(), offset + pageSize)
    List<Map> page = entries.subList(offset, end)
    String nextCursor = end < entries.size() ? "${snapshot.id}:${end}" : null
    Map data = [
        totalMatches: entries.size(),
        returned: page.size(),
        newestFirst: true,
        cacheHit: cacheHit,
        cacheAgeMs: Math.max(0L, now() - (snapshot.fetchedAt as Long)),
        rawLogCount: snapshot.rawCount,
        entries: page,
        nextCursor: nextCursor
    ]
    return mcpJsonResult(data)
}

Map mcpJsonResult(Map data) {
    String json = JsonOutput.toJson(data)
    if (json.getBytes("UTF-8").length > MAX_RESPONSE_BYTES) {
        return mcpError("The filtered result is still too large. Narrow the filters or reduce maxEntries.")
    }
    return [content: [[type: "text", text: json]], isError: false]
}

Map mcpError(String message) {
    return [content: [[type: "text", text: message]], isError: true]
}

Map renderJsonRpcError(Object id, int code, String message, int status) {
    return render(contentType: "application/json", data: JsonOutput.toJson([
        jsonrpc: "2.0", id: id, error: [code: code, message: message]
    ]), status: status)
}

// Local level-based logging
void logTrace(String message) { writeLog("trace", message) }
void logDebug(String message) { writeLog("debug", message) }
void logInfo(String message) { writeLog("info", message) }
void logWarn(String message) { writeLog("warn", message) }
void logError(String message) { writeLog("error", message) }

void writeLog(String level, String message) {
    List<String> levels = ["trace", "debug", "info", "warn", "error", "off"]
    String configured = settings.logLevel?.toString()?.toLowerCase()
    if (!levels.contains(configured)) { configured = "info" }
    if (levels.indexOf(level) < levels.indexOf(configured)) { return }
    switch (level) {
        case "trace": log.trace(message); break
        case "debug": log.debug(message); break
        case "info": log.info(message); break
        case "warn": log.warn(message); break
        case "error": log.error(message); break
    }
}
