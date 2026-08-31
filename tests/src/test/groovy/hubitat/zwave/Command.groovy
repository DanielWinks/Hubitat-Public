package hubitat.zwave

/** Minimal Hubitat Z-Wave command stub used to compile driver behavior tests. */
class Command {
  String formatted = 'zwave-command'
  Boolean nullFormat = false

  String format() { nullFormat ? null : formatted }
}
