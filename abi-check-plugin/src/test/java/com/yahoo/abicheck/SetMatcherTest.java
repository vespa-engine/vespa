// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.yahoo.abicheck.setmatcher.SetMatcher;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

public class SetMatcherTest {

  @Test
  public void testMissing() {
    Set<String> a = ImmutableSet.of("a", "b");

    @SuppressWarnings("unchecked") Consumer<String> missing = mock(Consumer.class);
    @SuppressWarnings("unchecked") Consumer<String> extra = mock(Consumer.class);
    @SuppressWarnings("unchecked") Predicate<String> itemsMatch = mock(Predicate.class);

    when(itemsMatch.test("a")).thenReturn(true);

    assertThat(SetMatcher.compare(a, Collections.singleton("a"), itemsMatch, missing, extra),
        equalTo(false));

    verify(missing, times(1)).accept("b");
    verify(extra, never()).accept(any());
    verify(itemsMatch, times(1)).test("a");
  }

  @Test
  public void testExtra() {
    Set<String> a = ImmutableSet.of("a");

    @SuppressWarnings("unchecked") Consumer<String> missing = mock(Consumer.class);
    @SuppressWarnings("unchecked") Consumer<String> extra = mock(Consumer.class);
    @SuppressWarnings("unchecked") Predicate<String> itemsMatch = mock(Predicate.class);

    when(itemsMatch.test("a")).thenReturn(true);

    assertThat(SetMatcher.compare(a, ImmutableSet.of("a", "b"), itemsMatch, missing, extra),
        equalTo(false));

    verify(missing, never()).accept(any());
    verify(extra, times(1)).accept("b");
    verify(itemsMatch, times(1)).test("a");
  }

  @Test
  public void testItemsMatch() {
    @SuppressWarnings("unchecked") Consumer<String> missing = mock(Consumer.class);
    @SuppressWarnings("unchecked") Consumer<String> extra = mock(Consumer.class);
    @SuppressWarnings("unchecked") Predicate<String> itemsMatch = mock(Predicate.class);

    when(itemsMatch.test("a")).thenReturn(false);

    assertThat(SetMatcher
        .compare(Collections.singleton("a"), Collections.singleton("a"), itemsMatch, missing,
            extra), equalTo(false));

    verify(itemsMatch, times(1)).test("a");
  }

  @Test
  public void testCompleteMatch() {
    @SuppressWarnings("unchecked") Consumer<String> missing = mock(Consumer.class);
    @SuppressWarnings("unchecked") Consumer<String> extra = mock(Consumer.class);
    @SuppressWarnings("unchecked") Predicate<String> itemsMatch = mock(Predicate.class);

    when(itemsMatch.test("a")).thenReturn(true);

    assertThat(SetMatcher
        .compare(Collections.singleton("a"), Collections.singleton("a"), itemsMatch, missing,
            extra), equalTo(true));

    verify(missing, never()).accept(any());
    verify(extra, never()).accept(any());
    verify(itemsMatch, times(1)).test("a");
  }
}
