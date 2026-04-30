package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.SourceKind
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression

/**
 * Validates that Apps/Drivers/Libraries each carry the metadata blocks Hubitat
 * needs for upload to succeed and that the required named arguments are
 * present and well-formed.
 *
 *  Apps     - top-level definition(name:, namespace:, author:, ...) + preferences { }
 *  Drivers  - metadata { definition(name:, namespace:, author:, ...) ; preferences { } }
 *  Libraries - top-level library(name:, namespace:, author:, ...)
 */
@CompileStatic
class MetadataRule implements Rule {

  static final Set<String> APP_REQUIRED      = (['name', 'namespace', 'author'] as Set)
  static final Set<String> DRIVER_REQUIRED   = (['name', 'namespace', 'author'] as Set)
  static final Set<String> LIBRARY_REQUIRED  = (['name', 'namespace', 'author'] as Set)

  String getName() { 'Metadata' }

  List<Violation> check(ParseResult pr) {
    if (pr.module == null) return []
    List<Violation> v = []

    switch (pr.kind) {
      case SourceKind.APP:
        checkApp(pr, v)
        break
      case SourceKind.DRIVER:
        checkDriver(pr, v)
        break
      case SourceKind.LIBRARY:
        checkLibrary(pr, v)
        break
      case SourceKind.UNKNOWN:
        v << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
          'File does not declare definition()/metadata{}/library() at the top level - Hubitat will reject it on install.')
        break
    }
    v
  }

  private void checkApp(ParseResult pr, List<Violation> v) {
    MethodCallExpression defCall = AstUtil.findTopLevelCall(pr.module, 'definition')
    if (defCall == null) {
      v << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
        'App is missing a top-level definition(...) call.')
    } else {
      checkRequiredFields(pr.filePath, defCall, APP_REQUIRED, 'definition', v)
    }

    MethodCallExpression prefs = AstUtil.findTopLevelCall(pr.module, 'preferences')
    if (prefs == null) {
      v << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
        'App is missing a top-level preferences { ... } block.')
    } else if (AstUtil.trailingClosure(prefs) == null) {
      v << new Violation(pr.filePath, prefs.lineNumber, prefs.columnNumber, name, Severity.ERROR,
        'preferences must be followed by a closure { ... }.')
    }

    // metadata { } in an App is invalid (Hubitat treats App and Driver definitions distinctly).
    MethodCallExpression metadata = AstUtil.findTopLevelCall(pr.module, 'metadata')
    if (metadata != null) {
      v << new Violation(pr.filePath, metadata.lineNumber, metadata.columnNumber, name, Severity.ERROR,
        'metadata { ... } belongs in Drivers; Apps use top-level definition(...) and preferences { ... }.')
    }
  }

  private void checkDriver(ParseResult pr, List<Violation> v) {
    MethodCallExpression metadata = AstUtil.findTopLevelCall(pr.module, 'metadata')
    if (metadata == null) {
      v << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
        'Driver is missing a top-level metadata { ... } block.')
      return
    }

    ClosureExpression body = AstUtil.trailingClosure(metadata)
    if (body == null) {
      v << new Violation(pr.filePath, metadata.lineNumber, metadata.columnNumber, name, Severity.ERROR,
        'metadata must be followed by a closure { ... }.')
      return
    }

    List<MethodCallExpression> innerCalls = AstUtil.callsInClosure(body)
    MethodCallExpression defCall = innerCalls.find { AstUtil.methodName(it) == 'definition' }
    if (defCall == null) {
      v << new Violation(pr.filePath, metadata.lineNumber, metadata.columnNumber, name, Severity.ERROR,
        'Driver metadata { ... } is missing a definition(...) call.')
    } else {
      checkRequiredFields(pr.filePath, defCall, DRIVER_REQUIRED, 'definition', v)
    }

    // preferences inside metadata is the canonical place; outside is also accepted by Hubitat
    boolean hasPrefsInside = innerCalls.any { AstUtil.methodName(it) == 'preferences' }
    boolean hasPrefsTop = AstUtil.findTopLevelCall(pr.module, 'preferences') != null
    if (!hasPrefsInside && !hasPrefsTop) {
      v << new Violation(pr.filePath, metadata.lineNumber, metadata.columnNumber, name, Severity.WARNING,
        'Driver has no preferences { ... } block; users will have no settings UI.')
    }
  }

  private void checkLibrary(ParseResult pr, List<Violation> v) {
    MethodCallExpression lib = AstUtil.findTopLevelCall(pr.module, 'library')
    if (lib == null) {
      v << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
        'Library is missing a top-level library(...) call.')
      return
    }
    checkRequiredFields(pr.filePath, lib, LIBRARY_REQUIRED, 'library', v)

    // Libraries cannot include other libraries - warn if #include is present
    if (!pr.preprocessed.includes.empty) {
      v << new Violation(pr.filePath, 0, 0, name, Severity.WARNING,
        "Libraries cannot transitively #include other libraries; found: ${pr.preprocessed.includes.join(', ')}")
    }
  }

  private void checkRequiredFields(String file, MethodCallExpression call, Set<String> required, String label, List<Violation> v) {
    Map<String, Expression> args = AstUtil.namedArgs(call)
    required.each { String key ->
      if (!args.containsKey(key)) {
        v << new Violation(file, call.lineNumber, call.columnNumber, name, Severity.ERROR,
          "${label}(...) is missing required field '${key}'.")
      } else {
        String s = AstUtil.constantString(args[key])
        if (s == null || s.trim().empty) {
          v << new Violation(file, call.lineNumber, call.columnNumber, name, Severity.ERROR,
            "${label}(...) field '${key}' must be a non-empty string literal.")
        }
      }
    }
  }
}
