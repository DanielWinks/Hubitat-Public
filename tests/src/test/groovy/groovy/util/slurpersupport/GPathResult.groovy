package groovy.util.slurpersupport

/**
 * Hubitat runs Groovy 2.4.21 where GPathResult lived in
 * {@code groovy.util.slurpersupport}. Newer Groovy moved it to
 * {@code groovy.xml.slurpersupport}. This shim lets Hubitat sources that
 * import the old package compile in our test harness.
 */
class GPathResult {
  // Empty stub - Hubitat code calls .text(), .name(), iteration etc. but
  // tests that exercise XML-shaped code can supply a real GPathResult by
  // delegating to XmlSlurper from the modern Groovy distribution.
}
