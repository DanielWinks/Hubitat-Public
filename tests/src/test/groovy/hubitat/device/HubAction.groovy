package hubitat.device

/** Capture-only HubAction stand-in for LAN-driver behavior tests. */
class HubAction {
  Map params = [:]
  Protocol protocol
  String deviceNetworkId

  HubAction(Map params) {
    this.params = params ?: [:]
  }

  HubAction(Map params, String deviceNetworkId) {
    this(params)
    this.deviceNetworkId = deviceNetworkId
  }

  HubAction(String request, Protocol protocol = null) {
    this.params = [request: request]
    this.protocol = protocol
  }
}
