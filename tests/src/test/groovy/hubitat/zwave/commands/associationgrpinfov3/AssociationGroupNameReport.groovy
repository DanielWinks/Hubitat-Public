package hubitat.zwave.commands.associationgrpinfov3

import hubitat.zwave.Command

/** Minimal Association Group Name Report stub used by the test harness. */
class AssociationGroupNameReport extends Command {
  Short groupingIdentifier
  List<Short> name = []
}
