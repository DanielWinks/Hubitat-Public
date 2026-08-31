package groovy.util.slurpersupport

/**
 * Hubitat runs Groovy 2.4.21 where GPathResult lived in
 * {@code groovy.util.slurpersupport}. Newer Groovy moved it to
 * {@code groovy.xml.slurpersupport}. This shim lets Hubitat sources that
 * import the old package compile in our test harness.
 */
class GPathResult implements Iterable<GPathResult> {
  final groovy.xml.slurpersupport.GPathResult delegate

  GPathResult(groovy.xml.slurpersupport.GPathResult delegate) {
    this.delegate = delegate
  }

  GPathResult getAt(String name) {
    wrap(delegate.getAt(name))
  }

  GPathResult children() {
    wrap(delegate.children())
  }

  GPathResult parent() {
    wrap(delegate.parent())
  }

  String text() { delegate.text() }
  String name() { delegate.name() }
  int size() { delegate.size() }
  boolean isEmpty() { delegate.isEmpty() }

  GPathResult find(Closure predicate) {
    for(groovy.xml.slurpersupport.GPathResult item : delegate) {
      GPathResult wrapped = wrap(item)
      if(predicate.call(wrapped)) { return wrapped }
    }
    return null
  }

  void each(Closure consumer) {
    for(groovy.xml.slurpersupport.GPathResult item : delegate) {
      consumer.call(wrap(item))
    }
  }

  @Override
  Iterator<GPathResult> iterator() {
    Iterator source = delegate.iterator()
    return new Iterator<GPathResult>() {
      boolean hasNext() { source.hasNext() }
      GPathResult next() { wrap((groovy.xml.slurpersupport.GPathResult)source.next()) }
    }
  }

  @Override
  String toString() { delegate.toString() }

  private static GPathResult wrap(Object value) {
    value == null ? null : new GPathResult((groovy.xml.slurpersupport.GPathResult)value)
  }
}
