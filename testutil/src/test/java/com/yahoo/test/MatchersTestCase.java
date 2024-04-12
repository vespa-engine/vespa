// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.List;

/**
 * Tests for com.yahoo.test.Matchers.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class MatchersTestCase {

    @Test
    public final void testHasItemWithMethodObjectString() {
        @SuppressWarnings("rawtypes")
        final Matcher<Iterable> m = Matchers.hasItemWithMethod("nalle", "toLowerCase");
        assertFalse(m.matches(List.of(new Object[]{1, 'c', "blbl"})));
        assertTrue(m.matches(List.of(new Object[]{'c', "NALLE"})));
    }

}
