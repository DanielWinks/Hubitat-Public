package dwinks.hubitat.lint.rules

import dwinks.hubitat.lint.AstUtil
import dwinks.hubitat.lint.ParseResult
import dwinks.hubitat.lint.Severity
import dwinks.hubitat.lint.SourceKind
import dwinks.hubitat.lint.Violation
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit

import java.lang.reflect.Modifier

/**
 * Parent/child concurrency rules for the multi-threaded Sonos Advanced app and drivers
 * (apps and drivers declared {@code singleThreaded: false}).
 *
 * <ul>
 *   <li><b>SynchronizedModifier</b> - a {@code synchronized} method modifier locks only the
 *       per-execution script instance on Hubitat, so it excludes nothing. Use a
 *       {@code synchronized(<static object>)} block. Applies to every file.</li>
 *   <li><b>ChildEntryPurity</b> - every method named in the file's {@code CHILD_ENTRY_POINTS} list is called
 *       by a child device; it may read and write app data but must not reach a child write
 *       ({@code sendEvent}, {@code updateDataValue}, driver commands on a child) through the local call graph.
 *       Work that writes a child belongs in a {@code runInMillis} callback, which the call graph does not
 *       follow because its target is a string.</li>
 *   <li><b>AppDataAccess</b> - keys listed in {@code APP_MEMORY_KEYS} or {@code ATOMIC_MIGRATED_STATE_KEYS}
 *       are never read or written through {@code state}.</li>
 *   <li><b>DriverParentHop</b> - a driver must not {@code runInMillis} a method whose only job is to call
 *       {@code parent.*}; the call is direct and the app entry point returns promptly.</li>
 * </ul>
 */
@CompileStatic
class ConcurrencyRule implements Rule {

  static final Set<String> CHILD_RECEIVERS = ([
    'child', 'player', 'coordinator', 'follower', 'source', 'groupDevice', 'playerDevice',
    'sourcePlayer', 'targetPlayer', 'leader'
  ] as Set<String>)

  /** Calls on a child wrapper that only read. */
  static final Set<String> CHILD_READS = ([
    'getDataValue', 'currentValue', 'getDeviceNetworkId', 'getLabel', 'getName', 'getId', 'getDisplayName',
    'getCurrentValue', 'getSetting', 'hasAudioClipCapability', 'getDeviceId', 'getIdAsLong', 'toString',
    'hashCode', 'equals', 'getProperties', 'getData', 'getSupportedAttributes', 'getSupportedCommands',
    'getCapabilities', 'hasCapability', 'getTypeName', 'getStatus', 'isDisabled', 'getDeviceType'
  ] as Set<String>)

  static final Set<String> CHILD_WRITE_FREE_CALLS = ([
    'sendEvent', 'updateDataValue', 'removeDataValue', 'deleteCurrentState'
  ] as Set<String>)

  String getName() { 'Concurrency' }

  List<Violation> check(ParseResult pr) {
    if (pr.module == null || pr.kind == SourceKind.LIBRARY) { return [] }
    List<Violation> out = []
    ClassNode script = pr.module.classes.find { ClassNode c -> c.isScript() } ?: (pr.module.classes ? pr.module.classes[0] : null)
    if (script == null) { return out }
    pr.module.classes.each { ClassNode c ->
      c.methods.each { MethodNode m ->
        if ((m.modifiers & Modifier.SYNCHRONIZED) != 0 && m.lineNumber > 0 && !m.isSynthetic()) {
          out << new Violation(pr.filePath, m.lineNumber, 0, name, Severity.ERROR,
            "SynchronizedModifier: '${m.name}' uses the synchronized method modifier, which excludes nothing on Hubitat. " +
            "Use synchronized(<static lock object>) around the shared data.")
        }
      }
    }
    Map<String, Expression> fields = fieldInits(pr)
    Map<String, String> constants = constantStrings(fields)
    out.addAll(checkEntryPoints(pr, script, fields))
    out.addAll(checkAppDataAccess(pr, script, fields, constants))
    if (pr.kind == SourceKind.DRIVER) { out.addAll(checkParentHops(pr, script)) }
    out
  }

  // ---------------------------------------------------------------- helpers
  /**
   * Top-level {@code @Field} initializers. At the compile phase the linter parses to, the @Field transformation has not
   * run, so the declarations are DeclarationExpressions in the script's statement block (see ProjectIndex.collectFields).
   */
  static Map<String, Expression> fieldInits(ParseResult pr) {
    Map<String, Expression> out = [:]
    if (pr.module?.statementBlock == null) { return out }
    pr.module.statementBlock.statements.each { Statement st ->
      if (!(st instanceof ExpressionStatement)) { return }
      Expression e = ((ExpressionStatement) st).expression
      if (e instanceof DeclarationExpression && ((DeclarationExpression) e).leftExpression instanceof VariableExpression) {
        out[((VariableExpression) ((DeclarationExpression) e).leftExpression).name] = ((DeclarationExpression) e).rightExpression
      }
    }
    out
  }

  static Map<String, String> constantStrings(Map<String, Expression> fields) {
    Map<String, String> out = [:]
    fields.each { String n, Expression init ->
      if (init instanceof ConstantExpression && ((ConstantExpression) init).value instanceof String) {
        out[n] = (String) ((ConstantExpression) init).value
      }
    }
    out
  }

  static List<Expression> listField(Map<String, Expression> fields, String fieldName) {
    Expression init = fields[fieldName]
    init instanceof ListExpression ? ((ListExpression) init).expressions : []
  }

  static List<String> namesIn(List<Expression> items, Map<String, String> constants, boolean values) {
    List<String> out = []
    items.each { Expression e ->
      if (e instanceof VariableExpression) {
        String n = ((VariableExpression) e).name
        out << (values ? constants[n] : n)
      } else if (e instanceof ConstantExpression) {
        out << ((ConstantExpression) e).value?.toString()
      }
    }
    out.findAll { String s -> s != null }
  }

  @CompileStatic
  static class CallCollector extends ClassCodeVisitorSupport {
    final List<MethodCallExpression> calls = []
    final List<Expression> expressions = []
    @Override protected SourceUnit getSourceUnit() { null }
    @Override void visitMethodCallExpression(MethodCallExpression call) {
      calls << call
      super.visitMethodCallExpression(call)
    }
    @Override void visitPropertyExpression(PropertyExpression expression) {
      expressions << expression
      super.visitPropertyExpression(expression)
    }
    @Override void visitBinaryExpression(BinaryExpression expression) {
      expressions << expression
      super.visitBinaryExpression(expression)
    }
  }

  static CallCollector collect(MethodNode m) {
    CallCollector c = new CallCollector()
    if (m.code != null) { m.code.visit(c) }
    c
  }

  static String receiverName(MethodCallExpression call) {
    Expression o = call.objectExpression
    o instanceof VariableExpression ? ((VariableExpression) o).name : null
  }

  // ------------------------------------------------------- ChildEntryPurity
  List<Violation> checkEntryPoints(ParseResult pr, ClassNode script, Map<String, Expression> fields) {
    List<Violation> out = []
    List<Expression> entryExprs = listField(fields, 'CHILD_ENTRY_POINTS')
    if (entryExprs.isEmpty()) { return out }
    List<String> entries = namesIn(entryExprs, [:], false)
    Map<String, MethodNode> methods = [:]
    script.methods.each { MethodNode m -> if (!methods.containsKey(m.name)) { methods[m.name] = m } }
    Map<String, CallCollector> bodies = [:]
    methods.each { String n, MethodNode m -> bodies[n] = collect(m) }

    entries.each { String entry ->
      if (!methods.containsKey(entry)) {
        out << new Violation(pr.filePath, 0, 0, name, Severity.ERROR,
          "ChildEntryPurity: CHILD_ENTRY_POINTS lists '${entry}' but no such method exists.")
        return
      }
      Set<String> seen = [] as Set<String>
      List<String> queue = [entry]
      Map<String, String> parentOf = [:]
      while (!queue.isEmpty()) {
        String current = queue.remove(0)
        if (!seen.add(current)) { continue }
        CallCollector body = bodies[current]
        if (body == null) { continue }
        body.calls.each { MethodCallExpression call ->
          String callee = AstUtil.methodName(call)
          if (callee == null) { return }
          String receiver = receiverName(call)
          boolean localCall = AstUtil.isImplicitThis(call) || call.implicitThis
          String why = childWriteReason(call, callee, receiver, localCall)
          if (why != null) {
            String path = pathTo(current, parentOf, entry)
            out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, Severity.ERROR,
              "ChildEntryPurity: child entry point '${entry}' reaches a child write (${why}) via ${path}. " +
              "Defer it with runInMillis(1, '<callback>', [data: ..., overwrite: false]).")
          }
          if (localCall && methods.containsKey(callee) && !seen.contains(callee) && !parentOf.containsKey(callee)) {
            parentOf[callee] = current
            queue << callee
          }
        }
      }
    }
    out
  }

  private static String pathTo(String current, Map<String, String> parentOf, String entry) {
    List<String> chain = [current]
    String cursor = current
    while (parentOf.containsKey(cursor)) {
      cursor = parentOf[cursor]
      chain.add(0, cursor)
    }
    chain.join(' -> ')
  }

  private static String childWriteReason(MethodCallExpression call, String callee, String receiver, boolean localCall) {
    if (callee == 'sendEvent') {
      int argCount = AstUtil.argList(call).size()
      if (!localCall) { return "${receiver ?: 'child'}.sendEvent" }
      if (argCount >= 2) { return 'app-level sendEvent(dni, ...)' }
    }
    if (!localCall && receiver != null && CHILD_RECEIVERS.contains(receiver)) {
      if (CHILD_WRITE_FREE_CALLS.contains(callee)) { return "${receiver}.${callee}" }
      if (!CHILD_READS.contains(callee)) { return "${receiver}.${callee}" }
    }
    if (localCall && callee in ['updateDataValue', 'removeDataValue']) { return callee }
    null
  }

  // ---------------------------------------------------------- AppDataAccess
  List<Violation> checkAppDataAccess(ParseResult pr, ClassNode script, Map<String, Expression> fields, Map<String, String> constants) {
    List<Violation> out = []
    List<String> keyNames = namesIn(listField(fields, 'APP_MEMORY_KEYS'), constants, false) +
        namesIn(listField(fields, 'ATOMIC_MIGRATED_STATE_KEYS'), constants, false)
    if (keyNames.isEmpty()) { return out }
    Set<String> classifiedVars = keyNames as Set<String>
    Set<String> classifiedValues = keyNames.collect { String n -> constants[n] }.findAll { it != null } as Set<String>

    script.methods.each { MethodNode m ->
      if (m.name == 'migrateSharedDataToAtomicState') { return }
      CallCollector body = collect(m)
      body.expressions.each { Expression e ->
        String key = null
        if (e instanceof BinaryExpression) {
          BinaryExpression b = (BinaryExpression) e
          if (b.operation.text == '[' && b.leftExpression instanceof VariableExpression &&
              ((VariableExpression) b.leftExpression).name == 'state') {
            key = keyOf(b.rightExpression, constants)
          }
        } else if (e instanceof PropertyExpression) {
          PropertyExpression p = (PropertyExpression) e
          if (p.objectExpression instanceof VariableExpression && ((VariableExpression) p.objectExpression).name == 'state') {
            key = p.propertyAsString
          }
        }
        if (key != null && (classifiedVars.contains(key) || classifiedValues.contains(key) || classifiedValues.contains(constants[key]))) {
          out << new Violation(pr.filePath, e.lineNumber, e.columnNumber, name, Severity.ERROR,
            "AppDataAccess: '${key}' is app memory or atomicState data and must not be read or written through state.")
        }
      }
      body.calls.each { MethodCallExpression call ->
        if (receiverName(call) == 'state' && AstUtil.methodName(call) == 'remove') {
          List<Expression> args = AstUtil.argList(call)
          String key = args ? keyOf(args[0], constants) : null
          if (key != null && (classifiedVars.contains(key) || classifiedValues.contains(key))) {
            out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, Severity.ERROR,
              "AppDataAccess: '${key}' is app memory or atomicState data and must not be removed through state.")
          }
        }
      }
    }
    out
  }

  private static String keyOf(Expression e, Map<String, String> constants) {
    if (e instanceof VariableExpression) { return ((VariableExpression) e).name }
    if (e instanceof ConstantExpression) { return ((ConstantExpression) e).value?.toString() }
    null
  }

  // ----------------------------------------------------------- DriverParentHop
  List<Violation> checkParentHops(ParseResult pr, ClassNode script) {
    List<Violation> out = []
    Map<String, MethodNode> methods = [:]
    script.methods.each { MethodNode m -> if (!methods.containsKey(m.name)) { methods[m.name] = m } }
    script.methods.each { MethodNode m ->
      collect(m).calls.each { MethodCallExpression call ->
        String callee = AstUtil.methodName(call)
        if (callee != 'runInMillis' && callee != 'runIn') { return }
        List<Expression> args = AstUtil.argList(call)
        if (args.size() < 2) { return }
        String target = AstUtil.constantString(args[1])
        MethodNode targetMethod = target != null ? methods[target] : null
        if (targetMethod == null) { return }
        CallCollector body = collect(targetMethod)
        int parentCalls = 0
        int otherCalls = 0
        body.calls.each { MethodCallExpression inner ->
          String innerName = AstUtil.methodName(inner)
          if (receiverName(inner) == 'parent') { parentCalls++; return }
          if (innerName != null && (innerName.startsWith('log') || innerName in ['getDeviceNetworkId', 'toString', 'get', 'put', 'containsKey'])) { return }
          otherCalls++
        }
        if (parentCalls > 0 && otherCalls == 0) {
          out << new Violation(pr.filePath, call.lineNumber, call.columnNumber, name, Severity.ERROR,
            "DriverParentHop: ${callee}(..., '${target}') only calls parent.*; call the parent directly instead of hopping.")
        }
      }
    }
    out
  }
}
