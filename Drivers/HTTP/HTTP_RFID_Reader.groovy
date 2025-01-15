
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
 **/

#include dwinks.UtilitiesAndLoggingLibrary
#include dwinks.httpLibrary

metadata {
  definition (name: 'HTTP RFID Reader', namespace: 'dwinks', author: 'Daniel Winks', importUrl:'') {
    capability 'Sensor'
    capability 'PushableButton'
    capability 'Switch'

    command 'restartESP'
    command 'checkConnection'
    // command 'setButtonNum'
    command 'setPendingChoreList'
    command 'setAllToNoMaintenanceNeeded'

    attribute 'lastUID', 'STRING'
    attribute 'tagMessage', 'STRING'
    attribute 'choreId', 'STRING'
    attribute 'commandId', 'STRING'
    attribute 'pendingChores', 'STRING'

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input 'ip', 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true
      input 'port', 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80'
    }
  }
}

void refresh() {}
void refreshCallback(AsyncResponse response, Map data = null){}

String getChildDNI(String name) {
  return "${device.id}-${name.replace(' ','')}"
}

ChildDeviceWrapper getOrCreateChildDevice(String name) {
  ChildDeviceWrapper child = getChildDevice(getChildDNI(name))
  if (child == null) {
    child = addChildDevice(
      'dwinks',
      'Virtual Auto Off Switch With Button And Consumable',
      "${getChildDNI(name)}",
      [ name: 'Virtual Auto Off Switch With Button And Consumable', label: "RFID - ${name}" ]
    )
  }
  return child
}

void murderChildren() {deleteChildDevices()}
// void setButtonNum() {sendEvent(name: 'numberOfButtons', value: 25)}

void push(Integer buttonNumber) { sendEvent(name: 'pushed', value: buttonNumber, isStateChange:true) }
void on() {}
void off() {}

void parse(message) {
  Map parsedMessage = parseLanMessage(message)
  Map jsonData = parseJson(parsedMessage.body)
  processJson(jsonData)
}

void processJson(Map jsonData) {
  logDebug(prettyJson(jsonData))
  if(jsonData?.tagMessage != null) { sendEvent(name: 'tagMessage', value: jsonData.tagMessage, isStateChange:true) }
  ChildDeviceWrapper child = null
  if(jsonData?.lastUID != null) {
    child = getOrCreateChildDevice(jsonData?.lastUID)
    child.on()
    if(jsonData?.beginChores == null && jsonData?.commandCard == null) { child.setConsumableStatus('good') }
    sendEvent(name: 'lastUID', value: jsonData.lastUID, isStateChange:true)
  }
  if(jsonData?.choreId != null) { sendEvent(name: 'choreId', value: jsonData.choreId, isStateChange:true) }
  if(jsonData?.commandId != null) { sendEvent(name: 'commandId', value: jsonData.commandId, isStateChange:true) }
  if(jsonData?.buttonNumber != null) { push(jsonData.buttonNumber as Integer) }
  if(jsonData?.commandCard != null) { sendEvent(name: 'switch', value: device.currentValue('switch') == 'on' ? 'off' : 'on') } // Enter parental command mode
  // While in parental mode, scanned tags will be set to off and maintenance_required
  if(device.currentValue('switch') == 'on' && jsonData?.commandCard == null) {
    child.setConsumableStatus('maintenance_required')
    child.off()
  }
  setPendingChoreList()
}

void setPendingChoreList() {
  String pendingChores = "The following chores need to be completed "
  List<ChildDeviceWrapper> children = []
  children = getChildDevices().findAll{it.currentValue('consumableStatus') == 'maintenance_required'}
  children.each{ child ->
    logDebug("Child needs completed: ${child}")
    pendingChores += "${child.getLabel()}".replace('Chore -','')
    pendingChores += ', '
  }
  if(children.size() == 0) { pendingChores = 'No chores need completed at this time'}
  sendEvent(name: 'pendingChores', value: pendingChores, isStateChange: true)
}

void setAllToNoMaintenanceNeeded() {
  List<ChildDeviceWrapper> children = getChildDevices()
  children.each { child ->
    child.setConsumableStatus('good')
  }
}