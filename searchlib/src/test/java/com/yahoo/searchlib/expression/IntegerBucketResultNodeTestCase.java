// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
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
        assertThat(bucket.getFrom(), is(4l));
        assertThat(bucket.getTo(), is(10l));
        assertFalse(bucket.empty());
        assertTrue(dumpNode(bucket).contains("from: 4"));
        assertTrue(dumpNode(bucket).contains("to: 10"));
        assertCorrectSerialization(bucket, new IntegerBucketResultNode());
    }
}
