package com.hubitat.app

/** Test stub for InstalledAppWrapper. */
class InstalledAppWrapper {
  Long id = 42L
  String label = 'Test App'
  String getLabel() { label }
  String getId()    { id?.toString() }
}
