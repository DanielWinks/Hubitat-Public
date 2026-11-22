package com.google.common.util.concurrent

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Minimal test stub for Guava's Striped (Hubitat ships Guava).
 * Only the lock(int) factory and get(Object) are needed by the
 * production drivers under test. Shards keys by hash code.
 */
abstract class Striped<L> {

  static <L> Striped<L> lock(int stripes) {
    return new FixedStripes<L>(stripes)
  }

  abstract L get(Object key)

  private static final class FixedStripes<L> extends Striped<L> {
    private final List<L> locks

    FixedStripes(int stripes) {
      int count = Math.max(1, stripes)
      List<L> created = new ArrayList<L>(count)
      for (int i = 0; i < count; i++) {
        created.add(new ReentrantLock() as L)
      }
      locks = created
    }

    @Override
    @SuppressWarnings('rawtypes')
    L get(Object key) {
      int index = (key == null ? 0 : (key.hashCode() & Integer.MAX_VALUE)) % locks.size()
      return locks[index]
    }
  }
}
