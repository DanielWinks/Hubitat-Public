package hubitat.device

/** Capture-only HubMultiAction stand-in for driver behavior tests. */
class HubMultiAction {
  List<String> commands
  Protocol protocol

  HubMultiAction(List<String> commands, Protocol protocol = null) {
    this.commands = commands
    this.protocol = protocol
  }
}
