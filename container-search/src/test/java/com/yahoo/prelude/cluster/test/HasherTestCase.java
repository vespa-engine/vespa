// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster.test;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.cluster.Hasher;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Hashing/failover/whatever functionality.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public class HasherTestCase {

    @Test
    public void testEmptyHasher() {
        Hasher hasher=new Hasher();
        assertNull(hasher.select(0));
    }

    private static class MockBackend extends VespaBackEndSearcher {

        @Override
        protected Result doSearch2(Query query, QueryPacket queryPacket,
                CacheKey cacheKey, Execution execution) {
            return null;
        }

        @Override
        protected void doPartialFill(Result result, String summaryClass) {
        }
    }

    @Test
    public void testOneHasher() {
        Hasher hasher = new Hasher();
        VespaBackEndSearcher o1 = new MockBackend();
        hasher.add(o1);
        assertSame(o1, hasher.select(0));
        assertSame(o1, hasher.select(1));

        hasher.remove(o1);
        assertNull(hasher.select(0));
    }

    @Test
    public void testAddAndRemove() {
        Hasher hasher = new Hasher();
        VespaBackEndSearcher v0 = new MockBackend();
        VespaBackEndSearcher v1 = new MockBackend();
        VespaBackEndSearcher v2 = new MockBackend();
        VespaBackEndSearcher v3 = new MockBackend();
        v1.setLocalDispatching(false);
        v3.setLocalDispatching(false);
        hasher.add(v1);
        hasher.add(v0);
        assertSame(v0, hasher.select(0));
        hasher.add(v2);
        VespaBackEndSearcher tmp1 = hasher.select(0);
        assertTrue(v0 == tmp1 || v2 == tmp1);
        if (tmp1 == v0) {
            assertSame(v2, hasher.select(0));
            assertSame(v0, hasher.select(0));
        } else {
            assertSame(v0, hasher.select(0));
            assertSame(v2, hasher.select(0));
        }
        hasher.remove(v2);
        hasher.remove(v2);
        assertEquals(2, hasher.getNodeCount());
        assertSame(v0, hasher.select(0));
        hasher.remove(v0);
        assertEquals(1, hasher.getNodeCount());
        assertSame(v1, hasher.select(0));
        hasher.add(v3);
        hasher.add(v0);
        assertSame(v0, hasher.select(0));
    }

    @Test
    public void testPreferLocal() {
        Hasher hasher = new Hasher();
        VespaBackEndSearcher v0 = new MockBackend();
        VespaBackEndSearcher v1 = new MockBackend();
        VespaBackEndSearcher v2 = new MockBackend();
        v1.setLocalDispatching(false);
        v2.setLocalDispatching(false);

        hasher.add(v1);
        hasher.add(v2);
        hasher.add(v0);
        assertTrue(hasher.select(0).isLocalDispatching());

        hasher = new Hasher();
        hasher.add(v1);
        hasher.add(v0);
        hasher.add(v2);
        assertTrue(hasher.select(0).isLocalDispatching());

        hasher = new Hasher();
        hasher.add(v0);
        hasher.add(v1);
        hasher.add(v2);
        assertTrue(hasher.select(0).isLocalDispatching());

        hasher = new Hasher();
        hasher.add(v0);
        hasher.add(v1);
        hasher.add(v2);
        hasher.remove(v1);
        assertTrue(hasher.select(0).isLocalDispatching());

        hasher = new Hasher();
        hasher.add(v0);
        hasher.add(v1);
        assertTrue(hasher.select(0).isLocalDispatching());
        hasher.add(v2);
        assertTrue(hasher.select(0).isLocalDispatching());
    }

}
