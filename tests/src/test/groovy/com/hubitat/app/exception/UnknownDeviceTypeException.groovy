package com.hubitat.app.exception

/** Test stub mirroring Hubitat's runtime exception. */
class UnknownDeviceTypeException extends RuntimeException {
  UnknownDeviceTypeException()                 { super() }
  UnknownDeviceTypeException(String msg)       { super(msg) }
  UnknownDeviceTypeException(String m, Throwable c) { super(m, c) }
}
