package hubitat.zwave.commands.multichannelassociationv3

class MultiChannelAssociationReport {
  Integer groupingIdentifier
  Integer maxNodesSupported
  Integer reportsToFollow
  List<Integer> nodeId = []
  List<Integer> multiChannelNodeId = []
}
