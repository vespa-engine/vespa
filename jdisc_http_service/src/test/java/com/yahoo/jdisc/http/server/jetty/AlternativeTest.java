// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

/**
 * @author bakksjo
 */
public class AlternativeTest {
    private static final String MAN = "man";
    private static final String BEAR = "bear";
    private static final String PIG = "pig";

    @Test
    public void singleValue() {
        assertThat(
                Alternative.preferred(MAN)
                        .orElseGet(() -> BEAR),
                is(MAN));
    }

    @Test
    public void singleNull() {
        assertThat(
                Alternative.preferred(null)
                        .orElseGet(() -> BEAR),
                is(BEAR));
    }

    @Test
    public void twoValues() {
        assertThat(
                Alternative.preferred(MAN)
                        .alternatively(() -> BEAR)
                        .orElseGet(() -> PIG),
                is(MAN));
    }

    @Test
    public void oneNullOneValue() {
        assertThat(
                Alternative.preferred(null)
                        .alternatively(() -> MAN)
                        .orElseGet(() -> BEAR),
                is(MAN));
    }

    @Test
    public void twoNulls() {
        assertThat(
                Alternative.preferred(null)
                        .alternatively(() -> null)
                        .orElseGet(() -> MAN),
                is(MAN));
    }

    @Test
    public void singleNullLastResortIsNull() {
        assertThat(
                Alternative.preferred(null)
                        .orElseGet(() -> null),
                is(nullValue()));
    }

    @Test
    public void twoNullsLastResortIsNull() {
        assertThat(
                Alternative.preferred(null)
                        .alternatively(() -> null)
                        .orElseGet(() -> null),
                is(nullValue()));
    }

    @Test
    public void oneNullTwoValues() {
        assertThat(
                Alternative.preferred(null)
                        .alternatively(() -> MAN)
                        .alternatively(() -> BEAR)
                        .orElseGet(() -> PIG),
                is(MAN));
    }

    @Test
    public void equalValuesMakeEqualAlternatives() {
        assertThat(Alternative.preferred(MAN), is(equalTo(Alternative.preferred(MAN))));
        assertThat(Alternative.preferred(BEAR), is(equalTo(Alternative.preferred(BEAR))));
        assertThat(Alternative.preferred(PIG), is(equalTo(Alternative.preferred(PIG))));
        assertThat(Alternative.preferred(null), is(equalTo(Alternative.preferred(null))));
    }

    @Test
    public void equalValuesMakeEqualHashCodes() {
        assertThat(Alternative.preferred(MAN).hashCode(), is(equalTo(Alternative.preferred(MAN).hashCode())));
        assertThat(Alternative.preferred(BEAR).hashCode(), is(equalTo(Alternative.preferred(BEAR).hashCode())));
        assertThat(Alternative.preferred(PIG).hashCode(), is(equalTo(Alternative.preferred(PIG).hashCode())));
        assertThat(Alternative.preferred(null).hashCode(), is(equalTo(Alternative.preferred(null).hashCode())));
    }

    @Test
    public void unequalValuesMakeUnequalAlternatives() {
        assertThat(Alternative.preferred(MAN), is(not(equalTo(Alternative.preferred(BEAR)))));
        assertThat(Alternative.preferred(MAN), is(not(equalTo(Alternative.preferred(PIG)))));
        assertThat(Alternative.preferred(MAN), is(not(equalTo(Alternative.preferred(null)))));
        assertThat(Alternative.preferred(BEAR), is(not(equalTo(Alternative.preferred(MAN)))));
        assertThat(Alternative.preferred(BEAR), is(not(equalTo(Alternative.preferred(PIG)))));
        assertThat(Alternative.preferred(BEAR), is(not(equalTo(Alternative.preferred(null)))));
        assertThat(Alternative.preferred(PIG), is(not(equalTo(Alternative.preferred(MAN)))));
        assertThat(Alternative.preferred(PIG), is(not(equalTo(Alternative.preferred(BEAR)))));
        assertThat(Alternative.preferred(PIG), is(not(equalTo(Alternative.preferred(null)))));
        assertThat(Alternative.preferred(null), is(not(equalTo(Alternative.preferred(MAN)))));
        assertThat(Alternative.preferred(null), is(not(equalTo(Alternative.preferred(BEAR)))));
        assertThat(Alternative.preferred(null), is(not(equalTo(Alternative.preferred(PIG)))));
    }

    @Test
    public void hashValuesAreDecent() {
        final String[] animals = { MAN, BEAR, PIG, "squirrel", "aardvark", "porcupine", "sasquatch", null };
        final Set<Integer> hashCodes = Stream.of(animals)
                .map(Alternative::preferred)
                .map(Alternative::hashCode)
                .collect(Collectors.toSet());
        assertThat(hashCodes.size(), is(greaterThan(animals.length / 2)));  // A modest requirement.
    }
}
