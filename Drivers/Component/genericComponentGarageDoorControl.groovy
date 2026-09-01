/**
 *  Generic Component Smart Garage Door Control
 *
 *  Virtual child device driver created by the Garage Door Controller parent app.
 *  Implements the standard Hubitat GarageDoorControl and ContactSensor
 *  capabilities so the door can be controlled from Dashboards, Rule Machine,
 *  Alexa, Google Home, etc.
 *
 *  Parent-child communication:
 *    Child → Parent: open() / close() / refresh() → parent component handlers
 *    Parent → Child: sendEvent() for door/contact attributes, setState() for sensor data
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

metadata {
    definition(
        name:      'Generic Component Smart Garage Door Control',
        namespace: 'dwinks',
        author:    'Daniel Winks',
        component: true
    ) {
        capability 'GarageDoorControl'  // door  — ENUM ['unknown', 'open', 'closing', 'closed', 'opening']
        capability 'ContactSensor'      // contact — ENUM ['closed', 'open']
        capability 'Refresh'
        command 'stop'
        command 'clearState'
    }
}

// =============================================================================
// LIFECYCLE HOOKS
// =============================================================================

void installed() {
    // Do NOT sendEvent here — Hubitat may deliver these events after
    // the parent app has already pushed the correct state via configure(),
    // causing the child to show stale values. The parent owns the door
    // and contact attributes; the child just logs and waits.
    logDebug('Child device installed.')
}

void initialize() {
    // Hubitat calls initialize() after installed(). If the parent app
    // has already sent the door state, this is a no-op. If not, the
    // parent's configure() will push state on the next sensor read.
    logDebug('Child device initialized.')
}

void updated() {
    logDebug('Child device updated.')
}

// =============================================================================
// COMMANDS (from external consumers → forwarded to parent app)
// =============================================================================

void open() {
    logDebug('Received open() command — forwarding to parent app.')
    parent?.componentOpen(device)
}

void close() {
    logDebug('Received close() command — forwarding to parent app.')
    parent?.componentClose(device)
}

void stop() {
    logDebug('Received stop() command — forwarding to parent app.')
    parent?.componentStop(device)
}

void refresh() {
    logDebug('Received refresh() command — forwarding to parent app.')
    parent?.componentRefresh()
}

// =============================================================================
// STATE MANAGEMENT (used by parent app to store per-sensor readings)
// =============================================================================

void setState(String stateName, String stateValue) {
    state[stateName] = stateValue
}

void clearState() {
    state.clear()
}

// =============================================================================
// LOGGING
//
// Component child devices do not have their own preferences, so we log
// unconditionally at debug level. The parent app controls verbosity.
// =============================================================================

void logDebug(String message) {
    log.debug "${device.label ?: device.name}: ${message}"
}
