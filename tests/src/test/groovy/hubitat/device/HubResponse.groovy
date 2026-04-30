package hubitat.device

/** Test stub for HubResponse used in LAN messaging. */
class HubResponse {
  String mac
  String ip
  Integer port
  String description
  String body
  String header
  Map headers = [:]
  Integer status = 200
}
