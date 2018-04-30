// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features.fieldmatch;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The "semantic distance" refers to the non-continuous distance from a token
 * to the next token used by the string match metrics algorithm. This class
 * tests invariants which must hold for any such distance metric as well as specifics
 * for the currently used distance metric.
 *
 * @author bratseth
 */
public class SemanticDistanceTestCase {

    FieldMatchMetricsComputer c;

    @Before
    public void setUp() {
        c=new FieldMatchMetricsComputer();
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < 150; i++)
            field.append("t" + i + " ");
        c.compute("query", field.toString()); // Just to set the field value
    }

    /** Must be true using any semantic distance function */
    @Test
    public void testBothWayConversionProducesOriginalValue() {
        assertBothWayConversionProducesOriginalValue(50);
        assertBothWayConversionProducesOriginalValue(10);
        assertBothWayConversionProducesOriginalValue(5);
        assertBothWayConversionProducesOriginalValue(0);
        assertBothWayConversionProducesOriginalValue(140);
        assertBothWayConversionProducesOriginalValue(145);
        assertBothWayConversionProducesOriginalValue(149);
    }

    /** Must be true using any semantic distance function */
    @Test
    public void testFunctionsAreOneToOne() {
        assertFunctionsAreOneToOne(50);
        assertFunctionsAreOneToOne(10);
        assertFunctionsAreOneToOne(5);
        assertFunctionsAreOneToOne(0);
        assertFunctionsAreOneToOne(140);
        assertFunctionsAreOneToOne(145);
        assertFunctionsAreOneToOne(149);
    }

    /** Specific to this particular distance function */
    @Test
    public void testFunction() {
        int zeroJ = 50;
        assertFunction(50,0, zeroJ);
        assertFunction(59,9, zeroJ);
        assertFunction(49,10, zeroJ);
        assertFunction(40,19, zeroJ);
        assertFunction(60,20, zeroJ);
        assertFunction(149,109, zeroJ);
        assertFunction(39,110, zeroJ);
        assertFunction(0,149, zeroJ);

        zeroJ = 0;
        assertFunction(0,0, zeroJ);
        assertFunction(10,10, zeroJ);
        assertFunction(20,20, zeroJ);
        assertFunction(149,149, zeroJ);

        zeroJ = 5;
        assertFunction(5,0, zeroJ);
        assertFunction(10,5, zeroJ);
        assertFunction(14,9, zeroJ);
        assertFunction(4,10, zeroJ);
        assertFunction(0,14, zeroJ);
        assertFunction(15,15, zeroJ);
        assertFunction(25,25, zeroJ);
        assertFunction(149,149, zeroJ);

        zeroJ = 149;
        assertFunction(149,0, zeroJ);
        assertFunction(140,9, zeroJ);
        assertFunction(130,19, zeroJ);
        assertFunction(0,149, zeroJ);

        zeroJ = 145;
        assertFunction(145,0, zeroJ);
        assertFunction(149,4, zeroJ);
        assertFunction(144,5, zeroJ);
        assertFunction(140,9, zeroJ);
        assertFunction(135,14, zeroJ);
        assertFunction(125,24, zeroJ);
        assertFunction(0,149, zeroJ);
    }

    /** Hits both limits at once */
    @Test
    public void testSmallField() {
        c = new FieldMatchMetricsComputer();
        c.compute("query","my field value four"); // Just to set the field value
        assertBothWayConversionProducesOriginalValue(2);
        assertBothWayConversionProducesOriginalValue(0);
        assertBothWayConversionProducesOriginalValue(3);
        assertFunctionsAreOneToOne(2);
        assertFunctionsAreOneToOne(0);
        assertFunctionsAreOneToOne(3);

        int zeroJ=2;
        assertFunction(2,0, zeroJ);
        assertFunction(3,1, zeroJ);
        assertFunction(1,2, zeroJ);
        assertFunction(0,3, zeroJ);
    }

    private void assertBothWayConversionProducesOriginalValue(int zeroJ) {
        // Starting point in the middle
        for (int j = 0; j < c.getField().terms().size(); j++) {
            int semanticDistance=c.fieldIndexToSemanticDistance(j, zeroJ);
            assertTrue("Using zeroJ=" + zeroJ + ": " + semanticDistance +">=0", semanticDistance >= 0);
            int backConvertedJ=c.semanticDistanceToFieldIndex(semanticDistance, zeroJ);
            assertEquals("Using zeroJ=" + zeroJ + ": " + j + "->" + semanticDistance + "->" + backConvertedJ,j, backConvertedJ);
        }
    }

    private void assertFunctionsAreOneToOne(int zeroJ) {
        Set<Integer> distances = new HashSet<>();
        for (int j = 0; j < c.getField().terms().size(); j++) {
            int semanticDistance = c.fieldIndexToSemanticDistance(j,zeroJ);
            assertTrue("Using zeroJ=" + zeroJ + ": " + j + "->" + semanticDistance + " is unique",
                       ! distances.contains(semanticDistance));
            distances.add(semanticDistance);
        }
    }

    private void assertFunction(int j,int semanticDistance, int zeroJ) {
        assertEquals(j + "->" + semanticDistance,semanticDistance, c.fieldIndexToSemanticDistance(j,zeroJ));
    }

}
