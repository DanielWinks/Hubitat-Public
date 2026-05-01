package com.hubitat.hub.domain

/** Test stub for Hubitat events delivered to subscribed handlers. */
class Event {
  String name
  Object value
  String unit
  String descriptionText
  Object device
  Date date = new Date()
  String getName() { name }
  Object getValue() { value }
  String getStringValue() { value?.toString() }
  Integer getIntegerValue() { value as Integer }
  Long    getLongValue()    { value as Long }
  BigDecimal getNumberValue() { value as BigDecimal }
  String getUnit() { unit }
  Object getDevice() { device }
}
