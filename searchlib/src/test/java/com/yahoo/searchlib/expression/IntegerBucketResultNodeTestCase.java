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
public class IntegerBucketResultNodeTestCase extends ResultNodeTest {

    @Test
    public void testEmptyRange() {
        IntegerBucketResultNode bucket = new IntegerBucketResultNode(4, 4);
        assertTrue(bucket.empty());
        assertCorrectSerialization(bucket, new IntegerBucketResultNode());
    }

    @Test
    public void testRange() {
        IntegerBucketResultNode bucket = new IntegerBucketResultNode(4, 10);
        assertEquals(4, bucket.getFrom());
        assertEquals(10, bucket.getTo());
        assertFalse(bucket.empty());
        assertTrue(dumpNode(bucket).contains("from: 4"));
        assertTrue(dumpNode(bucket).contains("to: 10"));
        assertCorrectSerialization(bucket, new IntegerBucketResultNode());
    }

    private IntegerBucketResultNode createNode(long from, long to) {
        return new IntegerBucketResultNode(from, to);
    }

    @Test
    public void testCmp() {
        assertOrder(createNode(Long.MIN_VALUE, 3), createNode(3, 9), createNode(9, Long.MAX_VALUE));
        assertOrder(createNode(6, 9), createNode(7, 9), createNode(8, 9));
        assertOrder(createNode(6, 7), createNode(6, 8), createNode(6, 9));
        assertOrder(createNode(6, 3), createNode(7, 2), createNode(8, 1));
        assertTrue(createNode(6, 8).onCmp(new NullResultNode()) != 0);
    }
}
