// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class FloatBucketResultNodeTestCase extends ResultNodeTest {
    @Test
    public void testEmpty() {
        final double val = 3.14;
        final FloatBucketResultNode node = createNode(val, val);
        assertTrue(node.empty());
        assertCorrectSerialization(node, new FloatBucketResultNode());
    }

    @Test
    public void testRange() {
        FloatBucketResultNode bucket = createNode(3.14, 6.9);
        assertFalse(bucket.empty());
        assertEquals(bucket.getFrom(), 3.14, 0.01);
        assertEquals(bucket.getTo(), 6.9, 0.01);
        assertCorrectSerialization(bucket, new FloatBucketResultNode());
        assertTrue(dumpNode(bucket).contains("from: 3.14"));
        assertTrue(dumpNode(bucket).contains("to: 6.9"));
    }

    private FloatBucketResultNode createNode(double from, double to) {
        return new FloatBucketResultNode(from, to);
    }

    @Test
    public void testCmp() {
        assertOrder(createNode(6, 9), createNode(7, 9), createNode(8, 9));
        assertOrder(createNode(6, 7), createNode(6, 8), createNode(6, 9));
        assertOrder(createNode(6, 3), createNode(7, 2), createNode(8, 1));
        assertTrue(createNode(6, 8).onCmp(new NullResultNode()) != 0);
    }
}
