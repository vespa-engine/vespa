// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.hamcrest.Matcher;
import org.junit.Test;

/**
 * Tests for com.yahoo.test.Matchers.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class MatchersTestCase {

    @Test
    public final void testHasItemWithMethodObjectString() {
        @SuppressWarnings("rawtypes")
        final Matcher<Iterable> m = Matchers.hasItemWithMethod("nalle",
                "toLowerCase");
        assertEquals(
                false,
                m.matches(Arrays.asList(new Object[] { Integer.valueOf(1),
                        Character.valueOf('c'), "blbl" })));
        assertEquals(
                true,
                m.matches(Arrays.asList(new Object[] { Character.valueOf('c'),
                        "NALLE" })));
    }

}
