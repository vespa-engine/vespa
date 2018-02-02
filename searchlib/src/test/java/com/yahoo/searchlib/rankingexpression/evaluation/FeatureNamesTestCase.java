// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests rank feature names.
 *
 * @author bratseth
 */
public class FeatureNamesTestCase {

    @Test
    public void testCanonicalization() {
        assertEquals("foo", FeatureNames.canonicalize("foo"));
        assertEquals("foo.out", FeatureNames.canonicalize("foo.out"));
        assertEquals("foo(bar)", FeatureNames.canonicalize("foo(bar)"));
        assertEquals("foo(bar)", FeatureNames.canonicalize("foo('bar')"));
        assertEquals("foo(bar)", FeatureNames.canonicalize("foo(\"bar\")"));
        assertEquals("foo(bar).out", FeatureNames.canonicalize("foo(bar).out"));
        assertEquals("foo(bar).out", FeatureNames.canonicalize("foo('bar').out"));
        assertEquals("foo(bar).out", FeatureNames.canonicalize("foo(\"bar\").out"));
        assertEquals("foo(\"ba.r\")", FeatureNames.canonicalize("foo(ba.r)"));
        assertEquals("foo(\"ba.r\")", FeatureNames.canonicalize("foo('ba.r')"));
        assertEquals("foo(\"ba.r\")", FeatureNames.canonicalize("foo(\"ba.r\")"));
        assertEquals("foo(bar1,\"b.ar2\",\"ba/r3\").out",
                     FeatureNames.canonicalize("foo(bar1,b.ar2,ba/r3).out"));
        assertEquals("foo(bar1,\"b.ar2\",\"ba/r3\").out",
                     FeatureNames.canonicalize("foo(bar1,'b.ar2',\"ba/r3\").out"));
    }

    @Test
    public void testArgument() {
        assertEquals("bar", FeatureNames.argumentOf("foo(bar)"));
        assertEquals("bar.baz", FeatureNames.argumentOf("foo(bar.baz)"));
    }

    @Test
    public void testConstantFeature() {
        assertEquals("constant(\"foo/bar\")", FeatureNames.asConstantFeature("foo/bar"));
    }

    @Test
    public void testAttributeFeature() {
        assertEquals("attribute(foo)", FeatureNames.asAttributeFeature("foo"));
    }

    @Test
    public void testQueryFeature() {
        assertEquals("query(\"foo.bar\")", FeatureNames.asQueryFeature("foo.bar"));
    }

}
