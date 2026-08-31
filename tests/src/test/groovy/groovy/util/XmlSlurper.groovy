package groovy.util

import groovy.util.slurpersupport.GPathResult

/** Groovy 2.4 package-compatibility wrapper backed by Groovy 4 XmlSlurper. */
class XmlSlurper {
  private final groovy.xml.XmlSlurper delegate = new groovy.xml.XmlSlurper()

  GPathResult parseText(String xml) {
    new GPathResult(delegate.parseText(xml))
  }
}
