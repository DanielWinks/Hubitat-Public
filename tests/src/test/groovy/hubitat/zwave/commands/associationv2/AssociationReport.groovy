package hubitat.zwave.commands.associationv2

import hubitat.zwave.Command

/** Minimal Association Report stub used by the test harness. */
class AssociationReport extends Command {
  Short groupingIdentifier
  Short maxNodesSupported
  Short reportsToFollow
  List<Short> nodeId = []
}
