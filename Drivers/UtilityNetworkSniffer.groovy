/**
 *  MIT License
 *  Copyright 2026 Daniel Winks (daniel.winks@gmail.com)
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
 */

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
  definition(
    name: 'Utility Network Sniffer',
    version: '1.0.0',
    namespace: 'dwinks',
    author: 'Daniel Winks',
    description: 'Utility driver for parsing and analyzing network traffic sent to Hubitat on the magic port 39501',
    importUrl: 'https://raw.githubusercontent.com/DanielWinks/Hubitat-Public/main/Drivers/UtilityNetworkSniffer.groovy'
  ) {
    capability 'Initialize'

    attribute 'lastPayload', 'string'
    attribute 'lastPayloadType', 'string'
    attribute 'lastPayloadParsed', 'string'
    attribute 'lastReceived', 'string'
    attribute 'messageCount', 'number'
    attribute 'dni', 'string'

    command 'clearStats'
    command 'updateDNI'
  }

  preferences {
    section('Device Settings') {
      input name: 'deviceIpAddress', type: 'string', title: 'Device IP Address', required: false, defaultValue: ''
      input name: 'deviceMacAddress', type: 'string', title: 'Device MAC Address', required: false, defaultValue: ''
      input name: 'maxPayloadDisplay', type: 'number', title: 'Maximum Payload Display Length', required: false, defaultValue: 500
      input name: 'logEnable', type: 'bool', title: 'Enable debug logging', required: false, defaultValue: true
      input name: 'descriptionTextEnable', type: 'bool', title: 'Enable description text logging', required: false, defaultValue: true
    }
  }
}

// =============================================================================
// Fields
// =============================================================================
@Field static final Integer MAGIC_PORT = 39501
@Field static final String MAGIC_PORT_HEX = '9A4D'

// =============================================================================
// Initialize and Configure
// =============================================================================
void initialize() {
  logInfo('Initializing Utility Network Sniffer')
  updateDNI()
  sendEvent(name: 'messageCount', value: 0)
}

void installed() {
  logInfo('Utility Network Sniffer installed')
  initialize()
}

void updated() {
  logInfo('Utility Network Sniffer updated')
  updateDNI()
  scheduleLogDisable()
}

// =============================================================================
// Settings Change Handler
// =============================================================================
void updateDNI() {
  String newDni = calculateDNI()
  if(newDni && newDni != device.getDeviceNetworkId()) {
    device.setDeviceNetworkId(newDni)
    logInfo("Updated DNI to: ${newDni}")
    sendEvent(name: 'dni', value: newDni)
  } else if(newDni) {
    logDebug("DNI already set to: ${newDni}")
    sendEvent(name: 'dni', value: newDni)
  } else {
    logWarn('Cannot calculate DNI - both IP and MAC are empty')
  }
}

String calculateDNI() {
  String ipAddress = getDeviceIpAddress()
  String macAddress = getDeviceMacAddress()

  if(macAddress && macAddress.length() >= 12) {
    // Prefer MAC address - Hubitat matches on MAC without port suffix
    String cleanMac = macAddress.replaceAll('[^0-9A-Fa-f]', '').toUpperCase()
    if(cleanMac.length() >= 12) {
      return cleanMac[0..11]
    }
  }

  if(ipAddress) {
    // Fall back to IP address in hex - Hubitat matches on IP hex without port suffix
    String hexIp = convertIpToHex(ipAddress)
    if(hexIp) {
      return hexIp
    }
  }

  return null
}

String convertIpToHex(String ipAddress) {
  if(!ipAddress) { return null }

  try {
    String[] octets = ipAddress.split('\\.')
    if(octets.length != 4) { return null }

    StringBuilder hexIp = new StringBuilder()
    for(int i = 0; i < 4; i++) {
      Integer octet = Integer.parseInt(octets[i])
      if(octet < 0 || octet > 255) { return null }
      hexIp.append(String.format('%02X', octet))
    }
    return hexIp.toString()
  } catch(Exception e) {
    logError("Failed to convert IP to hex: ${e.message}")
    return null
  }
}

// =============================================================================
// Parse Method - Comprehensive Payload Analysis
// =============================================================================
void parse(String description) {
  incrementMessageCount()
  sendEvent(name: 'lastReceived', value: new Date().format('yyyy-MM-dd HH:mm:ss'))

  if(!description) {
    logWarn('Received empty description')
    return
  }

  logDebug("Raw description: ${truncateForDisplay(description)}")

  // Try to parse as LAN message first
  Map parsedMap = null
  try {
    parsedMap = parseLanMessage(description)
    if(parsedMap) {
      logDebug("Parsed as LAN message: ${parsedMap.keySet()}")
      processLanMessage(parsedMap)
      return
    }
  } catch(Exception e) {
    logDebug("Not a standard LAN message format: ${e.message}")
  }

  // Try various parsing strategies for raw data
  parseRawPayload(description)
}

void processLanMessage(Map lanMessage) {
  String payloadType = 'LAN Message'
  StringBuilder parsed = new StringBuilder()

  // Extract common LAN message fields
  if(lanMessage.containsKey('headers')) {
    Map headers = (Map)lanMessage.headers
    parsed.append("Headers: ${headers.size()} entries\n")
    headers.each { key, value ->
      parsed.append("  ${key}: ${value}\n")
    }
  }

  if(lanMessage.containsKey('body')) {
    String body = (String)lanMessage.body
    parsed.append("\nBody:\n")
    String bodyParsed = parsePayloadContent(body)
    parsed.append(bodyParsed)
  }

  if(lanMessage.containsKey('mac')) {
    parsed.append("\nMAC: ${lanMessage.mac}\n")
  }

  if(lanMessage.containsKey('ip')) {
    parsed.append("IP: ${lanMessage.ip}\n")
  }

  if(lanMessage.containsKey('port')) {
    parsed.append("Port: ${lanMessage.port}\n")
  }

  if(lanMessage.containsKey('networkAddress')) {
    parsed.append("Network Address: ${lanMessage.networkAddress}\n")
  }

  if(lanMessage.containsKey('deviceAddress')) {
    parsed.append("Device Address: ${lanMessage.deviceAddress}\n")
  }

  updatePayloadAttributes(lanMessage.toString(), payloadType, parsed.toString())
}

void parseRawPayload(String payload) {
  String parsed = parsePayloadContent(payload)
  updatePayloadAttributes(payload, 'Raw', parsed)
}

String parsePayloadContent(String content) {
  if(!content) { return 'Empty payload' }

  // Try JSON parsing
  String jsonResult = tryParseJson(content)
  if(jsonResult) { return "JSON:\n${jsonResult}" }

  // Try Base64 decoding
  String base64Result = tryDecodeBase64(content)
  if(base64Result) { return "Base64 Decoded:\n${base64Result}" }

  // Try URL decoding
  String urlResult = tryUrlDecode(content)
  if(urlResult != content) { return "URL Decoded:\n${urlResult}" }

  // Try hex parsing
  String hexResult = tryParseHex(content)
  if(hexResult) { return "Hex Decoded:\n${hexResult}" }

  // Try XML parsing
  String xmlResult = tryParseXml(content)
  if(xmlResult) { return "XML:\n${xmlResult}" }

  // Check if it's form-encoded data
  if(content.contains('=') && (content.contains('&') || !content.contains(' '))) {
    String formResult = parseFormData(content)
    if(formResult) { return "Form Data:\n${formResult}" }
  }

  // Check for key-value pairs
  if(content.contains(':') && content.contains('\n')) {
    String kvResult = parseKeyValuePairs(content)
    if(kvResult) { return "Key-Value Pairs:\n${kvResult}" }
  }

  // Default: return as plain text with character analysis
  return analyzeRawText(content)
}

String tryParseJson(String content) {
  try {
    def slurper = new JsonSlurper()
    def parsed = slurper.parseText(content)
    return JsonOutput.prettyPrint(JsonOutput.toJson(parsed))
  } catch(Exception e) {
    return null
  }
}

String tryDecodeBase64(String content) {
  try {
    // Check if it looks like Base64
    if(content.matches('^[A-Za-z0-9+/]+=*$') && content.length() % 4 == 0) {
      byte[] decoded = content.decodeBase64()
      String decodedStr = new String(decoded, 'UTF-8')
      // Check if decoded content is printable
      if(decodedStr.matches('^[\\x20-\\x7E\\r\\n\\t]*$')) {
        // Try to parse decoded content recursively
        String nestedParse = parsePayloadContent(decodedStr)
        return "Base64 content: ${decodedStr}\n${nestedParse}"
      }
    }
  } catch(Exception e) {
    return null
  }
  return null
}

String tryUrlDecode(String content) {
  try {
    String decoded = URLDecoder.decode(content, 'UTF-8')
    if(decoded != content) {
      return decoded
    }
  } catch(Exception e) {
    return content
  }
  return content
}

String tryParseHex(String content) {
  try {
    // Check if it's all hex characters
    String cleaned = content.replaceAll('[\\s:]', '')
    if(cleaned.matches('^[0-9A-Fa-f]+$') && cleaned.length() % 2 == 0 && cleaned.length() >= 4) {
      StringBuilder result = new StringBuilder()
      for(int i = 0; i < cleaned.length(); i += 2) {
        String hexByte = cleaned.substring(i, i + 2)
        int value = Integer.parseInt(hexByte, 16)
        if(value >= 32 && value <= 126) {
          result.append((char)value)
        } else {
          result.append('.')
        }
      }
      String decoded = result.toString()
      if(decoded.replaceAll('\\.', '').length() > decoded.length() * 0.3) {
        return "Hex to ASCII: ${decoded}"
      }
    }
  } catch(Exception e) {
    return null
  }
  return null
}

String tryParseXml(String content) {
  try {
    if(content.trim().startsWith('<') && content.trim().endsWith('>')) {
      def slurper = new groovy.util.XmlSlurper()
      def xml = slurper.parseText(content)
      return formatXmlNode(xml, 0)
    }
  } catch(Exception e) {
    return null
  }
  return null
}

String formatXmlNode(Object node, Integer indent) {
  StringBuilder result = new StringBuilder()
  String indentStr = ''
  for(int i = 0; i < indent; i++) {
    indentStr += '  '
  }

  if(node instanceof groovy.util.slurpersupport.GPathResult) {
    groovy.util.slurpersupport.GPathResult gpath = (groovy.util.slurpersupport.GPathResult)node
    result.append("${indentStr}${gpath.name()}")

    // Get attributes
    Map attrs = gpath.attributes()
    if(attrs && attrs.size() > 0) {
      result.append(' [')
      attrs.each { key, value ->
        result.append("${key}=${value} ")
      }
      result.append(']')
    }

    String text = gpath.text()
    if(text && text.trim().length() > 0) {
      result.append(": ${text}")
    }
    result.append('\n')

    // Process children
    gpath.children().each { child ->
      result.append(formatXmlNode(child, indent + 1))
    }
  }

  return result.toString()
}

String parseFormData(String content) {
  try {
    StringBuilder result = new StringBuilder()
    String[] pairs = content.split('&')
    for(String pair : pairs) {
      String[] kv = pair.split('=', 2)
      if(kv.length == 2) {
        String key = URLDecoder.decode(kv[0], 'UTF-8')
        String value = URLDecoder.decode(kv[1], 'UTF-8')
        result.append("  ${key} = ${value}\n")
      }
    }
    return result.length() > 0 ? result.toString() : null
  } catch(Exception e) {
    return null
  }
}

String parseKeyValuePairs(String content) {
  try {
    StringBuilder result = new StringBuilder()
    String[] lines = content.split('\n')
    for(String line : lines) {
      if(line.contains(':')) {
        String[] parts = line.split(':', 2)
        if(parts.length == 2) {
          result.append("  ${parts[0].trim()} = ${parts[1].trim()}\n")
        }
      }
    }
    return result.length() > 0 ? result.toString() : null
  } catch(Exception e) {
    return null
  }
}

String analyzeRawText(String content) {
  StringBuilder analysis = new StringBuilder('Plain Text:\n')

  // Character type analysis
  int printable = 0
  int control = 0
  int extended = 0

  for(int i = 0; i < content.length(); i++) {
    char c = content.charAt(i)
    int value = (int)c
    if(value >= 32 && value <= 126) {
      printable++
    } else if(value < 32 || value == 127) {
      control++
    } else {
      extended++
    }
  }

  analysis.append("  Length: ${content.length()} characters\n")
  analysis.append("  Printable: ${printable}, Control: ${control}, Extended: ${extended}\n")

  if(control > printable * 0.5) {
    analysis.append("  Warning: High proportion of control characters - may be binary data\n")
  }

  analysis.append("\nContent:\n${content}")

  return analysis.toString()
}

void updatePayloadAttributes(String payload, String payloadType, String parsed) {
  String truncatedPayload = truncateForDisplay(payload)
  String truncatedParsed = truncateForDisplay(parsed)

  sendEvent(name: 'lastPayload', value: truncatedPayload)
  sendEvent(name: 'lastPayloadType', value: payloadType)
  sendEvent(name: 'lastPayloadParsed', value: truncatedParsed)

  logInfo("Received ${payloadType} payload (${payload.length()} bytes)")
  if(descriptionTextEnable) {
    logInfo("Parsed content:\n${truncatedParsed}")
  }
}

// =============================================================================
// Commands
// =============================================================================
void clearStats() {
  sendEvent(name: 'messageCount', value: 0)
  sendEvent(name: 'lastPayload', value: 'None')
  sendEvent(name: 'lastPayloadType', value: 'None')
  sendEvent(name: 'lastPayloadParsed', value: 'None')
  sendEvent(name: 'lastReceived', value: 'Never')
  logInfo('Cleared statistics')
}

// =============================================================================
// Helper Methods
// =============================================================================
void incrementMessageCount() {
  Integer count = getMessageCount()
  sendEvent(name: 'messageCount', value: count + 1)
}

Integer getMessageCount() {
  Object countObj = device.currentValue('messageCount')
  if(countObj == null) { return 0 }
  if(countObj instanceof Number) { return ((Number)countObj).intValue() }
  return 0
}

String truncateForDisplay(String text) {
  if(!text) { return '' }
  Integer maxLen = getMaxPayloadDisplay()
  if(text.length() <= maxLen) { return text }
  return text.substring(0, maxLen) + '... (truncated)'
}

String getDeviceIpAddress() {
  Object ip = settings?.deviceIpAddress
  return ip ? ip.toString() : ''
}

String getDeviceMacAddress() {
  Object mac = settings?.deviceMacAddress
  return mac ? mac.toString() : ''
}

Integer getMaxPayloadDisplay() {
  Object max = settings?.maxPayloadDisplay
  if(max == null) { return 500 }
  if(max instanceof Number) { return ((Number)max).intValue() }
  return 500
}

Boolean getLogEnable() {
  Object log = settings?.logEnable
  if(log == null) { return true }
  if(log instanceof Boolean) { return (Boolean)log }
  return true
}

Boolean getDescriptionTextEnable() {
  Object desc = settings?.descriptionTextEnable
  if(desc == null) { return true }
  if(desc instanceof Boolean) { return (Boolean)desc }
  return true
}

// =============================================================================
// Logging
// =============================================================================
void logInfo(String msg) {
  log.info("${device.displayName}: ${msg}")
}

void logDebug(String msg) {
  if(getLogEnable()) {
    log.debug("${device.displayName}: ${msg}")
  }
}

void logWarn(String msg) {
  log.warn("${device.displayName}: ${msg}")
}

void logError(String msg) {
  log.error("${device.displayName}: ${msg}")
}

void scheduleLogDisable() {
  runInHelper(3600, 'disableLogging')
}

void runInHelper(Integer seconds, String method) {
  runIn(seconds, method)
}

void disableLogging() {
  device.updateSetting('logEnable', [type: 'bool', value: false])
  logInfo('Debug logging disabled')
}
