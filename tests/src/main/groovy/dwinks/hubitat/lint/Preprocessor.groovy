package dwinks.hubitat.lint

import groovy.transform.CompileStatic
import groovy.transform.Immutable

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Strips Hubitat-specific {@code #include namespace.LibraryName} directives so
 * that the source can be fed to the standard Groovy parser. Replaces matched
 * lines with empty content so that line numbers in resulting violations match
 * the original file.
 */
@CompileStatic
class Preprocessor {

  /** Pattern that matches a single {@code #include namespace.LibraryName} directive. */
  static final Pattern INCLUDE_PATTERN = Pattern.compile(
    /^\s*#include\s+([A-Za-z_][\w.]*)\s*$/
  )

  static class Result {
    String source
    List<String> includes = []
    /** Maps zero-based line number in stripped source to original line number. */
    int[] lineMap

    boolean hasInclude(String fqn) {
      includes.contains(fqn)
    }
  }

  static Result process(String input) {
    Result r = new Result()
    String[] lines = input.split('\n', -1)
    int[] map = new int[lines.length]
    StringBuilder out = new StringBuilder(input.length())
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i]
      Matcher m = INCLUDE_PATTERN.matcher(line)
      if (m.matches()) {
        r.includes << m.group(1)
        out.append('') // blank line preserves line numbering
      } else {
        out.append(line)
      }
      if (i < lines.length - 1) {
        out.append('\n')
      }
      map[i] = i + 1
    }
    r.source = out.toString()
    r.lineMap = map
    r
  }
}
