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
import groovy.transform.Field

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

    section('<h2>Auto Re-Lock</h2>') {
      input('autoReLockEnabled', 'bool', title: 'Enable auto re-lock after unlock', required: false, defaultValue: false, submitOnChange: true)
      if (settings.autoReLockEnabled) {
        input('autoReLockDelay', 'number', title: 'Re-lock delay (seconds)', required: true, defaultValue: 300, range: '5..3600')
      }
    }

    section('<h2>Notifications</h2>') {
      input('notificationDevices', 'capability.notification', title: 'Notification devices', required: false, multiple: true)
      if (settings.notificationDevices) {
        input('notifyOnLock', 'bool', title: 'Notify when locked', required: false, defaultValue: true)
        input('notifyOnUnlock', 'bool', title: 'Notify when unlocked', required: false, defaultValue: true)
        input('notifyOnSafetyBlock', 'bool', title: 'Notify when lock is blocked (door open)', required: false, defaultValue: true)
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

void initialize() {
  state.pendingLock = false
  state.pendingReLock = false

  subscribeLockTriggers()
  subscribeUnlockTriggers()
  subscribeLockEvents()
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

  // Mode
  if (settings.lockModeEnabled && settings.lockModes) {
    subscribe(location, 'mode', 'lockModeHandler')
  }

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

  // Mode
  if (settings.unlockModeEnabled && settings.unlockModes) {
    subscribe(location, 'mode', 'unlockModeHandler')
  }

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
}

private void subscribeLockEvents() {
  // Monitor lock state changes for auto re-lock
  if (settings.autoReLockEnabled && settings.locks) {
    subscribe(settings.locks, 'lock.unlocked', 'lockUnlockedHandler')
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

void lockModeHandler(Event event) {
  logDebug("Lock mode event: ${event.value}")
  if (settings.lockModes && (event.value in settings.lockModes)) {
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

void unlockModeHandler(Event event) {
  logDebug("Unlock mode event: ${event.value}")
  if (settings.unlockModes && (event.value in settings.unlockModes)) {
    cancelPendingLock()
    performUnlock("mode changed to ${event.value}")
  }
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
 * Cancels any pending delayed lock operations.
 */
private void cancelPendingLock() {
  if (state.pendingLock) {
    logDebug('Cancelling pending lock')
    state.pendingLock = false
    unschedule('delayedLockPresence')
    unschedule('delayedLockContact')
    unschedule('delayedLockMotion')
  }
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
  }

  if (shouldNotify) {
    String prefixedMessage = "${app.label ?: 'Auto Lock'}: ${message}"
    settings.notificationDevices.each { DeviceWrapper device ->
      device.deviceNotification(prefixedMessage)
    }
  }
}
