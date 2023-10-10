// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author bratseth
 */
public class FieldTermMatchTestCase {

    private static final double delta = 0.0001;

    @Test
    public void testFieldTermMatch() {
        assertEquals(1.0, FieldTermMatch.compute("a", "a b c").get("occurrences").asDouble(), delta);
        assertEquals(0.0, FieldTermMatch.compute("a", "a b c").get("firstPosition").asDouble(), delta);

        assertEquals(3.0, FieldTermMatch.compute("a", "a a a").get("occurrences").asDouble(), delta);
        assertEquals(0.0, FieldTermMatch.compute("a", "a a a").get("firstPosition").asDouble(), delta);

        assertEquals(0.0, FieldTermMatch.compute("d", "a b c").get("occurrences").asDouble(), delta);
        assertEquals(1000000.0, FieldTermMatch.compute("d", "a b c").get("firstPosition").asDouble(), delta);

        assertEquals(0.0, FieldTermMatch.compute("d", "").get("occurrences").asDouble(), delta);
        assertEquals(1000000, FieldTermMatch.compute("d", "").get("firstPosition").asDouble(), delta);
    }

}
