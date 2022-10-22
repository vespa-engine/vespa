// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.setmatcher;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

public class SetMatcher {

  public static <T> boolean compare(Set<T> expected, Set<T> actual, Predicate<T> itemsMatch,
      Consumer<T> onMissing, Consumer<T> onExtra) {
    boolean mismatch = false;
    Set<T> missing = new HashSet<>(expected);
    missing.removeIf(actual::contains);
    for (T item : missing) {
      mismatch = true;
      onMissing.accept(item);
    }
    Set<T> extra = new HashSet<>(actual);
    extra.removeIf(expected::contains);
    for (T item : extra) {
      mismatch = true;
      onExtra.accept(item);
    }
    Set<T> both = new HashSet<>(actual);
    both.removeIf(not(expected::contains));
    for (T item : both) {
      if (!itemsMatch.test(item)) {
        mismatch = true;
      }
    }
    return !mismatch;
  }
}
