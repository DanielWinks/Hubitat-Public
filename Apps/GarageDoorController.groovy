/**
 *  Garage Door Controller
 *
 *  Monitors upper/lower tilt/contact sensors on a rolling garage door and
 *  controls a relay switch to operate the opener. Creates a virtual child
 *  device ("Generic Component Smart Garage Door Control") that exposes
 *  standard door/contact capabilities so other Hubitat apps (e.g., Rule
 *  Machine, Dashboards, Alexa/Google Home) can interact with the door.
 *
 *  State Machine
 *  -------------
 *  The door state is managed as a finite state machine with these states:
 *
 *    CLOSED          – Upper sensor is NOT tilted (door is fully closed).
 *    OPENING         – Optimistic: set immediately when openDoor() is called.
 *    OPEN            – Upper AND lower sensors are both tilted (fully open).
 *    PARTIALLY_OPEN  – Upper sensor tilted, lower sensor NOT tilted.
 *                       Only reported AFTER the sensor-settle delay expires.
 *    CLOSING         – Optimistic: set immediately when closeDoor() is called.
 *    UNKNOWN         – Upper sensor NOT tilted but lower sensor IS tilted.
 *                       Physically improbable; only reported AFTER settle.
 *
 *  Key design rules:
 *    • OPENING / CLOSING are *optimistic* — set by command, never by sensors.
 *    • The lower sensor firing during OPENING is a definitive "fully open"
 *      signal — state transitions to OPEN immediately, no settle wait.
 *    • The upper sensor reporting "closed" during CLOSING is a definitive
 *      "fully closed" signal — state transitions to CLOSED immediately.
 *    • When only an upper sensor is configured, the settle timer (checkDoor)
 *      acts as the transition trigger: after the timer expires, if the
 *      upper sensor is tripped the door is assumed fully open.
 *    • PARTIALLY_OPEN and UNKNOWN are only reported when the settle timer
 *      fires — they are never set mid-motion from transient sensor events.
 *    • Sensor events outside of command states (OPENING/CLOSING) are
 *      evaluated immediately with no deferral.
 *
 *  Sensor semantics for a rolling / sectional garage door:
 *    • "Upper" sensor — mounted near the top of the door opening.  The
 *      top panel tilts almost immediately when the door starts to open,
 *      so this sensor fires very early in the open cycle and very late
 *      in the close cycle.  **Required.**
 *    • "Lower" sensor — mounted near the bottom of the door opening.
 *      The lowest panel travels straight up until the door is nearly
 *      fully open before tilting, so this sensor only fires when the
 *      door has almost reached the fully-open position.  **Optional.**
 *
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

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event
import groovy.transform.Field

// =============================================================================
// CONSTANTS
//
// All string literals and magic numbers are defined here as @Field static final
// constants for clarity, performance, and to avoid typos from repeated inlining.
// =============================================================================

// -- Door state values (written to the child device's "door" attribute) --------

@Field static final String STATE_OPEN            = 'open'
@Field static final String STATE_CLOSED          = 'closed'
@Field static final String STATE_PARTIALLY_OPEN  = 'partially open'
@Field static final String STATE_OPENING         = 'opening'
@Field static final String STATE_CLOSING         = 'closing'
@Field static final String STATE_UNKNOWN         = 'unknown'

// -- Contact sensor states ----------------------------------------------------

@Field static final String CONTACT_OPEN   = 'open'
@Field static final String CONTACT_CLOSED = 'closed'

// -- Hubitat event attribute names --------------------------------------------

@Field static final String ATTR_DOOR    = 'door'
@Field static final String ATTR_CONTACT = 'contact'
@Field static final String ATTR_SWITCH  = 'switch'

// -- Switch values ------------------------------------------------------------

@Field static final String SWITCH_ON = 'on'

// -- Timing constants (all in milliseconds) -----------------------------------

@Field static final Integer RELAY_PULSE_DELAY_MS   = 750   // How long the relay stays on (momentary pulse)
@Field static final Integer SENSOR_SETTLE_DELAY_MS  = 5000  // Command timeout: max wait for door to respond to open/close

// -- Button control -----------------------------------------------------------

@Field static final String ATTR_PUSHED = 'pushed'

// NOTE: Hubitat's Groovy sandbox does not allow @Field initializers to
// reference other @Field constants, so NOTIFY_STATES uses string literals
// directly rather than the STATE_* constant names.
@Field static final List<String> NOTIFY_STATES = ['open', 'closed', 'unknown']

// -- Child device identifiers -------------------------------------------------

@Field static final String CHILD_NAMESPACE = 'dwinks'
@Field static final String CHILD_DRIVER    = 'Generic Component Smart Garage Door Control'
@Field static final String CHILD_ID_SUFFIX = '-DoorController'

// =============================================================================
// LOGGING UTILITIES
//
// Centralized logging helpers that respect the user's logging preferences.
// Each method prefixes the message with the device or app label for context.
// =============================================================================

void logError(String message) {
    if (settings.logEnable != false) {
        if (device) { log.error "${device.label ?: device.name}: ${message}" }
        if (app)    { log.error "${app.label ?: app.name}: ${message}" }
    }
}

void logWarn(String message) {
    if (settings.logEnable != false) {
        if (device) { log.warn "${device.label ?: device.name}: ${message}" }
        if (app)    { log.warn "${app.label ?: app.name}: ${message}" }
    }
}

void logInfo(String message) {
    if (settings.logEnable != false) {
        if (device) { log.info "${device.label ?: device.name}: ${message}" }
        if (app)    { log.info "${app.label ?: app.name}: ${message}" }
    }
}

void logDebug(String message) {
    if (settings.logEnable != false && settings.debugLogEnable != false) {
        if (device) { log.debug "${device.label ?: device.name}: ${message}" }
        if (app)    { log.debug "${app.label ?: app.name}: ${message}" }
    }
}

// =============================================================================
// APP DEFINITION & PREFERENCES
// =============================================================================

definition(
    name:        'Garage Door Controller',
    namespace:   'dwinks',
    author:      'Daniel Winks',
    description: 'Garage Door Controller',
    category:    '',
    iconUrl:     '',
    iconX2Url:   '',
    iconX3Url:   ''
)

preferences {
    page(name: 'mainPage', title: 'Garage Door Controller')
}

Map mainPage() {
    dynamicPage(
        name:           'mainPage',
        title:          '<h1>Garage Door Controller</h1>',
        install:        true,
        uninstall:      true,
        refreshInterval: 0
    ) {
        section('<b>Device Instructions</b>', hideable: true, hidden: true) {
            paragraph 'For a RATGDO, select the ESPHome RATGDO Garage Door driver below. Its direct open, close, and stop commands are used as the primary control path.'
            paragraph 'Upper sensor: reports open whenever the door has left fully closed. Required.'
            paragraph 'Lower sensor: reports open only when the door is fully open. Optional, but recommended for physical full-open verification.'
            paragraph 'The legacy relay input is retained only for existing non-RATGDO installations.'
        }

        section('<h2>Devices</h2>') {
            input 'doorUpperSensors', 'capability.contactSensor',
                title:    '<b>(Required) Upper tilt/contact sensor</b>',
                description: 'Mounted near top of door. Tilts (reports "open") almost immediately when door starts opening.',
                required: true,
                multiple: true
            input 'doorLowerSensors', 'capability.contactSensor',
                title:    '<b>(Optional) Lower tilt/contact sensor</b>',
                description: 'Mounted near bottom of door. Tilts (reports "open") only when door is nearly fully open.',
                required: false,
                multiple: true
            input 'ratgdoDoor', 'capability.garageDoorControl',
                title:    '<b>RATGDO Garage Door (recommended)</b>',
                description: 'Select a device using the ESPHome RATGDO Garage Door driver. This enables direct open, close, and stop commands.',
                required: false,
                multiple: false
            input 'relaySwitch', 'capability.switch',
                title:    '<b>Legacy Opener Relay Switch</b>',
                description: 'Only used when no RATGDO garage door device is selected.',
                required: false
            input 'disableModes', 'mode',
                title:    '<b>Disable Remote Access in modes</b>',
                multiple: true
        }

        section('<h2>Notification Devices</h2>') {
            input 'notificationDevices', 'capability.notification',
                title:    '<b>Notification Devices</b>',
                required: false,
                multiple: true
        }

        section('<h2>Button Control</h2>') {
            paragraph 'Select button devices to control the garage door. ' +
                'When the specified button number is pressed, the door toggles: ' +
                'open if closed, close if open. ' +
                'Presses are ignored while the door is already in motion (opening or closing). ' +
                'Mode restrictions (disableModes) are respected.'
            input 'buttonDevices', 'capability.pushableButton',
                title:    '<b>Button Device(s)</b>',
                required: false,
                multiple: true
            input 'buttonNumber', 'number',
                title:        '<b>Button Number</b>',
                description:  'Which button number to listen for (e.g., 1 for the top button).',
                required:     true,
                defaultValue: 1,
                range:        '1..99'
        }

        section('Logging') {
            input 'logEnable', 'bool',
                title:        'Enable Logging',
                required:     false,
                defaultValue: true
            input 'debugLogEnable', 'bool',
                title:        'Enable debug logging',
                required:     false,
                defaultValue: false
            input 'descriptionTextEnable', 'bool',
                title:        'Enable descriptionText logging',
                required:     false,
                defaultValue: true
        }

        section() {
            label title: 'Enter a name for this app instance', required: false
        }
    }
}

// =============================================================================
// LIFECYCLE HOOKS
//
// installed(): called once when the app is first installed.
// updated():   called each time preferences are saved.
// initialize(): compatibility alias; delegates to configure().
//
// All three paths call configure(), which rebuilds subscriptions, ensures
// the child device exists, and re-evaluates the current door state from
// sensor readings to keep everything in sync.
// =============================================================================

void installed() { configure() }
void updated()   { configure() }
void initialize() { configure() }

void configure() {
    unsubscribe()

    // Ensure the child device exists before subscribing to its events.
    // getDoorController() creates the child on first call if it doesn't exist.
    ChildDeviceWrapper doorController = getDoorController()

    if (hasRATGDO()) {
        subscribe(ratgdoDoor, ATTR_DOOR, ratgdoDoorEvent)
    } else if (relaySwitch != null) {
        subscribe(relaySwitch, ATTR_SWITCH, switchEvent)
    } else {
        logError('Configure a RATGDO Garage Door device or a legacy opener relay switch.')
    }
    subscribe(doorUpperSensors,  ATTR_CONTACT, upperContactEvent)
    subscribe(doorLowerSensors,  ATTR_CONTACT, lowerContactEvent)
    subscribe(doorController,    ATTR_DOOR,    doorControllerEvent)
    subscribe(buttonDevices,     ATTR_PUSHED,  buttonPushedEvent)

    // Push the current sensor-derived state to the child immediately.
    // On startup there is no motion, so immediate evaluation is correct.
    processContactSensors()

    // Hubitat may deliver the child's lifecycle events asynchronously.
    // Re-push state after a short delay to ensure it sticks.
    runInMillis(2000, resyncChildState)
}

// =============================================================================
// RELAY CONTROL
//
// The garage door opener is triggered by a momentary relay closure.
// relaySwitchOn() fires the relay; the switchEvent handler automatically
// turns it back off after RELAY_PULSE_DELAY_MS (a simulated button press).
// =============================================================================

void switchEvent(Event event) {
    logDebug("Received relay switch event: ${event.value}")
    if (event.value == SWITCH_ON) {
        runInMillis(RELAY_PULSE_DELAY_MS, 'relaySwitchOff', [overwrite: true])
    }
}

void relaySwitchOff() {
    if (relaySwitch == null) { return }
    relaySwitch.off()
    runInMillis(5000, 'relayStateVerification', [overwrite: true])
}
void relaySwitchOn()  {
    if (relaySwitch == null) {
        logError('No legacy opener relay switch is configured.')
        return
    }
    relaySwitch.on()
    runInMillis(RELAY_PULSE_DELAY_MS, 'relaySwitchOff', [overwrite: true])
}

// Verifies the relay state after a pulse; turns it off if still on after 5 seconds.
// Runs recursively every 5 seconds to ensure the relay is turned off.
// Calls refreshRelayState() to ensure the relay state is updated after turning it off.
void relayStateVerification() {
    if (relaySwitch == null) { return }
    if (relaySwitch.currentValue('switch', true) == SWITCH_ON) {
        relaySwitchOff()
        runInMillis(1000, 'refreshRelayState', [overwrite: true])
    } else {
        runInMillis(5000, 'relayStateVerification', [overwrite: true])
    }
}

// Refreshes the relay state by querying the relay switch's current value.
void refreshRelayState() {
    if (relaySwitch == null) { return }
    relaySwitch.refresh()
}

// =============================================================================
// CHILD DEVICE MANAGEMENT
//
// Returns the virtual child device that represents the garage door to the
// rest of the Hubitat ecosystem. The child is created eagerly in configure()
// (via installed/updated/initialize) so it always exists before any
// subscriptions or event handlers reference it.
//
// Child capabilities: GarageDoorControl (door) + ContactSensor (contact)
// =============================================================================

ChildDeviceWrapper getDoorController() {
    ChildDeviceWrapper doorController = getChildDevice(doorControllerId)
    if (!doorController) {
        doorController = addChildDevice(
            CHILD_NAMESPACE,
            CHILD_DRIVER,
            doorControllerId,
            [label: "${app.label}", isComponent: true]
        )
    }
    return doorController
}

String getDoorControllerId() {
    return "${app.id}${CHILD_ID_SUFFIX}"
}

// =============================================================================
// HELPER: SENSOR DISPLAY NAME
//
// Resolves a human-readable name for a sensor, preferring its user-assigned
// label over the internal device name.
// =============================================================================

String getSensorDisplayName(DeviceWrapper sensor) {
    String label = sensor.getLabel()
    return (label != null && label != '') ? label : sensor.getName()
}

// =============================================================================
// HELPER: SENSOR CONTACT MATCHING
//
// Returns the subset of a sensor list whose contact attribute matches the
// given target state. Accepts null lists (returns empty) for optional
// sensor groups like doorLowerSensors.
// =============================================================================

List<DeviceWrapper> findSensorsInContactState(List<DeviceWrapper> sensors, String contactState) {
    if (sensors == null) { return [] }
    return sensors.findAll { DeviceWrapper s ->
        s.currentState(ATTR_CONTACT).value == contactState
    }
}

// =============================================================================
// HELPER: LOWER-SENSOR AVAILABILITY
//
// Returns true when the user has configured at least one lower sensor,
// enabling detection of "partially open" vs "fully open".
// =============================================================================

Boolean hasDoorLowerSensors() {
    return doorLowerSensors != null && doorLowerSensors.size() > 0
}

/**
 * Returns true when this installation uses a RATGDO device with direct
 * open/close/stop control and authoritative movement state.
 */
Boolean hasRATGDO() {
    return ratgdoDoor != null
}

// =============================================================================
// DOOR STATE DETERMINATION (FINITE STATE MACHINE)
//
// This method evaluates both sensors and transitions to the appropriate
// settled state.  It is called in two contexts:
//
//   1. Immediately — on startup (configure / resyncChildState) and when
//      sensor events fire outside of a command state.
//   2. As the settle handler — when checkDoor fires after the command
//      timeout, or when a definitive sensor signal (lower sensor during
//      OPENING, upper sensor closing during CLOSING) triggers an immediate
//      transition.
//
// Important: this method NEVER produces OPENING or CLOSING.  Those are
// optimistic states set only by openDoor() / closeDoor().
//
// Sensor-driven state transitions:
//   Upper   Lower   →  New State
//   ------  ------     ---------
//   closed  closed     CLOSED          (door fully closed)
//   closed  open       UNKNOWN         (physically improbable)
//   open    (none)     OPEN            (no lower sensor configured)
//   open    closed     PARTIALLY_OPEN  (not yet fully open)
//   open    open       OPEN            (door fully open)
//
// =============================================================================

void processContactSensors() {
    ChildDeviceWrapper doorController = getDoorController()

    // Persist each sensor's current contact state on the child device so
    // other automations can inspect individual sensor readings if needed.
    updateSensorStatesOnChild(doorController)

    // Upper sensors are "tripped" when contact = "open" (door has left
    // the fully-closed position — the top panel has tilted).
    List<DeviceWrapper> trippedUpperSensors = findSensorsInContactState(doorUpperSensors, CONTACT_OPEN)
    // Lower sensors are "tripped" when contact = "open" (door has reached
    // the fully-open position — the bottom panel has tilted).
    List<DeviceWrapper> trippedLowerSensors = findSensorsInContactState(doorLowerSensors, CONTACT_OPEN)

    Integer upperSensorTrippedCount = trippedUpperSensors.size()
    Integer lowerSensorTrippedCount = trippedLowerSensors.size()
    Boolean hasLowerSensors         = hasDoorLowerSensors()

    String contactDoorState = determineDoorState(upperSensorTrippedCount, lowerSensorTrippedCount, hasLowerSensors)
    String doorState = contactDoorState
    if (hasRATGDO()) {
        String ratgdoState = ratgdoDoor.currentValue(ATTR_DOOR)?.toString() ?: STATE_UNKNOWN
        String currentChildState = doorController.currentValue(ATTR_DOOR)?.toString() ?: STATE_UNKNOWN
        doorState = resolveRATGDODoorState(ratgdoState, contactDoorState, currentChildState)
        logInfo("Door state resolved: '${doorState}' (ratgdo=${ratgdoState}, contacts=${contactDoorState}, upperTripped=${upperSensorTrippedCount}, lowerTripped=${lowerSensorTrippedCount})")
    } else {
        logInfo("Door state computed: '${doorState}' (upperTripped=${upperSensorTrippedCount}, lowerTripped=${lowerSensorTrippedCount}, hasLowerSensors=${hasLowerSensors})")
    }
    logSensorDiagnostics(trippedUpperSensors, trippedLowerSensors)

    // Publish the state to the virtual child device.
    // IMPORTANT: Use the app-level sendEvent(device, map) form, not
    // device.sendEvent(map). The app form ensures events are routed
    // correctly for parent\u2192child communication.
    sendEvent(doorController, [name: ATTR_DOOR, value: doorState])

    // Mirror terminal states to the "contact" attribute so the child device
    // also works as a contact sensor for apps that use that capability.
    if (doorState == STATE_OPEN || doorState == STATE_CLOSED) {
        sendEvent(doorController, [name: ATTR_CONTACT, value: doorState])
    }
}

/**
 * Combines RATGDO movement state with independently mounted contact sensors.
 * Contacts are definitive at their endpoints: the upper contact confirms
 * closed, while the lower contact confirms fully open. Between endpoints the
 * RATGDO movement state supplies direction and the contacts identify partial
 * opening after the motor is idle.
 */
String resolveRATGDODoorState(String ratgdoState, String contactDoorState, String currentChildState) {
    if (ratgdoState == STATE_OPENING) {
        return contactDoorState == STATE_OPEN ? STATE_OPEN : STATE_OPENING
    }
    if (ratgdoState == STATE_CLOSING) {
        return contactDoorState == STATE_CLOSED ? STATE_CLOSED : STATE_CLOSING
    }

    // A poll can briefly return the pre-command endpoint state. Preserve the
    // commanded direction until RATGDO reports movement or a contact confirms
    // the expected endpoint.
    if (currentChildState == STATE_OPENING && ratgdoState == STATE_CLOSED && contactDoorState != STATE_CLOSED) {
        return STATE_OPENING
    }
    if (currentChildState == STATE_CLOSING && ratgdoState == STATE_OPEN && contactDoorState != STATE_OPEN) {
        return STATE_CLOSING
    }

    if (ratgdoState == STATE_OPEN) {
        return contactDoorState == STATE_UNKNOWN ? STATE_UNKNOWN : contactDoorState
    }
    if (ratgdoState == STATE_CLOSED) {
        return contactDoorState == STATE_CLOSED ? STATE_CLOSED : STATE_UNKNOWN
    }

    return contactDoorState
}

/**
 * Re-evaluates and re-pushes the door state to the child device.
 * Used by configure() as a deferred safety net to overwrite any stale
 * values that may have been set by the child's asynchronous lifecycle events.
 */
void resyncChildState() {
    logDebug('Deferred re-sync: pushing current door state to child device.')
    processContactSensors()
}

/**
 * Logs per-sensor diagnostic information to help identify misconfiguration.
 * Shows which sensors are in each group, their current contact state,
 * and whether they are considered "tripped" for the state calculation.
 */
void logSensorDiagnostics(List<DeviceWrapper> trippedUpper, List<DeviceWrapper> trippedLower) {
    if (!(settings.logEnable != false && settings.debugLogEnable != false)) { return }

    logDebug('── Upper sensors ──')
    logSensorGroup('  doorUpperSensors', doorUpperSensors, trippedUpper)

    logDebug('── Lower sensors ──')
    logSensorGroup('  doorLowerSensors', doorLowerSensors, trippedLower)
}

void logSensorGroup(String label, List<DeviceWrapper> sensors, List<DeviceWrapper> trippedList) {
    if (sensors == null || sensors.size() == 0) {
        logDebug("${label}: (none configured)")
        return
    }
    sensors.each { DeviceWrapper s ->
        String contact = s.currentState(ATTR_CONTACT)?.value ?: '(no reading)'
        Boolean isTripped = trippedList?.contains(s) ?: false
        logDebug("${label}: ${getSensorDisplayName(s)} = '${contact}' ${isTripped ? '\u2190 TRIPPED' : ''}")
    }
}

/**
 * Writes each contact sensor's current state as a named state variable
 * on the child device. The variable name is the sensor's display name.
 */
void updateSensorStatesOnChild(ChildDeviceWrapper doorController) {
    doorUpperSensors.each { DeviceWrapper sensor ->
        doorController.setState(getSensorDisplayName(sensor), sensor.currentState(ATTR_CONTACT).value)
    }
    doorLowerSensors?.each { DeviceWrapper sensor ->
        doorController.setState(getSensorDisplayName(sensor), sensor.currentState(ATTR_CONTACT).value)
    }
}

/**
 * Pure function: maps upper/lower sensor readings to a door state string.
 *
 * Sensor semantics:
 *   • Upper sensor tripped (contact = "open") → door has left fully-closed.
 *   • Lower sensor tripped (contact = "open") → door has reached fully-open.
 *
 * @param upperSensorTrippedCount  How many upper sensors are tripped (>0 = door NOT fully closed)
 * @param lowerSensorTrippedCount  How many lower sensors are tripped (>0 = door IS fully open)
 * @param hasLowerSensors          Whether the user configured any lower sensors
 * @return One of: STATE_OPEN, STATE_CLOSED, STATE_PARTIALLY_OPEN, STATE_UNKNOWN
 */
String determineDoorState(Integer upperSensorTrippedCount, Integer lowerSensorTrippedCount, Boolean hasLowerSensors) {
    Boolean doorLeftClosed = upperSensorTrippedCount > 0     // Upper sensor tilted → door not at fully-closed
    Boolean doorAtFullOpen = hasLowerSensors && lowerSensorTrippedCount > 0  // Lower sensor tilted → door at fully-open

    // ── Upper sensor NOT tripped → door is at fully-closed position ───────
    if (!doorLeftClosed) {
        if (doorAtFullOpen) { return STATE_UNKNOWN }  // Upper=closed + Lower=open — physically improbable
        return STATE_CLOSED                            // Upper=closed — door is fully closed
    }

    // ── Upper sensor IS tripped → door has left the fully-closed position ─
    if (!hasLowerSensors) { return STATE_OPEN }         // No lower sensors — assume fully open
    if (doorAtFullOpen)    { return STATE_OPEN }         // Lower sensor confirms fully open
    return STATE_PARTIALLY_OPEN                          // Upper=open + Lower=closed — not yet fully open
}

// =============================================================================
// SENSOR EVENT HANDLERS
//
// These fire when any contact sensor changes state.  The behaviour depends
// on whether the door is currently executing a command (OPENING/CLOSING):
//
//   • During OPENING:
//       - Lower sensor fires → door IS fully open; transition to OPEN
//         immediately and cancel the settle timeout.
//       - Upper sensor fires (only upper sensor configured) → door has
//         started moving but we cannot confirm full-open yet; re-arm the
//         settle timer to give the door time to finish.
//       - Upper sensor fires (lower sensor also configured) → door has
//         started moving; wait for lower sensor or settle timeout.
//   • During CLOSING:
//       - Upper sensor reports "closed" → door IS fully closed; transition
//         to CLOSED immediately and cancel the settle timeout.
//   • Not in a command state (idle / settled):
//       - Evaluate sensors immediately — no deferral needed.
// =============================================================================

/**
 * Handles events from upper sensors.  The upper sensor tilts almost
 * immediately when the door starts opening, and is the last sensor to
 * un-tilt when the door closes.
 */
void upperContactEvent(Event event) {
    logDebug("Upper-sensor event (${getSensorDisplayName(event.getDevice())}): ${event.value}")
    ChildDeviceWrapper doorController = getDoorController()
    String currentState = doorController.currentState(ATTR_DOOR).value

    if (currentState == STATE_CLOSING && event.value == CONTACT_CLOSED) {
        // Door was closing and upper sensor confirms fully closed.
        logInfo('Upper sensor reports closed while closing — door is now closed.')
        unschedule('checkDoor')
        processContactSensors()
    } else if (currentState == STATE_OPENING && !hasDoorLowerSensors()) {
        // Only upper sensor available — door has started opening but we
        // cannot confirm full-open from this event alone.  Re-arm the
        // settle timer to give the door time to finish its travel.
        logDebug('Upper sensor fired during opening (no lower sensor) — re-arming settle timer.')
        runInMillis(SENSOR_SETTLE_DELAY_MS, 'checkDoor', [overwrite: true])
    } else if (!isTransientCommandState(currentState)) {
        // Not in a command state — evaluate immediately.
        processContactSensors()
    }
    // else: OPENING with lower sensors — upper sensor firing is expected;
    // we wait for the lower sensor or the settle timeout.
}

/**
 * Handles events from lower sensors.  The lower sensor only tilts when
 * the door is nearly fully open, making it a definitive "fully open" signal.
 */
void lowerContactEvent(Event event) {
    logDebug("Lower-sensor event (${getSensorDisplayName(event.getDevice())}): ${event.value}")
    ChildDeviceWrapper doorController = getDoorController()
    String currentState = doorController.currentState(ATTR_DOOR).value

    if (currentState == STATE_OPENING && event.value == CONTACT_OPEN) {
        // Door was opening and lower sensor confirms fully open.
        logInfo('Lower sensor reports open while opening — door is now fully open.')
        unschedule('checkDoor')
        processContactSensors()
    } else if (!isTransientCommandState(currentState)) {
        // Not in a command state — evaluate immediately.
        processContactSensors()
    }
}

/**
 * Handles state updates from the ESPHome RATGDO Garage Door driver. RATGDO
 * supplies authoritative motion direction; processContactSensors() merges that
 * state with the independent upper/lower endpoint contacts.
 */
void ratgdoDoorEvent(Event event) {
    logDebug("RATGDO door event: ${event.value}")
    processContactSensors()
}

// =============================================================================
// DOOR CONTROLLER EVENT HANDLER
//
// Listens for state changes on the child device and sends user notifications
// only for terminal or error states (open, closed, unknown). Transient states
// like "opening" and "partially open" are intentionally not notified to avoid
// alert fatigue.
// =============================================================================

void doorControllerEvent(Event event) {
    logDebug("Door controller state changed: ${event.value}")
    if (event.value in NOTIFY_STATES) {
        String message = "The garage door is currently ${event.value}"
        if (notificationDevices) {
            notificationDevices*.deviceNotification(message)
        }
    }
}

// =============================================================================
// DOOR OPEN / CLOSE COMMANDS
//
// These are the primary actions: optimistically set the door state to
// OPENING / CLOSING immediately, activate the relay (which toggles the
// opener motor), and schedule a settle-and-check timer.  The relay is
// pulsed momentarily — the switchEvent handler turns it off after
// RELAY_PULSE_DELAY_MS.
//
// The settle timer (checkDoor) serves as a command timeout: if sensor
// feedback hasn't confirmed the expected movement by the time it fires,
// the door is considered unresponsive.
// =============================================================================

void openDoor() {
    sendDoorEvent(STATE_OPENING)   // Optimistic: report "opening" immediately
    if (hasRATGDO()) {
        ratgdoDoor.open()
        runIn(2, 'processContactSensors', [overwrite: true])
    } else {
        relaySwitchOn()
        runInMillis(SENSOR_SETTLE_DELAY_MS, 'checkDoor', [overwrite: true])
    }
}

void closeDoor() {
    sendDoorEvent(STATE_CLOSING)   // Optimistic: report "closing" immediately
    if (hasRATGDO()) {
        ratgdoDoor.close()
        runIn(2, 'processContactSensors', [overwrite: true])
    } else {
        relaySwitchOn()
        runInMillis(SENSOR_SETTLE_DELAY_MS, 'checkDoor', [overwrite: true])
    }
}

void stopDoor() {
    if (!hasRATGDO()) {
        logWarn('Stop requires a RATGDO Garage Door device; the legacy relay can only toggle.')
        return
    }
    ratgdoDoor.stop()
    runIn(2, 'processContactSensors', [overwrite: true])
}

/**
 * Sets the door state optimistically on the child device.
 * Used internally by openDoor() and closeDoor() to immediately publish
 * the transient "opening" / "closing" state before sensor feedback
 * confirms the result.
 */
void sendDoorEvent(String doorState) {
    ChildDeviceWrapper doorController = getDoorController()
    sendEvent(doorController, [name: ATTR_DOOR, value: doorState])
}

// =============================================================================
// MODE-BASED ACCESS RESTRICTION
//
// componentClose() and componentOpen() are called by the child device's
// component interface (e.g., from Dashboards or voice assistants). If the
// current hub mode is in the user's disable list, the command is silently
// ignored. A null guard prevents NPE when no modes are configured.
// =============================================================================

void componentClose(DeviceWrapper device) {
    if (isRemoteAccessDisabled()) { return }
    closeDoor()
}

void componentOpen(DeviceWrapper device) {
    if (isRemoteAccessDisabled()) { return }
    openDoor()
}

void componentStop(DeviceWrapper device) {
    if (isRemoteAccessDisabled()) { return }
    stopDoor()
}

/**
 * Returns true if the current hub mode matches one of the user-selected
 * modes where remote door operation should be blocked.
 */
Boolean isRemoteAccessDisabled() {
    return settings.disableModes && location.mode in settings.disableModes
}

// =============================================================================
// BUTTON CONTROL
//
// Physical or virtual button devices (capability.pushableButton) can control
// the garage door. When the configured button number is pressed, the selected
// action (Toggle / Open / Close) is performed. Mode restrictions are respected.
// =============================================================================

void buttonPushedEvent(Event event) {
    logDebug("Button pushed: device=${getSensorDisplayName(event.getDevice())}, button=${event.value}")

    // Only respond to the configured button number.
    Integer configuredButton = (settings.buttonNumber ?: 1) as Integer
    if (event.value != configuredButton.toString()) {
        logDebug("Button ${event.value} ignored — listening for button ${configuredButton}.")
        return
    }

    // Respect mode-based access restrictions.
    if (isRemoteAccessDisabled()) {
        logDebug('Button blocked — current hub mode is in the disable list.')
        return
    }

    ChildDeviceWrapper doorController = getDoorController()
    String currentState = doorController.currentState(ATTR_DOOR).value

    // Never send a relay command while the door is already in motion.
    if (isTransientCommandState(currentState)) {
        logDebug("Button ignored — door is currently '${currentState}' (in motion).")
        return
    }

    // Toggle: open if closed, close if open.
    if (currentState == STATE_OPEN) {
        logInfo("Button toggle: door is 'open' — closing.")
        closeDoor()
    } else if (currentState == STATE_CLOSED) {
        logInfo("Button toggle: door is 'closed' — opening.")
        openDoor()
    } else {
        // 'partially open' or 'unknown' — not safe to toggle.
        logDebug("Button ignored — door is '${currentState}' (not clearly open or closed).")
    }
}

// =============================================================================
// DOOR MOVEMENT VERIFICATION (COMMAND TIMEOUT)
//
// Called SENSOR_SETTLE_DELAY_MS after a door open/close command.
//
// If sensor events have already confirmed the transition (e.g., lower
// sensor fired during OPENING, or upper sensor reported closed during
// CLOSING), the scheduled checkDoor will have been cancelled and this
// method will never run.
//
// If we get here, the optimistic OPENING/CLOSING state is still active.
// We evaluate the current sensor readings to determine whether the door
// actually moved, and transition to the appropriate settled state or
// log an error if the door didn't respond.
// =============================================================================

void checkDoor() {
    if (hasRATGDO()) {
        processContactSensors()
        return
    }
    ChildDeviceWrapper doorController = getDoorController()
    String currentState = doorController.currentState(ATTR_DOOR).value

    if (!isTransientCommandState(currentState)) {
        // Already settled (shouldn't normally happen since we unschedule
        // on confirmation, but handle gracefully).
        logDebug("checkDoor: door already settled to '${currentState}'.")
        return
    }

    // Check whether the key sensor indicates the door actually moved.
    Boolean upperTripped = findSensorsInContactState(doorUpperSensors, CONTACT_OPEN).size() > 0

    if (currentState == STATE_OPENING) {
        if (upperTripped) {
            // Door has at least started opening — evaluate and transition.
            logInfo('checkDoor: door moved (upper sensor tripped) — settling state.')
            processContactSensors()
        } else {
            logError(
                "Door failed to open — upper sensor still not tripped " +
                "after ${SENSOR_SETTLE_DELAY_MS}ms. " +
                'Check opener relay, sensor batteries, and door mechanics.'
            )
        }
    } else if (currentState == STATE_CLOSING) {
        if (!upperTripped) {
            // Door has closed — evaluate and transition.
            logInfo('checkDoor: door closed (upper sensor not tripped) — settling state.')
            processContactSensors()
        } else {
            logError(
                "Door failed to close — upper sensor still tripped " +
                "after ${SENSOR_SETTLE_DELAY_MS}ms. " +
                'Check opener relay, sensor batteries, and door mechanics.'
            )
        }
    }
}

/**
 * Returns true if the given state is an optimistic command state
 * (opening/closing) that has not yet been confirmed by sensor feedback.
 */
Boolean isTransientCommandState(String doorState) {
    return doorState == STATE_OPENING || doorState == STATE_CLOSING
}
