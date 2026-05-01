package dwinks.hubitat.stubs

import dwinks.hubitat.lint.Preprocessor
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

/**
 * Loads a Hubitat App, Driver or Library file as a {@link HubitatScriptHarness}
 * subclass instance so tests can call the methods declared in the file.
 *
 * Two important transformations:
 *   1. {@code #include} directives are stripped (and optionally inlined from
 *      sibling library files) - the standard Groovy parser does not know what
 *      they mean.
 *   2. Top-level Hubitat script idioms (definition/preferences/metadata/
 *      mappings/library) are turned into no-ops so loading the file doesn't
 *      throw because the script doesn't have those methods at JVM compile
 *      time. The HubitatScriptHarness base class supplies the rest.
 */
class ScriptLoader {

  /** Load a single file. Includes resolved against `libraryDir` if given. */
  static HubitatScriptHarness load(File file, File libraryDir = null) {
    String src = file.text
    Preprocessor.Result pp = Preprocessor.process(src)

    StringBuilder full = new StringBuilder()
    if (libraryDir != null) {
      pp.includes.each { String fqn ->
        File libFile = findLibrary(libraryDir, fqn)
        if (libFile != null) {
          // Strip the library() and (optional) preferences-if-device blocks so
          // the inlined source isn't re-running metadata declarations that the
          // host script also has.
          String libSrc = libFile.text
          libSrc = stripLibraryDeclaration(libSrc)
          full.append(libSrc).append('\n')
        }
      }
    }
    full.append(neutralizeMetadataCalls(pp.source))

    CompilerConfiguration cc = new CompilerConfiguration()
    cc.scriptBaseClass = HubitatScriptHarness.name
    ImportCustomizer ic = new ImportCustomizer()
    ic.addStarImports('dwinks.hubitat.stubs')
    cc.addCompilationCustomizers(ic)

    GroovyShell shell = new GroovyShell(ScriptLoader.classLoader, cc)
    Script s = shell.parse(full.toString(), file.name.replaceAll(/\W/, '_') + '.groovy')
    if (!(s instanceof HubitatScriptHarness)) {
      throw new IllegalStateException("Loaded script is not a HubitatScriptHarness: ${s.class}")
    }
    s as HubitatScriptHarness
  }

  /**
   * Replace top-level definition/metadata/preferences/mappings/library blocks
   * with calls that are no-ops in our harness. The base harness already
   * provides definition/preferences/etc as no-op methods, but those
   * declarations don't accept arbitrary closures - so we wrap with `try`.
   */
  private static String neutralizeMetadataCalls(String src) {
    // The HubitatScriptHarness exposes these methods so the script can call
    // them harmlessly. Concretely we just append the no-op definitions to the
    // top of the source.
    String shim = '''
      void definition(Map args) {}
      void preferences(Closure body) {}
      void metadata(Closure body) {}
      void mappings(Closure body) {}
      void library(Map args) {}
      Object dynamicPage(Map args, Closure body) { args }
      void section(Object... a) { if (a && a.last() instanceof Closure) ((Closure) a.last()).call() }
      void page(Map args, Closure body = null) { if (body != null) body.call() }
      void input(Object... a) {}
      void paragraph(Object... a) {}
      void label(Map args = [:]) {}
      void path(String p, Closure body) { if (body != null) body.call() }
      void capability(String s) {}
      void command(Object... s) {}
      void attribute(Object... s) {}

    '''.stripIndent()
    return shim + src
  }

  private static String stripLibraryDeclaration(String src) {
    // Drop the library(...) call if present (its arguments could fail to
    // resolve once inlined, and we don't need it for behavior tests).
    src.replaceFirst(/(?ms)^\s*library\s*\([^)]*\)\s*$/, '')
  }

  private static File findLibrary(File libraryDir, String fqn) {
    String simple = fqn.contains('.') ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn
    libraryDir.listFiles().find { File f ->
      f.name.equalsIgnoreCase("${simple}.groovy")
    }
  }
}
