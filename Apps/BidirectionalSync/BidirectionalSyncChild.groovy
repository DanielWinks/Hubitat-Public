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

// =============================================================================
// BIDIRECTIONAL SYNC CHILD APP
// =============================================================================
// Description: Keeps two lighting devices in perfect bidirectional sync.
//              Uses echo detection to prevent infinite feedback loops:
//              before commanding a target, the app records what it sent.
//              When the resulting echo event arrives, it is recognized and
//              suppressed — no loop, no delay.
//
// Pages:
//   - Device Selection: Pick primary and secondary devices
//   - Attribute Selection: Choose which attributes to mirror
// =============================================================================

import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event
import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

// =============================================================================
// Echo Detection Data Structures
// =============================================================================
// ConcurrentHashMap is used because Hubitat event handlers can fire
// concurrently. This provides thread-safe access without the overhead
// of atomicState. Keys use format: "${app.id}-${deviceId}-${attributeName}"
// =============================================================================

@Field static final ConcurrentHashMap<String, Map> pendingEchos = new ConcurrentHashMap<>()
@Field static final ConcurrentHashMap<String, Long> lastForwardTimestamps = new ConcurrentHashMap<>()

// =============================================================================
// Attribute-to-Command Mapping
// =============================================================================
// Defines how attribute values translate to device commands.
// Types:
//   - enum: value maps directly to a command name (e.g., "on" → device.on())
//   - numeric: value is passed as argument (e.g., 75 → device.setLevel(75))
//   - color_component: hue/saturation are batched into a single setColor() call
// =============================================================================

@Field static final Map<String, Map<String, Object>> ATTRIBUTE_COMMAND_MAP = [
  'switch': [
    type: 'enum',
    commands: ['on': 'on', 'off': 'off']
  ],
  'level': [
    type: 'numeric',
    command: 'setLevel'
  ],
  'colorTemperature': [
    type: 'numeric',
    command: 'setColorTemperature'
  ],
  'hue': [
    type: 'color_component',
    command: 'setColor'
  ],
  'saturation': [
    type: 'color_component',
    command: 'setColor'
  ]
]

// Attributes that are read-only or automatically set by other commands
@Field static final List<String> SKIP_ATTRIBUTES = ['colorMode', 'colorName', 'RGB', 'color']

definition(
  name: 'Bidirectional Sync Child',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Keeps two lighting devices in bidirectional sync using echo detection.',
  category: 'Convenience',
  parent: 'dwinks:Bidirectional Sync',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  documentationLink: ''
)

// =============================================================================
// Preferences / Pages
// =============================================================================

preferences {
  page(name: 'deviceSelectionPage')
  page(name: 'attributeSelectionPage')
}

// =============================================================================
// Page 1: Device Selection
// =============================================================================

Map deviceSelectionPage() {
  return dynamicPage(name: 'deviceSelectionPage', title: '<h1>Bidirectional Sync</h1>', nextPage: 'attributeSelectionPage', install: false, uninstall: true) {
    section('<h2>Device Selection</h2>') {
      paragraph('<i>Select two lighting devices to keep in sync. Changes on either device will be mirrored to the other.</i>')
      input('primaryDevice', 'capability.switch', title: 'Primary Device', required: true, multiple: false, submitOnChange: true)
      input('secondaryDevice', 'capability.switch', title: 'Secondary Device', required: true, multiple: false, submitOnChange: true)
    }

    if (settings.primaryDevice && settings.secondaryDevice) {
      List<String> mirrorable = getMirrorableAttributes()
      if (mirrorable) {
        section('<h2>Shared Mirrorable Attributes</h2>') {
          paragraph("<i>These attributes are shared between both devices and can be mirrored: <b>${mirrorable.join(', ')}</b></i>")
          paragraph('Configure mirroring options on the next page.')
        }
      } else {
        section('<h2>No Mirrorable Attributes</h2>') {
          paragraph('<b>Warning:</b> These devices share no mirrorable lighting attributes. Please select different devices.')
        }
      }
    }

    section() {
      label(title: 'Enter a name for this sync instance', required: true)
    }
  }
}

// =============================================================================
// Page 2: Attribute Selection
// =============================================================================

Map attributeSelectionPage() {
  return dynamicPage(name: 'attributeSelectionPage', title: '<h1>Attribute Configuration</h1>', install: true, uninstall: true) {
    if (!settings.primaryDevice || !settings.secondaryDevice) {
      section() {
        paragraph('<b>Please go back and select both devices first.</b>')
      }
      return
    }

    List<String> mirrorable = getMirrorableAttributes()

    if (mirrorable) {
      section('<h2>Attributes to Mirror</h2>') {
        paragraph('<i>Enable or disable mirroring for each attribute. Only attributes that both devices support are shown.</i>')
        mirrorable.each { String attrName ->
          String desc = getAttributeDescription(attrName)
          input("mirror_${attrName}", 'bool', title: "Mirror: ${attrName}${desc ? ' — ' + desc : ''}", required: false, defaultValue: true)
        }
      }
    } else {
      section() {
        paragraph('<b>No mirrorable attributes found between these devices.</b>')
      }
    }

    section('<h2>Timing</h2>') {
      input('echoWindowSeconds', 'number', title: 'Echo detection window (seconds)', required: false, defaultValue: 5, range: '1..30')
      paragraph('<i>How long to wait for an echo event after commanding a device. Increase if your devices respond slowly.</i>')
      input('minReforwardInterval', 'number', title: 'Minimum re-forward interval (seconds)', required: false, defaultValue: 2, range: '0..60')
      paragraph('<i>Minimum time between forwarding the same attribute. Prevents rapid-fire commands during fast changes.</i>')
    }

    section('<h2>Logging</h2>') {
      input('logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true)
      input('debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false)
      input('descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true)
    }
  }
}

// =============================================================================
// Attribute Discovery
// =============================================================================

/**
 * Returns the list of attribute names that can be mirrored between the
 * primary and secondary devices. An attribute is mirrorable if:
 *   1. Both devices report it via supportedAttributes
 *   2. It is not in the SKIP_ATTRIBUTES list
 *   3. The target device has the required command (verified per ATTRIBUTE_COMMAND_MAP
 *      or via a dynamic set${Attribute} command)
 */
private List<String> getMirrorableAttributes() {
  if (!settings.primaryDevice || !settings.secondaryDevice) { return [] }

  DeviceWrapper primary = settings.primaryDevice
  DeviceWrapper secondary = settings.secondaryDevice

  List<String> primaryAttrs = primary.supportedAttributes*.name
  List<String> secondaryAttrs = secondary.supportedAttributes*.name

  List<String> sharedAttrs = primaryAttrs.intersect(secondaryAttrs)

  List<String> primaryCommands = primary.supportedCommands*.name
  List<String> secondaryCommands = secondary.supportedCommands*.name

  List<String> mirrorable = []

  sharedAttrs.each { String attrName ->
    if (attrName in SKIP_ATTRIBUTES) { return }

    String requiredCommand = getRequiredCommand(attrName)

    // Both devices must have the command
    if (primaryCommands.contains(requiredCommand) && secondaryCommands.contains(requiredCommand)) {
      mirrorable.add(attrName)
    }
  }

  return mirrorable.sort()
}

/**
 * Returns the command name required to set a given attribute.
 * For known attributes, returns the mapped command name.
 * For unknown attributes, falls back to set${Attribute} convention;
 * getMirrorableAttributes() will verify the device actually has the command.
 */
private String getRequiredCommand(String attrName) {
  Map mapping = ATTRIBUTE_COMMAND_MAP[attrName] as Map
  if (mapping) {
    if (mapping.type == 'enum') {
      return (mapping.commands as Map).values().first() as String
    }
    return mapping.command as String
  }

  // Dynamic fallback: set${Attribute} convention
  return "set${attrName.capitalize()}"
}

/**
 * Returns a brief description of what an attribute controls, for the UI.
 */
private String getAttributeDescription(String attrName) {
  switch (attrName) {
    case 'switch': return 'on/off state'
    case 'level': return 'brightness percentage'
    case 'colorTemperature': return 'white color temperature in Kelvin'
    case 'hue': return 'color hue (0-100)'
    case 'saturation': return 'color saturation (0-100)'
    default: return ''
  }
}

// =============================================================================
// Lifecycle Methods
// =============================================================================

void installed() {
  logDebug('Installed...')
  initialize()
  if (settings.logEnable != false) { runIn(1800, 'logsOff') }
  if (settings.debugLogEnable != false) { runIn(1800, 'debugLogsOff') }
}

void updated() {
  logDebug('Updated...')
  configure()
}

void initialize() {
  configure()
}

void uninstalled() {
  cleanupStaleEntries()
  unsubscribe()
  unschedule()
}

void configure() {
  unsubscribe()
  cleanupStaleEntries()
  subscribeToDevices()
}

private void subscribeToDevices() {
  if (!settings.primaryDevice || !settings.secondaryDevice) { return }

  List<String> mirrorable = getMirrorableAttributes()
  mirrorable.each { String attrName ->
    if (settings["mirror_${attrName}"] != false) {
      subscribe(settings.primaryDevice, attrName, 'primaryEventHandler')
      subscribe(settings.secondaryDevice, attrName, 'secondaryEventHandler')
      logDebug("Subscribed to '${attrName}' on both devices")
    }
  }

  logInfo("Bidirectional sync configured: ${getEnabledAttributes().join(', ') ?: 'none'}")
}

/**
 * Returns the list of attribute names currently enabled for mirroring.
 */
private List<String> getEnabledAttributes() {
  List<String> mirrorable = getMirrorableAttributes()
  return mirrorable.findAll { String attrName -> settings["mirror_${attrName}"] != false }
}

/**
 * Removes stale echo and timestamp entries for this app instance.
 */
private void cleanupStaleEntries() {
  String prefix = "${app.id}-"
  pendingEchos.keySet().removeAll { String key -> key.startsWith(prefix) }
  lastForwardTimestamps.keySet().removeAll { String key -> key.startsWith(prefix) }
  logDebug('Cleaned up stale echo detection entries')
}

// =============================================================================
// Event Handling — Core Mirror Logic
// =============================================================================

/**
 * Backward-compatible shim: stale subscriptions from before the handler
 * rename will call this method. Re-subscribes with the correct handler
 * names so subsequent events route properly. The triggering event is
 * dropped (one-time cost during transition).
 */
void mirrorEventHandler(Event evt) {
  logWarn('Legacy subscription detected — re-subscribing with updated handlers')
  configure()
}

/**
 * Event handler for the primary device. Hubitat's subscribe() mechanism
 * guarantees this only fires for primaryDevice events, so no device ID
 * comparison is needed.
 */
void primaryEventHandler(Event evt) {
  if (!settings.primaryDevice || !settings.secondaryDevice) { return }
  handleMirrorEvent(evt, settings.primaryDevice, settings.secondaryDevice)
}

/**
 * Event handler for the secondary device.
 */
void secondaryEventHandler(Event evt) {
  if (!settings.primaryDevice || !settings.secondaryDevice) { return }
  handleMirrorEvent(evt, settings.secondaryDevice, settings.primaryDevice)
}

/**
 * Shared mirror logic: runs echo detection and forwards if appropriate.
 * Source and target are determined by the calling handler, eliminating the
 * need for device ID comparison (which is unreliable on Hubitat's runtime).
 */
private void handleMirrorEvent(Event evt, DeviceWrapper source, DeviceWrapper target) {
  String attrName = evt.name
  String eventValue = evt.value

  logDebug("Event: ${source.displayName} → ${attrName} = ${eventValue}")

  // Step 1: Echo detection — is this an echo of a command we sent?
  String echoKey = buildKey(source.id, attrName)
  if (isEcho(echoKey, eventValue)) {
    logDebug("Suppressed echo: ${source.displayName} ${attrName} = ${eventValue}")
    return
  }

  // Step 2: Check minimum re-forward interval
  String forwardKey = buildKey(target.id, attrName)
  if (!hasMinIntervalElapsed(forwardKey)) {
    logDebug("Skipped (min interval): ${attrName} → ${target.displayName}")
    return
  }

  // Step 3: Forward to target device
  forwardAttributeToDevice(source, target, attrName, eventValue)
}

// =============================================================================
// Echo Detection
// =============================================================================

/**
 * Builds a unique key for echo detection maps.
 * Format: "${appId}-${deviceId}-${attributeName}"
 */
private String buildKey(Object deviceId, String attrName) {
  return "${app.id}-${deviceId}-${attrName}"
}

/**
 * Checks if an incoming event matches a pending echo entry.
 * A match requires the same value (with ±1 numeric tolerance) and
 * arrival within the echo window.
 *
 * If the entry is stale (past echo window), it is cleaned up automatically.
 */
private Boolean isEcho(String key, String eventValue) {
  Map echoEntry = pendingEchos.get(key)
  if (echoEntry == null) { return false }

  Long echoTimestamp = echoEntry.timestamp as Long
  Integer windowMs = ((settings.echoWindowSeconds ?: 5) as Integer) * 1000

  // Stale entry — past the echo window
  if ((now() - echoTimestamp) > windowMs) {
    pendingEchos.remove(key)
    return false
  }

  // Value match with numeric tolerance
  String echoValue = echoEntry.value as String
  if (valuesMatch(echoValue, eventValue)) {
    pendingEchos.remove(key)
    return true
  }

  return false
}

/**
 * Compares two values for equality, with ±1 numeric tolerance for
 * rounding differences (e.g., device reports 74 when commanded 75).
 */
private Boolean valuesMatch(String expected, String actual) {
  if (expected == actual) { return true }

  // Try numeric comparison with tolerance
  try {
    BigDecimal expectedNum = new BigDecimal(expected)
    BigDecimal actualNum = new BigDecimal(actual)
    return (expectedNum - actualNum).abs() <= 1
  } catch (NumberFormatException ignored) {
    return false
  }
}

/**
 * Records a pending echo for a device+attribute combination.
 * Called just before sending a command to the target device.
 */
private void recordPendingEcho(Object deviceId, String attrName, String value) {
  String key = buildKey(deviceId, attrName)
  pendingEchos.put(key, [value: value, timestamp: now()])
}

// =============================================================================
// Re-forward Interval
// =============================================================================

/**
 * Checks whether enough time has elapsed since the last forward for
 * the given target device+attribute. Returns true if forwarding is allowed.
 */
private Boolean hasMinIntervalElapsed(String forwardKey) {
  Integer minIntervalMs = ((settings.minReforwardInterval ?: 2) as Integer) * 1000
  if (minIntervalMs <= 0) { return true }

  Long lastForward = lastForwardTimestamps.get(forwardKey)
  if (lastForward == null) { return true }

  return (now() - lastForward) >= minIntervalMs
}

/**
 * Records the current timestamp as the last forward time for a key.
 */
private void recordForwardTimestamp(String forwardKey) {
  lastForwardTimestamps.put(forwardKey, now())
}

// =============================================================================
// Command Dispatch
// =============================================================================

/**
 * Forwards an attribute change from the source device to the target device.
 * Records pending echo entries before sending the command so that the
 * resulting event will be recognized and suppressed.
 */
private void forwardAttributeToDevice(DeviceWrapper source, DeviceWrapper target, String attrName, String value) {
  Map mapping = ATTRIBUTE_COMMAND_MAP[attrName] as Map

  try {
    if (mapping) {
      String type = mapping.type as String

      switch (type) {
        case 'enum':
          forwardEnumAttribute(target, attrName, value, mapping)
          break
        case 'numeric':
          forwardNumericAttribute(target, attrName, value, mapping)
          break
        case 'color_component':
          forwardColorAttribute(source, target, attrName)
          break
        default:
          logWarn("Unknown attribute type '${type}' for ${attrName}")
          return
      }
    } else {
      // Dynamic fallback: set${Attribute}(value)
      forwardDynamicAttribute(target, attrName, value)
    }

    // Record forward timestamp
    String forwardKey = buildKey(target.id, attrName)
    recordForwardTimestamp(forwardKey)

    logInfo("Mirroring ${attrName} = ${value} → ${target.displayName}")

  } catch (Exception e) {
    logError("Failed to mirror ${attrName} to ${target.displayName}: ${e.message}")
    // Clean up echo entry on failure so it doesn't block future events
    String echoKey = buildKey(target.id, attrName)
    pendingEchos.remove(echoKey)
  }
}

/**
 * Forwards an enum attribute (e.g., switch on/off).
 * The value maps directly to a command name.
 */
private void forwardEnumAttribute(DeviceWrapper target, String attrName, String value, Map mapping) {
  Map commands = mapping.commands as Map
  String commandName = commands[value] as String
  if (commandName == null) {
    logWarn("No command mapping for ${attrName} = ${value}")
    return
  }

  recordPendingEcho(target.id, attrName, value)
  target."${commandName}"()
}

/**
 * Forwards a numeric attribute (e.g., level, colorTemperature).
 * The value is passed as an argument to the command.
 */
private void forwardNumericAttribute(DeviceWrapper target, String attrName, String value, Map mapping) {
  String commandName = mapping.command as String
  Integer numValue = Math.round(new BigDecimal(value)) as Integer

  recordPendingEcho(target.id, attrName, value)
  target."${commandName}"(numValue)
}

/**
 * Forwards a color component attribute (hue or saturation).
 * Reads the current hue, saturation, and level from the source device
 * and sends a single setColor() call to the target. Records echoes
 * for all three attributes since setColor() generates events for each.
 */
private void forwardColorAttribute(DeviceWrapper source, DeviceWrapper target, String attrName) {
  Integer hue = source.currentValue('hue') as Integer
  Integer saturation = source.currentValue('saturation') as Integer
  Integer level = source.currentValue('level') as Integer

  if (hue == null || saturation == null || level == null) {
    logWarn("Cannot mirror color: missing hue/saturation/level on ${source.displayName}")
    return
  }

  Map colorMap = [hue: hue, saturation: saturation, level: level]

  // Record echoes for color attributes before sending command.
  // setColor() generates events for hue, saturation, and level.
  recordPendingEcho(target.id, 'hue', hue.toString())
  recordPendingEcho(target.id, 'saturation', saturation.toString())
  // Only record level echo if level mirroring is enabled, to avoid
  // suppressing a legitimate independent level change on the target
  if (settings['mirror_level'] != false) {
    recordPendingEcho(target.id, 'level', level.toString())
  }

  target.setColor(colorMap)
}

/**
 * Forwards an attribute using the dynamic set${Attribute}() convention.
 * Used for attributes not in ATTRIBUTE_COMMAND_MAP that both devices support.
 */
private void forwardDynamicAttribute(DeviceWrapper target, String attrName, String value) {
  String commandName = "set${attrName.capitalize()}"

  // Verify the target actually has this command
  if (!target.supportedCommands*.name.contains(commandName)) {
    logWarn("Target device ${target.displayName} does not support command ${commandName}")
    return
  }

  recordPendingEcho(target.id, attrName, value)

  // Try numeric first, fall back to string
  try {
    BigDecimal numValue = new BigDecimal(value)
    target."${commandName}"(numValue)
  } catch (NumberFormatException ignored) {
    target."${commandName}"(value)
  }
}

// =============================================================================
// Logging Methods
// =============================================================================

private void logError(String message) {
  if (settings.logEnable != false) {
    log.error("${app.label ?: app.name}: ${message}")
  }
}

private void logWarn(String message) {
  if (settings.logEnable != false) {
    log.warn("${app.label ?: app.name}: ${message}")
  }
}

private void logInfo(String message) {
  if (settings.logEnable != false) {
    log.info("${app.label ?: app.name}: ${message}")
  }
}

private void logDebug(String message) {
  if (settings.logEnable != false && settings.debugLogEnable != false) {
    log.debug("${app.label ?: app.name}: ${message}")
  }
}

void logsOff() {
  logWarn('Logging disabled')
  app.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

void debugLogsOff() {
  logWarn('Debug logging disabled')
  app.updateSetting('debugLogEnable', [value: 'false', type: 'bool'])
}
