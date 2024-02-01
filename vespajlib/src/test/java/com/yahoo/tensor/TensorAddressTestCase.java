// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import static com.yahoo.tensor.TensorAddress.of;
import static com.yahoo.tensor.TensorAddress.ofLabels;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for tensor address.
 *
 * @author baldersheim
 */
public class TensorAddressTestCase {
    public static void equal(TensorAddress a, TensorAddress b) {
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.label(i), b.label(i));
            assertEquals(a.numericLabel(i), b.numericLabel(i));
        }
    }
    public static void notEqual(TensorAddress a, TensorAddress b) {
        assertNotEquals(a.hashCode(), b.hashCode()); // This might not hold, but is bad if not very rare
        assertNotEquals(a, b);
    }
    @Test
    void testStringVersusNumericAddressEquality() {
        equal(ofLabels("0"), of(0));
        equal(ofLabels("1"), of(1));
    }
    @Test
    void testInEquality() {
        notEqual(ofLabels("1"), ofLabels("2"));
        notEqual(of(1), of(2));
        notEqual(ofLabels("1"), ofLabels("01"));
        notEqual(ofLabels("0"), ofLabels("00"));
    }
    @Test
    void testDimensionsEffectsEqualityAndHash() {
        notEqual(ofLabels("1"), ofLabels("1", "1"));
        notEqual(of(1), of(1, 1));
    }
    @Test
    void testAllowNullDimension() {
        TensorAddress s1 = ofLabels("1", null, "2");
        TensorAddress s2 = ofLabels("1", "2");
        assertNotEquals(s1, s2);
        assertEquals(-1, s1.numericLabel(1));
        assertNull(s1.label(1));
    }

    private static void verifyWithLabel(int dimensions) {
        int [] indexes = new int[dimensions];
        Arrays.fill(indexes, 1);
        TensorAddress next = of(indexes);
        for (int i = 0; i < dimensions; i++) {
            indexes[i] = 3;
            assertEquals(of(indexes), next = next.withLabel(i, 3));
        }
    }
    @Test
    void testWithLabel() {
        for (int i=0; i < 10; i++) {
            verifyWithLabel(i);
        }
    }

    @Test
    void testPartialCopy() {
        var abcd = ofLabels("a", "b", "c", "d");
        int[] o_1_3_2 = {1,3,2};
        equal(ofLabels("b", "d", "c"), abcd.partialCopy(o_1_3_2));
    }

}
