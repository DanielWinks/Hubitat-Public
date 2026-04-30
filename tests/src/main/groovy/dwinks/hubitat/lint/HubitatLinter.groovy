package dwinks.hubitat.lint

import dwinks.hubitat.lint.reporters.ConsoleReporter
import dwinks.hubitat.lint.reporters.GitHubAnnotationReporter
import dwinks.hubitat.lint.reporters.Reporter
import dwinks.hubitat.lint.rules.CompileStaticRule
import dwinks.hubitat.lint.rules.DriverDefinitionRule
import dwinks.hubitat.lint.rules.ImportRule
import dwinks.hubitat.lint.rules.MetadataRule
import dwinks.hubitat.lint.rules.MethodReferenceRule
import dwinks.hubitat.lint.rules.Rule
import dwinks.hubitat.lint.rules.SandboxRule
import dwinks.hubitat.lint.rules.SyntaxRule
import groovy.transform.CompileStatic

import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Entry point for the Hubitat linter. Walks one or more roots, parses every
 * Groovy file, runs the rule pipeline, and reports the result.
 *
 * Usage:
 *   hubitat-lint <path> [<path>...] [--github] [--no-color] [--exclude=<glob>]
 *
 * Exit codes:
 *   0 - no errors (warnings/info allowed)
 *   1 - one or more ERROR-level violations
 *   2 - bad usage
 */
@CompileStatic
class HubitatLinter {

  static final List<String> DEFAULT_EXCLUDES = [
    '**/Bundles/**',          // generated bundle copies
    '**/build/**',
    '**/.gradle/**',
    '**/test/resources/fixtures/**'
  ]

  static void main(String[] args) {
    Options opts = parseArgs(args)
    if (opts == null) {
      System.exit(2)
      return
    }

    List<File> files = collectGroovyFiles(opts.roots, opts.excludes)
    if (files.empty) {
      System.err.println("No Groovy files found under: ${opts.roots}")
      System.exit(0)
      return
    }

    List<Rule> rules = [
      new SyntaxRule(),
      new MetadataRule(),
      ImportRule.fromResource(),
      new CompileStaticRule(),
      new MethodReferenceRule(),
      DriverDefinitionRule.fromResource(),
      new SandboxRule()
    ]

    // Two-pass: parse everything first so library symbols can be resolved
    // by rules that look at #include references in the second pass.
    Map<File, ParseResult> parsed = [:]
    ProjectIndex index = new ProjectIndex()
    files.each { File f ->
      ParseResult pr = HubitatScriptParser.parse(f)
      parsed[f] = pr
      index.register(pr)
    }

    List<Violation> all = []
    parsed.each { File f, ParseResult pr ->
      rules.each { Rule r ->
        try {
          all.addAll(r.check(pr, index))
        } catch (Exception ex) {
          all << new Violation(f.path, 0, 0, r.name, Severity.ERROR,
            "Rule threw ${ex.class.simpleName}: ${ex.message}")
        }
      }
    }

    Reporter reporter = opts.github ? new GitHubAnnotationReporter() : new ConsoleReporter(!opts.noColor)
    reporter.report(all)

    int errors = (int) all.count { Violation v -> v.severity == Severity.ERROR }
    System.exit(errors > 0 ? 1 : 0)
  }

  static List<File> collectGroovyFiles(List<String> roots, List<String> excludes) {
    List<File> out = []
    roots.each { String root ->
      Path p = Paths.get(root)
      if (!Files.exists(p)) {
        System.err.println("Path does not exist: ${root}")
        return
      }
      if (Files.isRegularFile(p)) {
        if (p.toString().endsWith('.groovy') && !isExcluded(p.toString(), excludes)) {
          out << p.toFile()
        }
        return
      }
      Stream<Path> stream = Files.walk(p, FileVisitOption.FOLLOW_LINKS)
      try {
        out.addAll(
          stream.filter { Path q -> Files.isRegularFile(q) && q.toString().endsWith('.groovy') && !isExcluded(q.toString(), excludes) }
                .map { Path q -> q.toFile() }
                .collect(Collectors.toList())
        )
      } finally {
        stream.close()
      }
    }
    out.sort { File a, File b -> a.path <=> b.path }
    out
  }

  static boolean isExcluded(String path, List<String> globs) {
    String norm = path.replace('\\', '/')
    return globs.any { String g -> matchesGlob(norm, g) }
  }

  /** Minimal glob matcher supporting `**`, `*`, and `?`. */
  static boolean matchesGlob(String path, String glob) {
    String regex = '^' + glob
      .replace('.', '\\.')
      .replace('**', '__DOUBLE_STAR__')
      .replace('*', '[^/]*')
      .replace('__DOUBLE_STAR__', '.*')
      .replace('?', '.') + '$'
    return path.matches(regex)
  }

  static Options parseArgs(String[] args) {
    Options opts = new Options()
    args.each { String a ->
      switch (a) {
        case '--github':
          opts.github = true
          break
        case '--no-color':
          opts.noColor = true
          break
        case '-h':
        case '--help':
          printUsage()
          opts = null
          return
        default:
          if (a.startsWith('--exclude=')) {
            opts.excludes << a.substring('--exclude='.length())
          } else if (a.startsWith('--')) {
            System.err.println("Unknown option: ${a}")
            printUsage()
            opts = null
          } else {
            opts.roots << a
          }
      }
    }
    if (opts != null && opts.roots.empty) {
      printUsage()
      return null
    }
    if (opts != null) {
      opts.excludes.addAll(DEFAULT_EXCLUDES)
    }
    opts
  }

  static void printUsage() {
    System.err.println('''
Usage: hubitat-lint <path> [<path>...] [options]

Options:
  --github            emit GitHub Actions annotations instead of human output
  --no-color          disable ANSI colors in console output
  --exclude=<glob>    exclude files matching glob (repeatable)
  -h, --help          show this help

Paths can be files or directories; directories are walked recursively for
*.groovy files. The Bundles/ tree is excluded by default.
'''.stripIndent())
  }

  @groovy.transform.PackageScope
  static class Options {
    List<String> roots = []
    List<String> excludes = []
    boolean github
    boolean noColor
  }
}
