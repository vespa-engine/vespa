// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class RawBucketResultNodeTestCase extends ResultNodeTest {
    @Test
    public void testEmpty() {
        RawBucketResultNode bucket = new RawBucketResultNode(new RawResultNode(new byte[]{6, 9}), new RawResultNode(new byte[]{6, 9}));
        assertTrue(bucket.empty());
        assertCorrectSerialization(bucket, new RawBucketResultNode());
    }

    @Test
    public void testRange() {
        RawBucketResultNode bucket = new RawBucketResultNode(new RawResultNode(new byte[]{6, 9}), new RawResultNode(new byte[]{9, 6}));
        assertFalse(bucket.empty());
        assertArrayEquals(new byte[]{6, 9}, bucket.getFrom());
        assertArrayEquals(new byte[]{9, 6}, bucket.getTo());
        assertCorrectSerialization(bucket, new RawBucketResultNode());
        assertTrue(dumpNode(bucket).contains("value: RawData(data = [6, 9])"));
        assertTrue(dumpNode(bucket).contains("value: RawData(data = [9, 6])"));
    }

    private RawBucketResultNode createNode(int from, int to) {
        return new RawBucketResultNode(new RawResultNode(new byte[]{(byte)from}),
                                       new RawResultNode(new byte[]{(byte)to}));
    }

    @Test
    public void testCmp() {
        assertOrder(createNode(6, 9), createNode(7, 9), createNode(8, 9));
        assertOrder(createNode(6, 7), createNode(6, 8), createNode(6, 9));
        assertOrder(createNode(6, 3), createNode(7, 2), createNode(8, 1));
        assertTrue(createNode(6, 8).onCmp(new NullResultNode()) != 0);
    }
}
