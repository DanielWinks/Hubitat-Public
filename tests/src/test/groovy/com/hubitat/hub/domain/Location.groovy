package com.hubitat.hub.domain

/** Test stub for Hubitat's hub-wide Location object. */
class Location {
  String name = 'Test Location'
  String mode = 'Day'
  TimeZone timeZone = TimeZone.getTimeZone('America/New_York')
  Double latitude = 40.7128d
  Double longitude = -74.0060d
}
