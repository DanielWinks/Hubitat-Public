package dwinks.hubitat.lint

import groovy.transform.CompileStatic

@CompileStatic
enum SourceKind {
  APP, DRIVER, LIBRARY, UNKNOWN
}
