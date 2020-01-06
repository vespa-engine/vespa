// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.setmatcher;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SetMatcher {

  public static <T> boolean compare(Set<T> expected, Set<T> actual, Predicate<T> itemsMatch,
      Consumer<T> onMissing, Consumer<T> onExtra) {
    boolean mismatch = false;
    Set<T> missing = Sets.difference(expected, actual);
    for (T item : missing) {
      mismatch = true;
      onMissing.accept(item);
    }
    Set<T> extra = Sets.difference(actual, expected);
    for (T item : extra) {
      mismatch = true;
      onExtra.accept(item);
    }
    Set<T> both = Sets.intersection(actual, expected);
    for (T item : both) {
      if (!itemsMatch.test(item)) {
        mismatch = true;
      }
    }
    return !mismatch;
  }
}
