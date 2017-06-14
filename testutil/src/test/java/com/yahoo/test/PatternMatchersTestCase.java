// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

/**
 * Check CollectionPatternMatcher, LinePatternMatcher and PatternMatcher.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class PatternMatchersTestCase {

    @Test
    public final void testCollections() {
        CollectionPatternMatcher cm = new CollectionPatternMatcher("a.*");
        String[] coll = new String[] {};
        assertEquals(false, cm.matches(Arrays.asList(coll)));
        coll = new String[] { "ba", "ab" };
        assertEquals(true, cm.matches(Arrays.asList(coll)));
    }

    @Test
    public final void testLines() {
        LinePatternMatcher lp = new LinePatternMatcher("a");
        assertEquals(true, lp.matches("a\nab"));
        assertEquals(false, lp.matches("ab\nb"));
    }

    @Test
    public final void testPatterns() {
        PatternMatcher m = new PatternMatcher(".*a.*");
        assertEquals(true, m.matches("ab"));
        assertEquals(false, m.matches("b"));
    }

}
