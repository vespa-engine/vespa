// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class StringBucketResultNodeTestCase extends ResultNodeTest {
    @Test
    public void testEmpty() {
        StringBucketResultNode bucket = new StringBucketResultNode("aaa", "aaa");
        assertTrue(bucket.empty());
        assertCorrectSerialization(bucket, new StringBucketResultNode());
    }

    @Test
    public void testRange() {
        StringBucketResultNode bucket = new StringBucketResultNode("a", "d");
        assertEquals("a", bucket.getFrom());
        assertEquals("d", bucket.getTo());
        assertTrue(dumpNode(bucket).contains("value: 'a'"));
        assertTrue(dumpNode(bucket).contains("value: 'd'"));
        assertCorrectSerialization(bucket, new StringBucketResultNode());
    }

    @Test
    public void testCmp() {
        StringBucketResultNode b1 = new StringBucketResultNode("a", "d");
        StringBucketResultNode b2 = new StringBucketResultNode("d", "h");
        StringBucketResultNode b3 = new StringBucketResultNode("h", "u");
        assertTrue(b1.onCmp(b1) == 0);
        assertTrue(b1.onCmp(b2) < 0);
        assertTrue(b1.onCmp(b3) < 0);

        assertTrue(b2.onCmp(b1) > 0);
        assertTrue(b2.onCmp(b2) == 0);
        assertTrue(b2.onCmp(b3) < 0);

        assertTrue(b3.onCmp(b1) > 0);
        assertTrue(b3.onCmp(b2) > 0);
        assertTrue(b3.onCmp(b3) == 0);

        b2 = new StringBucketResultNode("a", "b");
        assertTrue(b1.onCmp(b2) > 0);
        b2 = new StringBucketResultNode("a", "f");
        assertTrue(b1.onCmp(b2) < 0);
        b2 = new StringBucketResultNode("k", "a");
        assertTrue(b1.onCmp(b2) < 0);
        assertTrue(b1.onCmp(new NullResultNode()) != 0);
    }
}
