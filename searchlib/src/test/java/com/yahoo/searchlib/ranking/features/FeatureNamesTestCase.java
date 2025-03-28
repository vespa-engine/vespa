// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import org.junit.Test;

import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests rank feature names.
 *
 * @author bratseth
 */
public class FeatureNamesTestCase {

    @Test
    public void testArgument() {
        assertFalse(FeatureNames.argumentOf("foo(bar)").isPresent());
        assertFalse(FeatureNames.argumentOf("foo(bar.baz)").isPresent());
        assertEquals("bar", FeatureNames.argumentOf("query(bar)").get());
        assertEquals("bar.baz", FeatureNames.argumentOf("query(bar.baz)").get());
        assertEquals("bar", FeatureNames.argumentOf("attribute(bar)").get());
        assertEquals("bar.baz", FeatureNames.argumentOf("attribute(bar.baz)").get());
        assertEquals("bar", FeatureNames.argumentOf("constant(bar)").get());
        assertEquals("bar.baz", FeatureNames.argumentOf("constant(bar.baz)").get());
    }

    @Test
    public void testConstantFeature() {
        assertEquals("constant(foo)",
                     FeatureNames.asConstantFeature("foo").toString());
    }

    @Test
    public void testAttributeFeature() {
        assertEquals("attribute(foo)",
                     FeatureNames.asAttributeFeature("foo").toString());
    }

    @Test
    public void testQueryFeature() {
        assertEquals("query(\"foo.bar\")",
                     FeatureNames.asQueryFeature("foo.bar").toString());
    }

    @Test
    public void testLegalFeatureNames() {
        assertFalse(FeatureNames.needsQuoting("_"));
        assertTrue(FeatureNames.needsQuoting("-"));
        assertFalse(FeatureNames.needsQuoting("_-"));
        assertFalse(FeatureNames.needsQuoting("0_-azAZxy98-_"));
        assertTrue(FeatureNames.needsQuoting("0_-azAZxy98-_+"));
    }

    /*
     * Unignore to verify performance
     * 2021/09/05 performance was a factor of 5.25
     * 'Identifier handcoded validity check took 4301ms
     *  Identifier regexp validity check took 22609ms'
     */
    // @Test
    public void benchMarkPatternMatching() {
        Pattern identifierRegexp = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");
        String[] strings = new String[1000];
        for (int i = 0; i < strings.length; i++) {
            strings[i] = i + "-legal_string" + i;
        }

        countValid(strings, 1000, "handcoded warmup", FeatureNames::needsQuoting);
        countValid(strings, 1000, "regexp warmup", (s) -> identifierRegexp.matcher(s).matches());

        countValid(strings, 100000, "handcoded", FeatureNames::needsQuoting);
        countValid(strings, 100000, "regexp", (s) -> identifierRegexp.matcher(s).matches());
    }

    private void countValid(String [] strings, int numReps, String text, Function<String, Boolean> func) {
        long start = System.nanoTime();
        int validCount = 0;
        for (int i = 0; i < numReps; i++) {
            for (String s : strings) {
                if ( ! func.apply(s)) validCount++;
            }
        }
        long end = System.nanoTime();
        assertEquals(strings.length * numReps, validCount);
        System.out.println("Identifier " + text + " validity check took " + (end - start)/1000000 + "ms");
    }

}
