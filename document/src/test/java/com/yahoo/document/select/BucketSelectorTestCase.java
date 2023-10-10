// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

/**
  * @author Håkon Humberset
  */
public class BucketSelectorTestCase {

    @Test
    public void testExpressions() throws Exception {
        assertBucketCount("id = \"id:ns:mytype:n=123:foobar\"", 1);
        assertBucketCount("id = \"id:ns:mytype:n=123:foo*\"", 0);
        assertBucketCount("id == \"id:ns:mytype:n=123:f?oo*\"", 1);
        assertBucketCount("id =~ \"id:ns:mytype:n=123:foo*\"", 0);
        assertBucketCount("id =~ \"id:ns:mytype:n=123:foo?\"", 0);
        assertBucketCount("id.user = 123", 1);
        assertBucketCount("id.user == 123", 1);
        assertBucketCount("id.group = \"yahoo.com\"", 1);
        assertBucketCount("id.group = \"yahoo.com\" or id.user=123", 2);
        assertBucketCount("id.group = \"yahoo.com\" and id.user=123", 0);
        assertBucketCount("id.group = \"yahoo.com\" and testdoctype1.hstringval=\"Doe\"", 1);
        assertBucketCount("not id.group = \"yahoo.com\"", 0);
        assertBucketCount("id.group != \"yahoo.com\"", 0);
        assertBucketCount("id.group <= \"yahoo.com\"", 0);

        assertBucketCount("id.bucket = 0x4000000000003018", 1); // Bucket 16:12312
        assertBucketCount("id.bucket == 0x4000000000000258", 1); // Bucket 16:600

        // Check that the correct buckets is found
        assertBucket("id.bucket = 0x4000000000003018", new BucketId(16, 12312));
        assertBucket("id.bucket == 0x4000000000000258", new BucketId(16, 600));

        assertBucket("id = \"id:ns:mytype:n=123:foobar\"", new BucketId(0xe8bd6e280000007bL));
        assertBucket("id.user = 123", new BucketId(32, 123));
        assertBucket("id.group = \"yahoo.com\"", new BucketId(32, 0x035837189a1acd50L));

        // Check that overlapping works
        Set<BucketId> expected = new TreeSet<BucketId>();
        expected.add(new BucketId(32, 123));
        expected.add(new BucketId(16, 123));
        assertBuckets("id.user == 123 or id.bucket == 0x400000000000007b", expected);
    }

    @Test
    public void parenthesis_enclosed_expressions_inherit_bucket_selectors_from_children() throws Exception {
        assertBucketCount("(id == \"id:ns:mytype:n=123:foobar\")", 1);
        assertBucket("(id = \"id:ns:mytype:n=123:foobar\")", new BucketId(0xe8bd6e280000007bL));
        assertBucketCount("(id.group = \"yahoo.com\" and (testdoctype1.hstringval == \"Doe\"))", 1);
        assertBucket("(id.group = \"yahoo.com\" and (testdoctype1 and (id.namespace == 'foo')))", new BucketId(32, 0x035837189a1acd50L));
    }

    public void assertBucketCount(String expr, int count) throws Exception {
        BucketIdFactory factory = new BucketIdFactory();
        BucketSelector selector = new BucketSelector(factory);
        Set<BucketId> buckets = selector.getBucketList(expr);
        assertEquals(count, buckets == null ? 0 : buckets.size());
    }

    public void assertBucket(String expr, BucketId bucket) throws Exception {
        BucketIdFactory factory = new BucketIdFactory();
        BucketSelector selector = new BucketSelector(factory);
        Set<BucketId> buckets = selector.getBucketList(expr);
        assertEquals(1, buckets == null ? 0 : buckets.size());
        assertEquals(bucket, buckets.toArray()[0]);
    }

    public void assertBuckets(String expr, Set<BucketId> expected) throws Exception {
        BucketIdFactory factory = new BucketIdFactory();
        BucketSelector selector = new BucketSelector(factory);
        Set<BucketId> actual = new TreeSet<BucketId>(selector.getBucketList(expr));
        assertEquals(expected, actual);
    }
}
