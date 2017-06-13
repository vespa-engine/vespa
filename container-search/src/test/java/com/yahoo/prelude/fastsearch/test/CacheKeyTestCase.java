// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;


import com.yahoo.fs4.QueryPacket;
import com.yahoo.search.Query;
import com.yahoo.prelude.fastsearch.CacheKey;


/**
 * @author  <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CacheKeyTestCase extends junit.framework.TestCase {

    public CacheKeyTestCase(String name) {
        super(name);
    }

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
}
