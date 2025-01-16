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

#include dwinks.UtilitiesAndLoggingLibrary

metadata {
  definition (name: 'Virtual Auto Off Switch With Button And Consumable', namespace: 'dwinks', author: 'Daniel Winks', importUrl: '') {
    capability 'Switch'
    capability 'PushableButton'
    capability 'Consumable' //consumableStatus - ENUM ['missing', 'order', 'maintenance_required', 'good', 'replace']

    attribute 'assignee', 'STRING' // To be used for auto-off 'chore switches'
    command 'setAssignee'
    command 'setMaintenanceRequired'
    command 'setMaintenanceRequiredIfSwitchOff'

  }
  preferences() {
    section(){
      input 'autoOffTime', 'enum', title: 'Duration Before Auto-Off', required: true, defaultValue: 300, options:
      [
        15:'15 Seconds',
        30:'30 Seconds',
        45:'45 Seconds',
        60:'1 Minute',
        120:'2 Minutes',
        180:'3 Minutes',
        240:'4 Minutes',
        300:'5 Minutes',
        600:'10 Minutes',
        900:'15 Minutes',
        1200:'20 Minutes',
        1800:'30 Minutes',
        2700:'45 Minutes',
        3600:'60 Minutes',
        5400:'90 Minutes',
        7200:'2 Hours',
        14400:'4 Hours',
        21600:'6 Hours',
        28800:'8 Hours',
        43200:'12 Hours',
        57600:'16 Hours',
        86400:'24 Hours',
        172800:'2 Days',
        259200:'3 Days',
        345600:'4 Days',
        432000:'5 Days',
        518400:'6 Days',
        604800:'7 Days',
        864000:'10 Days',
        1209600:'14 Days',
        1814400:'21 Days',
        2419200:'28 Days'
      ]
    }
  }
}

void initialize() { configure() }

void configure() {
  if(this.device.currentValue('switch', true) == 'on') {scheduleAutoOff()}
  sendEvent(name: 'numberOfButtons', value: 1)
}

void scheduleAutoOff() {
  Long offset = now() + ((autoOffTime as Long) * 1000L)
  Date date = new java.util.Date(offset)
  runOnce(date, 'autoOff', [overwrite: true])
}

void on() {
  scheduleAutoOff()
  sendEvent(name: 'switch', value: 'on')
  push(1)
  setConsumableStatus('good')
  setAssignee('unassigned')
  parent?.update()
}

void off() {
  unschedule('autoOff')
  sendEvent(name: 'switch', value: 'off')
  parent?.update()
}

void setConsumableStatus(String status) {
  if(status in ['missing', 'order', 'maintenance_required', 'good', 'replace']) {
    sendEvent(name: 'consumableStatus', value: status)
    parent?.update()
  }
}

void setAssignee(String assigneeName) {
  sendEvent(name: 'assignee', value: assigneeName)
  parent?.update()
}

void autoOff() { off() }
void push(buttonNumber) {
  sendEvent(name: 'pushed', value: buttonNumber, isStateChange:true)
  parent?.update()
}

void setMaintenanceRequired() {
  off()
  setConsumableStatus('maintenance_required')
}

void setMaintenanceRequiredIfSwitchOff() {
  if(device.currentValue('switch') == 'off') {
    setConsumableStatus('maintenance_required')
    setAssignee('unassigned')
  }
}