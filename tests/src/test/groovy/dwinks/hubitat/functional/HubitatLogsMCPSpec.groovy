package dwinks.hubitat.functional

import dwinks.hubitat.stubs.HubitatScriptHarness
import dwinks.hubitat.stubs.ScriptLoader
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification

class HubitatLogsMCPSpec extends Specification {
  @Shared HubitatScriptHarness app

  def setupSpec() {
    app = ScriptLoader.load(new File('../Apps/HubitatLogsMCP.groovy'), null, true)
  }

  def "device filters distinguish devices from apps with the same ID"() {
    given:
    List<Map> rows = [
      [sourceType: 'app', sourceId: '42', level: 'ERROR', timestamp: '2026-01-01 00:00:00.000'],
      [sourceType: 'dev', sourceId: '42', level: 'ERROR', timestamp: '2026-01-01 00:00:00.000'],
      [sourceType: 'dev', sourceId: '43', level: 'INFO', timestamp: '2026-01-01 00:00:00.000']
    ]

    expect:
    app.filterLogEntries(rows, app.validateLogFilters([deviceId: 42, sourceType: 'dev'], true).filters) == [rows[1]]
    app.filterLogEntries(rows, app.validateLogFilters([sourceType: 'app'], false).filters) == [rows[0]]
    app.filterLogEntries(rows, app.validateLogFilters([:], true).filters) == rows
  }

  def "invalid and conflicting source filters are rejected"() {
    expect:
    app.validateLogFilters(arguments, true).error

    where:
    arguments << [[deviceId: 0], [deviceId: 'bad'], [appId: 1, deviceId: 1],
                  [appId: 1, sourceType: 'dev'], [deviceId: 1, sourceType: 'app'], [sourceType: 'other']]
  }

  def "empty search results return a valid page"() {
    when:
    Map response = app.renderLogPage([id: 'empty', entries: [], fetchedAt: System.currentTimeMillis(), rawCount: 0], 0, 25, false)
    Map body = (Map) new JsonSlurper().parseText(response.content[0].text)

    then:
    !response.isError
    body.returned == 0
    body.entries == []
    body.nextCursor == null
  }

  def "timeline combines selected app and device logs in stable chronological order"() {
    given:
    List<Map> rows = [
      [sourceType: 'dev', sourceId: '42', timestamp: '2026-01-01 12:00:02.000', message: 'later'],
      [sourceType: 'app', sourceId: '42', timestamp: '2026-01-01 12:00:01.000', message: 'app'],
      [sourceType: 'dev', sourceId: '42', timestamp: '2026-01-01 12:00:01.000', message: 'device'],
      [sourceType: 'dev', sourceId: '42', timestamp: '2026-01-01 12:00:01.1', message: 'fraction'],
      [sourceType: 'app', sourceId: '43', timestamp: '2026-01-01 12:00:01.000', message: 'other'],
      [sourceType: 'dev', sourceId: '42', timestamp: '2026-01-01 12:00:03.000', message: 'outside']
    ]

    when:
    List<Map> selected = app.selectTimelineEntries(rows, [appIds: ['42'], deviceIds: ['42']], '2026-01-01 12:00:01.000', '2026-01-01 12:00:03.000')
    Map snapshot = app.createSearchSnapshot(selected, System.currentTimeMillis(), rows.size(), false, 'timeline')
    Map first = (Map) new JsonSlurper().parseText(app.renderLogPage(snapshot, 0, 2, false).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.continueLogSearch(first.nextCursor, 2, 'timeline').content[0].text)

    then:
    first.newestFirst == false
    first.entries*.message == ['app', 'device']
    second.entries*.message == ['fraction', 'later']
    second.nextCursor == null
    app.continueLogSearch(first.nextCursor, 2).isError
  }

  def "timeline validates time bounds and source IDs"() {
    expect:
    app.diagnosticTimeline(arguments).isError

    where:
    arguments << [
      [:],
      [since: '2026-01-01 12:00:00.000', before: '2026-01-01 12:00:00.000', appIds: [1]],
      [since: '2026-02-30 12:00:00.000', before: '2026-03-01 12:00:00.000', appIds: [1]],
      [since: '2026-01-01 12:00:00.000', before: '2026-01-02 12:00:00.000'],
      [since: '2026-01-01 12:00:00.000', before: '2026-01-02 12:00:00.000', deviceIds: [-1]]
    ]
  }

  def "device detail sections preserve schedules, apps and nested device data"() {
    given:
    Map detail = [device: [id: 530, data: [serial: 'abc'], currentStates: [switch: [value: 'on']]],
                  scheduledJobs: [[handler: 'refresh']], appsUsing: [[id: 8], [id: 9]],
                  appsUsingForDialog: [[id: 8]], deviceState: [ready: true], unknownField: [future: 1], enabled: true]

    expect:
    app.selectDeviceDetailSection(detail, 'scheduledJobs').entries == [[index: 0, value: [handler: 'refresh']]]
    app.selectDeviceDetailSection(detail, 'appsUsing').entries*.value*.id == [8, 9]
    app.selectDeviceDetailSection(detail, 'data').entries == [[key: 'serial', value: 'abc']]
    app.selectDeviceDetailSection(detail, 'attributes').entries[0].value.value == 'on'
    app.selectDeviceDetailSection(detail, 'state').entries == [[key: 'ready', value: true]]
    app.selectDeviceDetailSection(detail, 'overview').entries == [[key: 'enabled', value: true]]
    app.selectDeviceDetailSection(detail, 'raw').entries.find { it.key == 'unknownField' }.value == [future: 1]
    !app.selectDeviceDetailSection(detail, 'settings').present
  }

  def "device detail lookup uses fixed numeric route and paginates a stable snapshot"() {
    given:
    List<Map> requests = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      requests.add(params)
      callback.call([status: 200, data: [device: [id: 530], scheduledJobs: [[handler: 'first'], [handler: 'second']]]])
    }

    when:
    Map first = (Map) new JsonSlurper().parseText(app.getDeviceDetails([deviceId: 530, section: 'scheduledJobs', maxEntries: 1]).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.getDeviceDetails([deviceId: 530, section: 'scheduledJobs', cursor: first.nextCursor]).content[0].text)

    then:
    requests.size() == 1
    requests[0].uri == 'http://127.0.0.1:8080/device/fullJson/530'
    first.entries[0].value.handler == 'first'
    second.entries[0].value.handler == 'second'
    second.nextCursor == null
    app.getDeviceDetails([deviceId: 531, section: 'scheduledJobs', cursor: first.nextCursor]).isError
    app.getDeviceDetails([deviceId: 530, section: 'commands', cursor: first.nextCursor]).isError
    app.getDeviceDetails([deviceId: '../530']).isError

    cleanup:
    app.metaClass = null
  }

  def "device detail lookup rejects errors and unexpected response shapes"() {
    given:
    app.metaClass.httpGet = { Map params, Closure callback -> callback.call(response) }

    expect:
    app.getDeviceDetails([deviceId: 530]).isError

    cleanup:
    app.metaClass = null

    where:
    response << [[status: 404, data: [:]], [status: 200, data: []],
                 [status: 200, data: [device: [id: 531]]]]
  }

  def "device detail pages shrink to fit and preserve remaining entries"() {
    given:
    List<Map> entries = [[key: 'first', value: 'a' * 40000], [key: 'second', value: 'b' * 40000]]
    Map snapshot = app.createSearchSnapshot(entries, System.currentTimeMillis(), 0, false, 'deviceDetails|530|data|object|true')

    when:
    Map first = (Map) new JsonSlurper().parseText(app.renderDeviceDetailPage(snapshot, 0, 25).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.continueDeviceDetails(first.nextCursor, '530', 'data', 25).content[0].text)

    then:
    first.returned == 1
    first.entries[0].key == 'first'
    second.entries[0].key == 'second'
    second.nextCursor == null
    app.renderDeviceDetailPage([id: 'large', kind: 'deviceDetails|530|data|object|true', entries: [[key: 'x', value: 'x' * 70000]]], 0, 1).isError
  }

  def "runtime diagnostics reads selected logs json sections and paginates rows"() {
    given:
    List<Map> requests = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      requests.add(params)
      callback.call([status: 200, data: [uptime: 42, appStats: [[id: 1, name: 'Rule', password: 'hidden'], [id: 2, name: 'Other']], deviceStats: ["530": [name: 'Arc']], jobs: [[name: 'job']], runningJobs: []]])
    }

    when:
    Map first = (Map) new JsonSlurper().parseText(app.getRuntimeDiagnostics([section: 'appStats', maxEntries: 1]).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.getRuntimeDiagnostics([section: 'appStats', cursor: first.nextCursor]).content[0].text)
    Map summary = (Map) new JsonSlurper().parseText(app.getRuntimeDiagnostics([:]).content[0].text)

    then:
    requests*.uri == ['http://127.0.0.1:8080/logs/json', 'http://127.0.0.1:8080/logs/json']
    first.entries[0].value.password == null
    first.entries[0].value.id == 1
    second.entries[0].value.id == 2
    second.nextCursor == null
    summary.summary.uptime == 42
    app.getRuntimeDiagnostics([section: 'notASection']).isError

    cleanup:
    app.metaClass = null
  }

  def "app diagnostics returns subscriptions and jobs while excluding settings and app state"() {
    given:
    List<Map> requests = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      requests.add(params)
      callback.call([status: 200, data: [app: [id: 331, label: 'Morning Rule'], eventSubscriptions: [[deviceId: 530, attribute: 'switch', accessToken: 'secret']], scheduledJobs: [[handler: 'runRule']], appSettings: [apiToken: 'hidden'], appState: [secret: 'hidden']]])
    }

    when:
    Map first = (Map) new JsonSlurper().parseText(app.getAppDiagnostics([appId: 331, maxEntries: 1]).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.getAppDiagnostics([appId: 331, cursor: first.nextCursor, maxEntries: 1]).content[0].text)

    then:
    requests*.uri == ['http://127.0.0.1:8080/installedapp/statusJson/331']
    first.entries[0].section == 'app'
    second.entries[0].section == 'eventSubscriptions'
    second.entries[0].value.deviceId == 530
    second.entries[0].value.accessToken == null
    !([first, second].toString().contains('appSettings'))
    app.getAppDiagnostics([appId: '../331']).isError

    cleanup:
    app.metaClass = null
  }

  def "advertised device commands use the fixed route and preserve argument order"() {
    given:
    List<Map> requests = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      callback.call([status: 200, data: [device: [id: 530], commands: [[name: 'unmute'], [name: 'setLevel']]]])
    }
    app.metaClass.httpPost = { Map params, Closure callback ->
      requests.add(params)
      callback.call([status: 200, data: [success: true, message: null]])
    }

    when:
    Map result = (Map) new JsonSlurper().parseText(app.invokeDeviceCommand([deviceId: 530, command: 'setLevel', args: [25, 2]]).content[0].text)
    Map noArgs = app.invokeDeviceCommand([deviceId: 530, command: 'unmute'])

    then:
    result.success
    requests*.uri == ['http://127.0.0.1:8080/device/runmethod', 'http://127.0.0.1:8080/device/runmethod']
    requests[0].body == [id: 530L, method: 'setLevel', args: [25, 2]]
    requests[1].body == [id: 530L, method: 'unmute', args: []]
    requests[0].requestContentType == 'application/json'
    !noArgs.isError

    cleanup:
    app.metaClass = null
  }

  def "unadvertised and invalid commands never send a command request"() {
    given:
    List<Map> requests = []
    app.metaClass.httpGet = { Map params, Closure callback -> callback.call([status: 200, data: [device: [id: 530], commands: [[name: 'unmute']]]]) }
    app.metaClass.httpPost = { Map params, Closure callback -> requests.add(params) }

    expect:
    app.invokeDeviceCommand(arguments).isError
    requests.empty

    cleanup:
    app.metaClass = null

    where:
    arguments << [[deviceId: 530, command: 'other'], [deviceId: '../530', command: 'unmute'],
                  [deviceId: 530, command: 'unmute', args: 'bad'], [deviceId: 530, command: ''],
                  [deviceId: 530, command: 'unmute', args: (1..17).toList()]]
  }

  def "command failures distinguish rejection from uncertain execution"() {
    given:
    app.metaClass.httpGet = { Map params, Closure callback -> callback.call([status: 200, data: [device: [id: 530], commands: [[name: 'unmute']]]]) }
    app.metaClass.httpPost = { Map params, Closure callback -> callback.call([status: 200, data: response]) }

    expect:
    app.invokeDeviceCommand([deviceId: 530, command: 'unmute']).content[0].text.contains(expected)

    cleanup:
    app.metaClass = null

    where:
    response                            | expected
    [success: false, message: 'denied'] | 'denied'
    [unexpected: true]                  | 'may have executed'
  }

  def "hub reboot tool is marked destructive and requires explicit authorization"() {
    given:
    List<Map> requests = []
    app.metaClass.httpPost = { Map params, Closure callback -> requests.add(params) }

    when:
    Map definition = app.hubRebootToolDefinition()
    Map rejected = app.rebootHub([:])

    then:
    definition.name == 'hubitat_reboot_hub'
    definition.annotations.destructiveHint == true
    definition.inputSchema.required == ['allowSensitive']
    rejected.isError
    requests.empty

    cleanup:
    app.metaClass = null
  }

  def "authorized hub reboot uses the fixed endpoint without purging or rebuilding"() {
    given:
    List<Map> requests = []
    app.metaClass.httpPost = { Map params, Closure callback ->
      requests.add(params)
      callback.call([status: 200, data: 'Rebooting'])
    }

    when:
    Map result = (Map) new JsonSlurper().parseText(app.dispatchTool([
      name: 'hubitat_reboot_hub', arguments: [allowSensitive: true]
    ]).content[0].text)

    then:
    result.success
    result.outcome == 'accepted'
    requests.size() == 1
    requests[0].uri == 'http://127.0.0.1:8080/hub/reboot'
    requests[0].requestContentType == 'application/json'
    requests[0].body == [rebuildDataBase: false, purgeLogs: false]
    requests[0].followRedirects == false

    cleanup:
    app.metaClass = null
  }

  def "hub reboot transport failure is reported as uncertain without retrying"() {
    given:
    int requests = 0
    app.metaClass.httpPost = { Map params, Closure callback ->
      requests++
      throw new RuntimeException('connection closed')
    }

    when:
    Map result = (Map) new JsonSlurper().parseText(app.rebootHub([allowSensitive: true]).content[0].text)

    then:
    !result.success
    result.outcome == 'unknown'
    result.retryable == false
    result.message.contains('Check hub connectivity before retrying')
    requests == 1

    cleanup:
    app.metaClass = null
  }

  def "device inventory finds children and paginates filtered snapshots without current states"() {
    given:
    List<Map> calls = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      calls.add(params)
      callback.call([status: 200, data: [devices: [
        [data: [id: 10, name: 'Lamp', type: 'Switch', roomName: 'Office', currentStates: [[key: 'sensitive', value: 'private']]],
         children: [[data: [id: 11, name: 'Lamp Child', roomName: '', dni: 'child-id', currentStates: []], children: []]]],
        [data: [id: 12, name: 'Other', roomName: 'Kitchen'], children: []]
      ]]])
    }

    when:
    Map first = (Map) new JsonSlurper().parseText(app.getDeviceInventory([query: 'lamp', maxEntries: 1]).content[0].text)
    Map next = (Map) new JsonSlurper().parseText(app.getDeviceInventory([cursor: first.nextCursor]).content[0].text)
    Map child = (Map) new JsonSlurper().parseText(app.getDeviceInventory([deviceId: 11]).content[0].text)
    Map office = (Map) new JsonSlurper().parseText(app.getDeviceInventory([room: 'office']).content[0].text)

    then:
    calls[0].uri == 'http://127.0.0.1:8080/hub2/devicesList'
    first.totalMatches == 2
    first.devices[0].id == 10
    !first.devices[0].containsKey('currentStates')
    next.devices[0].id == 11
    next.devices[0].parentId == 10
    next.nextCursor == null
    child.devices*.id == [11]
    office.devices*.id == [10]
    calls.size() == 3

    cleanup:
    app.metaClass = null
  }

  def "device inventory reports invalid filters and malformed hub responses"() {
    given:
    app.metaClass.httpGet = { Map params, Closure callback -> callback.call([status: 200, data: [devices: 'invalid']]) }

    expect:
    app.getDeviceInventory([deviceId: '../10']).isError
    app.getDeviceInventory([query: 'x' * 121]).isError
    app.getDeviceInventory([:]).isError
    app.continueDeviceInventory('bad', 25).isError

    cleanup:
    app.metaClass = null
  }

  def "app inventory flattens nested instances and preserves snapshot pages"() {
    given:
    List<Map> calls = []
    app.metaClass.httpGet = { Map params, Closure callback ->
      calls.add(params)
      callback.call([status: 200, data: [apps: [
        [data: [id: 1, appTypeId: 10, name: 'Parent', type: 'Controller'], children: [
          [data: [id: 2, appTypeId: 11, name: 'Rule <span>(Paused)</span>', type: 'Rule'], children: [
            [data: [id: 3, appTypeId: 12, name: 'Rule Child', type: 'Rule'], children: []]
          ]]
        ]]
      ], systemAppTypes: [[id: 10, name: 'System Type']], userAppTypes: [[id: 10, name: 'User Type']],
      appCollections: [[id: 5, name: 'Lights', parentAppId: 1, appIds: [2, 3]]]]])
    }

    when:
    Map first = (Map) new JsonSlurper().parseText(app.getAppInventory([query: 'rule', maxEntries: 1]).content[0].text)
    Map second = (Map) new JsonSlurper().parseText(app.getAppInventory([cursor: first.nextCursor]).content[0].text)
    Map byId = (Map) new JsonSlurper().parseText(app.getAppInventory([appId: 3]).content[0].text)
    Map types = (Map) new JsonSlurper().parseText(app.getAppInventory([section: 'userAppTypes']).content[0].text)
    Map collections = (Map) new JsonSlurper().parseText(app.getAppInventory([section: 'appCollections']).content[0].text)

    then:
    calls[0].uri == 'http://127.0.0.1:8080/hub2/appsList'
    first.totalMatches == 2
    first.items[0].name == 'Rule (Paused)'
    first.items[0].parentId == 1
    first.items[0].hasChildren
    second.items[0].parentId == 2
    second.items[0].appTypeId == 12
    second.nextCursor == null
    byId.items*.id == [3]
    types.section == 'userAppTypes'
    types.items[0].name == 'User Type'
    collections.items[0].appIds == [2, 3]
    calls.size() == 4

    cleanup:
    app.metaClass = null
  }

  def "app inventory validates filters and handles malformed responses and cursors"() {
    given:
    app.metaClass.httpGet = { Map params, Closure callback -> callback.call([status: 200, data: [apps: 'invalid']]) }
    Map deviceSnapshot = app.createSearchSnapshot([], System.currentTimeMillis(), 0, false, 'deviceInventory')

    expect:
    app.getAppInventory([appId: 0]).isError
    app.getAppInventory([section: 'userAppTypes', appId: 1]).isError
    app.getAppInventory([section: 'unknown']).isError
    app.getAppInventory([query: 'x' * 121]).isError
    app.getAppInventory([:]).isError
    app.continueAppInventory('bad', 25).isError
    app.continueAppInventory("${deviceSnapshot.id}:0", 25).isError

    cleanup:
    app.metaClass = null
  }
}
