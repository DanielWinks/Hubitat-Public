/*
 * Hubitat Logs MCP Server
 * MIT License
 * Copyright (c) 2026 Daniel Winks
 */

import groovy.json.JsonOutput
import groovy.transform.Field
import java.text.SimpleDateFormat

@Field static final String APP_NAME = "Hubitat Logs MCP Server"
@Field static final String LOGS_URI = "http://127.0.0.1:8080/logs/past/json"
@Field static final String LAN_LOGS_URI = "http://192.168.1.4/logs/past/json"
@Field static final String MCP_TOOL_NAME = "hubitat_get_logs"
@Field static final String SUMMARY_TOOL_NAME = "hubitat_get_log_summary"
@Field static final String LIVE_TOOL_NAME = "hubitat_get_live_logs"
@Field static final String TIMELINE_TOOL_NAME = "hubitat_get_diagnostic_timeline"
@Field static final String DEVICE_DETAILS_TOOL_NAME = "hubitat_get_device_details"
@Field static final String DEVICE_DETAILS_URI = "http://127.0.0.1:8080/device/fullJson/"
@Field static final String DEVICE_COMMAND_TOOL_NAME = "hubitat_invoke_device_command"
@Field static final String DEVICE_COMMAND_URI = "http://127.0.0.1:8080/device/runmethod"
@Field static final String HUB_REBOOT_TOOL_NAME = "hubitat_reboot_hub"
@Field static final String HUB_REBOOT_URI = "http://127.0.0.1:8080/hub/reboot"
@Field static final String DEVICE_LIST_TOOL_NAME = "hubitat_list_device_inventory"
@Field static final String DEVICE_LIST_URI = "http://127.0.0.1:8080/hub2/devicesList"
@Field static final String APP_LIST_TOOL_NAME = "hubitat_list_app_inventory"
@Field static final String APP_LIST_URI = "http://127.0.0.1:8080/hub2/appsList"
@Field static final String LOG_DIAGNOSTICS_URI = "http://127.0.0.1:8080/logs/json"
@Field static final String APP_STATUS_URI = "http://127.0.0.1:8080/installedapp/statusJson/"
@Field static final String LOG_DIAGNOSTICS_TOOL_NAME = "hubitat_get_runtime_diagnostics"
@Field static final String APP_STATUS_TOOL_NAME = "hubitat_get_app_diagnostics"
@Field static final List<String> LOG_DIAGNOSTIC_SECTIONS = ["summary", "appStats", "deviceStats", "jobs", "runningJobs"]
@Field static final List<String> APP_INVENTORY_SECTIONS = ["apps", "systemAppTypes", "userAppTypes", "appCollections"]
@Field static final List<String> DEVICE_DETAIL_SECTIONS = ["overview", "device", "data", "attributes", "state", "settings", "inputValues", "commands", "scheduledJobs", "appsUsing", "appsUsingForDialog", "childDevices", "parentApp", "dashboards", "dashboardTypes", "raw"]
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
    description: "Access Hubitat logs, diagnostics, device commands, and guarded hub controls through MCP.",
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
            paragraph("This server exposes logs, runtime and app diagnostics, device inventory, device details, device commands, and a guarded hub reboot action.")
            paragraph("The MCP URL permits device commands and a hub reboot. Share it only with trusted clients.")
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
                result = [tools: [logsToolDefinition(), logSummaryToolDefinition(), liveLogsToolDefinition(), timelineToolDefinition(), deviceDetailsToolDefinition(), deviceCommandToolDefinition(), hubRebootToolDefinition(), deviceInventoryToolDefinition(), appInventoryToolDefinition(), runtimeDiagnosticsToolDefinition(), appDiagnosticsToolDefinition()]]
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
        serverInfo: [name: "hubitat-logs-mcp", version: "1.2.0"],
        instructions: "Use ${MCP_TOOL_NAME} for filtered historical log pages, ${SUMMARY_TOOL_NAME} for compact counts and recurring issues, ${LOG_DIAGNOSTICS_TOOL_NAME} for Hubitat performance counters and jobs, ${APP_STATUS_TOOL_NAME} for app subscriptions and scheduled jobs, ${TIMELINE_TOOL_NAME} for a chronological log timeline, ${LIVE_TOOL_NAME} for the live stream, ${DEVICE_LIST_TOOL_NAME} to find devices, ${APP_LIST_TOOL_NAME} to find apps, and ${DEVICE_DETAILS_TOOL_NAME} for device diagnostics. ${DEVICE_COMMAND_TOOL_NAME} changes device state; invoke it only when the user explicitly requests the command. ${HUB_REBOOT_TOOL_NAME} immediately reboots the hub; invoke it only when the user explicitly requests a reboot and pass allowSensitive=true."
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
                deviceId: [type: "integer", minimum: 1, description: "Filter to a device ID."],
                sourceType: [type: "string", enum: ["app", "dev"], description: "Filter to app or device log sources."],
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
                deviceId: [type: "integer", minimum: 1, description: "Optional device ID."],
                sourceType: [type: "string", enum: ["app", "dev"], description: "Filter to app or device log sources."],
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
                since: [type: "string", description: "Optional inclusive timestamp, formatted YYYY-MM-DD HH:mm:ss.SSS."],
                appId: [type: "integer", minimum: 1, description: "Optional app instance ID."],
                deviceId: [type: "integer", minimum: 1, description: "Optional device ID."],
                sourceType: [type: "string", enum: ["app", "dev"], description: "Filter to app or device log sources."]
            ],
            additionalProperties: false
        ]
    ]
}

Map timelineToolDefinition() {
    return [
        name: TIMELINE_TOOL_NAME,
        description: "Return a chronological timeline of historical log entries for selected apps and devices. Timestamps are hub-local; this does not include device events.",
        inputSchema: [type: "object", properties: [
            since: [type: "string", description: "Inclusive hub-local timestamp YYYY-MM-DD HH:mm:ss.SSS."],
            before: [type: "string", description: "Exclusive hub-local timestamp YYYY-MM-DD HH:mm:ss.SSS."],
            appIds: [type: "array", items: [type: "integer", minimum: 1], maxItems: 50],
            deviceIds: [type: "array", items: [type: "integer", minimum: 1], maxItems: 50],
            maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE],
            cursor: [type: "string", description: "Continue a previous timeline snapshot; other filters are ignored."]
        ], additionalProperties: false]
    ]
}

Map deviceDetailsToolDefinition() {
    return [name: DEVICE_DETAILS_TOOL_NAME,
        description: "Read paginated sections of a device's detailed Hubitat JSON, including schedules, apps using it, settings, state, attributes, and commands. Values can contain sensitive data.",
        inputSchema: [type: "object", properties: [
            deviceId: [type: "integer", minimum: 1, description: "Hubitat device ID, required on every page."],
            section: [type: "string", enum: DEVICE_DETAIL_SECTIONS, description: "Detail section, default overview; repeat on every page."],
            maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Maximum top-level entries per page; default 25."],
            cursor: [type: "string", description: "Continue the same device and section snapshot."]
        ], required: ["deviceId"], additionalProperties: false]]
}

Map deviceCommandToolDefinition() {
    return [name: DEVICE_COMMAND_TOOL_NAME,
        description: "Invoke a command advertised by a device. This can change device state; use only when explicitly requested. Inspect the device's commands section first.",
        annotations: [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [
            deviceId: [type: "integer", minimum: 1, description: "Hubitat device ID."],
            command: [type: "string", minLength: 1, maxLength: 128, description: "Exact command name advertised by the device."],
            args: [type: "array", maxItems: 16, description: "Command arguments in driver order; defaults to an empty array."]
        ], required: ["deviceId", "command"], additionalProperties: false]]
}

Map hubRebootToolDefinition() {
    return [name: HUB_REBOOT_TOOL_NAME,
        description: "Immediately reboot this Hubitat hub. This interrupts automations and device communication. Use only when the user explicitly requests a reboot; set allowSensitive=true.",
        annotations: [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [
            allowSensitive: [type: "boolean", const: true, description: "Required authorization flag. Set true only after the user explicitly requests a hub reboot."]
        ], required: ["allowSensitive"], additionalProperties: false]]
}

Map deviceInventoryToolDefinition() {
    return [name: DEVICE_LIST_TOOL_NAME, description: "Search a paginated device inventory, including child devices. Results contain device names, IDs, types, rooms, and other metadata; current attribute values are omitted.",
        annotations: [readOnlyHint: true, destructiveHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [
            query: [type: "string", maxLength: 120, description: "Case-insensitive substring of device ID, name, type, network ID, or room."],
            room: [type: "string", maxLength: 120, description: "Case-insensitive exact room name."],
            deviceId: [type: "integer", minimum: 1, description: "Exact device ID."],
            maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Page size, default 25."],
            cursor: [type: "string", description: "Continue an inventory snapshot; filters are ignored."]
        ], additionalProperties: false]]
}

Map appInventoryToolDefinition() {
    return [name: APP_LIST_TOOL_NAME, description: "Search installed apps and child apps, or list system app types, user app types, and app collections.",
        annotations: [readOnlyHint: true, destructiveHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [
            section: [type: "string", enum: APP_INVENTORY_SECTIONS, description: "Inventory section; defaults to apps."],
            query: [type: "string", maxLength: 120, description: "Case-insensitive text match on ID, name, type, description, category, or menu."],
            appId: [type: "integer", minimum: 1, description: "Exact installed app instance ID; only valid for the apps section."],
            maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Page size, default 25."],
            cursor: [type: "string", description: "Continue an inventory snapshot; section and filters are ignored."]
        ], additionalProperties: false]]
}

Map runtimeDiagnosticsToolDefinition() {
    return [name: LOG_DIAGNOSTICS_TOOL_NAME,
        description: "Read Hubitat runtime diagnostics from /logs/json: app/device performance statistics, scheduled jobs, running jobs, and hub uptime counters. This may take several seconds on a busy hub.",
        annotations: [readOnlyHint: true, destructiveHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [section: [type: "string", enum: LOG_DIAGNOSTIC_SECTIONS, description: "Select summary, appStats, deviceStats, jobs, or runningJobs; default summary."], maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Page size for list sections, default 25."], cursor: [type: "string", description: "Continue a list section snapshot."]], additionalProperties: false]]
}

Map appDiagnosticsToolDefinition() {
    return [name: APP_STATUS_TOOL_NAME,
        description: "Inspect one installed app's status JSON for event subscriptions, scheduled jobs, app identity, and child apps. App settings and internal state are deliberately omitted because they may contain credentials.",
        annotations: [readOnlyHint: true, destructiveHint: false, openWorldHint: false],
        inputSchema: [type: "object", properties: [appId: [type: "integer", minimum: 1, description: "Installed Hubitat app instance ID."], maxEntries: [type: "integer", minimum: 1, maximum: MAX_PAGE_SIZE, description: "Page size, default 25."], cursor: [type: "string", description: "Continue a result snapshot."]], required: ["appId"], additionalProperties: false]]
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
        case TIMELINE_TOOL_NAME:
            return diagnosticTimeline(arguments)
        case DEVICE_DETAILS_TOOL_NAME:
            return getDeviceDetails(arguments)
        case DEVICE_COMMAND_TOOL_NAME:
            return invokeDeviceCommand(arguments)
        case HUB_REBOOT_TOOL_NAME:
            return rebootHub(arguments)
        case DEVICE_LIST_TOOL_NAME:
            return getDeviceInventory(arguments)
        case APP_LIST_TOOL_NAME:
            return getAppInventory(arguments)
        case LOG_DIAGNOSTICS_TOOL_NAME:
            return getRuntimeDiagnostics(arguments)
        case APP_STATUS_TOOL_NAME:
            return getAppDiagnostics(arguments)
        default:
            return mcpError("Unknown tool: ${params.name ?: "(missing)"}".toString())
    }
}

Map getAppInventory(Map arguments) {
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    if (arguments.cursor) { return continueAppInventory(arguments.cursor.toString(), pageSize) }
    String section = arguments.section == null ? "apps" : arguments.section.toString()
    if (!APP_INVENTORY_SECTIONS.contains(section)) { return mcpError("Unknown app inventory section.") }
    String appId = arguments.appId?.toString()
    if (arguments.appId != null && (!appId || !(appId ==~ /[1-9]\d*/) || appId.length() > 18)) { return mcpError("appId must be a positive integer.") }
    if (appId && section != "apps") { return mcpError("appId is only supported for installed apps.") }
    if (arguments.query != null && (!(arguments.query instanceof String) || arguments.query.toString().length() > 120)) { return mcpError("query must be a string of at most 120 characters.") }
    try {
        Object responseData
        httpGet([uri: APP_LIST_URI, timeout: 15, contentType: "application/json"]) { response ->
            if (response.status < 200 || response.status >= 300) { throw new RuntimeException("App inventory endpoint returned an error.") }
            responseData = response.data
        }
        if (responseData instanceof String) { responseData = parseJson((String) responseData) }
        if (!(responseData instanceof Map) || !(((Map) responseData).get(section) instanceof List)) { throw new RuntimeException("App inventory endpoint returned an unexpected response.") }
        List rows = (List) ((Map) responseData).get(section)
        List<Map> items = section == "apps" ? flattenAppInventory(rows) : rows.findAll { Object item -> item instanceof Map }.collect { Map item -> new LinkedHashMap(item) }
        String query = arguments.query?.toString()?.trim()?.toLowerCase()
        items = items.findAll { Map item ->
            if (appId && item.id?.toString() != appId) { return false }
            if (query && ![item.id, item.name, item.type, item.description, item.category, item.menu].any { Object value -> value?.toString()?.toLowerCase()?.contains(query) }) { return false }
            return true
        }
        Map snapshot = createSearchSnapshot(items, now(), items.size(), false, "appInventory|${section}".toString())
        return renderAppInventoryPage(snapshot, 0, pageSize)
    } catch (Exception e) {
        logWarn("App inventory lookup failed: ${e.class.simpleName}")
        return mcpError("Could not load app inventory from the hub's local app list endpoint.")
    }
}

Map getRuntimeDiagnostics(Map arguments) {
    String section = arguments.section == null ? "summary" : arguments.section.toString()
    if (!LOG_DIAGNOSTIC_SECTIONS.contains(section)) { return mcpError("Unknown runtime diagnostic section.") }
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    if (arguments.cursor) { return continueOperationalSnapshot(arguments.cursor.toString(), pageSize, "runtime|${section}".toString()) }
    try {
        Object responseData
        httpGet([uri: LOG_DIAGNOSTICS_URI, timeout: 25, contentType: "application/json"]) { response ->
            if (response.status < 200 || response.status >= 300) { throw new RuntimeException("Runtime diagnostics endpoint returned an error.") }
            responseData = response.data
        }
        if (responseData instanceof String) { responseData = parseJson((String) responseData) }
        if (!(responseData instanceof Map)) { throw new RuntimeException("Unexpected runtime diagnostics response.") }
        Map source = (Map) responseData
        if (section == "summary") {
            Map summary = [:]
            source.each { Object key, Object value ->
                if (!(value instanceof List) && !(value instanceof Map) && !isSensitiveField(key?.toString())) { summary.put(key.toString(), value) }
            }
            return mcpJsonResult([section: section, summary: summary, availableSections: LOG_DIAGNOSTIC_SECTIONS.findAll { String name -> source.get(name) instanceof List || source.get(name) instanceof Map }])
        }
        Object value = source.get(section)
        if (!(value instanceof List) && !(value instanceof Map)) { return mcpError("The hub response does not include ${section} data.") }
        List<Map> rows = operationalRows(value)
        Map snapshot = createSearchSnapshot(rows, now(), rows.size(), false, "runtime|${section}".toString())
        return renderOperationalPage(snapshot, 0, pageSize, section)
    } catch (Exception e) {
        logWarn("Runtime diagnostics lookup failed: ${e.class.simpleName}")
        return mcpError("Could not load runtime diagnostics from the hub's local /logs/json endpoint.")
    }
}

Map getAppDiagnostics(Map arguments) {
    String appId = arguments.appId?.toString()
    if (!appId || !(appId ==~ /[1-9]\d*/) || appId.length() > 18) { return mcpError("appId must be a positive integer.") }
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    String kind = "appDiagnostics|${appId}".toString()
    if (arguments.cursor) { return continueOperationalSnapshot(arguments.cursor.toString(), pageSize, kind) }
    try {
        Object responseData
        httpGet([uri: "${APP_STATUS_URI}${appId}".toString(), timeout: 15, contentType: "application/json"]) { response ->
            if (response.status < 200 || response.status >= 300) { throw new RuntimeException("App status endpoint returned an error.") }
            responseData = response.data
        }
        if (responseData instanceof String) { responseData = parseJson((String) responseData) }
        if (!(responseData instanceof Map)) { throw new RuntimeException("Unexpected app status response.") }
        Map source = (Map) responseData
        Map safe = [:]
        ["app", "installedApp", "eventSubscriptions", "scheduledJobs", "children"].each { String key ->
            if (source.containsKey(key)) { safe.put(key, redactSensitiveFields(source.get(key))) }
        }
        if (safe.isEmpty()) { throw new RuntimeException("App status response has no supported diagnostic fields.") }
        if (safe.app instanceof Map && ((Map) safe.app).id?.toString() != appId) { throw new RuntimeException("App status endpoint returned a different app.") }
        List<Map> rows = []
        safe.each { Object key, Object value ->
            if (key.toString() == "app" || key.toString() == "installedApp") {
                rows.add([section: key.toString(), value: value])
            } else if (value instanceof List || value instanceof Map) {
                rows.addAll(operationalRows(value, key.toString()))
            } else {
                rows.add([section: key.toString(), value: value])
            }
        }
        Map snapshot = createSearchSnapshot(rows, now(), rows.size(), false, kind)
        return renderOperationalPage(snapshot, 0, pageSize, "appDiagnostics")
    } catch (Exception e) {
        logWarn("App diagnostics lookup failed for app ${appId}: ${e.class.simpleName}")
        return mcpError("Could not load app diagnostics. Check the app ID and local status endpoint availability.")
    }
}

List<Map> operationalRows(Object value, String section = null) {
    List<Map> rows = []
    if (value instanceof List) {
        int index = 0
        for (Object item : (List) value) { rows.add([section: section, index: index++, value: redactSensitiveFields(item)]) }
    } else if (value instanceof Map) {
        ((Map) value).each { Object key, Object item -> rows.add([section: section, key: key?.toString(), value: redactSensitiveFields(item)]) }
    }
    return rows
}

Object redactSensitiveFields(Object value) {
    if (value instanceof Map) {
        Map safe = [:]
        ((Map) value).each { Object key, Object item ->
            if (!isSensitiveField(key?.toString())) { safe.put(key, redactSensitiveFields(item)) }
        }
        return safe
    }
    if (value instanceof List) { return ((List) value).collect { Object item -> redactSensitiveFields(item) } }
    return value
}

boolean isSensitiveField(String key) {
    return key != null && key.toLowerCase() ==~ /.*(password|passwd|token|secret|credential|authorization|lockcode|accesskey|privatekey).*/
}

Map continueOperationalSnapshot(String cursor, int pageSize, String expectedKind) {
    int separator = cursor.lastIndexOf(":")
    if (separator < 1) { return mcpError("Invalid diagnostics cursor. Start a new request.") }
    int offset
    try { offset = Integer.parseInt(cursor.substring(separator + 1)) } catch (NumberFormatException ignored) { return mcpError("Invalid diagnostics cursor. Start a new request.") }
    String snapshotId = cursor.substring(0, separator)
    Map snapshot
    synchronized (LOG_SEARCH_SNAPSHOTS) { expireSearchSnapshots(); snapshot = LOG_SEARCH_SNAPSHOTS.get(snapshotId) as Map }
    if (snapshot == null) { return mcpError("This diagnostics cursor has expired. Start a new request.") }
    if (snapshot.kind != expectedKind) { return mcpError("This cursor belongs to a different diagnostics section or app.") }
    return renderOperationalPage(snapshot + [id: snapshotId], offset, pageSize, expectedKind.startsWith("runtime|") ? expectedKind.substring("runtime|".length()) : "appDiagnostics")
}

Map renderOperationalPage(Map snapshot, int offset, int pageSize, String section) {
    List<Map> entries = (List<Map>) snapshot.entries
    if (offset < 0 || (offset >= entries.size() && !(offset == 0 && entries.isEmpty()))) { return mcpError("The diagnostics cursor points past the result set.") }
    int end = Math.min(entries.size(), offset + pageSize)
    while (true) {
        Map result = [section: section, total: entries.size(), returned: end - offset, entries: entries.subList(offset, end), nextCursor: end < entries.size() ? "${snapshot.id}:${end}" : null]
        if (JsonOutput.toJson(result).getBytes("UTF-8").length <= MAX_RESPONSE_BYTES) { return mcpJsonResult(result) }
        if (end - offset <= 1) { return mcpError("One diagnostics entry exceeds the response size limit.") }
        end--
    }
}

List<Map> flattenAppInventory(List nodes) {
    List<Map> apps = []
    List<Map> pending = []
    for (Object node : nodes) { if (node instanceof Map) { pending.add([node: node, parentId: null]) } }
    while (!pending.isEmpty()) {
        Map work = pending.remove(pending.size() - 1)
        Map node = (Map) work.node
        if (node.data instanceof Map) {
            Map data = new LinkedHashMap((Map) node.data)
            if (data.name instanceof String) { data.name = compactLogText((String) data.name, 1000) }
            data.put("parentId", work.parentId)
            data.put("hasChildren", node.children instanceof List && !((List) node.children).isEmpty())
            apps.add(data)
            if (node.children instanceof List) {
                for (Object child : (List) node.children) {
                    if (child instanceof Map) { pending.add([node: child, parentId: data.id]) }
                }
            }
        }
    }
    return apps.sort { Map left, Map right -> (left.name?.toString() ?: "").toLowerCase() <=> (right.name?.toString() ?: "").toLowerCase() ?: (left.id?.toString() ?: "") <=> (right.id?.toString() ?: "") }
}

Map continueAppInventory(String cursor, int pageSize) {
    int separator = cursor.lastIndexOf(":")
    if (separator < 1) { return mcpError("Invalid app inventory cursor. Start a new request.") }
    int offset
    try { offset = Integer.parseInt(cursor.substring(separator + 1)) } catch (NumberFormatException ignored) { return mcpError("Invalid app inventory cursor. Start a new request.") }
    String snapshotId = cursor.substring(0, separator)
    Map snapshot
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        expireSearchSnapshots()
        snapshot = LOG_SEARCH_SNAPSHOTS.get(snapshotId) as Map
    }
    if (snapshot == null) { return mcpError("This app inventory cursor has expired. Start a new request.") }
    if (!snapshot.kind?.toString()?.startsWith("appInventory|")) { return mcpError("This cursor belongs to a different tool.") }
    return renderAppInventoryPage(snapshot + [id: snapshotId], offset, pageSize)
}

Map renderAppInventoryPage(Map snapshot, int offset, int pageSize) {
    List<Map> entries = (List<Map>) snapshot.entries
    if (offset < 0 || (offset >= entries.size() && !(offset == 0 && entries.isEmpty()))) { return mcpError("The app inventory cursor points past the result set.") }
    int end = Math.min(entries.size(), offset + pageSize)
    while (true) {
        Map result = [section: snapshot.kind.toString().substring("appInventory|".length()), totalMatches: entries.size(), returned: end - offset, items: entries.subList(offset, end), nextCursor: end < entries.size() ? "${snapshot.id}:${end}" : null]
        if (JsonOutput.toJson(result).getBytes("UTF-8").length <= MAX_RESPONSE_BYTES) { return mcpJsonResult(result) }
        if (end - offset <= 1) { return mcpError("One app inventory entry exceeds the response size limit.") }
        end--
    }
}

Map getDeviceInventory(Map arguments) {
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    if (arguments.cursor) { return continueDeviceInventory(arguments.cursor.toString(), pageSize) }
    String deviceId = arguments.deviceId?.toString()
    if (arguments.deviceId != null && (!deviceId || !(deviceId ==~ /[1-9]\d*/) || deviceId.length() > 18)) { return mcpError("deviceId must be a positive integer.") }
    for (String key : ["query", "room"]) {
        if (arguments.get(key) != null && (!(arguments.get(key) instanceof String) || arguments.get(key).toString().length() > 120)) { return mcpError("${key} must be a string of at most 120 characters.") }
    }
    try {
        Object responseData
        httpGet([uri: DEVICE_LIST_URI, timeout: 15, contentType: "application/json"]) { response ->
            if (response.status < 200 || response.status >= 300) { throw new RuntimeException("Device inventory endpoint returned an error.") }
            responseData = response.data
        }
        if (responseData instanceof String) { responseData = parseJson((String) responseData) }
        if (!(responseData instanceof Map) || !(((Map) responseData).devices instanceof List)) { throw new RuntimeException("Device inventory endpoint returned an unexpected response.") }
        List<Map> devices = flattenDeviceInventory((List) ((Map) responseData).devices)
        String query = arguments.query?.toString()?.trim()?.toLowerCase()
        String room = arguments.room?.toString()?.trim()?.toLowerCase()
        devices = devices.findAll { Map item ->
            if (deviceId && item.id?.toString() != deviceId) { return false }
            if (room && item.roomName?.toString()?.toLowerCase() != room) { return false }
            if (query && ![item.id, item.name, item.secondaryName, item.type, item.dni, item.roomName].any { Object value -> value?.toString()?.toLowerCase()?.contains(query) }) { return false }
            return true
        }
        Map snapshot = createSearchSnapshot(devices, now(), devices.size(), false, "deviceInventory")
        return renderDeviceInventoryPage(snapshot, 0, pageSize)
    } catch (Exception e) {
        logWarn("Device inventory lookup failed: ${e.class.simpleName}")
        return mcpError("Could not load device inventory from the hub's local device list endpoint.")
    }
}

List<Map> flattenDeviceInventory(List nodes) {
    List<Map> devices = []
    List<Map> pending = []
    for (Object node : nodes) { if (node instanceof Map) { pending.add([node: node, parentId: null]) } }
    while (!pending.isEmpty()) {
        Map work = pending.remove(pending.size() - 1)
        Map node = (Map) work.node
        if (node.data instanceof Map) {
            Map data = new LinkedHashMap((Map) node.data)
            data.remove("currentStates")
            data.put("parentId", work.parentId)
            devices.add(data)
            if (node.children instanceof List) {
                for (Object child : (List) node.children) {
                    if (child instanceof Map) { pending.add([node: child, parentId: data.id]) }
                }
            }
        }
    }
    return devices.sort { Map left, Map right -> (left.name?.toString() ?: "").toLowerCase() <=> (right.name?.toString() ?: "").toLowerCase() ?: (left.id?.toString() ?: "") <=> (right.id?.toString() ?: "") }
}

Map continueDeviceInventory(String cursor, int pageSize) {
    int separator = cursor.lastIndexOf(":")
    if (separator < 1) { return mcpError("Invalid device inventory cursor. Start a new request.") }
    int offset
    try { offset = Integer.parseInt(cursor.substring(separator + 1)) } catch (NumberFormatException ignored) { return mcpError("Invalid device inventory cursor. Start a new request.") }
    String snapshotId = cursor.substring(0, separator)
    Map snapshot
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        expireSearchSnapshots()
        snapshot = LOG_SEARCH_SNAPSHOTS.get(snapshotId) as Map
    }
    if (snapshot == null) { return mcpError("This device inventory cursor has expired. Start a new request.") }
    if (snapshot.kind != "deviceInventory") { return mcpError("This cursor belongs to a different tool.") }
    return renderDeviceInventoryPage(snapshot + [id: snapshotId], offset, pageSize)
}

Map renderDeviceInventoryPage(Map snapshot, int offset, int pageSize) {
    List<Map> entries = (List<Map>) snapshot.entries
    if (offset < 0 || (offset >= entries.size() && !(offset == 0 && entries.isEmpty()))) { return mcpError("The inventory cursor points past the result set.") }
    int end = Math.min(entries.size(), offset + pageSize)
    while (true) {
        Map result = [totalMatches: entries.size(), returned: end - offset, devices: entries.subList(offset, end), nextCursor: end < entries.size() ? "${snapshot.id}:${end}" : null]
        if (JsonOutput.toJson(result).getBytes("UTF-8").length <= MAX_RESPONSE_BYTES) { return mcpJsonResult(result) }
        if (end - offset <= 1) { return mcpError("One device inventory entry exceeds the response size limit.") }
        end--
    }
}

Map invokeDeviceCommand(Map arguments) {
    String deviceId = arguments.deviceId?.toString()
    if (!deviceId || !(deviceId ==~ /[1-9]\d*/) || deviceId.length() > 18) { return mcpError("deviceId must be a positive integer.") }
    if (!(arguments.command instanceof String) || !arguments.command || arguments.command.size() > 128) { return mcpError("command must be a non-empty command name of at most 128 characters.") }
    if (arguments.args != null && (!(arguments.args instanceof List) || ((List) arguments.args).size() > 16)) { return mcpError("args must be an array containing at most 16 arguments.") }
    String command = (String) arguments.command
    List commandArgs = arguments.args == null ? [] : (List) arguments.args
    try {
        Map details = fetchDeviceDetails(deviceId)
        if (!(details.commands instanceof List)) { return mcpError("Device command metadata is unavailable; no command was sent.") }
        boolean advertised = ((List) details.commands).any { Object item -> item instanceof Map && ((Map) item).name == command }
        if (!advertised) { return mcpError("The device does not advertise this command; no command was sent.") }
    } catch (Exception e) {
        logWarn("Device command metadata lookup failed for device ${deviceId}: ${e.class.simpleName}")
        return mcpError("Could not verify device command metadata; no command was sent.")
    }
    Object responseData
    try {
        httpPost([uri: DEVICE_COMMAND_URI, timeout: 15, requestContentType: "application/json", contentType: "application/json",
            body: [id: Long.valueOf(deviceId), method: command, args: commandArgs]]) { response ->
            if (response.status < 200 || response.status >= 300) { throw new RuntimeException("Device command endpoint returned an error.") }
            responseData = response.data
        }
        if (responseData instanceof String) { responseData = parseJson((String) responseData) }
        if (!(responseData instanceof Map) || !(((Map) responseData).success instanceof Boolean)) {
            return mcpError("The hub returned an unexpected response. The command may have executed; check device state before retrying.")
        }
        Map result = (Map) responseData
        if (result.success != true) { return mcpError("The hub rejected the command: ${result.message ?: 'no reason provided'}".toString()) }
        return mcpJsonResult([deviceId: deviceId, command: command, success: true, message: result.message])
    } catch (Exception e) {
        logWarn("Device command request failed for device ${deviceId}: ${e.class.simpleName}")
        return mcpError("The command request failed or timed out. The command may have executed; check device state before retrying.")
    }
}

Map rebootHub(Map arguments) {
    if (arguments.allowSensitive != true) {
        return mcpError("Hub reboot requires allowSensitive=true and an explicit user request.")
    }
    try {
        Integer responseStatus = 0
        httpPost([uri: HUB_REBOOT_URI, timeout: 15, requestContentType: "application/json",
            contentType: "application/json", followRedirects: false,
            body: [rebuildDataBase: false, purgeLogs: false]]) { response ->
            responseStatus = response.status as Integer
        }
        if (responseStatus >= 200 && responseStatus < 300) {
            return mcpJsonResult([success: true, outcome: "accepted", message: "The hub accepted the reboot request. Its connection may drop while it restarts."])
        }
        return mcpJsonResult([success: false, outcome: "unknown", retryable: false, httpStatus: responseStatus,
            message: "The hub returned an unexpected response. The reboot may have started; check hub connectivity before retrying."])
    } catch (Exception e) {
        logWarn("Hub reboot request ended without confirmation: ${e.class.simpleName}")
        return mcpJsonResult([success: false, outcome: "unknown", requestSent: true, retryable: false,
            message: "The reboot request may have been accepted before the connection closed. Check hub connectivity before retrying."])
    }
}

// Device diagnostics from Hubitat's fixed local device detail route.
Map getDeviceDetails(Map arguments) {
    String deviceId = arguments.deviceId?.toString()
    if (!deviceId || !(deviceId ==~ /[1-9]\d*/) || deviceId.length() > 18) {
        return mcpError("deviceId must be a positive integer.")
    }
    String section = arguments.section == null ? "overview" : arguments.section.toString()
    if (!DEVICE_DETAIL_SECTIONS.contains(section)) { return mcpError("Unknown device detail section.") }
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    if (arguments.cursor) { return continueDeviceDetails(arguments.cursor.toString(), deviceId, section, pageSize) }
    try {
        Map details = fetchDeviceDetails(deviceId)
        Map selected = selectDeviceDetailSection(details, section)
        String kind = "deviceDetails|${deviceId}|${section}|${selected.shape}|${selected.present}"
        Map snapshot = createSearchSnapshot((List<Map>) selected.entries, now(), 0, false, kind)
        return renderDeviceDetailPage(snapshot, 0, pageSize)
    } catch (Exception e) {
        logWarn("Device detail lookup failed for device ${deviceId}: ${e.class.simpleName}")
        return mcpError("Could not load device details. Check that the device exists and the hub's local device endpoint is accessible.")
    }
}

Map fetchDeviceDetails(String deviceId) {
    Object responseData
    httpGet([uri: "${DEVICE_DETAILS_URI}${deviceId}".toString(), timeout: 15, contentType: "application/json"]) { response ->
        if (response.status < 200 || response.status >= 300) { throw new RuntimeException("Device detail endpoint returned an error.") }
        responseData = response.data
    }
    if (responseData instanceof String) { responseData = parseJson((String) responseData) }
    if (!(responseData instanceof Map) || !(((Map) responseData).device instanceof Map)) {
        throw new RuntimeException("Device detail endpoint returned an unexpected response.")
    }
    Map device = (Map) ((Map) responseData).device
    if (device.id?.toString() != deviceId && device.deviceId?.toString() != deviceId) {
        throw new RuntimeException("Device detail endpoint returned a different device.")
    }
    return (Map) responseData
}

Map selectDeviceDetailSection(Map details, String section) {
    Object value
    boolean present = true
    if (section == "raw") {
        value = details
    } else if (section == "overview") {
        Map overview = [:]
        details.each { Object key, Object item ->
            if (!(item instanceof Map) && !(item instanceof List)) { overview.put(key, item) }
        }
        value = overview
    } else if (section in ["data", "attributes"]) {
        Map device = details.device instanceof Map ? (Map) details.device : [:]
        String key = section == "data" ? "data" : "currentStates"
        present = device.containsKey(key)
        value = device.get(key)
    } else {
        String key = section == "state" ? "deviceState" : section
        present = details.containsKey(key)
        value = details.get(key)
    }
    List<Map> entries = []
    String shape = "scalar"
    if (value instanceof Map) {
        shape = "object"
        ((Map) value).each { Object key, Object item -> entries.add([key: key.toString(), value: item]) }
    } else if (value instanceof List) {
        shape = "array"
        int index = 0
        for (Object item : (List) value) { entries.add([index: index++, value: item]) }
    } else if (present) {
        entries.add([value: value])
    }
    return [present: present, shape: shape, entries: entries]
}

Map continueDeviceDetails(String cursor, String deviceId, String section, int pageSize) {
    int separator = cursor.lastIndexOf(":")
    if (separator < 1) { return mcpError("Invalid device detail cursor. Start a new request.") }
    int offset
    try { offset = Integer.parseInt(cursor.substring(separator + 1)) } catch (NumberFormatException ignored) {
        return mcpError("Invalid device detail cursor. Start a new request.")
    }
    String snapshotId = cursor.substring(0, separator)
    Map snapshot
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        expireSearchSnapshots()
        snapshot = LOG_SEARCH_SNAPSHOTS.get(snapshotId) as Map
    }
    if (snapshot == null) { return mcpError("This device detail cursor has expired. Start a new request.") }
    String prefix = "deviceDetails|${deviceId}|${section}|"
    if (!snapshot.kind?.toString()?.startsWith(prefix)) { return mcpError("This cursor belongs to a different device, section, or tool.") }
    return renderDeviceDetailPage(snapshot + [id: snapshotId], offset, pageSize)
}

Map renderDeviceDetailPage(Map snapshot, int offset, int pageSize) {
    List<Map> entries = (List<Map>) snapshot.entries
    if (offset < 0 || (offset >= entries.size() && !(offset == 0 && entries.isEmpty()))) { return mcpError("The device detail cursor points past the result set.") }
    String[] parts = snapshot.kind.toString().split(/\|/, -1)
    int end = Math.min(entries.size(), offset + pageSize)
    while (true) {
        Map result = [deviceId: parts[1], section: parts[2], shape: parts[3], present: parts[4] == "true",
            totalEntries: entries.size(), returned: end - offset, entries: entries.subList(offset, end),
            nextCursor: end < entries.size() ? "${snapshot.id}:${end}" : null]
        if (JsonOutput.toJson(result).getBytes("UTF-8").length <= MAX_RESPONSE_BYTES) { return mcpJsonResult(result) }
        if (end - offset <= 1) { return mcpError("One device detail entry exceeds the response limit; try a more specific section.") }
        end--
    }
}

Map diagnosticTimeline(Map arguments) {
    Map paging = validateLogFilters([maxEntries: arguments.maxEntries], true)
    if (paging.error) { return mcpError(paging.error.toString()) }
    int pageSize = (Integer) paging.pageSize
    if (arguments.cursor) { return continueLogSearch(arguments.cursor.toString(), pageSize, "timeline") }
    String since = arguments.since?.toString()
    String before = arguments.before?.toString()
    if (!isTimelineTimestamp(since) || !isTimelineTimestamp(before) || since >= before) {
        return mcpError("Provide since and before in ascending order using YYYY-MM-DD HH:mm:ss.SSS (hub local time).")
    }
    Map ids = [:]
    for (String key : ["appIds", "deviceIds"]) {
        Object value = arguments.get(key)
        if (value == null) { ids.put(key, []); continue }
        if (!(value instanceof List) || ((List) value).size() > 50) { return mcpError("${key} must be an array of at most 50 positive IDs.") }
        List<String> normalized = []
        for (Object item : (List) value) {
            String text = item?.toString()
            if (!text || !(text ==~ /[1-9]\d*/) || text.length() > 18) { return mcpError("${key} must contain positive integer IDs.") }
            normalized.add(text)
        }
        ids.put(key, normalized)
    }
    if (((List) ids.appIds).isEmpty() && ((List) ids.deviceIds).isEmpty()) { return mcpError("Provide at least one appId or deviceId in appIds or deviceIds.") }
    try {
        Map cache = getParsedLogSnapshot()
        List<Map> matches = selectTimelineEntries((List<Map>) cache.entries, ids, since, before)
        Map snapshot = createSearchSnapshot(matches, cache.fetchedAt as Long, cache.rawCount as Integer, cache.cacheHit as Boolean, "timeline")
        return renderLogPage(snapshot, 0, pageSize, (Boolean) snapshot.cacheHit)
    } catch (Exception e) {
        logWarn("Diagnostic timeline failed: ${e.class.simpleName}")
        return mcpError("Could not load diagnostic timeline: ${e.message ?: e.class.simpleName}".toString())
    }
}

boolean isTimelineTimestamp(String value) {
    if (!value || !(value ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}/)) { return false }
    try {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        parser.setLenient(false)
        parser.parse(value)
        return true
    } catch (Exception ignored) {
        return false
    }
}

List<Map> selectTimelineEntries(List<Map> entries, Map ids, String since, String before) {
    List<Map> matches = entries.findAll { Map entry ->
        String timestamp = normalizeTimelineTimestamp(entry.timestamp?.toString())
        if (!timestamp || timestamp < since || timestamp >= before) { return false }
        return (entry.sourceType == "app" && ((List) ids.appIds).contains(entry.sourceId?.toString())) ||
            (entry.sourceType == "dev" && ((List) ids.deviceIds).contains(entry.sourceId?.toString()))
    }
    return matches.sort { Map left, Map right -> normalizeTimelineTimestamp(left.timestamp?.toString()) <=> normalizeTimelineTimestamp(right.timestamp?.toString()) }
}

String normalizeTimelineTimestamp(String value) {
    if (!value || !(value ==~ /\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{1,3}/)) { return null }
    return value.padRight(23, "0")
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
    Map validation = validateLogFilters(arguments, false)
    if (validation.error) { return mcpError(validation.error.toString()) }
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
    entries = filterLogEntries(entries, validation.filters as Map)
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
    if (arguments.appId != null && arguments.deviceId != null) {
        return [error: "appId and deviceId cannot be combined."]
    }
    if (arguments.appId != null) {
        try {
            Integer appId = Integer.valueOf(arguments.appId.toString())
            if (appId < 1) { return [error: "appId must be a positive integer."] }
            filters.appId = appId
        } catch (NumberFormatException ignored) {
            return [error: "appId must be a positive integer."]
        }
    }
    if (arguments.deviceId != null) {
        try {
            Integer deviceId = Integer.valueOf(arguments.deviceId.toString())
            if (deviceId < 1) { return [error: "deviceId must be a positive integer."] }
            filters.deviceId = deviceId
        } catch (NumberFormatException ignored) {
            return [error: "deviceId must be a positive integer."]
        }
    }
    if (arguments.sourceType != null) {
        String sourceType = arguments.sourceType.toString().trim().toLowerCase()
        if (!(sourceType in ["app", "dev"])) { return [error: "sourceType must be app or dev."] }
        if ((filters.appId != null && sourceType != "app") || (filters.deviceId != null && sourceType != "dev")) {
            return [error: "sourceType conflicts with the selected source ID."]
        }
        filters.sourceType = sourceType
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
        if (filters.deviceId != null && !(entry.sourceType == "dev" && entry.sourceId == filters.deviceId.toString())) { return false }
        if (filters.sourceType != null && entry.sourceType != filters.sourceType) { return false }
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

Map createSearchSnapshot(List<Map> entries, long fetchedAt, int rawCount, boolean cacheHit, String kind = "logs") {
    String snapshotId
    synchronized (LOG_SEARCH_SNAPSHOTS) {
        SEARCH_SNAPSHOT_SEQUENCE++
        snapshotId = "${now()}-${SEARCH_SNAPSHOT_SEQUENCE}"
        LOG_SEARCH_SNAPSHOTS.put(snapshotId, [entries: entries, createdAt: now(), fetchedAt: fetchedAt, rawCount: rawCount, cacheHit: cacheHit, kind: kind])
        expireSearchSnapshots()
    }
    return [id: snapshotId, entries: entries, fetchedAt: fetchedAt, rawCount: rawCount, cacheHit: cacheHit, kind: kind]
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

Map continueLogSearch(String cursor, int pageSize, String kind = "logs") {
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
    if ((snapshot.kind ?: "logs") != kind) { return mcpError("This cursor belongs to a different tool. Start a new search.") }
    return renderLogPage(snapshot + [id: snapshotId], offset, pageSize, (Boolean) (snapshot.cacheHit ?: false))
}

Map renderLogPage(Map snapshot, int offset, int pageSize, boolean cacheHit) {
    List<Map> entries = snapshot.entries as List<Map>
    if (offset < 0 || (offset >= entries.size() && !(offset == 0 && entries.isEmpty()))) { return mcpError("The log cursor points past the end of this result set.") }
    int end = Math.min(entries.size(), offset + pageSize)
    List<Map> page = entries.subList(offset, end)
    String nextCursor = end < entries.size() ? "${snapshot.id}:${end}" : null
    Map data = [
        totalMatches: entries.size(),
        returned: page.size(),
        newestFirst: snapshot.kind != "timeline",
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
