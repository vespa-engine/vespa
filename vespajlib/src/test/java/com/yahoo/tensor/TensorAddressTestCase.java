// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Test for tensor address.
 *
 * @author baldersheim
 */
public class TensorAddressTestCase {
    private void equal(Object a, Object b) {
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
    }
    private void notEqual(Object a, Object b) {
        assertNotEquals(a.hashCode(), b.hashCode()); // This might not hold, but is bad if not very rare
        assertNotEquals(a, b);
    }
    @Test
    void testStringVersusNumericAddressEquality() {
        equal(TensorAddress.ofLabels("1"), TensorAddress.of(1));
    }
    @Test
    void testInEquality() {
        notEqual(TensorAddress.ofLabels("1"), TensorAddress.ofLabels("2"));
        notEqual(TensorAddress.of(1), TensorAddress.of(2));
    }
    @Test
    void testDimensionsEffectsEqualityAndHash() {
        notEqual(TensorAddress.ofLabels("1"), TensorAddress.ofLabels("1", "1"));
        notEqual(TensorAddress.of(1), TensorAddress.of(1, 1));
    }
    @Test
    void testAllowNullDimension() {
        TensorAddress s1 = TensorAddress.ofLabels("1", null, "2");
        TensorAddress s2 = TensorAddress.ofLabels("1", "2");
        assertNotEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
