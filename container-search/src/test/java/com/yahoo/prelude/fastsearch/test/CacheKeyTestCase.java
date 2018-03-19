// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.search.Query;
import com.yahoo.prelude.fastsearch.CacheKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Steinar Knutsen
 */
public class CacheKeyTestCase {

    @Test
    public void testHitsOffsetEquality() {
        Query a = new Query("/?query=abcd");
        QueryPacket p1 = QueryPacket.create(a);
        a.setWindow(100, 1000);
        QueryPacket p2 = QueryPacket.create(a);
        CacheKey k1 = new CacheKey(p1);
        CacheKey k2 = new CacheKey(p2);
        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
    }

    @Test
    public void testSessionKeyIgnored() {
        Query a = new Query("/?query=abcd");
        QueryPacket ap = QueryPacket.create(a);
        Query b = new Query("/?query=abcd&ranking.queryCache=true");
        QueryPacket bp = QueryPacket.create(b);
        CacheKey ak = new CacheKey(ap);
        CacheKey bk = new CacheKey(bp);
        assertEquals(ak, bk);
        assertEquals(ak.hashCode(), bk.hashCode());
        assertFalse(ap.getQueryPacketData().equals(bp.getQueryPacketData()));
    }

}
