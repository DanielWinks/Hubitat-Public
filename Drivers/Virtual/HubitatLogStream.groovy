/*
 * Hubitat Log Stream Driver
 * MIT License
 * Copyright (c) 2026 Daniel Winks
 */

import groovy.json.JsonOutput

metadata {
    definition(name: "Hubitat Log Stream", namespace: "dwinks", author: "Daniel Winks") {
        capability "Sensor"
        attribute "connectionStatus", "string"
        attribute "logEntry", "string"
        command "connectStream"
        command "disconnectStream"
    }
    preferences {
        input name: "logLevel", type: "enum", title: "Log level", options: [trace: "Trace", debug: "Debug", info: "Info", warn: "Warn", error: "Error", off: "Off"], defaultValue: "info", required: true
    }
}

void installed() {
    state.stopRequested = false
    setConnectionStatus("ready")
}

void updated() {
    initialize()
}

void initialize() {
    state.stopRequested = false
    connectStream()
}

void uninstalled() {
    disconnectStream()
}

void connectStream() {
    state.stopRequested = false
    String currentStatus = device.currentValue("connectionStatus")?.toString()
    if (currentStatus == "connected" || currentStatus == "connecting") {
        return
    }
    try {
        setConnectionStatus("connecting")
        interfaces.webSocket.connect("ws://localhost:8080/logsocket")
    } catch (Exception e) {
        setConnectionStatus("error")
        logWarn("Unable to connect to Hubitat's live log socket: ${e.class.simpleName}")
        scheduleReconnect()
    }
}

void disconnectStream() {
    state.stopRequested = true
    unschedule("connectStream")
    try {
        interfaces.webSocket.close()
    } catch (Exception ignored) {
        setConnectionStatus("disconnected")
    }
}

void webSocketStatus(String socketStatus) {
    if (socketStatus == "status: open") {
        unschedule("connectStream")
        setConnectionStatus("connected")
        return
    }
    if (socketStatus == "status: closing") {
        setConnectionStatus(state.stopRequested ? "disconnected" : "reconnecting")
        if (!state.stopRequested) {
            scheduleReconnect()
        }
        return
    }
    if (socketStatus?.startsWith("failure:")) {
        setConnectionStatus("error")
        if (!state.stopRequested) {
            logWarn("Live log stream disconnected; retrying.")
            scheduleReconnect()
        }
        return
    }
    setConnectionStatus("reconnecting")
    if (!state.stopRequested) {
        scheduleReconnect()
    }
}

void parse(String description) {
    try {
        Object parsed = parseJson(description)
        if (!(parsed instanceof Map)) {
            return
        }
        Map raw = (Map) parsed
        Map entry = [
            timestamp: raw.time?.toString() ?: new Date(now()).format("yyyy-MM-dd HH:mm:ss.SSS"),
            level: raw.level?.toString()?.trim()?.toUpperCase() ?: "INFO",
            sourceId: raw.id?.toString() ?: "",
            sourceName: compactText(raw.name?.toString() ?: "", 120),
            sourceType: raw.type?.toString() ?: "",
            message: compactText(raw.msg?.toString() ?: "", 1400)
        ]
        sendEvent(name: "logEntry", value: JsonOutput.toJson(entry), isStateChange: true)
    } catch (Exception e) {
        logDebug("Ignored an unparseable live log message (${e.class.simpleName}).")
    }
}

void setConnectionStatus(String value) {
    sendEvent(name: "connectionStatus", value: value, isStateChange: true)
}

void scheduleReconnect() {
    runIn(20, "connectStream", [overwrite: false])
}

String compactText(String value, int maxLength) {
    String compact = value.replaceAll(/[\r\n\t]+/, " ").trim()
    return compact.length() > maxLength ? compact.substring(0, maxLength) : compact
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
