package com.hubitat.app

/** Test stub for InstalledAppWrapper. */
class InstalledAppWrapper {
  Long id = 42L
  String label = 'Test App'
  List<Map> events = []
  String getLabel() { label }
  void updateLabel(String value) { label = value }
  String getId()    { id?.toString() }
  void sendEvent(Map event) { events << event }
}
