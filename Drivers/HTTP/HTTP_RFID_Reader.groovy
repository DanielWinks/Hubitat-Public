
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
    command 'updateChores'
    command 'setAllToNoMaintenanceNeeded'
    command 'setNextChore'

    command 'updatePendingPersonalChoreTextDaughter'
    command 'updatePendingPersonalChoreTextSon'

    attribute 'lastUID', 'STRING'
    attribute 'tagMessage', 'STRING'
    attribute 'lastAssignee', 'STRING'
    attribute 'choreId', 'STRING'
    attribute 'commandId', 'STRING'
    attribute 'pendingChoresList', 'STRING'
    attribute 'pendingChoresListText', 'STRING'
    attribute 'pendingChoreDaughter', 'STRING'
    attribute 'pendingChoreSon', 'STRING'
    attribute 'pendingPersonalChoreTextDaughter', 'STRING'
    attribute 'pendingPersonalChoreTextSon', 'STRING'
    attribute 'assignedChoresNeedCompleted', 'ENUM', ['true','false']
    attribute 'personalChoresNeedCompleted', 'ENUM', ['true','false']

    attribute 'status', 'ENUM', ['online', 'offline']
    attribute 'uptime', 'NUMBER'
  }

    preferences {
    section('Device Settings') {
      input(name: 'ip', type: 'string', title:'IP Address', description: '', required: true, displayDuringSetup: true)
      input(name: 'port', type: 'string', title:'Port', description: '', required: true, displayDuringSetup: true, defaultValue: '80')
      input(name: 'daughterName', type: 'string', title:'Daughter Name', description: '', required: true, displayDuringSetup: true)
      input(name: 'sonName', type: 'string', title:'Son Name', description: '', required: true, displayDuringSetup: true)
      input(name: 'reminderInterval', type:'number', title:'Reminder Interval', description: '', required: true, displayDuringSetup: true, defaultValue: 10)
    }
  }
}

void refresh() {}
void refreshCallback(AsyncResponse response, Map data = null){}

String getChildDNI(String name) { return "${device.id}-${name.replace(' ','')}" }

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
    String lastAssignee = child.currentValue('assignee')
    sendEvent(name: 'lastAssignee', value: lastAssignee, isStateChange:true)
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
  updateChores()
}

void updateChores() {
  updatePendingChoresList()
  updatePendingPersonalChoreTextDaughter()
  updatePendingPersonalChoreTextSon()
  setNextChore()
  updateAssignedChoresNeedCompleted()
  updatePersonalChoresNeedCompleted()
}

void update() { runIn(1, 'updateChores', [overwrite: true]) }

void updatePendingChoresList() {
  String pendingChores = ''
  List<ChildDeviceWrapper> children = []
  children = getChildDevices().findAll{it.currentValue('consumableStatus') == 'maintenance_required' && !(it.getLabel().contains(settings.daughterName)) && !(it.getLabel().contains(settings.sonName))}
  children.each{ child ->
    logDebug("Child needs completed: ${child}")
    pendingChores += "${child.getLabel()}".replace('Chore -','')
    pendingChores += ', '
  }
  if(pendingChores.size() > 0) { pendingChores = pendingChores[0..-2] }
  if(children.size() == 0) { pendingChores = 'Nothing '}
  sendEvent(name: 'pendingChoresList', value: pendingChores, isStateChange: true)
  updatePendingChoreListText(pendingChores)
}

void updatePendingChoreListText(String pendingChoresList) {
  String pendingChores = "The following chores need to be completed "
  pendingChores += pendingChoresList
  if(pendingChoresList.startsWith('Nothing')) {
    pendingChores = 'No chores need completed at this time '
  }
  sendEvent(name: 'pendingChoresListText', value: pendingChores, isStateChange: true)
}

String getPendingPersonalChoresForChildText(String name) {
  List<ChildDeviceWrapper> children = getPendingPersonalChoresForChild(name)
  String pendingChores = "The following chores need to be completed by ${name} "
  children.each{ child ->
    logDebug("Child needs completed: ${child}")
    pendingChores += "${child.getLabel()}".replace('Chore -','')
    pendingChores += ', '
  }

  if(children.size() == 0) { pendingChores = 'No chores need completed at this time '}
  pendingChores = pendingChores[0..-2]
  return pendingChores
}

List<ChildDeviceWrapper> getPendingPersonalChoresForChild(String name) {
  return getChildDevices().findAll{it.currentValue('consumableStatus') == 'maintenance_required' && it.getLabel().contains(name)}
}



void setAllToNoMaintenanceNeeded() {
  getChildDevices().each { child ->
    child.setConsumableStatus('good')
    child.setAssignee('unassigned')
  }
}
void updatePendingPersonalChoreTextDaughter() { sendEvent(name: 'pendingPersonalChoreTextDaughter', value: getPendingPersonalChoresForChildText(settings.daughterName), isStateChange: true) }
void updatePendingPersonalChoreTextSon() { sendEvent(name: 'pendingPersonalChoreTextSon', value: getPendingPersonalChoresForChildText(settings.sonName), isStateChange: true) }

String getNextChoreByChild(String childName, Integer i) {
  logDebug("Assigning chore to ${childName}")
  ChildDeviceWrapper chore = getCurrentlyAssignedChoreForChild(childName)
  logDebug("getCurrentlyAssignedChoreForChild: ${chore}")
  String choreName = 'Nothing'
  if(chore != null) {choreName = "${chore.getLabel()}"}
  else if(i != 0) {
    List<ChildDeviceWrapper> children = getUnassignedChoresNeedingCompleted()
    if(children != null) {
      choreName = "${children[i-1].getLabel()}"
      children[i-1].setAssignee(childName)
    }
  }
  choreName = choreName.replace('Chore -','')
  return "${choreName} has been assigned to ${childName}"
}

List<ChildDeviceWrapper> getUnassignedChoresNeedingCompleted() {
  List<ChildDeviceWrapper> children = []
  children = getChildDevices().findAll{it.currentValue('assignee') == 'unassigned' && it.currentValue('consumableStatus') == 'maintenance_required' && !(it.getLabel().contains(settings.daughterName)) && !(it.getLabel().contains(settings.sonName))}
  return children
}

ChildDeviceWrapper getCurrentlyAssignedChoreForChild(String childName) {
  List<ChildDeviceWrapper> children = []
  children = getChildDevices().findAll{it.currentValue('assignee') == childName}
  return children[0]
}

Boolean childHasAssignedChore(String childName) {
  List<ChildDeviceWrapper> children = []
  children = getChildDevices().findAll{it.currentValue('assignee') == childName}
  if(children != null) { return children.size() > 0 }
  return false
}

String getNextChoreDaughter(Integer i) {
  String pendingChore = getNextChoreByChild(settings.daughterName, i)
  sendEvent(name: 'pendingChoreDaughter', value: pendingChore, isStateChange: true)
  return pendingChore
}

String getNextChoreSon(Integer i) {
  String pendingChore = getNextChoreByChild(settings.sonName, i)
  sendEvent(name: 'pendingChoreSon', value: pendingChore, isStateChange: true)
  return pendingChore
}

Boolean daughterHasNoAssignedChores() { return childHasAssignedChore(settings.daughterName) == false }
Boolean sonHasNoAssignedChores() { return childHasAssignedChore(settings.sonName) == false }
Boolean childrenHaveAssignedChores() { return (childHasAssignedChore(settings.daughterName) || childHasAssignedChore(settings.sonName)) }
void updateAssignedChoresNeedCompleted() { sendEvent(name: 'assignedChoresNeedCompleted', value: "${childrenHaveAssignedChores()}", isStateChange: true) }

Boolean childrenHavePersonalChores() {
  List<ChildDeviceWrapper> personalChoresDaughter = getPendingPersonalChoresForChild(settings.daughterName)
  List<ChildDeviceWrapper> personalChoresSon = getPendingPersonalChoresForChild(settings.sonName)
  return (getPendingPersonalChoresForChild(settings.daughterName)?.size() > 0 || getPendingPersonalChoresForChild(settings.sonName)?.size() > 0)
}
void updatePersonalChoresNeedCompleted() { sendEvent(name: 'personalChoresNeedCompleted', value: "${childrenHavePersonalChores()}", isStateChange: true) }


void setNextChore() {
  List<ChildDeviceWrapper> children = getUnassignedChoresNeedingCompleted()
  logDebug("Unassigned chores: ${children}")
  if(children.size() >= 2) {
    logDebug("Assigning 2+ chores to children.")
    Integer i = new Random().nextInt(children.size()) + 1
    Integer j = new Random().nextInt(children.size()) + 1

    if(i == j && i < children.size()) { j = i+1 }
    else if(i == j && i == children.size()) { j = i-1 }

    getNextChoreDaughter(i)
    getNextChoreSon(j)
  } else if(children.size() == 1) {
    logDebug("Assigning 1 chore...")
    logDebug("Daughter has no assigned chores: ${daughterHasNoAssignedChores()}")
    logDebug("Son has no assigned chores: ${sonHasNoAssignedChores()}")
    if(daughterHasNoAssignedChores() && sonHasNoAssignedChores()) {
      logDebug("No children have assigned chores. Assigning 1 chore based on whether day is even or odd.")
      if(nowDays() % 2 == 0) { getNextChoreDaughter(1) }
      else { getNextChoreSon(1) }
    }
  } else {
    logDebug("No chores to assign to children")
    getNextChoreDaughter(0)
    getNextChoreSon(0)
  }
}