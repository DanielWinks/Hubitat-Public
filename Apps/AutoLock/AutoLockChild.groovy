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
// AUTO LOCK CHILD APP
// =============================================================================
// Description: Manages automatic locking and unlocking of door locks based on
//              configurable triggers including presence, mode, time of day,
//              contact sensors, motion sensors, and switches.
//
// Safety: The app will NEVER lock if the door contact sensor reports "open"
//         or if the door state is unavailable.
//
// Pages:
//   - Main Page: Lock selection, door contact sensor, auto re-lock, notifications
//   - Means to Lock: Triggers that cause the lock(s) to lock
//   - Means to Unlock: Triggers that cause the lock(s) to unlock
// =============================================================================

#include dwinks.UtilitiesAndLoggingLibrary

import com.hubitat.app.DeviceWrapper
import com.hubitat.hub.domain.Event

definition(
  name: 'Auto Lock Child',
  namespace: 'dwinks',
  author: 'Daniel Winks',
  description: 'Automatically lock and unlock a door based on presence, mode, time, contact sensors, motion sensors, and switches.',
  category: 'Safety & Security',
  parent: 'dwinks:Auto Lock',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  documentationLink: ''
)

// =============================================================================
// Preferences / Pages
// =============================================================================

preferences {
  page(name: 'mainPage')
  page(name: 'meansToLockPage')
  page(name: 'meansToUnlockPage')
}

// =============================================================================
// Main Page
// =============================================================================

Map mainPage() {
  return dynamicPage(name: 'mainPage', title: '<h1>Auto Lock</h1>', nextPage: 'meansToLockPage', install: false, uninstall: true) {
    section('<h2>Lock Selection</h2>') {
      input('locks', 'capability.lock', title: 'Lock(s) to control', required: true, multiple: true)
    }

    section('<h2>Door Contact Sensor (Safety)</h2>') {
      paragraph '<i>The app will <b>never</b> lock if the door contact sensor reports "open" or if the state is unavailable. This prevents locking while the door is ajar.</i>'
      input('doorContactSensor', 'capability.contactSensor', title: 'Door contact sensor', required: false, multiple: false)
    }

    section('<h2>Door Left Open Warning</h2>') {
      paragraph '<i>Send a notification if the door contact sensor stays open for too long.</i>'
      input('doorLeftOpenEnabled', 'bool', title: 'Enable door left open warning', required: false, defaultValue: false, submitOnChange: true)
      if (settings.doorLeftOpenEnabled) {
        input('doorLeftOpenMinutes', 'number', title: 'Warn after door is open for (minutes)', required: true, defaultValue: 5, range: '1..60')
        input('doorLeftOpenRepeatMinutes', 'number', title: 'Repeat warning every (minutes, 0 = no repeat)', required: false, defaultValue: 0, range: '0..60')
      }
    }

    section('<h2>Auto Re-Lock</h2>') {
      input('autoReLockEnabled', 'bool', title: 'Enable auto re-lock after unlock', required: false, defaultValue: false, submitOnChange: true)
      if (settings.autoReLockEnabled) {
        input('autoReLockDelay', 'number', title: 'Re-lock delay (seconds)', required: true, defaultValue: 300, range: '5..3600')
      }
    }

    section('<h2>Lock Confirmation</h2>') {
      paragraph '<i>After a lock command is sent, verify the lock actually reports "locked". Sends a notification if it does not.</i>'
      input('lockConfirmEnabled', 'bool', title: 'Enable lock confirmation check', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockConfirmEnabled) {
        input('lockConfirmDelay', 'number', title: 'Check lock state after (seconds)', required: true, defaultValue: 15, range: '5..120')
      }
    }

    section('<h2>Temporary Disable</h2>') {
      paragraph '<i>When this switch is ON, all automatic locking and unlocking is suppressed.</i>'
      input('disableSwitch', 'capability.switch', title: 'Disable switch (optional)', required: false, multiple: false)
    }

    section('<h2>Battery Monitoring</h2>') {
      paragraph '<i>Check lock battery levels once daily at the configured time and notify if below threshold.</i>'
      input('batteryAlertEnabled', 'bool', title: 'Enable low battery alert', required: false, defaultValue: false, submitOnChange: true)
      if (settings.batteryAlertEnabled) {
        input('batteryThreshold', 'number', title: 'Alert when battery below (%)', required: true, defaultValue: 20, range: '5..50')
        input('batteryAlertTime', 'time', title: 'Check battery at this time daily', required: true)
      }
    }

    section('<h2>Notifications</h2>') {
      input('notificationDevices', 'capability.notification', title: 'Notification devices', required: false, multiple: true)
      if (settings.notificationDevices) {
        input('notifyOnLock', 'bool', title: 'Notify when locked', required: false, defaultValue: true)
        input('notifyOnUnlock', 'bool', title: 'Notify when unlocked', required: false, defaultValue: true)
        input('notifyOnSafetyBlock', 'bool', title: 'Notify when lock is blocked (door open)', required: false, defaultValue: true)
        input('notifyOnConfirmFail', 'bool', title: 'Notify when lock confirmation fails', required: false, defaultValue: true)
        input('notifyOnJammed', 'bool', title: 'Notify when lock reports jammed/unknown', required: false, defaultValue: true)
        input('notifyOnDoorLeftOpen', 'bool', title: 'Notify when door is left open', required: false, defaultValue: true)
        input('notifyOnBatteryLow', 'bool', title: 'Notify when lock battery is low', required: false, defaultValue: true)
      }
    }

    section('<h2>Logging</h2>') {
      input('logEnable', 'bool', title: 'Enable Logging', required: false, defaultValue: true)
      input('debugLogEnable', 'bool', title: 'Enable debug logging', required: false, defaultValue: false)
      input('descriptionTextEnable', 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true)
    }

    section() {
      label title: 'Enter a name for this app instance', required: true
    }
  }
}

// =============================================================================
// Means to Lock Page
// =============================================================================

Map meansToLockPage() {
  return dynamicPage(name: 'meansToLockPage', title: '<h1>Means to Lock</h1>', nextPage: 'meansToUnlockPage', install: false, uninstall: false) {
    section() {
      paragraph '<i>Configure one or more triggers that will cause the lock(s) to lock. Any single trigger firing will lock the door (OR logic).</i>'
    }

    // --- Presence: Everyone Left ---
    section('<h2>Everyone Left</h2>') {
      input('lockPresenceEnabled', 'bool', title: 'Enable lock on everyone leaving', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockPresenceEnabled) {
        input('lockPresenceSensors', 'capability.presenceSensor', title: 'Presence sensors to monitor', required: true, multiple: true)
        input('lockPresenceDelay', 'number', title: 'Delay after last person leaves (seconds)', required: false, defaultValue: 0, range: '0..3600')
      }
    }

    // --- Mode ---
    section('<h2>Hubitat Mode</h2>') {
      input('lockModeEnabled', 'bool', title: 'Enable lock on mode change', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockModeEnabled) {
        input('lockModes', 'mode', title: 'Lock when hub enters these modes', required: true, multiple: true)
      }
    }

    // --- Time of Day ---
    section('<h2>Time of Day</h2>') {
      input('lockTimeEnabled', 'bool', title: 'Enable lock at specific time', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockTimeEnabled) {
        input('lockTime', 'time', title: 'Lock at this time daily', required: true)
      }
      input('lockTimeRangeEnabled', 'bool', title: 'Enable lock when entering a time range', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockTimeRangeEnabled) {
        input('lockTimeRangeStart', 'time', title: 'Time range start (lock when entering)', required: true)
        input('lockTimeRangeEnd', 'time', title: 'Time range end', required: true)
      }
    }

    // --- Contact Sensors ---
    section('<h2>Contact Sensors</h2>') {
      input('lockContactEnabled', 'bool', title: 'Enable lock on contact sensor closing', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockContactEnabled) {
        input('lockContactSensors', 'capability.contactSensor', title: 'Contact sensors', required: true, multiple: true)
        input('lockContactDelay', 'number', title: 'Delay after close before locking (seconds)', required: false, defaultValue: 10, range: '0..3600')
      }
    }

    // --- Motion Sensors ---
    section('<h2>Motion Sensors</h2>') {
      input('lockMotionEnabled', 'bool', title: 'Enable lock on motion inactive', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockMotionEnabled) {
        input('lockMotionSensors', 'capability.motionSensor', title: 'Motion sensors', required: true, multiple: true)
        input('lockMotionDelay', 'number', title: 'Delay after motion stops (seconds)', required: false, defaultValue: 60, range: '0..3600')
      }
    }

    // --- Switches ---
    section('<h2>Switches</h2>') {
      input('lockSwitchEnabled', 'bool', title: 'Enable lock on switch state change', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockSwitchEnabled) {
        input('lockSwitches', 'capability.switch', title: 'Switches', required: true, multiple: true)
        input('lockSwitchState', 'enum', title: 'Lock when switch turns', options: ['on', 'off'], required: true, defaultValue: 'on')
      }
    }

    // --- HSM ---
    section('<h2>Hubitat Safety Monitor (HSM)</h2>') {
      input('lockHsmEnabled', 'bool', title: 'Enable lock on HSM arm', required: false, defaultValue: false, submitOnChange: true)
      if (settings.lockHsmEnabled) {
        input('lockHsmStates', 'enum', title: 'Lock when HSM enters these states', options: ['armedAway', 'armedHome', 'armedNight'], required: true, multiple: true)
      }
    }
  }
}

// =============================================================================
// Means to Unlock Page
// =============================================================================

Map meansToUnlockPage() {
  return dynamicPage(name: 'meansToUnlockPage', title: '<h1>Means to Unlock</h1>', install: true, uninstall: false) {
    section() {
      paragraph '<i>Configure one or more triggers that will cause the lock(s) to unlock. Any single trigger firing will unlock the door (OR logic).</i>'
    }

    // --- Presence: Anyone Arrived ---
    section('<h2>Anyone Arrived</h2>') {
      input('unlockPresenceEnabled', 'bool', title: 'Enable unlock on anyone arriving', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockPresenceEnabled) {
        input('unlockPresenceSensors', 'capability.presenceSensor', title: 'Presence sensors to monitor', required: true, multiple: true)
      }
    }

    // --- Mode ---
    section('<h2>Hubitat Mode</h2>') {
      input('unlockModeEnabled', 'bool', title: 'Enable unlock on mode change', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockModeEnabled) {
        input('unlockModes', 'mode', title: 'Unlock when hub enters these modes', required: true, multiple: true)
      }
    }

    // --- Time of Day ---
    section('<h2>Time of Day</h2>') {
      input('unlockTimeEnabled', 'bool', title: 'Enable unlock at specific time', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockTimeEnabled) {
        input('unlockTime', 'time', title: 'Unlock at this time daily', required: true)
      }
      input('unlockTimeRangeEnabled', 'bool', title: 'Enable unlock when leaving a time range', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockTimeRangeEnabled) {
        input('unlockTimeRangeStart', 'time', title: 'Time range start', required: true)
        input('unlockTimeRangeEnd', 'time', title: 'Time range end (unlock when leaving)', required: true)
      }
    }

    // --- Contact Sensors ---
    section('<h2>Contact Sensors</h2>') {
      input('unlockContactEnabled', 'bool', title: 'Enable unlock on contact sensor opening', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockContactEnabled) {
        input('unlockContactSensors', 'capability.contactSensor', title: 'Contact sensors', required: true, multiple: true)
      }
    }

    // --- Motion Sensors ---
    section('<h2>Motion Sensors</h2>') {
      input('unlockMotionEnabled', 'bool', title: 'Enable unlock on motion active', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockMotionEnabled) {
        input('unlockMotionSensors', 'capability.motionSensor', title: 'Motion sensors', required: true, multiple: true)
      }
    }

    // --- Switches ---
    section('<h2>Switches</h2>') {
      input('unlockSwitchEnabled', 'bool', title: 'Enable unlock on switch state change', required: false, defaultValue: false, submitOnChange: true)
      if (settings.unlockSwitchEnabled) {
        input('unlockSwitches', 'capability.switch', title: 'Switches', required: true, multiple: true)
        input('unlockSwitchState', 'enum', title: 'Unlock when switch turns', options: ['on', 'off'], required: true, defaultValue: 'on')
      }
    }

  }
}

// =============================================================================
// Lifecycle Methods
// =============================================================================

void configure() {
  unsubscribe()
  unschedule()
  initialize()
}

void uninstalled() {
  unsubscribe()
  unschedule()
}

void initialize() {
  state.pendingLock = false
  state.pendingReLock = false

  subscribeLockTriggers()
  subscribeUnlockTriggers()
  subscribeModeHandler()
  subscribeHsmHandler()
  subscribeLockEvents()
  subscribeLockMonitoring()
  subscribeDoorLeftOpen()
  scheduleTriggers()
}

// =============================================================================
// Subscriptions
// =============================================================================

private void subscribeLockTriggers() {
  // Presence: lock when everyone leaves
  if (settings.lockPresenceEnabled && settings.lockPresenceSensors) {
    subscribe(settings.lockPresenceSensors, 'presence', 'lockPresenceHandler')
  }

  // Mode -- handled by consolidated modeChangeHandler (see below)

  // Contact sensors: lock when closed
  if (settings.lockContactEnabled && settings.lockContactSensors) {
    subscribe(settings.lockContactSensors, 'contact.closed', 'lockContactClosedHandler')
  }

  // Motion sensors: lock when inactive
  if (settings.lockMotionEnabled && settings.lockMotionSensors) {
    subscribe(settings.lockMotionSensors, 'motion.inactive', 'lockMotionInactiveHandler')
  }

  // Switches
  if (settings.lockSwitchEnabled && settings.lockSwitches) {
    String eventValue = "switch.${settings.lockSwitchState}"
    subscribe(settings.lockSwitches, eventValue, 'lockSwitchHandler')
  }

  // Time range: check if we are currently in the lock time range at startup
  if (settings.lockTimeRangeEnabled && settings.lockTimeRangeStart && settings.lockTimeRangeEnd) {
    if (timeOfDayIsBetween(toDateTime(settings.lockTimeRangeStart), toDateTime(settings.lockTimeRangeEnd), new Date(), location.timeZone)) {
      logDebug('Currently within lock time range at startup')
      lockWithSafetyCheck('lock time range (startup)')
    }
  }
}

private void subscribeUnlockTriggers() {
  // Presence: unlock when anyone arrives
  if (settings.unlockPresenceEnabled && settings.unlockPresenceSensors) {
    subscribe(settings.unlockPresenceSensors, 'presence.present', 'unlockPresenceHandler')
  }

  // Mode -- handled by consolidated modeChangeHandler (see below)

  // Contact sensors: unlock when opened
  if (settings.unlockContactEnabled && settings.unlockContactSensors) {
    subscribe(settings.unlockContactSensors, 'contact.open', 'unlockContactOpenHandler')
  }

  // Motion sensors: unlock when active
  if (settings.unlockMotionEnabled && settings.unlockMotionSensors) {
    subscribe(settings.unlockMotionSensors, 'motion.active', 'unlockMotionActiveHandler')
  }

  // Switches
  if (settings.unlockSwitchEnabled && settings.unlockSwitches) {
    String eventValue = "switch.${settings.unlockSwitchState}"
    subscribe(settings.unlockSwitches, eventValue, 'unlockSwitchHandler')
  }

  // Time range: check if we are currently outside the unlock time range at startup
  // (i.e., unlock time range has ended and we should have unlocked)
  if (settings.unlockTimeRangeEnabled && settings.unlockTimeRangeStart && settings.unlockTimeRangeEnd) {
    if (!timeOfDayIsBetween(toDateTime(settings.unlockTimeRangeStart), toDateTime(settings.unlockTimeRangeEnd), new Date(), location.timeZone)) {
      logDebug('Currently outside unlock time range at startup -- unlocking')
      performUnlock('unlock time range (startup)')
    }
  }
}

private void subscribeModeHandler() {
  // Use a single subscription to location.mode to avoid race conditions
  // when the same mode appears in both lock and unlock mode lists
  Boolean needsMode = (settings.lockModeEnabled && settings.lockModes) ||
                      (settings.unlockModeEnabled && settings.unlockModes)
  if (needsMode) {
    subscribe(location, 'mode', 'modeChangeHandler')
  }
}

private void subscribeHsmHandler() {
  if (settings.lockHsmEnabled && settings.lockHsmStates) {
    subscribe(location, 'hsmStatus', 'hsmStatusHandler')
  }
}

private void subscribeLockEvents() {
  // Monitor lock state changes for auto re-lock
  if (settings.autoReLockEnabled && settings.locks) {
    subscribe(settings.locks, 'lock.unlocked', 'lockUnlockedHandler')
  }
}

private void subscribeLockMonitoring() {
  if (!settings.locks || !settings.notificationDevices) { return }

  // Jammed / unknown state monitoring
  subscribe(settings.locks, 'lock', 'lockStateMonitorHandler')
}

private void subscribeDoorLeftOpen() {
  if (settings.doorLeftOpenEnabled && settings.doorContactSensor && settings.doorLeftOpenMinutes) {
    subscribe(settings.doorContactSensor, 'contact', 'doorLeftOpenContactHandler')
  }
}

private void scheduleTriggers() {
  // Lock at specific time
  if (settings.lockTimeEnabled && settings.lockTime) {
    schedule(settings.lockTime, 'scheduledLock')
    logDebug("Lock scheduled daily at ${settings.lockTime}")
  }

  // Unlock at specific time
  if (settings.unlockTimeEnabled && settings.unlockTime) {
    schedule(settings.unlockTime, 'scheduledUnlock')
    logDebug("Unlock scheduled daily at ${settings.unlockTime}")
  }

  // Lock time range start
  if (settings.lockTimeRangeEnabled && settings.lockTimeRangeStart) {
    schedule(settings.lockTimeRangeStart, 'scheduledLockTimeRangeStart')
    logDebug("Lock time range start scheduled at ${settings.lockTimeRangeStart}")
  }

  // Unlock time range end
  if (settings.unlockTimeRangeEnabled && settings.unlockTimeRangeEnd) {
    schedule(settings.unlockTimeRangeEnd, 'scheduledUnlockTimeRangeEnd')
    logDebug("Unlock time range end scheduled at ${settings.unlockTimeRangeEnd}")
  }

  // Battery check
  if (settings.batteryAlertEnabled && settings.batteryAlertTime) {
    schedule(settings.batteryAlertTime, 'scheduledBatteryCheck')
    logDebug("Battery check scheduled daily at ${settings.batteryAlertTime}")
  }
}

// =============================================================================
// Lock Trigger Handlers
// =============================================================================

void lockPresenceHandler(Event event) {
  logDebug("Lock presence event: ${event.displayName} is ${event.value}")
  if (event.value != 'not present') { return }

  // Check if everyone has left
  Boolean everyoneGone = settings.lockPresenceSensors.every { DeviceWrapper sensor ->
    sensor.currentValue('presence') == 'not present'
  }

  if (everyoneGone) {
    logInfo('Everyone has left')
    Integer delay = (settings.lockPresenceDelay ?: 0) as Integer
    if (delay > 0) {
      logDebug("Scheduling lock in ${delay} seconds (everyone left)")
      state.pendingLock = true
      runIn(delay, 'delayedLockPresence')
    } else {
      lockWithSafetyCheck('everyone left')
    }
  }
}

void delayedLockPresence() {
  if (!state.pendingLock) { return }
  state.pendingLock = false

  // Re-verify everyone is still gone
  Boolean everyoneGone = settings.lockPresenceSensors.every { DeviceWrapper sensor ->
    sensor.currentValue('presence') == 'not present'
  }
  if (everyoneGone) {
    lockWithSafetyCheck('everyone left (delayed)')
  } else {
    logDebug('Lock cancelled: someone returned during delay')
  }
}

/**
 * Consolidated mode change handler.
 * Unlock is checked first -- if the mode triggers an unlock, the lock branch is skipped.
 * This avoids a lock-then-unlock race when a mode appears in both lists.
 */
void modeChangeHandler(Event event) {
  logDebug("Mode change event: ${event.value}")

  // Unlock takes priority
  if (settings.unlockModeEnabled && settings.unlockModes && (event.value in settings.unlockModes)) {
    cancelPendingLock()
    performUnlock("mode changed to ${event.value}")
    return
  }

  if (settings.lockModeEnabled && settings.lockModes && (event.value in settings.lockModes)) {
    lockWithSafetyCheck("mode changed to ${event.value}")
  }
}

void lockContactClosedHandler(Event event) {
  logDebug("Lock contact closed event: ${event.displayName}")
  Integer delay = (settings.lockContactDelay ?: 0) as Integer
  if (delay > 0) {
    logDebug("Scheduling lock in ${delay} seconds after contact closed")
    runIn(delay, 'delayedLockContact')
  } else {
    lockWithSafetyCheck("contact sensor ${event.displayName} closed")
  }
}

void delayedLockContact() {
  // Verify all monitored contact sensors are still closed
  Boolean allClosed = settings.lockContactSensors.every { DeviceWrapper sensor ->
    sensor.currentValue('contact') == 'closed'
  }
  if (allClosed) {
    lockWithSafetyCheck('contact sensor closed (delayed)')
  } else {
    logDebug('Lock cancelled: a contact sensor reopened during delay')
  }
}

void lockMotionInactiveHandler(Event event) {
  logDebug("Lock motion inactive event: ${event.displayName}")
  Integer delay = (settings.lockMotionDelay ?: 0) as Integer
  if (delay > 0) {
    logDebug("Scheduling lock in ${delay} seconds after motion inactive")
    runIn(delay, 'delayedLockMotion')
  } else {
    lockWithSafetyCheck("motion sensor ${event.displayName} inactive")
  }
}

void delayedLockMotion() {
  // Verify all monitored motion sensors are still inactive
  Boolean allInactive = settings.lockMotionSensors.every { DeviceWrapper sensor ->
    sensor.currentValue('motion') == 'inactive'
  }
  if (allInactive) {
    lockWithSafetyCheck('motion inactive (delayed)')
  } else {
    logDebug('Lock cancelled: motion detected during delay')
  }
}

void lockSwitchHandler(Event event) {
  logDebug("Lock switch event: ${event.displayName} turned ${event.value}")
  lockWithSafetyCheck("switch ${event.displayName} turned ${event.value}")
}

void scheduledLock() {
  lockWithSafetyCheck('scheduled time')
}

void scheduledLockTimeRangeStart() {
  lockWithSafetyCheck('lock time range start')
}

// =============================================================================
// Unlock Trigger Handlers
// =============================================================================

void unlockPresenceHandler(Event event) {
  logDebug("Unlock presence event: ${event.displayName} arrived")
  cancelPendingLock()
  performUnlock("${event.displayName} arrived")
}

void unlockContactOpenHandler(Event event) {
  logDebug("Unlock contact open event: ${event.displayName}")
  cancelPendingLock()
  performUnlock("contact sensor ${event.displayName} opened")
}

void unlockMotionActiveHandler(Event event) {
  logDebug("Unlock motion active event: ${event.displayName}")
  cancelPendingLock()
  performUnlock("motion sensor ${event.displayName} active")
}

void unlockSwitchHandler(Event event) {
  logDebug("Unlock switch event: ${event.displayName} turned ${event.value}")
  cancelPendingLock()
  performUnlock("switch ${event.displayName} turned ${event.value}")
}

void scheduledUnlock() {
  cancelPendingLock()
  performUnlock('scheduled time')
}

void scheduledUnlockTimeRangeEnd() {
  cancelPendingLock()
  performUnlock('unlock time range end')
}

// =============================================================================
// HSM Handler
// =============================================================================

void hsmStatusHandler(Event event) {
  logDebug("HSM status event: ${event.value}")
  if (settings.lockHsmEnabled && settings.lockHsmStates && (event.value in settings.lockHsmStates)) {
    lockWithSafetyCheck("HSM ${event.value}")
  }
}

// =============================================================================
// Door Left Open Warning
// =============================================================================

void doorLeftOpenContactHandler(Event event) {
  if (event.value == 'open') {
    Integer minutes = (settings.doorLeftOpenMinutes ?: 5) as Integer
    logDebug("Door opened -- scheduling left-open warning in ${minutes} minutes")
    runIn(minutes * 60, 'doorLeftOpenWarning')
  } else {
    logDebug('Door closed -- cancelling left-open warning')
    unschedule('doorLeftOpenWarning')
  }
}

void doorLeftOpenWarning() {
  // Verify door is still open
  String contactState = settings.doorContactSensor?.currentValue('contact')
  if (contactState == 'open') {
    logWarn('Door is still open')
    sendNotification('Door has been left open', 'doorLeftOpen')

    // Schedule repeat warning if configured
    Integer repeatMinutes = (settings.doorLeftOpenRepeatMinutes ?: 0) as Integer
    if (repeatMinutes > 0) {
      logDebug("Scheduling repeat door-left-open warning in ${repeatMinutes} minutes")
      runIn(repeatMinutes * 60, 'doorLeftOpenWarning')
    }
  }
}

// =============================================================================
// Lock State & Battery Monitoring
// =============================================================================

void lockStateMonitorHandler(Event event) {
  String val = event.value
  if (val == 'unknown' || val == 'jammed') {
    logWarn("Lock ${event.displayName} reported: ${val}")
    sendNotification("Lock ${event.displayName} reported: ${val}", 'jammed')
  }
}

void scheduledBatteryCheck() {
  Integer threshold = (settings.batteryThreshold ?: 20) as Integer
  List<String> lowBatteryLocks = []
  settings.locks?.each { DeviceWrapper lock ->
    Integer level = lock.currentValue('battery') as Integer
    if (level != null && level <= threshold) {
      lowBatteryLocks.add("${lock.displayName} (${level}%)")
    }
  }
  if (lowBatteryLocks) {
    String msg = "Low battery: ${lowBatteryLocks.join(', ')}"
    logWarn(msg)
    sendNotification(msg, 'batteryLow')
  } else {
    logDebug('Battery check: all locks above threshold')
  }
}

// =============================================================================
// Auto Re-Lock Handler
// =============================================================================

void lockUnlockedHandler(Event event) {
  if (!settings.autoReLockEnabled) { return }
  logDebug("Lock was unlocked: ${event.displayName} -- scheduling auto re-lock")
  Integer delay = (settings.autoReLockDelay ?: 300) as Integer
  state.pendingReLock = true
  runIn(delay, 'autoReLock')
}

void autoReLock() {
  if (!state.pendingReLock) { return }
  state.pendingReLock = false
  lockWithSafetyCheck('auto re-lock')
}

// =============================================================================
// Lock and Unlock Actions
// =============================================================================

/**
 * Locks the configured lock(s) after verifying the door is not open.
 * Will refuse to lock if the door contact sensor reports "open" or if its
 * state is unavailable.
 *
 * @param reason A description of why the lock is being triggered.
 */
private void lockWithSafetyCheck(String reason) {
  // Temporary disable check
  if (isDisabled()) {
    logDebug("Lock suppressed (${reason}): disable switch is on")
    return
  }

  // Safety check: never lock if door is open or state is unavailable
  if (settings.doorContactSensor) {
    String contactState = settings.doorContactSensor.currentValue('contact')
    if (contactState == null) {
      logWarn("Lock BLOCKED (${reason}): door contact sensor state is unavailable")
      sendNotification("Lock blocked: door sensor state unavailable (${reason})", 'safetyBlock')
      return
    }
    if (contactState == 'open') {
      logWarn("Lock BLOCKED (${reason}): door is open")
      sendNotification("Lock blocked: door is open (${reason})", 'safetyBlock')
      return
    }
  }

  logInfo("Locking (${reason})")
  settings.locks?.each { DeviceWrapper lock ->
    if (lock.currentValue('lock') != 'locked') {
      lock.lock()
    }
  }
  sendNotification("Door locked (${reason})", 'lock')

  // Schedule lock confirmation check
  if (settings.lockConfirmEnabled) {
    Integer confirmDelay = (settings.lockConfirmDelay ?: 15) as Integer
    runIn(confirmDelay, 'verifyLockConfirmation')
  }

  // Cancel any pending re-lock since we just locked
  state.pendingReLock = false
  unschedule('autoReLock')
}

/**
 * Unlocks the configured lock(s).
 *
 * @param reason A description of why the unlock is being triggered.
 */
private void performUnlock(String reason) {
  // Temporary disable check
  if (isDisabled()) {
    logDebug("Unlock suppressed (${reason}): disable switch is on")
    return
  }

  logInfo("Unlocking (${reason})")
  settings.locks?.each { DeviceWrapper lock ->
    if (lock.currentValue('lock') != 'unlocked') {
      lock.unlock()
    }
  }
  sendNotification("Door unlocked (${reason})", 'unlock')

  // Cancel any pending re-lock since an explicit unlock trigger fired
  state.pendingReLock = false
  unschedule('autoReLock')
}

// =============================================================================
// Helper Methods
// =============================================================================

/**
 * Returns true if the disable switch is configured and currently ON.
 */
private Boolean isDisabled() {
  return settings.disableSwitch && settings.disableSwitch.currentValue('switch') == 'on'
}

/**
 * Verifies that all locks actually report "locked" after a lock command.
 * Sends a notification if any lock failed to confirm.
 */
void verifyLockConfirmation() {
  List<String> failedLocks = []
  settings.locks?.each { DeviceWrapper lock ->
    String lockState = lock.currentValue('lock')
    if (lockState != 'locked') {
      failedLocks.add("${lock.displayName} (${lockState ?: 'unknown'})")
    }
  }
  if (failedLocks) {
    String msg = "Lock confirmation failed: ${failedLocks.join(', ')}"
    logWarn(msg)
    sendNotification(msg, 'confirmFail')
  } else {
    logDebug('Lock confirmation: all locks confirmed locked')
  }
}

/**
 * Cancels any pending delayed lock operations.
 */
private void cancelPendingLock() {
  logDebug('Cancelling all pending lock operations')
  state.pendingLock = false
  unschedule('delayedLockPresence')
  unschedule('delayedLockContact')
  unschedule('delayedLockMotion')
}

/**
 * Sends a notification to configured notification devices.
 *
 * @param message The notification message.
 * @param type The notification type: 'lock', 'unlock', or 'safetyBlock'.
 */
private void sendNotification(String message, String type) {
  if (!settings.notificationDevices) { return }

  Boolean shouldNotify = false
  switch (type) {
    case 'lock':
      shouldNotify = settings.notifyOnLock != false
      break
    case 'unlock':
      shouldNotify = settings.notifyOnUnlock != false
      break
    case 'safetyBlock':
      shouldNotify = settings.notifyOnSafetyBlock != false
      break
    case 'confirmFail':
      shouldNotify = settings.notifyOnConfirmFail != false
      break
    case 'jammed':
      shouldNotify = settings.notifyOnJammed != false
      break
    case 'doorLeftOpen':
      shouldNotify = settings.notifyOnDoorLeftOpen != false
      break
    case 'batteryLow':
      shouldNotify = settings.notifyOnBatteryLow != false
      break
  }

  if (shouldNotify) {
    String prefixedMessage = "${app.label ?: 'Auto Lock'}: ${message}"
    settings.notificationDevices.each { DeviceWrapper device ->
      device.deviceNotification(prefixedMessage)
    }
  }
}
