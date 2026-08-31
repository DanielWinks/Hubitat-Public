package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import hubitat.zwave.commands.associationv2.AssociationReport
import hubitat.zwave.commands.associationv2.AssociationGroupingsReport
import hubitat.scheduling.AsyncResponse
import spock.lang.Shared
import spock.lang.Specification

class GenericZWaveAssociationSpec extends Specification {

  @Shared HubitatScriptHarness driver

  def setupSpec() {
    File file = new File('../Drivers/ZWave/GenericZWaveAssociation.groovy')
    assert file.exists(), "Could not find ${file.absolutePath}"
    driver = ScriptLoader.load(file)
  }

  def setup() {
    driver.state.clear()
    driver.settings.clear()
    driver.events.clear()
    driver.scheduled.clear()
    driver.unschedules.clear()
    driver.hubCommands.clear()
    driver.asyncCalls.clear()
    driver.zwave.associationV2.nullFormats = false
    driver.zwave.associationGrpInfoV1.nullCommands = false
    driver.device.deviceNetworkId = '0A'
    driver.device.dataValues.clear()
    driver.device.updatedSettings.clear()
    driver.settings.externalMetadataFallback = false
  }

  def "each discovered association-group preference is parsed independently"() {
    given:
    driver.settings.associationGroup1 = '01'
    driver.settings.associationGroup2 = '0A, 14 0C'
    driver.settings.associationGroup3 = ''

    when:
    Map result = driver.readDesiredAssociationsFromSettings(3)

    then:
    result.errors == []
    result.associations == [
      '1': [1],
      '2': [10, 12, 20],
      '3': []
    ]
  }

  def "group preferences report invalid node values and treat blank groups as empty"() {
    given:
    driver.settings.associationGroup1 = '01'
    driver.settings.associationGroup2 = 'E9'

    when:
    Map result = driver.readDesiredAssociationsFromSettings(3)

    then:
    result.associations == ['1': [1], '2': [], '3': []]
    result.errors.size() == 1
    result.errors[0].contains("Group 2 has invalid hexadecimal node ID 'E9'")
  }

  def "association status formatting is stable and human readable"() {
    expect:
    driver.formatAssociationMap(['10': [35], '2': [], '3': [11, 10]], true) ==
      'Group 2: (none); Group 3: 0A, 0B; Group 10: 23'
    driver.formatAssociationMap([:]) == 'None'
    driver.formatNodeList([]) == '(none)'
  }

  def "node parser uses Hubitat hexadecimal IDs and rejects Z-Wave LR IDs"() {
    expect:
    driver.parseHexNodeId(token) == expected

    where:
    token  || expected
    '01'   || 1
    '0x0A' || 10
    '14'   || 20
    'E8'   || 232
    '00'   || null
    'E9'   || null
  }

  def "association creation retries at 5 10 15 and 30 seconds before failing"() {
    given:
    driver.state.supportedAssociationGroups = 2
    driver.state.associationPreferencesReady = true
    driver.state.currentAssociations = ['1': [1], '2': [12]]
    driver.settings.associationGroup1 = '01'
    driver.settings.associationGroup2 = '0B'

    when: 'the initial association command is sent'
    driver.applyAssociations()
    String operationId = driver.state.operationId

    then:
    driver.state.pendingAssociations == ['2': [11]]
    driver.scheduled.last() == [5, 'retryPendingAssociations', [overwrite: true, data: [operationId: operationId, retryIndex: 0]]]

    when: 'each verification retry expires without a device report'
    driver.retryPendingAssociations([operationId: operationId, retryIndex: 0])
    driver.retryPendingAssociations([operationId: operationId, retryIndex: 1])
    driver.retryPendingAssociations([operationId: operationId, retryIndex: 2])
    driver.retryPendingAssociations([operationId: operationId, retryIndex: 3])

    then: 'the relative delays place retries at 5, 10, 15, and 30 seconds'
    driver.scheduled.findAll { List call -> call[1] == 'retryPendingAssociations' }*.getAt(0) == [5, 5, 5, 15]
    driver.scheduled.last() == [5, 'finalizeAssociationFailure', [overwrite: true, data: [operationId: operationId]]]

    when: 'the final response window expires'
    driver.finalizeAssociationFailure([operationId: operationId])

    then:
    driver.state.operationId == null
    driver.state.pendingAssociations == [:]
    driver.state.failedAssociations == ['2': [11]]
    driver.events.findAll { Map event -> event.name == 'associationStatus' }.last().value == 'failed'
    driver.events.findAll { Map event -> event.name == 'failedAssociations' }.last().value.contains('5, 10, 15, and 30 seconds')
  }

  def "exact synchronization removes nodes omitted from a group preference"() {
    given:
    driver.state.supportedAssociationGroups = 2
    driver.state.associationPreferencesReady = true
    driver.state.currentAssociations = ['1': [1], '2': [10, 11]]
    driver.settings.associationGroup1 = '01'
    driver.settings.associationGroup2 = '0B, 0C'

    when:
    driver.applyAssociations()
    List<String> commands = driver.hubCommands.last().commands

    then:
    commands.any { String command -> command.contains('associationRemove:2:[10]') }
    commands.any { String command -> command.contains('associationSet:2:[12]') }
    driver.events.findAll { Map event -> event.name == 'pendingAssociations' }.last().value ==
      'Group 2: add 0C, remove 0A'
  }

  def "refresh reports prefill the matching group preference"() {
    given:
    driver.state.refreshInProgress = true
    driver.state.refreshId = 'refresh-1'
    driver.state.refreshGroupCountReceived = true
    driver.state.refreshPendingGroups = ['2']
    driver.state.refreshReceivedGroups = []
    driver.state.currentAssociations = [:]
    AssociationReport report = new AssociationReport(
      groupingIdentifier: (short) 2,
      maxNodesSupported: (short) 5,
      reportsToFollow: (short) 0,
      nodeId: [(short) 10, (short) 12]
    )

    when:
    driver.zwaveEvent(report)

    then:
    driver.device.updatedSettings.associationGroup2 == '0A, 0C'
    driver.state.associationPreferencesReady != true
    driver.scheduled.last() == [2, 'finishRefreshAfterMetadataGrace', [overwrite: true, data: [refreshId: 'refresh-1']]]

    when:
    driver.finishRefreshAfterMetadataGrace([refreshId: 'refresh-1'])

    then:
    driver.state.associationPreferencesReady == true
  }

  def "refresh cancels pending association work before querying the device"() {
    given:
    driver.state.pendingAssociations = ['2': [10]]
    driver.state.operationId = 'apply-1'

    when:
    driver.refresh()

    then:
    driver.state.operationId == null
    driver.state.pendingAssociations == [:]
    driver.events.findAll { Map event -> event.name == 'pendingAssociations' }.last().value == 'None'
  }

  def "Z-WaveJS commands use the typed secure-encapsulation overload when legacy format is null"() {
    given:
    driver.zwave.associationV2.nullFormats = true

    when:
    driver.refresh()

    then:
    noExceptionThrown()
    driver.hubCommands.last().commands == ['secure:associationGroupingsGet']
  }

  def "unavailable optional association-info commands cannot break membership refresh"() {
    given:
    driver.state.refreshInProgress = true
    driver.state.refreshReceivedGroups = []
    driver.zwave.associationGrpInfoV1.nullCommands = true
    AssociationGroupingsReport report = new AssociationGroupingsReport(supportedGroupings: (short) 2)

    when:
    driver.zwaveEvent(report)

    then:
    noExceptionThrown()
    driver.hubCommands.last().commands == [
      'secure:associationGet:1',
      'secure:associationGet:2'
    ]
    driver.state.refreshPendingGroups == ['1', '2']
  }

  def "Z-WaveJS refresh sends association-info queries as non-null raw commands"() {
    given:
    driver.state.zwaveJsBackend = true
    driver.state.refreshInProgress = true
    driver.state.refreshReceivedGroups = []
    AssociationGroupingsReport report = new AssociationGroupingsReport(supportedGroupings: (short) 2)

    when:
    driver.zwaveEvent(report)

    then:
    noExceptionThrown()
    driver.hubCommands.last().commands == [
      'secure:associationGet:1',
      'secure:590101',
      'secure:59050001',
      'secure:associationGet:2',
      'secure:590102',
      'secure:59050002'
    ]
  }

  def "JSON reports mark the device as using the Z-WaveJS backend"() {
    when:
    driver.parse('{"cc":133,"cmd":6,"ep":0,"values":[]}')

    then:
    driver.state.zwaveJsBackend == true
  }

  def "group names and command lists produce descriptive preference titles"() {
    given:
    driver.state.associationGroupMetadata = [
      '2': [name: 'On/Off Control', commands: ['Basic Set']],
      '3': [commands: driver.describeAssociationCommands([0x26, 0x01])]
    ]

    expect:
    driver.associationGroupPreferenceTitle(2) == 'Group 2: On/Off Control — Basic Set'
    driver.associationGroupPreferenceTitle(3) == 'Group 3: Multilevel Switch Set'
  }

  def "refresh falls back to OpenSmartHouse when AGI does not return every group name"() {
    given:
    driver.settings.externalMetadataFallback = true
    driver.device.dataValues = [
      manufacturer: '031E',
      deviceType: '000A',
      deviceId: '0001',
      firmwareVersion: '1.05'
    ]
    driver.state.refreshInProgress = true
    driver.state.refreshId = 'refresh-external'
    driver.state.refreshGroupCountReceived = true
    driver.state.refreshPendingGroups = []
    driver.state.supportedAssociationGroups = 3
    driver.state.agiNamedGroups = ['1']
    driver.state.associationGroupMetadata = ['1': [name: 'Lifeline', source: 'Device AGI']]

    when:
    driver.finishRefreshAfterMetadataGrace([refreshId: 'refresh-external'])

    then:
    driver.asyncCalls.size() == 1
    driver.asyncCalls[0].cb == 'handleOpenSmartHouseDeviceListResponse'
    driver.asyncCalls[0].params.uri.contains('manufacturer%3A031E%20000A%3A0001')
    driver.state.externalMetadataLookupInProgress == true

    when:
    driver.handleOpenSmartHouseDeviceListResponse(
      new AsyncResponse(data: '{"devices":[{"id":1346,"version_min":"0.000","version_max":"255.255"}]}'),
      driver.asyncCalls[0].data as Map
    )

    then:
    driver.asyncCalls.size() == 2
    driver.asyncCalls[1].params.uri.endsWith('device/read.php?device_id=1346')

    when:
    driver.handleOpenSmartHouseDeviceResponse(
      new AsyncResponse(data: '''{"associations":[
        {"group_id":1,"label":"Association Group 1 - Lifeline","max_nodes":5,"description":"Controller reports"},
        {"group_id":2,"label":"Association Group 2","max_nodes":5,"description":"Uses Basic command class when the paddle is pressed"},
        {"group_id":3,"label":"Multilevel","max_nodes":5,"description":"Controls dimmers"}
      ]}'''),
      driver.asyncCalls[1].data as Map
    )

    then:
    driver.state.associationPreferencesReady == true
    driver.state.associationGroupMetadata['1'].name == 'Lifeline'
    driver.state.associationGroupMetadata['1'].source == 'Device AGI'
    driver.state.associationGroupMetadata['2'].name == 'Basic'
    driver.state.associationGroupMetadata['2'].description.contains('paddle')
    driver.state.associationGroupMetadata['3'].name == 'Multilevel'
    driver.associationGroupPreferenceTitle(2) == 'Group 2: Basic'
    driver.associationGroupPreferenceDescription(2).startsWith('Purpose: Uses Basic command class')
    driver.events.findAll { Map event -> event.name == 'associationMetadataSource' }.last().value ==
      'Device AGI (1/3) + OpenSmartHouse (2 fallback)'
  }

  def "cached external metadata is reused without another HTTP request"() {
    given:
    driver.settings.externalMetadataFallback = true
    driver.device.dataValues = [manufacturer: '031E', deviceType: '000A', deviceId: '0001', firmwareVersion: '1.05']
    driver.state.refreshInProgress = true
    driver.state.refreshId = 'refresh-cache'
    driver.state.refreshGroupCountReceived = true
    driver.state.refreshPendingGroups = []
    driver.state.supportedAssociationGroups = 2
    driver.state.agiNamedGroups = ['1']
    driver.state.associationGroupMetadata = ['1': [name: 'Lifeline', source: 'Device AGI']]
    driver.state.externalAssociationMetadataCache = [
      fingerprint: '031E:000A:0001:1.05',
      groups: ['2': [name: 'Basic Set', description: 'Controls associated switches', source: 'OpenSmartHouse']]
    ]

    when:
    driver.finishRefreshAfterMetadataGrace([refreshId: 'refresh-cache'])

    then:
    driver.asyncCalls.isEmpty()
    driver.state.associationPreferencesReady == true
    driver.associationGroupPreferenceTitle(2) == 'Group 2: Basic Set'
  }

  def "external lookup failure does not prevent association refresh completion"() {
    given:
    driver.settings.externalMetadataFallback = true
    driver.device.dataValues = [manufacturer: '031E', deviceType: '000A', deviceId: '0001']
    driver.state.refreshInProgress = true
    driver.state.refreshId = 'refresh-failure'
    driver.state.refreshGroupCountReceived = true
    driver.state.refreshPendingGroups = []
    driver.state.supportedAssociationGroups = 1
    driver.state.agiNamedGroups = []
    driver.state.associationGroupMetadata = [:]

    when:
    driver.finishRefreshAfterMetadataGrace([refreshId: 'refresh-failure'])
    driver.handleOpenSmartHouseDeviceListResponse(
      new AsyncResponse(status: 503, error: true, errorMessage: 'Unavailable'),
      driver.asyncCalls[0].data as Map
    )

    then:
    driver.state.associationPreferencesReady == true
    driver.state.externalMetadataLookupInProgress == false
    driver.events.findAll { Map event -> event.name == 'associationMetadataSource' }.last().value.contains('OpenSmartHouse search failed')
  }

  def "external lookup timeout cannot turn a complete live refresh into a partial refresh"() {
    given:
    driver.state.refreshInProgress = true
    driver.state.refreshId = 'refresh-timeout'
    driver.state.refreshGroupCountReceived = true
    driver.state.refreshPendingGroups = []
    driver.state.supportedAssociationGroups = 2
    driver.state.agiNamedGroups = []
    driver.state.externalMetadataLookupInProgress = true

    when:
    driver.refreshTimedOut([refreshId: 'refresh-timeout'])

    then:
    driver.state.associationPreferencesReady == true
    driver.state.externalMetadataLookupInProgress == false
    driver.events.findAll { Map event -> event.name == 'associationStatus' }.last().value == 'refreshed'
    driver.events.findAll { Map event -> event.name == 'associationMetadataSource' }.last().value.contains('External metadata lookup timed out')
  }
}
