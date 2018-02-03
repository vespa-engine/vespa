/*
 * // Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 *
 */
package com.yahoo.searchdefinition;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests rank feature names.
 *
 * @author bratseth
 */
public class FeatureNamesTestCase {

    @Test
    public void testCanonicalization() {
        assertFalse(FeatureNames.canonicalizeIfValid("foo").isPresent());
        assertEquals("query(bar)", FeatureNames.canonicalize("query(bar)"));
        assertEquals("query(bar)", FeatureNames.canonicalize("query('bar')"));
        assertEquals("constant(bar)", FeatureNames.canonicalize("constant(\"bar\")"));
        assertEquals("query(\"ba.r\")", FeatureNames.canonicalize("query(ba.r)"));
        assertEquals("query(\"ba.r\")", FeatureNames.canonicalize("query('ba.r')"));
        assertEquals("attribute(\"ba.r\")", FeatureNames.canonicalize("attribute(\"ba.r\")"));
    }

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
