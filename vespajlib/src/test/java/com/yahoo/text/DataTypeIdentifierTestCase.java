// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class DataTypeIdentifierTestCase {
    @Test
    public void testDataTypeIdentifier() {
        assertEquals("", new DataTypeIdentifier("").toString());
        assertEquals("a", new DataTypeIdentifier("a").toString());
        assertEquals("_", new DataTypeIdentifier("_").toString());
        try {
            assertEquals("aB", new DataTypeIdentifier("aB").toString());
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal character 'B' of identifier 'aB'.");
        }
        try {
            assertEquals("1", new DataTypeIdentifier("1").toString());
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '1' of identifier '1'.");
        }
        assertEquals("a1", new DataTypeIdentifier("a1").toString());
        assertEquals("array<b>", DataTypeIdentifier.createArrayDataTypeIdentifier(new DataTypeIdentifier("b")).toString());
        assertEquals("weightedset<b>", DataTypeIdentifier.createWeightedSetTypeIdentifier(new DataTypeIdentifier("b"), false, false).toString());
        assertEquals("weightedset<b>;add", DataTypeIdentifier.createWeightedSetTypeIdentifier(new DataTypeIdentifier("b"), true, false).toString());
        assertEquals("weightedset<b>;remove", DataTypeIdentifier.createWeightedSetTypeIdentifier(new DataTypeIdentifier("b"), false, true).toString());
        assertEquals("weightedset<b>;add;remove", DataTypeIdentifier.createWeightedSetTypeIdentifier(new DataTypeIdentifier("b"), true, true).toString());
        assertEquals("annotationreference<b>", DataTypeIdentifier.createAnnotationReferenceDataTypeIdentifier(new DataTypeIdentifier("b")).toString());
        assertEquals("map<k,v>", DataTypeIdentifier.createMapDataTypeIdentifier(new DataTypeIdentifier("k"), new DataTypeIdentifier("v")).toString());
    }
}
