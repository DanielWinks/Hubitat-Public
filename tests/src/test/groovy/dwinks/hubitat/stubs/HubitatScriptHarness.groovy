package dwinks.hubitat.stubs

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Base class that supplies the bits of the Hubitat runtime an App or Driver
 * normally relies on so we can load and invoke Groovy code in plain JVM tests.
 * Test specs subclass this (or extend the values directly) to model state,
 * device behavior, and to assert what side-effects a method produced.
 *
 * The harness is intentionally simple: scheduling, subscriptions and events
 * are recorded into in-memory lists rather than fired. That lets a spec say
 * things like "after calling configure(), runEvery30Minutes('refresh') should
 * have been scheduled" without needing real time to pass.
 */
class HubitatScriptHarness extends Script {

  /** Settings UI values - tests pre-populate this Map. */
  Map<String, Object> settings = [:]

  /** Mutable runtime state - tests assert on this Map after invocations. */
  Map<String, Object> state = [:]

  /** Per-call recordings the spec can assert against. */
  List<Map> events = []
  List<Map> childEvents = []
  List<List> scheduled = []      // [[secondsOrMs, methodName, opts]]
  List<List> cronSchedules = []  // [[expr, method]]
  List<List> recurring = []      // [[every, method]]
  List<List> subscriptions = []  // [[target, attr, handler]]
  List<String> unschedules = []  // method names (or '__all__')
  List<String> logs = []         // human-readable log lines
  Map<String, Object> updatedSettings = [:]

  /** Mock device. Tests set this via {@code device = new MockDevice(...)}. */
  MockDevice device = new MockDevice()
  MockApp app = new MockApp()
  MockLog log = new MockLog(this)
  Map location = [name: 'Test Location', mode: 'Day', timeZone: TimeZone.getTimeZone('America/New_York')]
  String hubUID = 'TEST-HUB-UID'

  /** Optional override for asynchttp callback responses (path -> response map). */
  Map<String, Object> httpResponses = [:]

  // ----- Required Script overrides -----
  @Override
  Object run() { null }

  // ----- Scheduling / subscription stubs -----
  void runIn(Number seconds, String method, Map opts = [:]) {
    scheduled << [seconds, method, opts]
  }

  void runInMillis(Number ms, String method, Map opts = [:]) {
    scheduled << [ms, method, opts]
  }

  void schedule(String cron, String method) {
    cronSchedules << [cron, method]
  }

  void runEvery1Minute(String method)  { recurring << ['1m', method] }
  void runEvery5Minutes(String method) { recurring << ['5m', method] }
  void runEvery10Minutes(String method) { recurring << ['10m', method] }
  void runEvery15Minutes(String method) { recurring << ['15m', method] }
  void runEvery30Minutes(String method) { recurring << ['30m', method] }
  void runEvery1Hour(String method)    { recurring << ['1h', method] }
  void runEveryHour(String method)     { recurring << ['1h', method] }
  void runEvery3Hours(String method)   { recurring << ['3h', method] }

  void unschedule()             { unschedules << '__all__' }
  void unschedule(String method) { unschedules << method }

  void subscribe(Object target, String attribute, String handler) {
    subscriptions << [target, attribute, handler]
  }
  void subscribe(Object target, String handler) {
    subscriptions << [target, null, handler]
  }
  void unsubscribe()              { subscriptions.clear() }
  void unsubscribe(Object target) { subscriptions.removeAll { it[0] == target } }

  // ----- Event stubs -----
  void sendEvent(Map event) { events << event }
  void sendEvent(Object dev, Map event) { childEvents << ([device: dev] + event) }

  // ----- HTTP stubs (capture rather than execute) -----
  List<Map> asyncCalls = []
  void asynchttpGet(String callback, Map params, Map data = null)  { asyncCalls << [verb: 'GET', cb: callback, params: params, data: data] }
  void asynchttpPost(String callback, Map params, Map data = null) { asyncCalls << [verb: 'POST', cb: callback, params: params, data: data] }
  void asynchttpPut(String callback, Map params, Map data = null)  { asyncCalls << [verb: 'PUT', cb: callback, params: params, data: data] }
  void asynchttpPatch(String callback, Map params, Map data = null) { asyncCalls << [verb: 'PATCH', cb: callback, params: params, data: data] }
  void asynchttpDelete(String callback, Map params, Map data = null) { asyncCalls << [verb: 'DELETE', cb: callback, params: params, data: data] }
  void asynchttpHead(String callback, Map params, Map data = null) { asyncCalls << [verb: 'HEAD', cb: callback, params: params, data: data] }

  // ----- JSON helpers (real implementations) -----
  Object parseJson(String s)        { new JsonSlurper().parseText(s) }
  String  prettyJson(Object input)  { JsonOutput.prettyPrint(JsonOutput.toJson(input)) }

  // ----- Settings/access stubs -----
  // Note: real Hubitat scripts often provide their own tryCreateAccessToken()
  // which calls into the platform's createAccessToken(). Our harness supplies
  // createAccessToken() so those user-defined wrappers work.
  String createAccessToken() { state.accessToken = 'test-token-123'; state.accessToken }
  String getFullLocalApiServerUrl() { 'http://hub.local/apps/api/1' }
  String getApiServerUrl()          { 'https://cloud.hubitat.com' }
  String getHubUID()                { 'TEST-HUB-UID' }

  // ----- Child device stubs (override in spec for richer behavior) -----
  List<MockDevice> children = []
  List<MockDevice> getChildDevices() { children }
  MockDevice getChildDevice(String dni) { children.find { it.deviceNetworkId == dni } }
  MockDevice addChildDevice(String namespace, String typeName, String dni, Map props = [:]) {
    MockDevice d = new MockDevice(deviceNetworkId: dni, displayName: props.label ?: typeName, properties: props.properties ?: [:])
    children << d
    d
  }
  void deleteChildDevice(String dni) { children.removeAll { it.deviceNetworkId == dni } }

  // ----- Misc Hubitat helpers commonly referenced -----
  String getMACFromIP(String ip) { "AA:BB:CC:DD:EE:${ip.hashCode() & 0xFF as String}" }
  /** Days since the Unix epoch. Mirrors UtilitiesAndLoggingLibrary.nowDays(). */
  double nowDays() { System.currentTimeMillis() / 86400000d }
  /** Hubitat's now() returns current millis. */
  long   now() { System.currentTimeMillis() }

  // ----- Setting updaters -----
  void updateSetting(String name, Map opts) { updatedSettings[name] = opts.value }
  void updateSetting(String name, Object value) { updatedSettings[name] = value }
}

/** Tiny mock for the {@code log.*} object used pervasively in Hubitat code. */
class MockLog {
  HubitatScriptHarness harness
  MockLog(HubitatScriptHarness h) { this.harness = h }
  void debug(Object m) { harness.logs << "DEBUG ${m}" }
  void info (Object m) { harness.logs << "INFO ${m}" }
  void warn (Object m) { harness.logs << "WARN ${m}" }
  void error(Object m) { harness.logs << "ERROR ${m}" }
  void trace(Object m) { harness.logs << "TRACE ${m}" }
}

/** Stand-in for a Hubitat DeviceWrapper - tests can pre-populate currentStates. */
class MockDevice {
  String deviceNetworkId = 'TEST-DNI'
  String displayName = 'Test Device'
  String label = 'Test Device'
  String name = 'Test Device'
  Long id = 1L
  Map<String, Object> properties = [:]
  Map<String, Object> currentValues = [:]
  Map<String, Object> updatedSettings = [:]

  Object currentValue(String n, boolean skipCache = false) { currentValues[n] }
  void   updateSetting(String n, Object v) { updatedSettings[n] = v }
  void   updateSetting(String n, Map opts) { updatedSettings[n] = opts.value }
  void   setDeviceNetworkId(String dni)    { this.deviceNetworkId = dni }
  String getDeviceNetworkId() { deviceNetworkId }
  String getDisplayName()     { displayName }
  String getLabel()           { label }
  String getName()            { name }
  Long   getId()              { id }
  List<Map> getCurrentStates() {
    currentValues.collect { String n, Object v -> [name: n, value: v] }
  }
  void deleteCurrentState(String n) { currentValues.remove(n) }
}

/** Stand-in for an InstalledAppWrapper. */
class MockApp {
  Long id = 42L
  String label = 'Test App'
  String getId() { id?.toString() }
  String getLabel() { label }
}
